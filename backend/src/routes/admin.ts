import { Router } from 'express';
import { requireAuth, requireRole } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';
import { paymentSettingsStatus, savePaymentSettings } from '../services/paymentSettings';

const router = Router();
router.use(requireAuth, requireRole('admin'));
const featureKeys = ['advanced', 'sim', 'vpn', 'mock_location', 'broadcast'];

router.get('/payment-secrets', async (_req, res) => {
  try { return res.json({ data: await paymentSettingsStatus() }); } catch { return res.status(500).json({ error: { code: 'PAYMENT_SETTINGS_UNAVAILABLE', message: 'تعذر تحميل حالة إعدادات الدفع.' } }); }
});

router.put('/payment-secrets', async (req, res) => {
  const body = req.body ?? {};
  const values: Record<string, string> = {};
  const fields: Record<string, string> = {
    oxapayApiBase: 'payment_oxapay_api_base', oxapayMerchantKey: 'payment_oxapay_merchant_key', oxapayWebhookUrl: 'payment_oxapay_webhook_url', oxapayFeePaidByPayer: 'payment_oxapay_fee_paid_by_payer',
    syriaApiBase: 'payment_syria_api_base', syriaToken: 'payment_syria_token', syriaQuery: 'payment_syria_query',
  };
  for (const [input, key] of Object.entries(fields)) if (typeof body[input] === 'string' && body[input].trim()) values[key] = body[input].trim();
  if (values.payment_oxapay_api_base) { try { if (!['http:', 'https:'].includes(new URL(values.payment_oxapay_api_base).protocol)) throw new Error(); } catch { return res.status(400).json({ error: { code: 'INVALID_OXAPAY_URL', message: 'رابط OxaPay غير صالح.' } }); } }
  if (values.payment_oxapay_webhook_url) { try { if (new URL(values.payment_oxapay_webhook_url).protocol !== 'https:') throw new Error(); } catch { return res.status(400).json({ error: { code: 'INVALID_WEBHOOK_URL', message: 'رابط Webhook يجب أن يكون HTTPS.' } }); } }
  if (values.payment_syria_api_base) { try { if (!['http:', 'https:'].includes(new URL(values.payment_syria_api_base).protocol)) throw new Error(); } catch { return res.status(400).json({ error: { code: 'INVALID_SYRIA_URL', message: 'رابط مزود الدفع السوري غير صالح.' } }); } }
  try { await savePaymentSettings(values); return res.json({ data: await paymentSettingsStatus() }); } catch { return res.status(400).json({ error: { code: 'PAYMENT_SETTINGS_SAVE_FAILED', message: 'تعذر حفظ إعدادات الدفع.' } }); }
});

router.get('/landing-settings', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('app_settings').select('key,value,updated_at').eq('key', 'landing_download_url').maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'LANDING_SETTINGS_UNAVAILABLE', message: 'تعذر تحميل إعدادات صفحة الهبوط.' } });
  return res.json({ data: { downloadUrl: data?.value ?? '', updatedAt: data?.updated_at ?? null } });
});

router.put('/landing-settings', async (req, res) => {
  const downloadUrl = typeof req.body?.downloadUrl === 'string' ? req.body.downloadUrl.trim() : '';
  let parsed: URL;
  try { parsed = new URL(downloadUrl); } catch { return res.status(400).json({ error: { code: 'INVALID_DOWNLOAD_URL', message: 'أدخل رابط تحميل صحيحًا.' } }); }
  if (!['http:', 'https:'].includes(parsed.protocol) || downloadUrl.length > 2048) return res.status(400).json({ error: { code: 'INVALID_DOWNLOAD_URL', message: 'يجب أن يكون رابط التحميل HTTPS أو HTTP وبطول صالح.' } });
  const { data, error } = await supabaseAdmin.from('app_settings').upsert({ key: 'landing_download_url', value: downloadUrl, updated_at: new Date().toISOString() }, { onConflict: 'key' }).select('key,value,updated_at').single();
  if (error) return res.status(400).json({ error: { code: 'LANDING_SETTINGS_SAVE_FAILED', message: 'تعذر حفظ رابط التحميل.' } });
  return res.json({ data: { downloadUrl: data.value, updatedAt: data.updated_at } });
});

router.get('/support-settings', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('app_settings').select('key,value,updated_at').eq('key', 'support_telegram_username').maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'SUPPORT_SETTINGS_UNAVAILABLE', message: 'تعذر تحميل حساب الدعم.' } });
  return res.json({ data: { telegramUsername: (data?.value ?? 'eng_Eyad_saleh').replace(/^@/, ''), updatedAt: data?.updated_at ?? null } });
});

