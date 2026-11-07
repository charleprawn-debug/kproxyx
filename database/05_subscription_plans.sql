-- Independent subscription packages. These are not proxy marketplace products.
CREATE TABLE IF NOT EXISTS public.subscription_plans (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(120) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  price NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  duration_days INTEGER NOT NULL DEFAULT 30 CHECK (duration_days > 0),
  feature_flags JSONB NOT NULL DEFAULT '{"advanced": false, "sim": false, "vpn": false, "mock_location": false}'::jsonb,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.subscriptions ADD COLUMN IF NOT EXISTS subscription_plan_id UUID REFERENCES public.subscription_plans(id) ON DELETE RESTRICT;
ALTER TABLE public.subscriptions ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE public.subscription_plans ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Anyone can view active subscription plans" ON public.subscription_plans;
CREATE POLICY "Anyone can view active subscription plans" ON public.subscription_plans FOR SELECT USING (is_active = TRUE);

CREATE OR REPLACE FUNCTION public.purchase_subscription_plan(p_user_id UUID, p_plan_id UUID)
RETURNS TABLE(success BOOLEAN, message TEXT, subscription_id UUID, new_balance NUMERIC) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE plan public.subscription_plans; wallet public.wallets; sub_id UUID;
BEGIN
  SELECT * INTO plan FROM public.subscription_plans WHERE id = p_plan_id AND is_active = TRUE;
  IF NOT FOUND THEN RETURN QUERY SELECT FALSE, 'Subscription plan not found'::TEXT, NULL::UUID, NULL::NUMERIC; RETURN; END IF;
  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO wallet FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
  IF wallet.balance < plan.price THEN RETURN QUERY SELECT FALSE, 'Insufficient wallet balance'::TEXT, NULL::UUID, wallet.balance; RETURN; END IF;
  INSERT INTO public.subscriptions(user_id, product_id, subscription_plan_id, status, duration_type, started_at, expires_at, auto_renew, will_renew)
  VALUES (p_user_id, NULL, p_plan_id, 'active', 'custom', NOW(), NOW() + make_interval(days => plan.duration_days), FALSE, FALSE)
  RETURNING id INTO sub_id;
  UPDATE public.wallets SET balance = balance - plan.price, updated_at = NOW() WHERE id = wallet.id RETURNING balance INTO wallet.balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id)
  VALUES (p_user_id, -plan.price, wallet.balance, 'plan_purchase', 'Subscription plan purchase', sub_id);
  RETURN QUERY SELECT TRUE, 'Subscription plan activated'::TEXT, sub_id, wallet.balance;
END; $$;

CREATE INDEX IF NOT EXISTS idx_subscription_plans_active ON public.subscription_plans(is_active, sort_order);
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan ON public.subscriptions(subscription_plan_id);
