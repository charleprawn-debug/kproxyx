-- Wallet top-up methods and auditable deposit requests.
-- Addresses/rates are managed by admins and are never hard-coded into Android.

CREATE TABLE IF NOT EXISTS public.wallet_topup_methods (
  code VARCHAR(40) PRIMARY KEY,
  name TEXT NOT NULL,
  currency VARCHAR(8) NOT NULL,
  network VARCHAR(30),
  address TEXT NOT NULL DEFAULT '',
  min_amount NUMERIC(30, 12) NOT NULL DEFAULT 0 CHECK (min_amount >= 0),
  exchange_rate NUMERIC(30, 12) NOT NULL DEFAULT 1 CHECK (exchange_rate > 0),
  instructions TEXT NOT NULL DEFAULT '',
  is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by UUID REFERENCES public.users(id)
);

INSERT INTO public.wallet_topup_methods(code, name, currency, network, sort_order)
VALUES
  ('syriatel_cash', 'سرياتيل كاش', 'SYP', NULL, 10),
  ('sham_cash_syp', 'شام كاش سوري', 'SYP', NULL, 20),
  ('sham_cash_usd', 'شام كاش دولار', 'USD', NULL, 30),
  ('usdt_bep20', 'USDT BEP20', 'USDT', 'BEP20', 40),
  ('usdt_trc20', 'USDT TRC20', 'USDT', 'TRC20', 50)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.wallet_topup_requests (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  method_code VARCHAR(40) NOT NULL REFERENCES public.wallet_topup_methods(code),
  submitted_amount NUMERIC(30, 12) NOT NULL CHECK (submitted_amount > 0),
  submitted_currency VARCHAR(8) NOT NULL,
  expected_usd NUMERIC(12, 2) NOT NULL CHECK (expected_usd > 0),
  transaction_reference VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','verified','rejected','expired')),
  provider_reference VARCHAR(255),
  verification_source VARCHAR(40),
  user_note TEXT,
  admin_note TEXT,
  reviewed_by UUID REFERENCES public.users(id),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(method_code, transaction_reference)
);

CREATE INDEX IF NOT EXISTS idx_wallet_topup_requests_user_created ON public.wallet_topup_requests(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_topup_requests_status_created ON public.wallet_topup_requests(status, created_at DESC);

ALTER TABLE public.wallet_topup_methods ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallet_topup_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view enabled topup methods" ON public.wallet_topup_methods;
CREATE POLICY "Users can view enabled topup methods" ON public.wallet_topup_methods FOR SELECT USING (is_enabled = TRUE);
DROP POLICY IF EXISTS "Users can view own topup requests" ON public.wallet_topup_requests;
CREATE POLICY "Users can view own topup requests" ON public.wallet_topup_requests FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.approve_wallet_topup(
  p_request_id UUID,
  p_admin_id UUID,
  p_admin_note TEXT DEFAULT NULL
)
RETURNS TABLE(success BOOLEAN, message TEXT, new_balance NUMERIC)
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  request_row public.wallet_topup_requests;
  wallet_row public.wallets;
BEGIN
  SELECT * INTO request_row FROM public.wallet_topup_requests WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND THEN RETURN QUERY SELECT FALSE, 'Top-up request not found'::TEXT, NULL::NUMERIC; RETURN; END IF;
  IF request_row.status = 'verified' THEN
    SELECT balance INTO wallet_row.balance FROM public.wallets WHERE user_id = request_row.user_id;
    RETURN QUERY SELECT TRUE, 'Already approved'::TEXT, wallet_row.balance; RETURN;
  END IF;
  IF request_row.status <> 'pending' THEN RETURN QUERY SELECT FALSE, 'Request is not pending'::TEXT, NULL::NUMERIC; RETURN; END IF;

  INSERT INTO public.wallets(user_id) VALUES (request_row.user_id) ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO wallet_row FROM public.wallets WHERE user_id = request_row.user_id FOR UPDATE;
  UPDATE public.wallets SET balance = balance + request_row.expected_usd, updated_at = NOW()
    WHERE id = wallet_row.id RETURNING balance INTO wallet_row.balance;
  INSERT INTO public.wallet_transactions(user_id, amount, balance_after, transaction_type, description, reference_id, metadata)
    VALUES (request_row.user_id, request_row.expected_usd, wallet_row.balance, 'top_up', 'Wallet top-up', request_row.id,
            jsonb_build_object('method_code', request_row.method_code, 'transaction_reference', request_row.transaction_reference));
  UPDATE public.wallet_topup_requests SET status = 'verified', reviewed_by = p_admin_id, reviewed_at = NOW(), admin_note = p_admin_note, updated_at = NOW() WHERE id = request_row.id;
  RETURN QUERY SELECT TRUE, 'Top-up approved'::TEXT, wallet_row.balance;
END; $$;
