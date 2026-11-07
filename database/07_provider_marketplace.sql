-- Provider-backed marketplace: purchases may be created directly from the provider catalog.
-- Existing proxy_products remain available for legacy/admin data, but are not required
-- for the provider-driven marketplace flow.
ALTER TABLE public.marketplace_proxy_orders
  ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE public.user_proxy_credentials
  ALTER COLUMN product_id DROP NOT NULL;

COMMENT ON COLUMN public.marketplace_proxy_orders.product_id IS
  'Optional legacy catalog product. Provider-direct purchases leave this NULL.';
COMMENT ON COLUMN public.user_proxy_credentials.product_id IS
  'Optional legacy catalog product. Provider-direct purchases leave this NULL.';
