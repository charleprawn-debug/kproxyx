-- Network broadcast is a subscription-controlled feature.
-- Existing plans remain unchanged; admins can opt them into the feature.
ALTER TABLE public.proxy_products
  ALTER COLUMN feature_flags SET DEFAULT '{"advanced": false, "sim": false, "vpn": false, "mock_location": false, "broadcast": false}'::jsonb;

ALTER TABLE public.subscription_plans
  ALTER COLUMN feature_flags SET DEFAULT '{"advanced": false, "sim": false, "vpn": false, "mock_location": false, "broadcast": false}'::jsonb;

UPDATE public.proxy_products
SET feature_flags = feature_flags || '{"broadcast": false}'::jsonb
WHERE NOT (feature_flags ? 'broadcast');

UPDATE public.subscription_plans
SET feature_flags = feature_flags || '{"broadcast": false}'::jsonb
WHERE NOT (feature_flags ? 'broadcast');

CREATE INDEX IF NOT EXISTS idx_subscription_plans_feature_flags
  ON public.subscription_plans USING GIN(feature_flags);