router.put('/support-settings', async (req, res) => {
  const telegramUsername = typeof req.body?.telegramUsername === 'string' ? req.body.telegramUsername.trim().replace(/^@/, '') : '';
  if (!/^[A-Za-z0-9_]{5,64}$/.test(telegramUsername)) return res.status(400).json({ error: { code: 'INVALID_SUPPORT_USERNAME', message: 'أدخل اسم حساب Telegram صالحًا.' } });
  const { data, error } = await supabaseAdmin.from('app_settings').upsert({ key: 'support_telegram_username', value: telegramUsername, updated_at: new Date().toISOString() }, { onConflict: 'key' }).select('key,value,updated_at').single();
  if (error) return res.status(400).json({ error: { code: 'SUPPORT_SETTINGS_SAVE_FAILED', message: 'تعذر حفظ حساب الدعم.' } });
  return res.json({ data: { telegramUsername: data.value, updatedAt: data.updated_at } });
});

router.get('/app-update-settings', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('app_settings').select('key,value,updated_at').in('key', ['app_update_enabled', 'app_min_version_code', 'app_update_message', 'app_update_url']);
  if (error) return res.status(500).json({ error: { code: 'APP_UPDATE_SETTINGS_UNAVAILABLE', message: 'تعذر تحميل إعدادات التحديث.' } });
  const settings = Object.fromEntries((data ?? []).map((row) => [row.key, row.value]));
  return res.json({ data: { enabled: settings.app_update_enabled === 'true', minimumVersionCode: Math.max(0, Number(settings.app_min_version_code ?? 0)), message: settings.app_update_message || '', updateUrl: settings.app_update_url || '' } });
});

router.put('/app-update-settings', async (req, res) => {
  const enabled = req.body?.enabled === true;
  const minimumVersionCode = Number(req.body?.minimumVersionCode);
  const message = typeof req.body?.message === 'string' ? req.body.message.trim().slice(0, 500) : '';
  const updateUrl = typeof req.body?.updateUrl === 'string' ? req.body.updateUrl.trim() : '';
  if (!Number.isInteger(minimumVersionCode) || minimumVersionCode < 0 || minimumVersionCode > 2147483647 || !message) return res.status(400).json({ error: { code: 'INVALID_APP_UPDATE_SETTINGS', message: 'أدخل رقم إصدار ورسالة تحديث صحيحة.' } });
  try { const parsed = new URL(updateUrl); if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('invalid'); } catch { return res.status(400).json({ error: { code: 'INVALID_APP_UPDATE_URL', message: 'أدخل رابط تحديث صحيحًا.' } }); }
  const rows = [
    { key: 'app_update_enabled', value: String(enabled) },
    { key: 'app_min_version_code', value: String(minimumVersionCode) },
    { key: 'app_update_message', value: message },
    { key: 'app_update_url', value: updateUrl },
  ].map((row) => ({ ...row, updated_at: new Date().toISOString() }));
  const { error } = await supabaseAdmin.from('app_settings').upsert(rows, { onConflict: 'key' });
  if (error) return res.status(400).json({ error: { code: 'APP_UPDATE_SETTINGS_SAVE_FAILED', message: 'تعذر حفظ إعدادات التحديث.' } });
  return res.json({ data: { enabled, minimumVersionCode, message, updateUrl } });
});

router.get('/overview', async (_req, res) => {
  const tables = ['users', 'proxy_products', 'subscriptions', 'orders', 'connection_sessions'] as const;
  const counts = await Promise.all(tables.map(async (table) => { const result = await supabaseAdmin.from(table).select('id', { count: 'exact', head: true }); return [table, result.error ? null : result.count ?? 0] as const; }));
  const { count: activeSubscriptions } = await supabaseAdmin.from('subscriptions').select('id', { count: 'exact', head: true }).eq('status', 'active');
  const { data: recentUsers } = await supabaseAdmin.from('users').select('id,email,full_name,role,status,created_at,last_login_at').order('created_at', { ascending: false }).limit(10);
  const { data: recentPlans } = await supabaseAdmin.from('proxy_products').select('id,name,protocol,country_name,price_monthly,is_active,stock_available,feature_flags,created_at').order('created_at', { ascending: false }).limit(10);
  return res.json({ data: { counts: Object.fromEntries(counts), activeSubscriptions: activeSubscriptions ?? 0, recentUsers: recentUsers ?? [], recentPlans: recentPlans ?? [] } });
});

