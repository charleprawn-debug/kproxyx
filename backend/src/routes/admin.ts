import { Router } from 'express';
import { requireAuth, requireRole } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';

const router = Router();
router.use(requireAuth, requireRole('admin'));
const featureKeys = ['advanced', 'sim', 'vpn', 'mock_location'];

router.get('/overview', async (_req, res) => {
  const tables = ['users', 'proxy_products', 'subscriptions', 'orders', 'connection_sessions'] as const;
  const counts = await Promise.all(tables.map(async (table) => { const result = await supabaseAdmin.from(table).select('id', { count: 'exact', head: true }); return [table, result.error ? null : result.count ?? 0] as const; }));
  const { count: activeSubscriptions } = await supabaseAdmin.from('subscriptions').select('id', { count: 'exact', head: true }).eq('status', 'active');
  const { data: recentUsers } = await supabaseAdmin.from('users').select('id,email,full_name,role,status,created_at,last_login_at').order('created_at', { ascending: false }).limit(10);
  const { data: recentPlans } = await supabaseAdmin.from('proxy_products').select('id,name,protocol,country_name,price_monthly,is_active,stock_available,feature_flags,created_at').order('created_at', { ascending: false }).limit(10);
  return res.json({ data: { counts: Object.fromEntries(counts), activeSubscriptions: activeSubscriptions ?? 0, recentUsers: recentUsers ?? [], recentPlans: recentPlans ?? [] } });
});

router.get('/users', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit ?? 100), 1), 250);
  const { data, error } = await supabaseAdmin.from('users').select('id,email,full_name,role,status,is_email_verified,created_at,last_login_at,login_count').order('created_at', { ascending: false }).limit(limit);
  if (error) return res.status(500).json({ error: { code: 'USERS_UNAVAILABLE', message: 'Could not load users' } });
  return res.json({ data: data ?? [] });
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
  return res.json({ data: updated });
});

router.post('/users/:id/grant-plan', async (req, res) => {
  const planId = String(req.body?.planId ?? '');
  if (!planId) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'planId is required' } });
  const days = Math.min(Math.max(Number(req.body?.days ?? 30), 1), 3650);
  const { data, error } = await supabaseAdmin.from('subscriptions').insert({ user_id: req.params.id, product_id: null, subscription_plan_id: planId, status: 'active', duration_type: 'custom', started_at: new Date().toISOString(), expires_at: new Date(Date.now() + days * 86400000).toISOString(), auto_renew: false, will_renew: false }).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'PLAN_GRANT_FAILED', message: error.message } });
  return res.status(201).json({ data });
});

router.post('/users/:id/feature', async (req, res) => {
  const feature = String(req.body?.feature ?? '');
  if (!featureKeys.includes(feature)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Unsupported feature' } });
  const { data, error } = await supabaseAdmin.from('user_feature_grants').upsert({ user_id: req.params.id, feature_key: feature, enabled: req.body?.enabled !== false, expires_at: req.body?.expiresAt ?? null, granted_by: req.user!.id }, { onConflict: 'user_id,feature_key' }).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'FEATURE_GRANT_FAILED', message: error.message } });
  return res.json({ data });
});

router.get('/plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('proxy_products').select('id,name,description,protocol,country_code,country_name,city,price_daily,price_weekly,price_monthly,price_quarterly,price_yearly,device_limit,traffic_limit_gb,is_active,is_featured,stock_available,category,tags,feature_flags,created_at,updated_at').order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PLANS_UNAVAILABLE', message: 'Could not load plans' } });
  return res.json({ data: data ?? [] });
});

router.get('/subscription-plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('subscription_plans').select('id,name,description,price,currency,duration_days,is_active,is_featured,sort_order,feature_flags,created_at,updated_at').order('sort_order', { ascending: true }).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLANS_UNAVAILABLE', message: 'Could not load subscription plans' } });
  return res.json({ data: data ?? [] });
});

router.post('/subscription-plans', async (req, res) => {
  const body = req.body ?? {};
  if (typeof body.name !== 'string' || !body.name.trim()) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Subscription plan name is required' } });
  const flags = Object.fromEntries(featureKeys.map((key) => [key, body.featureFlags?.[key] === true]));
  const payload = { name: body.name.trim(), description: typeof body.description === 'string' ? body.description.trim() : '', price: Math.max(0, Number(body.price || 0)), currency: body.currency || 'USD', duration_days: Math.max(1, Number(body.durationDays || 30)), feature_flags: flags, is_active: body.isActive !== false, is_featured: body.isFeatured === true, sort_order: Number(body.sortOrder || 0) };
  const { data, error } = await supabaseAdmin.from('subscription_plans').insert(payload).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'SUBSCRIPTION_PLAN_CREATE_FAILED', message: error.message } });
  return res.status(201).json({ data });
});

router.patch('/subscription-plans/:id', async (req, res) => {
  const allowed = ['name', 'description', 'price', 'currency', 'duration_days', 'feature_flags', 'is_active', 'is_featured', 'sort_order'];
  const patch = Object.fromEntries(Object.entries(req.body ?? {}).filter(([key]) => allowed.includes(key)));
  if (!Object.keys(patch).length) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'No editable fields supplied' } });
  const { data, error } = await supabaseAdmin.from('subscription_plans').update({ ...patch, updated_at: new Date().toISOString() }).eq('id', req.params.id).select('*').single();
  if (error) return res.status(400).json({ error: { code: 'SUBSCRIPTION_PLAN_UPDATE_FAILED', message: error.message } });
  return res.json({ data });
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
