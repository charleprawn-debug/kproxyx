-- Persist the selected upstream provider for auditing and support.
ALTER TABLE public.marketplace_proxy_orders
  ADD COLUMN IF NOT EXISTS provider_code VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_marketplace_proxy_orders_provider
  ON public.marketplace_proxy_orders(provider_code, created_at DESC);