router.get('/stats', async (_req, res) => {
  const now = Date.now();
  const periods = { day: new Date(now - 24 * 60 * 60 * 1000).toISOString(), week: new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString(), month: new Date(now - 30 * 24 * 60 * 60 * 1000).toISOString() };
  const [{ count: totalUsers }, { count: activeSubscriptions }, { data: wallets, error: walletsError }, { data: subscriptions, error: subscriptionsError }, { count: sessionsLast30Days }] = await Promise.all([
    supabaseAdmin.from('users').select('id', { count: 'exact', head: true }),
    supabaseAdmin.from('subscriptions').select('id', { count: 'exact', head: true }).eq('status', 'active').gt('expires_at', new Date().toISOString()),
    supabaseAdmin.from('wallets').select('user_id,balance,currency'),
    supabaseAdmin.from('subscriptions').select('id,subscription_plan_id,status,expires_at,subscription_plans(name)').eq('status', 'active').gt('expires_at', new Date().toISOString()),
    supabaseAdmin.from('connection_sessions').select('id', { count: 'exact', head: true }).gte('started_at', periods.month),
  ]);
  if (walletsError || subscriptionsError) return res.status(500).json({ error: { code: 'STATS_UNAVAILABLE', message: 'تعذر تحميل إحصائيات الأرصدة والاشتراكات.' } });
  const activeUsers = await Promise.all(Object.entries(periods).map(async ([key, since]) => {
    const { count } = await supabaseAdmin.from('users').select('id', { count: 'exact', head: true }).gte('last_login_at', since);
    return [key, count ?? 0] as const;
  }));
  const balanceTotal = (wallets ?? []).reduce((sum, wallet) => sum + Number(wallet.balance ?? 0), 0);
  const planCounts = new Map<string, { name: string; count: number }>();
  for (const subscription of subscriptions ?? []) {
    const relation = Array.isArray(subscription.subscription_plans) ? subscription.subscription_plans[0] : subscription.subscription_plans;
    const name = relation?.name ?? 'بدون باقة'; const current = planCounts.get(name) ?? { name, count: 0 }; current.count += 1; planCounts.set(name, current);
  }
  return res.json({ data: { totalUsers: totalUsers ?? 0, activeSubscriptions: activeSubscriptions ?? 0, walletsCount: wallets?.length ?? 0, balanceTotal, sessionsLast30Days: sessionsLast30Days ?? 0, activeUsers: Object.fromEntries(activeUsers), subscriptionsByPlan: [...planCounts.values()].sort((a, b) => b.count - a.count) } });
});

router.get('/users', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit ?? 100), 1), 250);
  const search = typeof req.query.search === 'string' ? req.query.search.trim() : '';
  const activeWithinHours = Number(req.query.activeWithinHours ?? 0);
  const activeSince = Number.isFinite(activeWithinHours) && activeWithinHours > 0
    ? new Date(Date.now() - Math.min(activeWithinHours, 168) * 60 * 60 * 1000).toISOString()
    : null;
  let query = supabaseAdmin.from('users').select('id,email,full_name,role,status,is_email_verified,created_at,last_login_at,login_count').order('created_at', { ascending: false }).limit(limit);
  if (search) query = query.or(`email.ilike.%${search}%,full_name.ilike.%${search}%`);
  if (activeSince) query = query.gte('last_login_at', activeSince);
  const { data, error } = await query;
  if (error) return res.status(500).json({ error: { code: 'USERS_UNAVAILABLE', message: 'Could not load users' } });
  const { data: wallets } = await supabaseAdmin.from('wallets').select('user_id,balance,currency').in('user_id', (data ?? []).map((user) => user.id));
  const walletByUser = new Map((wallets ?? []).map((wallet) => [wallet.user_id, wallet]));
  return res.json({ data: (data ?? []).map((user) => ({ ...user, wallet: walletByUser.get(user.id) ?? { balance: 0, currency: 'USD' }, activeWithin24Hours: Boolean(user.last_login_at && new Date(user.last_login_at).getTime() >= Date.now() - 24 * 60 * 60 * 1000) })) });
});
router.get('/users/:id/wallet', async (req, res) => {
  const { data, error } = await supabaseAdmin.rpc('ensure_wallet', { p_user_id: req.params.id });
  if (error || !data) return res.status(404).json({ error: { code: 'WALLET_UNAVAILABLE', message: 'تعذر تحميل رصيد المستخدم.' } });
  const wallet = Array.isArray(data) ? data[0] : data;
  const { data: transactions, error: transactionsError } = await supabaseAdmin.from('wallet_transactions').select('id,amount,balance_after,transaction_type,description,created_at').eq('user_id', req.params.id).order('created_at', { ascending: false }).limit(20);
  if (transactionsError) return res.status(500).json({ error: { code: 'WALLET_HISTORY_UNAVAILABLE', message: 'تعذر تحميل حركة الرصيد.' } });
  return res.json({ data: { wallet, transactions: transactions ?? [] } });
});

