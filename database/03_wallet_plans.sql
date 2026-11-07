-- Wallets, plan features, and subscription purchase support.
ALTER TABLE public.proxy_products
  ADD COLUMN IF NOT EXISTS feature_flags JSONB NOT NULL DEFAULT '{"advanced": false, "sim": false, "vpn": false, "mock_location": false}'::jsonb;

CREATE TABLE IF NOT EXISTS public.wallets (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE CASCADE,
  balance NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.wallet_transactions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  amount NUMERIC(12,2) NOT NULL,
  balance_after NUMERIC(12,2) NOT NULL CHECK (balance_after >= 0),
  transaction_type VARCHAR(40) NOT NULL CHECK (transaction_type IN ('top_up','plan_purchase','admin_credit','admin_debit','refund','adjustment')),
  status VARCHAR(30) NOT NULL DEFAULT 'completed',
  description TEXT NOT NULL,
  reference_id UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view own wallet" ON public.wallets;
CREATE POLICY "Users can view own wallet" ON public.wallets FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));
DROP POLICY IF EXISTS "Users can view own wallet transactions" ON public.wallet_transactions;
CREATE POLICY "Users can view own wallet transactions" ON public.wallet_transactions FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.ensure_wallet(p_user_id UUID)
RETURNS public.wallets LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE result public.wallets;
BEGIN
  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO result FROM public.wallets WHERE user_id = p_user_id;
  RETURN result;
END; $$;

CREATE OR REPLACE FUNCTION public.purchase_plan(p_user_id UUID, p_product_id UUID)
RETURNS TABLE(success BOOLEAN, message TEXT, subscription_id UUID, new_balance NUMERIC) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  plan public.proxy_products;
  wallet public.wallets;
  duration_days INTEGER := 30;
  price NUMERIC := 0;
  sub_id UUID;
BEGIN
  SELECT * INTO plan FROM public.proxy_products WHERE id = p_product_id AND is_active = TRUE;
  IF NOT FOUND THEN RETURN QUERY SELECT FALSE, 'Plan not found'::TEXT, NULL::UUID, NULL::NUMERIC; RETURN; END IF;
  price := COALESCE(plan.price_monthly, 0);
  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO wallet FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
  IF wallet.balance < price THEN RETURN QUERY SELECT FALSE, 'Insufficient wallet balance'::TEXT, NULL::UUID, wallet.balance; RETURN; END IF;
  INSERT INTO public.subscriptions(user_id, product_id, status, duration_type, started_at, expires_at, auto_renew, will_renew)
  VALUES (p_user_id, p_product_id, 'active', 'monthly', NOW(), NOW() + make_interval(days => duration_days), FALSE, FALSE)
  RETURNING id INTO sub_id;
  UPDATE public.wallets SET balance = balance - price, updated_at = NOW() WHERE id = wallet.id RETURNING balance INTO wallet.balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id)
  VALUES (p_user_id, -price, wallet.balance, 'plan_purchase', 'Plan purchase', sub_id);
  RETURN QUERY SELECT TRUE, 'Plan activated'::TEXT, sub_id, wallet.balance;
END; $$;

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_user_created ON public.wallet_transactions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_proxy_products_features ON public.proxy_products USING GIN(feature_flags);

UPDATE public.proxy_products SET feature_flags = '{"advanced": true, "sim": true, "vpn": true, "mock_location": true}'::jsonb WHERE category = 'premium';
UPDATE public.proxy_products SET feature_flags = '{"advanced": false, "sim": false, "vpn": true, "mock_location": false}'::jsonb WHERE category = 'basic';
