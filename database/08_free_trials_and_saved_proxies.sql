-- Free one-time claims, paid-plan trials, and cross-device saved proxy profiles.
ALTER TABLE public.subscription_plans
  ADD COLUMN IF NOT EXISTS free_claim_once BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS free_duration_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.subscription_plans
  DROP CONSTRAINT IF EXISTS subscription_plans_free_duration_minutes_check;
ALTER TABLE public.subscription_plans
  ADD CONSTRAINT subscription_plans_free_duration_minutes_check CHECK (free_duration_minutes >= 0);

CREATE TABLE IF NOT EXISTS public.subscription_plan_claims (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  plan_id UUID NOT NULL REFERENCES public.subscription_plans(id) ON DELETE CASCADE,
  subscription_id UUID REFERENCES public.subscriptions(id) ON DELETE SET NULL,
  claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, plan_id)
);
CREATE INDEX IF NOT EXISTS idx_subscription_plan_claims_user ON public.subscription_plan_claims(user_id, claimed_at DESC);
ALTER TABLE public.subscription_plan_claims ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view own plan claims" ON public.subscription_plan_claims;
CREATE POLICY "Users can view own plan claims" ON public.subscription_plan_claims FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));

CREATE TABLE IF NOT EXISTS public.user_saved_proxies (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  protocol VARCHAR(20) NOT NULL CHECK (protocol IN ('http', 'socks5')),
  host VARCHAR(255) NOT NULL,
  port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
  auth_required BOOLEAN NOT NULL DEFAULT FALSE,
  username_encrypted TEXT NOT NULL DEFAULT '',
  password_encrypted TEXT NOT NULL DEFAULT '',
  is_selected BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_saved_proxies_user_updated ON public.user_saved_proxies(user_id, updated_at DESC);
ALTER TABLE public.user_saved_proxies ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view own saved proxies" ON public.user_saved_proxies;
CREATE POLICY "Users can view own saved proxies" ON public.user_saved_proxies FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.purchase_subscription_plan(p_user_id UUID, p_plan_id UUID)
RETURNS TABLE(success BOOLEAN, message TEXT, subscription_id UUID, new_balance NUMERIC, free_activation BOOLEAN, trial_minutes INTEGER)
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  plan public.subscription_plans;
  wallet public.wallets;
  sub_id UUID;
  balance NUMERIC;
  is_free_claim BOOLEAN := FALSE;
  duration_minutes INTEGER;
BEGIN
  SELECT * INTO plan FROM public.subscription_plans WHERE id = p_plan_id AND is_active = TRUE FOR UPDATE;
  IF NOT FOUND THEN
    RETURN QUERY SELECT FALSE, 'Subscription plan not found'::TEXT, NULL::UUID, NULL::NUMERIC, FALSE, 0;
    RETURN;
  END IF;

  IF plan.free_claim_once THEN
    SELECT EXISTS (SELECT 1 FROM public.subscription_plan_claims WHERE user_id = p_user_id AND plan_id = p_plan_id) INTO is_free_claim;
    IF NOT is_free_claim THEN
      duration_minutes := CASE WHEN plan.free_duration_minutes > 0 THEN plan.free_duration_minutes ELSE plan.duration_days * 1440 END;
      INSERT INTO public.subscriptions(user_id, product_id, subscription_plan_id, status, duration_type, started_at, expires_at, auto_renew, will_renew)
      VALUES (p_user_id, NULL, p_plan_id, 'active', 'trial', NOW(), NOW() + make_interval(mins => duration_minutes), FALSE, FALSE)
      RETURNING id INTO sub_id;
      INSERT INTO public.subscription_plan_claims(user_id, plan_id, subscription_id) VALUES (p_user_id, p_plan_id, sub_id);
      INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
      SELECT balance INTO balance FROM public.wallets WHERE user_id = p_user_id;
      RETURN QUERY SELECT TRUE, 'Free access activated'::TEXT, sub_id, balance, TRUE, duration_minutes;
      RETURN;
    ELSIF plan.price = 0 THEN
      INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
      SELECT balance INTO balance FROM public.wallets WHERE user_id = p_user_id;
      RETURN QUERY SELECT FALSE, 'This free plan has already been claimed'::TEXT, NULL::UUID, balance, FALSE, 0;
      RETURN;
    END IF;
  END IF;

  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO wallet FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
  IF wallet.balance < plan.price THEN
    RETURN QUERY SELECT FALSE, 'Insufficient wallet balance'::TEXT, NULL::UUID, wallet.balance, FALSE, 0;
    RETURN;
  END IF;
  INSERT INTO public.subscriptions(user_id, product_id, subscription_plan_id, status, duration_type, started_at, expires_at, auto_renew, will_renew)
  VALUES (p_user_id, NULL, p_plan_id, 'active', 'custom', NOW(), NOW() + make_interval(days => plan.duration_days), FALSE, FALSE)
  RETURNING id INTO sub_id;
  UPDATE public.wallets SET balance = balance - plan.price, updated_at = NOW() WHERE id = wallet.id RETURNING balance INTO balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id)
  VALUES (p_user_id, -plan.price, balance, 'plan_purchase', 'Subscription plan purchase', sub_id);
  RETURN QUERY SELECT TRUE, 'Subscription plan activated'::TEXT, sub_id, balance, FALSE, 0;
END; $$;

CREATE OR REPLACE FUNCTION public.cancel_plan_subscriptions(p_plan_id UUID)
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE changed INTEGER;
BEGIN
  UPDATE public.subscriptions
  SET status = 'cancelled', cancelled_at = NOW(), will_renew = FALSE, auto_renew = FALSE, updated_at = NOW()
  WHERE subscription_plan_id = p_plan_id AND status = 'active';
  GET DIAGNOSTICS changed = ROW_COUNT;
  RETURN changed;
END; $$;
