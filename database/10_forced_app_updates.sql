-- Forced application update policy managed from the admin dashboard.
INSERT INTO public.app_settings(key, value)
VALUES
  ('app_update_enabled', 'false'),
  ('app_min_version_code', '0'),
  ('app_update_message', 'يتوفر إصدار جديد من التطبيق. حدّث التطبيق للمتابعة.'),
  ('app_update_url', '')
ON CONFLICT (key) DO NOTHING;
