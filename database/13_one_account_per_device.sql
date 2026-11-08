-- One application account per Android device.
-- The backend stores only a keyed digest, never the raw Android identifier.
CREATE TABLE IF NOT EXISTS public.device_account_bindings (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  device_fingerprint_hash TEXT NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_account_bindings_user
  ON public.device_account_bindings(user_id);

ALTER TABLE public.device_account_bindings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own device binding" ON public.device_account_bindings;
CREATE POLICY "Users can view own device binding"
  ON public.device_account_bindings
  FOR SELECT
  USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));
