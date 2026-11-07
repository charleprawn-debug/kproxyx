-- Marketplace proxy purchases backed by EZ Proxy API.
ALTER TABLE public.wallet_transactions DROP CONSTRAINT IF EXISTS wallet_transactions_transaction_type_check;
ALTER TABLE public.wallet_transactions ADD CONSTRAINT wallet_transactions_transaction_type_check
  CHECK (transaction_type IN ('top_up','plan_purchase','proxy_purchase','admin_credit','admin_debit','refund','adjustment'));

CREATE TABLE IF NOT EXISTS public.marketplace_proxy_orders (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES public.proxy_products(id) ON DELETE RESTRICT,
  client_request_id UUID NOT NULL UNIQUE,
  provider_order_id VARCHAR(120),
  provider_proxy_id VARCHAR(120),
  status VARCHAR(30) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','completed','failed','refunded')),
  protocol VARCHAR(20) NOT NULL,
  plan_id INTEGER NOT NULL,
  location_id INTEGER NOT NULL,
  amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  error_message TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.user_proxy_credentials (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  order_id UUID NOT NULL UNIQUE REFERENCES public.marketplace_proxy_orders(id) ON DELETE CASCADE,
  provider_proxy_id VARCHAR(120) NOT NULL,
  product_id UUID NOT NULL REFERENCES public.proxy_products(id) ON DELETE RESTRICT,
  protocol VARCHAR(20) NOT NULL,
  host TEXT NOT NULL,
  port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
  username TEXT NOT NULL,
  password_encrypted TEXT NOT NULL,
  proxy_url TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'active',
  expires_at TIMESTAMPTZ,
  location JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_marketplace_proxy_orders_user_created ON public.marketplace_proxy_orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_proxy_credentials_user_created ON public.user_proxy_credentials(user_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.reserve_proxy_purchase(p_user_id UUID, p_amount NUMERIC, p_reference_id UUID, p_description TEXT)
RETURNS TABLE(success BOOLEAN, message TEXT, new_balance NUMERIC) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE w public.wallets;
BEGIN
  IF p_amount < 0 THEN RETURN QUERY SELECT FALSE, 'Invalid amount'::TEXT, NULL::NUMERIC; RETURN; END IF;
  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO w FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
  IF w.balance < p_amount THEN RETURN QUERY SELECT FALSE, 'Insufficient wallet balance'::TEXT, w.balance; RETURN; END IF;
  UPDATE public.wallets SET balance = balance - p_amount, updated_at = NOW() WHERE id = w.id RETURNING balance INTO w.balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id)
    VALUES (p_user_id, -p_amount, w.balance, 'proxy_purchase', p_description, p_reference_id);
  RETURN QUERY SELECT TRUE, 'Wallet reserved'::TEXT, w.balance;
END; $$;

CREATE OR REPLACE FUNCTION public.refund_proxy_purchase(p_user_id UUID, p_amount NUMERIC, p_reference_id UUID, p_description TEXT)
RETURNS TABLE(success BOOLEAN, message TEXT, new_balance NUMERIC) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE w public.wallets;
BEGIN
  INSERT INTO public.wallets(user_id) VALUES (p_user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO w FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
  UPDATE public.wallets SET balance = balance + p_amount, updated_at = NOW() WHERE id = w.id RETURNING balance INTO w.balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id)
    VALUES (p_user_id, p_amount, w.balance, 'refund', p_description, p_reference_id);
  RETURN QUERY SELECT TRUE, 'Wallet refunded'::TEXT, w.balance;
END; $$;