router.get('/users/:id/activity', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('user_activity_logs').select('id,action,entity_type,entity_id,metadata,created_at').eq('user_id', req.params.id).order('created_at', { ascending: false }).limit(200);
  // Older installations may not have applied migration 14 yet. Keep the admin
  // screen usable while the migration is pending; new installations still get
  // the full activity history from user_activity_logs.
  if (error) {
    const missingActivityTable = error.code === 'PGRST205' || error.message?.includes('user_activity_logs');
    if (missingActivityTable) return res.json({ data: [] });
    return res.status(500).json({ error: { code: 'USER_ACTIVITY_UNAVAILABLE', message: 'تعذر تحميل سجلات المستخدم.' } });
  }
  return res.json({ data: data ?? [] });
});

router.post('/broadcasts', async (req, res) => {
  const title = typeof req.body?.title === 'string' ? req.body.title.trim().slice(0, 120) : '';
  const message = typeof req.body?.message === 'string' ? req.body.message.trim().slice(0, 2000) : '';
  if (!title || !message) return res.status(400).json({ error: { code: 'INVALID_BROADCAST', message: 'العنوان والرسالة مطلوبان.' } });
  const { data: users, error: usersError } = await supabaseAdmin.from('users').select('id').eq('status', 'active');
  if (usersError) return res.status(500).json({ error: { code: 'BROADCAST_USERS_UNAVAILABLE', message: 'تعذر تحميل المستخدمين.' } });
  const rows = (users ?? []).map((user) => ({ user_id: user.id, title, message, notification_type: 'admin_broadcast', category: 'system' }));
  // Insert in bounded batches so a large user base receives the announcement
  // reliably instead of depending on one oversized PostgREST request.
  for (let offset = 0; offset < rows.length; offset += 500) {
    const { error } = await supabaseAdmin.from('notifications').insert(rows.slice(offset, offset + 500));
    if (error) return res.status(500).json({ error: { code: 'BROADCAST_FAILED', message: 'تعذر إرسال الرسالة.' } });
  }
  await supabaseAdmin.from('audit_logs').insert({ action: 'broadcast_notification', entity_type: 'notification', new_values: { title, recipient_count: rows.length }, user_agent: req.get('user-agent') ?? null });
  return res.status(201).json({ data: { recipients: rows.length } });
});

router.patch('/users/:id', async (req, res) => {
  const patch: Record<string, unknown> = {};
  if (typeof req.body?.status === 'string' && ['active', 'banned', 'suspended'].includes(req.body.status)) patch.status = req.body.status;
  if (typeof req.body?.role === 'string' && ['user', 'admin', 'moderator'].includes(req.body.role)) patch.role = req.body.role;
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Valid status or role is required' } });
  const { data, error } = await supabaseAdmin.from('users').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('id,email,role,status').single();
  if (error) return res.status(400).json({ error: { code: 'USER_UPDATE_FAILED', message: error.message } });
  return res.json({ data });
});

router.post('/users/:id/password', async (req, res) => {
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (password.length < 8 || password.length > 128) return res.status(400).json({ error: { code: 'INVALID_PASSWORD', message: 'كلمة المرور يجب أن تكون بين 8 و128 محرفًا.' } });
  const { data: profile, error: profileError } = await supabaseAdmin.from('users').select('id,auth_id,email').eq('id', req.params.id).maybeSingle();
  if (profileError || !profile) return res.status(404).json({ error: { code: 'USER_NOT_FOUND', message: 'المستخدم غير موجود.' } });
  const { error } = await supabaseAdmin.auth.admin.updateUserById(profile.auth_id, { password });
  if (error) return res.status(400).json({ error: { code: 'PASSWORD_UPDATE_FAILED', message: 'تعذر تغيير كلمة مرور المستخدم.' } });
  await supabaseAdmin.from('user_activity_logs').insert({ user_id: profile.id, action: 'admin_password_reset', entity_type: 'user', entity_id: profile.id, metadata: { admin_id: req.user!.id } });
  return res.json({ data: { id: profile.id, email: profile.email } });
});

