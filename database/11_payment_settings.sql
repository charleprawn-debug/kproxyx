-- Encrypted payment settings managed by the admin dashboard.
-- Values are written encrypted by the backend; these rows only establish the keys.
INSERT INTO public.app_settings(key, value)
VALUES
  ('payment_oxapay_api_base', ''),
  ('payment_oxapay_merchant_key', ''),
  ('payment_oxapay_webhook_url', ''),
  ('payment_oxapay_fee_paid_by_payer', 'false'),
  ('payment_syria_api_base', ''),
  ('payment_syria_token', ''),
  ('payment_syria_query', '')
ON CONFLICT (key) DO NOTHING;
