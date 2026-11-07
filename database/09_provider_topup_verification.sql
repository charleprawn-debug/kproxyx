-- Provider payment metadata required for automatic verification.
ALTER TABLE public.wallet_topup_requests
  ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(160),
  ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(160),
  ADD COLUMN IF NOT EXISTS provider_address TEXT,
  ADD COLUMN IF NOT EXISTS provider_pay_amount NUMERIC(30, 12),
  ADD COLUMN IF NOT EXISTS provider_network VARCHAR(30),
  ADD COLUMN IF NOT EXISTS provider_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_topups_provider_payment
  ON public.wallet_topup_requests(provider_payment_id)
  WHERE provider_payment_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_topups_provider_order
  ON public.wallet_topup_requests(provider_order_id)
  WHERE provider_order_id IS NOT NULL;