router.delete('/users/:id', async (req, res) => {
  const { data: profile, error: profileError } = await supabaseAdmin.from('users').select('id,auth_id,email').eq('id', req.params.id).maybeSingle();
  if (profileError || !profile) return res.status(404).json({ error: { code: 'USER_NOT_FOUND', message: 'المستخدم غير موجود.' } });
  if (profile.id === req.user!.id) return res.status(400).json({ error: { code: 'SELF_DELETE_FORBIDDEN', message: 'لا يمكن حذف حساب الأدمن الحالي.' } });
  const { error: bindingError } = await supabaseAdmin.from('device_account_bindings').delete().eq('user_id', profile.id);
  if (bindingError) return res.status(400).json({ error: { code: 'ACCOUNT_DELETE_FAILED', message: 'تعذر تحرير ربط الجهاز.' } });
  const { error: profileDeleteError } = await supabaseAdmin.from('users').delete().eq('id', profile.id);
  if (profileDeleteError) return res.status(400).json({ error: { code: 'ACCOUNT_DELETE_FAILED', message: 'تعذر حذف ملف المستخدم.' } });
  const { error: authDeleteError } = await supabaseAdmin.auth.admin.deleteUser(profile.auth_id);
  if (authDeleteError) return res.status(400).json({ error: { code: 'ACCOUNT_DELETE_AUTH_FAILED', message: 'تم حذف ملف المستخدم لكن تعذر حذف سجل المصادقة؛ راجع السجلات.' } });
  return res.json({ data: { id: profile.id, email: profile.email, deleted: true } });
});

router.post('/users/:id/wallet', async (req, res) => {
  const amount = Number(req.body?.amount);
  if (!Number.isFinite(amount) || amount === 0 || Math.abs(amount) > 1000000) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'A non-zero wallet amount is required' } });
  const { data: wallet, error: walletError } = await supabaseAdmin.rpc('ensure_wallet', { p_user_id: req.params.id });
  if (walletError || !wallet) return res.status(400).json({ error: { code: 'WALLET_UNAVAILABLE', message: walletError?.message ?? 'Wallet unavailable' } });
  const current = Array.isArray(wallet) ? wallet[0] : wallet;
  const next = Number(current.balance) + amount;
  if (next < 0) return res.status(409).json({ error: { code: 'INSUFFICIENT_BALANCE', message: 'Wallet cannot become negative' } });
  const { data: updated, error } = await supabaseAdmin.from('wallets').update({ balance: next, updated_at: new Date().toISOString() }).eq('user_id', req.params.id).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'WALLET_UPDATE_FAILED', message: error.message } });
  await supabaseAdmin.from('wallet_transactions').insert({ user_id: req.params.id, amount, balance_after: next, transaction_type: amount > 0 ? 'admin_credit' : 'admin_debit', description: amount > 0 ? 'Admin wallet credit' : 'Admin wallet debit', metadata: { admin_id: req.user!.id } });
  await supabaseAdmin.from('user_activity_logs').insert({ user_id: req.params.id, action: amount > 0 ? 'admin_wallet_credit' : 'admin_wallet_debit', entity_type: 'wallet', metadata: { amount, admin_id: req.user!.id } });
  return res.json({ data: updated });
});

router.post('/users/:id/grant-plan', async (req, res) => {
  const planId = String(req.body?.planId ?? '');
  if (!planId) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'planId is required' } });
  const days = Math.min(Math.max(Number(req.body?.days ?? 30), 1), 3650);
  const { data: plan } = await supabaseAdmin.from('subscription_plans').select('id,is_active').eq('id', planId).maybeSingle();
  if (!plan || !plan.is_active) return res.status(404).json({ error: { code: 'PLAN_NOT_FOUND', message: 'Subscription plan not found or inactive' } });
  const { data, error } = await supabaseAdmin.from('subscriptions').insert({ user_id: req.params.id, product_id: null, subscription_plan_id: planId, status: 'active', duration_type: 'custom', started_at: new Date().toISOString(), expires_at: new Date(Date.now() + days * 86400000).toISOString(), auto_renew: false, will_renew: false }).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_GRANT_FAILED', message: error.message } });
  await supabaseAdmin.from('user_activity_logs').insert({ user_id: req.params.id, action: 'admin_plan_granted', entity_type: 'subscription', entity_id: data.id, metadata: { plan_id: planId, days, admin_id: req.user!.id } });
  return res.status(201).json({ data });
});

