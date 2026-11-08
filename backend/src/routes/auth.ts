import { Router } from 'express';
import crypto from 'crypto';
import config from '../config';
import { supabaseAdmin, supabaseClient } from '../utils/supabase';

const router = Router();

function normalizeEmail(value: unknown): string {
  return typeof value === 'string' ? value.trim().toLowerCase() : '';
}

const adminEmails = new Set((process.env.ADMIN_EMAILS ?? '').split(',').map((email) => email.trim().toLowerCase()).filter(Boolean));

function deviceHash(value: unknown): string {
  const deviceId = typeof value === 'string' ? value.trim() : '';
  if (deviceId.length < 8 || deviceId.length > 512) return '';
  return crypto.createHmac('sha256', config.encryption.key).update(deviceId, 'utf8').digest('hex');
}

router.post('/register', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const { password, fullName } = req.body ?? {};
  const deviceFingerprintHash = deviceHash(req.body?.deviceId);

  if (!/^\S+@\S+\.\S+$/.test(email) || typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Valid email and password of at least 8 characters are required' } });
  }
  if (!deviceFingerprintHash) return res.status(400).json({ error: { code: 'DEVICE_ID_REQUIRED', message: 'لا يمكن إنشاء الحساب دون التحقق من الجهاز.' } });

  const existingBinding = await supabaseAdmin.from('device_account_bindings').select('id').eq('device_fingerprint_hash', deviceFingerprintHash).maybeSingle();
  if (existingBinding.data) return res.status(409).json({ error: { code: 'DEVICE_ALREADY_REGISTERED', message: 'تم إنشاء حساب مسبقًا من هذا الجهاز. استخدم تسجيل الدخول للحساب الحالي.' } });
  if (existingBinding.error && existingBinding.error.code !== 'PGRST116') return res.status(503).json({ error: { code: 'DEVICE_CHECK_UNAVAILABLE', message: 'تعذر التحقق من الجهاز حاليًا.' } });

  // This application does not expose an email-verification flow. Confirm the
  // Auth user at creation time so the account can sign in immediately after
  // the app reports successful registration.
  const created = await supabaseAdmin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: fullName ?? '' },
  });
  if (created.error || !created.data.user) return res.status(400).json({ error: { code: 'REGISTRATION_FAILED', message: created.error?.message ?? 'Could not create user' } });

  const profile = await supabaseAdmin
    .from('users')
    .insert({ auth_id: created.data.user.id, email, full_name: fullName ?? null, is_email_verified: true, role: adminEmails.has(email) ? 'admin' : 'user' })
    .select('id,email,full_name,role')
    .single();
  if (profile.error) {
    await supabaseAdmin.auth.admin.deleteUser(created.data.user.id);
    return res.status(500).json({ error: { code: 'PROFILE_CREATE_FAILED', message: 'Could not create user profile' } });
  }
  const binding = await supabaseAdmin.from('device_account_bindings').insert({ device_fingerprint_hash: deviceFingerprintHash, user_id: profile.data.id }).select('id').single();
  if (binding.error) {
    await supabaseAdmin.from('users').delete().eq('id', profile.data.id);
    await supabaseAdmin.auth.admin.deleteUser(created.data.user.id);
    if (binding.error.code === '23505') return res.status(409).json({ error: { code: 'DEVICE_ALREADY_REGISTERED', message: 'تم إنشاء حساب مسبقًا من هذا الجهاز. استخدم تسجيل الدخول للحساب الحالي.' } });
    return res.status(500).json({ error: { code: 'DEVICE_BINDING_FAILED', message: 'تعذر تثبيت الحساب على الجهاز.' } });
  }
  return res.status(201).json({ data: profile.data });
});

router.post('/login', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const { password } = req.body ?? {};
  const deviceFingerprintHash = deviceHash(req.body?.deviceId);
  const result = await supabaseClient.auth.signInWithPassword({ email, password });
  if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect' } });
  if (deviceFingerprintHash && result.data.user) {
    const profile = await supabaseAdmin.from('users').select('id').eq('auth_id', result.data.user.id).maybeSingle();
    if (profile.data) {
      const existing = await supabaseAdmin.from('device_account_bindings').select('id').eq('device_fingerprint_hash', deviceFingerprintHash).maybeSingle();
      if (!existing.data && !existing.error) {
        await supabaseAdmin.from('device_account_bindings').insert({ device_fingerprint_hash: deviceFingerprintHash, user_id: profile.data.id });
      }
    }
  }
  return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } });
});

router.post('/refresh', async (req, res) => {
  const result = await supabaseClient.auth.refreshSession({ refresh_token: req.body?.refreshToken });
  if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'REFRESH_FAILED', message: 'Refresh token is invalid' } });
  return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } });
});

export default router;
