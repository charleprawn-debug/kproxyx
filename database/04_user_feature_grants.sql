CREATE TABLE IF NOT EXISTS public.user_feature_grants (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  feature_key VARCHAR(50) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  expires_at TIMESTAMPTZ,
  granted_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, feature_key)
);
ALTER TABLE public.user_feature_grants ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view own feature grants" ON public.user_feature_grants;
CREATE POLICY "Users can view own feature grants" ON public.user_feature_grants FOR SELECT USING (user_id = (SELECT id FROM public.users WHERE auth_id = auth.uid()));