router.get('/users/:id/access', async (req, res) => {
  const [{ data: grants, error: grantsError }, { data: subscriptions, error: subscriptionsError }] = await Promise.all([
    supabaseAdmin.from('user_feature_grants').select('feature_key,enabled,expires_at').eq('user_id', req.params.id),
    supabaseAdmin.from('subscriptions').select('id,status,started_at,expires_at,subscription_plan_id,subscription_plans(id,name,feature_flags)').eq('user_id', req.params.id).order('created_at', { ascending: false }).limit(20),
  ]);
  if (grantsError || subscriptionsError) return res.status(500).json({ error: { code: 'USER_ACCESS_UNAVAILABLE', message: 'Could not load user access' } });
  return res.json({ data: { grants: grants ?? [], subscriptions: subscriptions ?? [] } });
});

router.post('/users/:id/feature', async (req, res) => {
  const feature = String(req.body?.feature ?? '');
  if (!featureKeys.includes(feature)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Unsupported feature' } });
  const { data, error } = await supabaseAdmin.from('user_feature_grants').upsert({ user_id: req.params.id, feature_key: feature, enabled: req.body?.enabled !== false, expires_at: req.body?.expiresAt ?? null, granted_by: req.user!.id }, { onConflict: 'user_id,feature_key' }).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'FEATURE_GRANT_FAILED', message: error.message } });
  return res.json({ data });
});

router.put('/users/:id/features', async (req, res) => {
  const requested = req.body?.features;
  if (!requested || typeof requested !== 'object') return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'features object is required' } });
  const expiresAt = typeof req.body?.expiresAt === 'string' && req.body.expiresAt ? req.body.expiresAt : null;
  const results = await Promise.all(featureKeys.map((feature) => supabaseAdmin.from('user_feature_grants').upsert({ user_id: req.params.id, feature_key: feature, enabled: requested[feature] === true, expires_at: expiresAt, granted_by: req.user!.id }, { onConflict: 'user_id,feature_key' }).select('feature_key,enabled,expires_at').single()));
  const failed = results.find((result) => result.error);
  if (failed?.error) return res.status(400).json({ error: { code: 'FEATURES_UPDATE_FAILED', message: failed.error.message } });
  await supabaseAdmin.from('user_activity_logs').insert({ user_id: req.params.id, action: 'admin_features_updated', entity_type: 'feature_grants', metadata: { features: requested, expires_at: expiresAt, admin_id: req.user!.id } });
  return res.json({ data: results.map((result) => result.data) });
});

router.get('/plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('proxy_products').select('id,name,description,protocol,country_code,country_name,city,price_daily,price_weekly,price_monthly,price_quarterly,price_yearly,device_limit,traffic_limit_gb,is_active,is_featured,stock_available,category,tags,feature_flags,created_at,updated_at').order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PLANS_UNAVAILABLE', message: 'Could not load plans' } });
  return res.json({ data: data ?? [] });
});

router.get('/subscription-plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('subscription_plans').select('id,name,description,price,currency,duration_days,free_claim_once,free_duration_minutes,is_active,is_featured,sort_order,feature_flags,created_at,updated_at').order('sort_order', { ascending: true }).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLANS_UNAVAILABLE', message: 'Could not load subscription plans' } });
  return res.json({ data: data ?? [] });
});

router.post('/subscription-plans', async (req, res) => {
  const body = req.body ?? {};
  if (typeof body.name !== 'string' || !body.name.trim()) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Subscription plan name is required' } });
  const flags = Object.fromEntries(featureKeys.map((key) => [key, body.featureFlags?.[key] === true]));
  const payload = { name: body.name.trim(), description: typeof body.description === 'string' ? body.description.trim() : '', price: Math.max(0, Number(body.price || 0)), currency: body.currency || 'USD', duration_days: Math.max(1, Number(body.durationDays || 30)), feature_flags: flags, is_active: body.isActive !== false, is_featured: body.isFeatured === true, sort_order: Number(body.sortOrder || 0), free_claim_once: body.freeClaimOnce === true, free_duration_minutes: Math.max(0, Number(body.freeDurationMinutes || 0)) };
  const { data, error } = await supabaseAdmin.from('subscription_plans').insert(payload).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'SUBSCRIPTION_PLAN_CREATE_FAILED', message: error.message } });
  return res.status(201).json({ data });
});

