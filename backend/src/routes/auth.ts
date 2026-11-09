import { Router } from 'express';
import crypto from 'crypto';
import config from '../config';
import { supabaseAdmin, supabaseClient } from '../utils/supabase';

const router = Router();

function normalizeEmail(value: unknown): string {
  return typeof value === 'string' ? value.trim().toLowerCase() : '';
}

const adminEmails = new Set((process.env.ADMIN_EMAILS ?? '').split(',').map((email) => email.trim().toLowerCase()).filter(Boolean));

function deviceHashes(value: unknown): string[] {
  const deviceId = typeof value === 'string' ? value.trim() : '';
  if (deviceId.length < 8 || deviceId.length > 512) return [];
  const values = [deviceId, `com.proxyplatform.app:${deviceId}`, `com.proxyplatform.app.debug:${deviceId}`];
  return [...new Set(values.map((item) => crypto.createHmac('sha256', config.encryption.key).update(item, 'utf8').digest('hex')))];
}
function deviceHash(value: unknown): string { return deviceHashes(value)[0] ?? ''; }

router.post('/register', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const { password, fullName } = req.body ?? {};
  const deviceFingerprintHash = deviceHash(req.body?.deviceId);
  const deviceFingerprintHashes = deviceHashes(req.body?.deviceId);

  if (!/^\S+@\S+\.\S+$/.test(email) || typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Valid email and password of at least 8 characters are required' } });
  }
  if (!deviceFingerprintHash) return res.status(400).json({ error: { code: 'DEVICE_ID_REQUIRED', message: 'لا يمكن إنشاء الحساب دون التحقق من الجهاز.' } });

  const existingBinding = await supabaseAdmin.from('device_account_bindings').select('id').in('device_fingerprint_hash', deviceFingerprintHashes).limit(1).maybeSingle();
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
  const result = await supabaseClient.auth.signInWithPassword({ email, password });
  if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect' } });
  // Login never creates a new device binding. Bindings are created only during registration,
  // so signing in from another device cannot silently make that device eligible for recovery.
  return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } });
});

router.post('/refresh', async (req, res) => {
  const result = await supabaseClient.auth.refreshSession({ refresh_token: req.body?.refreshToken });
  if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'REFRESH_FAILED', message: 'Refresh token is invalid' } });
  return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } });
});

router.post('/forgot-password', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  if (!/^\S+@\S+\.\S+$/.test(email)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'أدخل بريدًا إلكترونيًا صالحًا.' } });
  await supabaseClient.auth.resetPasswordForEmail(email, { redirectTo: 'https://kuncloak.work.gd:10059/' });
  return res.json({ data: { message: 'إذا كان البريد مسجلًا، سيصلك رابط آمن لإعادة تعيين كلمة المرور.' } });
});

router.post('/device-reset-password', async (req, res) => {
  const email = normalizeEmail(req.body?.email);
  const newPassword = typeof req.body?.newPassword === 'string' ? req.body.newPassword : '';
  const fingerprint = deviceHash(req.body?.deviceId);
  const fingerprints = deviceHashes(req.body?.deviceId);
  if (!/^\S+@\S+\.\S+$/.test(email) || newPassword.length < 8 || !fingerprint) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'أدخل البريد وكلمة مرور جديدة صالحة.' } });
  const { data: profile } = await supabaseAdmin.from('users').select('id,auth_id').eq('email', email).maybeSingle();
  const { data: binding } = await supabaseAdmin.from('device_account_bindings').select('user_id').in('device_fingerprint_hash', fingerprints).limit(1).maybeSingle();
  if (!profile || !binding || binding.user_id !== profile.id) return res.status(403).json({ error: { code: 'DEVICE_ACCOUNT_MISMATCH', message: 'هذا البريد غير مرتبط بهذا الجهاز.' } });
  const { error } = await supabaseAdmin.auth.admin.updateUserById(profile.auth_id, { password: newPassword });
  if (error) return res.status(503).json({ error: { code: 'PASSWORD_RESET_FAILED', message: 'تعذر تغيير كلمة المرور حاليًا.' } });
  await supabaseAdmin.from('user_activity_logs').insert({ user_id: profile.id, action: 'device_password_reset', entity_type: 'user', entity_id: profile.id, metadata: { method: 'device_binding' } });
  return res.json({ data: { message: 'تم تغيير كلمة المرور بنجاح. استخدم كلمة المرور الجديدة لتسجيل الدخول.' } });
});

export default router;