router.patch('/subscription-plans/:id', async (req, res) => {
  const body = req.body ?? {};
  const patch: Record<string, unknown> = {};
  if (typeof body.name === 'string' && body.name.trim()) patch.name = body.name.trim();
  if (typeof body.description === 'string') patch.description = body.description.trim();
  if (body.price !== undefined && Number.isFinite(Number(body.price)) && Number(body.price) >= 0) patch.price = Number(body.price);
  if (typeof body.currency === 'string' && /^[A-Za-z]{3}$/.test(body.currency)) patch.currency = body.currency.toUpperCase();
  if (body.durationDays !== undefined && Number.isInteger(Number(body.durationDays)) && Number(body.durationDays) > 0) patch.duration_days = Number(body.durationDays);
  if (body.freeClaimOnce !== undefined) patch.free_claim_once = body.freeClaimOnce === true;
  if (body.freeDurationMinutes !== undefined && Number.isInteger(Number(body.freeDurationMinutes)) && Number(body.freeDurationMinutes) >= 0) patch.free_duration_minutes = Number(body.freeDurationMinutes);
  if (body.featureFlags && typeof body.featureFlags === 'object') patch.feature_flags = Object.fromEntries(featureKeys.map((key) => [key, body.featureFlags[key] === true]));
  if (typeof body.isActive === 'boolean') patch.is_active = body.isActive;
  if (typeof body.isFeatured === 'boolean') patch.is_featured = body.isFeatured;
  if (body.sortOrder !== undefined && Number.isInteger(Number(body.sortOrder))) patch.sort_order = Number(body.sortOrder);
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'No editable fields supplied' } });
  const { data, error } = await supabaseAdmin.from('subscription_plans').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'SUBSCRIPTION_PLAN_UPDATE_FAILED', message: error.message } });
  if (patch.is_active === false) {
    const { error: cancelError } = await supabaseAdmin.rpc('cancel_plan_subscriptions', { p_plan_id: req.params.id });
    if (cancelError) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLAN_CANCEL_FAILED', message: 'تعذر إلغاء اشتراكات هذه الباقة.' } });
  }
  return res.json({ data });
});

router.delete('/subscription-plans/:id', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('subscription_plans').update({ is_active: false, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('id').single();
  if (error || !data) return res.status(404).json({ error: { code: 'SUBSCRIPTION_PLAN_DELETE_FAILED', message: 'الباقة غير موجودة.' } });
  const { error: cancelError } = await supabaseAdmin.rpc('cancel_plan_subscriptions', { p_plan_id: req.params.id });
  if (cancelError) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLAN_CANCEL_FAILED', message: 'تعذر إلغاء اشتراكات هذه الباقة.' } });
  return res.status(204).send();
});

router.post('/plans', async (req, res) => {
  const body = req.body ?? {};
  if (typeof body.name !== 'string' || !body.name.trim() || typeof body.protocol !== 'string') return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Plan name and protocol are required' } });
  const flags = Object.fromEntries(featureKeys.map((key) => [key, body.featureFlags?.[key] === true]));
  const payload = { name: body.name.trim(), description: typeof body.description === 'string' ? body.description.trim() : null, protocol: body.protocol, country_code: body.countryCode || null, country_name: body.countryName || null, city: body.city || null, price_daily: Number(body.priceDaily || 0), price_weekly: Number(body.priceWeekly || 0), price_monthly: Number(body.priceMonthly || 0), price_quarterly: Number(body.priceQuarterly || 0), price_yearly: Number(body.priceYearly || 0), device_limit: Math.max(1, Number(body.deviceLimit || 1)), stock_available: Math.max(0, Number(body.stockAvailable || 0)), category: body.category || 'basic', is_active: body.isActive !== false, is_featured: body.isFeatured === true, feature_flags: flags };
  const { data, error } = await supabaseAdmin.from('proxy_products').insert(payload).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_CREATE_FAILED', message: error.message } });
  return res.status(201).json({ data });
});

router.patch('/plans/:id', async (req, res) => {
  const allowed = ['name', 'description', 'price_daily', 'price_weekly', 'price_monthly', 'price_quarterly', 'price_yearly', 'device_limit', 'stock_available', 'is_active', 'is_featured', 'category', 'feature_flags'];
  const patch = Object.fromEntries(Object.entries(req.body ?? {}).filter(([key]) => allowed.includes(key)));
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'No editable fields supplied' } });
  const { data, error } = await supabaseAdmin.from('proxy_products').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_UPDATE_FAILED', message: error.message } });
  return res.json({ data });
});

router.get('/top-up-methods', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('wallet_topup_methods').select('code,name,currency,network,address,min_amount,exchange_rate,instructions,is_enabled,sort_order,updated_at').order('sort_order', { ascending: true });
  if (error) return res.status(500).json({ error: { code: 'TOPUP_METHODS_UNAVAILABLE', message: 'Could not load top-up methods' } });
  return res.json({ data: data ?? [] });
});

router.patch('/top-up-methods/:code', async (req, res) => {
  const body = req.body ?? {};
  const patch: Record<string, unknown> = {};
  if (typeof body.address === 'string') patch.address = body.address.trim().slice(0, 500);
  if (typeof body.instructions === 'string') patch.instructions = body.instructions.trim().slice(0, 2000);
  if (body.minAmount !== undefined) { const value = Number(body.minAmount); if (!Number.isFinite(value) || value < 0) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Invalid minimum amount' } }); patch.min_amount = value; }
  if (body.exchangeRate !== undefined) { const value = Number(body.exchangeRate); if (!Number.isFinite(value) || value <= 0) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Invalid exchange rate' } }); patch.exchange_rate = value; }
  if (typeof body.isEnabled === 'boolean') patch.is_enabled = body.isEnabled;
  if (body.sortOrder !== undefined) { const value = Number(body.sortOrder); if (!Number.isInteger(value)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Invalid sort order' } }); patch.sort_order = value; }
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'No editable fields supplied' } });
  const { data, error } = await supabaseAdmin.from('wallet_topup_methods').update({ ...patch, updated_by: req.user!.id, updated_at: new Date().toISOString() }).eq('code', req.params.code).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'TOPUP_METHOD_UPDATE_FAILED', message: error.message } });
  return res.json({ data });
});

router.get('/top-up-requests', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit ?? 100), 1), 250);
  let query = supabaseAdmin.from('wallet_topup_requests').select('id,user_id,method_code,submitted_amount,submitted_currency,expected_usd,transaction_reference,status,verification_source,admin_note,created_at,updated_at').order('created_at', { ascending: false }).limit(limit);
  if (typeof req.query.status === 'string' && ['pending', 'verified', 'rejected', 'expired'].includes(req.query.status)) query = query.eq('status', req.query.status);
  const { data, error } = await query;
  if (error) return res.status(500).json({ error: { code: 'TOPUP_REQUESTS_UNAVAILABLE', message: 'Could not load top-up requests' } });
  const rows = data ?? [];
  const userIds = [...new Set(rows.map((row) => row.user_id))];
  const { data: users } = userIds.length ? await supabaseAdmin.from('users').select('id,email,full_name').in('id', userIds) : { data: [] };
  const usersById = new Map((users ?? []).map((user) => [user.id, user]));
  return res.json({ data: rows.map((row) => ({ ...row, users: usersById.get(row.user_id) ?? null })) });
});

router.post('/top-up-requests/:id/review', async (req, res) => {
  const decision = String(req.body?.decision ?? '').toLowerCase();
  if (!['approve', 'reject'].includes(decision)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Decision must be approve or reject' } });
  const adminNote = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 1000) : null;
  if (decision === 'reject') {
    const { data, error } = await supabaseAdmin.from('wallet_topup_requests').update({ status: 'rejected', reviewed_by: req.user!.id, reviewed_at: new Date().toISOString(), admin_note: adminNote, updated_at: new Date().toISOString() }).eq('id', req.params.id).eq('status', 'pending').select('*').single();
    if (error) return res.status(409).json({ error: { code: 'TOPUP_REVIEW_FAILED', message: error.message } });
    return res.json({ data });
  }
  const { data, error } = await supabaseAdmin.rpc('approve_wallet_topup', { p_request_id: req.params.id, p_admin_id: req.user!.id, p_admin_note: adminNote });
  if (error) return res.status(409).json({ error: { code: 'TOPUP_APPROVAL_FAILED', message: error.message } });
  const result = Array.isArray(data) ? data[0] : data;
  if (!result?.success) return res.status(409).json({ error: { code: 'TOPUP_APPROVAL_REJECTED', message: result?.message ?? 'Could not approve top-up' } });
  return res.json({ data: result });
});

export default router;
