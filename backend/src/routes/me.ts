import { Router } from 'express';
import crypto from 'crypto';
import { requireAuth } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';
import { encryptionService } from '../utils/encryption';
import { verifyShamCash, verifySyriatel } from '../services/syriaPayments';
import { createWhiteLabelPayment, isConfigured as isOxaConfigured } from '../services/oxaPay';
import { proxyCheapService } from '../services/proxyCheap';
import { proxySellerService } from '../services/proxySeller';

const router = Router();
router.use(requireAuth);

router.get('/', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('users').select('id,email,full_name,avatar_url,country_code,language,role,status,is_email_verified,created_at').eq('id', req.user!.id).single();
  if (error) return res.status(500).json({ error: { code: 'PROFILE_UNAVAILABLE', message: 'Could not load profile' } });
  return res.json({ data });
});

router.get('/subscriptions', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('subscriptions').select('*,proxy_products(id,name,description,protocol,country_code,country_name,price_monthly,feature_flags),subscription_plans(id,name,description,price,currency,duration_days,feature_flags)').eq('user_id', req.user!.id).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTIONS_UNAVAILABLE', message: 'Could not load subscriptions' } });
  return res.json({ data: data ?? [] });
});

router.get('/subscription-plans', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('subscription_plans').select('id,name,description,price,currency,duration_days,is_active,is_featured,feature_flags').eq('is_active', true).order('is_featured', { ascending: false }).order('sort_order', { ascending: true });
  if (error) return res.status(500).json({ error: { code: 'SUBSCRIPTION_PLANS_UNAVAILABLE', message: 'Could not load subscription plans' } });
  return res.json({ data: data ?? [] });
});

router.get('/wallet', async (req, res) => {
  const { data, error } = await supabaseAdmin.rpc('ensure_wallet', { p_user_id: req.user!.id });
  if (error) return res.status(500).json({ error: { code: 'WALLET_UNAVAILABLE', message: 'Could not load wallet' } });
  return res.json({ data });
});

router.get('/wallet/transactions', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('wallet_transactions').select('id,amount,balance_after,transaction_type,status,description,reference_id,created_at').eq('user_id', req.user!.id).order('created_at', { ascending: false }).limit(100);
  if (error) return res.status(500).json({ error: { code: 'WALLET_HISTORY_UNAVAILABLE', message: 'Could not load wallet history' } });
  return res.json({ data: data ?? [] });
});

router.get('/proxies', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('user_proxy_credentials').select('id,order_id,provider_proxy_id,protocol,host,port,username,password_encrypted,proxy_url,status,expires_at,location,created_at').eq('user_id', req.user!.id).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PROXIES_UNAVAILABLE', message: 'Could not load saved proxies' } });
  try {
    return res.json({ data: (data ?? []).map((row) => ({ ...row, password_encrypted: undefined, password: encryptionService.decrypt(row.password_encrypted) })) });
  } catch {
    return res.status(500).json({ error: { code: 'PROXY_CREDENTIALS_UNAVAILABLE', message: 'Could not decrypt proxy credentials' } });
  }
});

router.patch('/proxies/:id', async (req, res) => {
  const action = String(req.body?.action ?? '').trim();
  const { data: proxy, error: proxyError } = await supabaseAdmin.from('user_proxy_credentials').select('id,order_id,provider_proxy_id,status').eq('id', req.params.id).eq('user_id', req.user!.id).single();
  if (proxyError || !proxy) return res.status(404).json({ error: { code: 'PROXY_NOT_FOUND', message: 'البروكسي غير موجود.' } });
  const { data: order, error: orderError } = await supabaseAdmin.from('marketplace_proxy_orders').select('provider_code').eq('id', proxy.order_id).eq('user_id', req.user!.id).single();
  if (orderError || !order?.provider_code) return res.status(409).json({ error: { code: 'PROVIDER_UNKNOWN', message: 'لا يمكن تحديد مزود البروكسي.' } });
  try {
    if (order.provider_code === 'proxy-cheap') await proxyCheapService.proxyAction(String(proxy.provider_proxy_id), action, req.body?.payload ?? {});
    else if (order.provider_code === 'proxy-seller') {
      const payload = { ...(req.body?.payload ?? {}), id: Number(proxy.provider_proxy_id) };
      if (action === 'rename') await proxySellerService.renameResidentialList(payload);
      else if (action === 'rotation') await proxySellerService.rotateResidentialList(payload);
      else if (action === 'delete') { await proxySellerService.deleteResidentialList(payload); await supabaseAdmin.from('user_proxy_credentials').update({ status: 'deleted', updated_at: new Date().toISOString() }).eq('id', proxy.id); }
      else return res.status(400).json({ error: { code: 'UNSUPPORTED_PROXY_ACTION', message: 'هذه العملية غير مدعومة لهذا المزود.' } });
    } else return res.status(400).json({ error: { code: 'UNSUPPORTED_PROXY_ACTION', message: 'إدارة هذا المزود غير متاحة من API.' } });
    return res.json({ data: { id: proxy.id, action, status: action === 'delete' ? 'deleted' : proxy.status } });
  } catch (error) { return res.status(502).json({ error: { code: 'PROXY_ACTION_FAILED', message: error instanceof Error ? error.message : 'تعذر تنفيذ العملية لدى المزود.' } }); }
});

router.post('/wallet/top-up', async (_req, res) => res.status(409).json({ error: { code: 'TOP_UP_NOT_AVAILABLE', message: 'Wallet top-up methods are not available yet' } }));

router.get('/wallet/top-up/methods', async (_req, res) => {
  const { data, error } = await supabaseAdmin.from('wallet_topup_methods').select('code,name,currency,network,address,min_amount,exchange_rate,instructions,sort_order,is_enabled').eq('is_enabled', true).order('sort_order', { ascending: true });
  if (error) return res.status(500).json({ error: { code: 'TOPUP_METHODS_UNAVAILABLE', message: 'تعذر تحميل طرق الشحن.' } });
  return res.json({ data: data ?? [] });
});

router.get('/wallet/top-up/requests', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('wallet_topup_requests').select('id,method_code,submitted_amount,submitted_currency,expected_usd,transaction_reference,status,verification_source,admin_note,created_at,updated_at').eq('user_id', req.user!.id).order('created_at', { ascending: false }).limit(50);
  if (error) return res.status(500).json({ error: { code: 'TOPUP_REQUESTS_UNAVAILABLE', message: 'تعذر تحميل طلبات الشحن.' } });
  return res.json({ data: data ?? [] });
});

router.post('/wallet/top-up/requests', async (req, res) => {
  const methodCode = String(req.body?.methodCode ?? '').trim();
  const amount = Number(req.body?.amount);
  if (!methodCode || !Number.isFinite(amount) || amount <= 0 || amount > 1_000_000_000) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'أدخل مبلغًا صالحًا.' } });
  const { data: method, error: methodError } = await supabaseAdmin.from('wallet_topup_methods').select('code,name,currency,network,address,min_amount,exchange_rate,instructions,is_enabled').eq('code', methodCode).eq('is_enabled', true).single();
  if (methodError || !method) return res.status(409).json({ error: { code: 'TOPUP_METHOD_UNAVAILABLE', message: 'طريقة الشحن غير متاحة حاليًا.' } });
  if (!method.address?.trim() && method.currency !== 'USDT') return res.status(409).json({ error: { code: 'TOPUP_METHOD_NOT_CONFIGURED', message: 'لم يجهز الأدمن عنوان هذه الطريقة بعد.' } });
  if (amount < Number(method.min_amount)) return res.status(400).json({ error: { code: 'TOPUP_MINIMUM_NOT_MET', message: `الحد الأدنى للشحن هو ${method.min_amount} ${method.currency}.` } });
  const expectedUsd = method.currency === 'SYP' ? amount / Number(method.exchange_rate) : amount;
  if (!Number.isFinite(expectedUsd) || expectedUsd <= 0) return res.status(400).json({ error: { code: 'INVALID_EXCHANGE_RATE', message: 'سعر الصرف غير صالح.' } });
  const requestId = crypto.randomUUID();
  if (method.currency === 'USDT' && !isOxaConfigured()) return res.status(503).json({ error: { code: 'OXAPAY_NOT_CONFIGURED', message: 'الدفع بالـUSDT غير مهيأ حاليًا.' } });
  const { data, error } = await supabaseAdmin.from('wallet_topup_requests').insert({ id: requestId, user_id: req.user!.id, method_code: method.code, submitted_amount: amount, submitted_currency: method.currency, expected_usd: Math.round(expectedUsd * 100) / 100, transaction_reference: `pending-${requestId}`, provider_order_id: method.currency === 'USDT' ? requestId : null, provider_network: method.network || null, status: 'pending' }).select('id,method_code,submitted_amount,submitted_currency,expected_usd,status,created_at,provider_order_id').single();
  if (error) return res.status(400).json({ error: { code: 'TOPUP_REQUEST_CREATE_FAILED', message: error.code === '23505' ? 'تعذر إنشاء طلب الشحن.' : error.message } });
  if (method.currency !== 'USDT') return res.status(201).json({ data: { ...data, method } });
  try {
    const payment = await createWhiteLabelPayment(expectedUsd, method.network ?? '', requestId);
    const { error: paymentUpdateError } = await supabaseAdmin.from('wallet_topup_requests').update({ provider_payment_id: payment.trackId, provider_address: payment.address, provider_pay_amount: payment.payAmount, provider_network: payment.network, provider_payload: payment.raw, updated_at: new Date().toISOString() }).eq('id', requestId);
    if (paymentUpdateError) throw paymentUpdateError;
    return res.status(201).json({ data: { ...data, method: { ...method, address: payment.address, instructions: `${method.instructions || ''}\nأرسل ${payment.payAmount} USDT على شبكة ${payment.network}.`.trim() }, payment: { trackId: payment.trackId, address: payment.address, payAmount: payment.payAmount, network: payment.network } } });
  } catch {
    await supabaseAdmin.from('wallet_topup_requests').update({ status: 'rejected', admin_note: 'OxaPay payment creation failed', updated_at: new Date().toISOString() }).eq('id', requestId);
    return res.status(503).json({ error: { code: 'OXAPAY_PAYMENT_CREATE_FAILED', message: 'تعذر إنشاء عنوان USDT حاليًا. حاول لاحقًا.' } });
  }
});

router.post('/wallet/top-up/requests/:id/verify', async (req, res) => {
  const reference = String(req.body?.transactionReference ?? '').trim();
  if (!/^[A-Za-z0-9._:/-]{3,255}$/.test(reference)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'أدخل رقم عملية صالحًا.' } });
  const { data: request, error: requestError } = await supabaseAdmin.from('wallet_topup_requests').select('id,status,method_code,submitted_amount,submitted_currency,expected_usd,created_at,provider_payment_id,provider_order_id,provider_network,provider_payload').eq('id', req.params.id).eq('user_id', req.user!.id).single();
  if (requestError || !request) return res.status(404).json({ error: { code: 'TOPUP_REQUEST_NOT_FOUND', message: 'طلب الشحن غير موجود.' } });
  if (request.status !== 'pending') return res.status(409).json({ error: { code: 'TOPUP_REQUEST_CLOSED', message: 'تمت معالجة طلب الشحن مسبقًا.' } });
  const { data: duplicate } = await supabaseAdmin.from('wallet_topup_requests').select('id').eq('method_code', request.method_code).eq('transaction_reference', reference).neq('id', request.id).maybeSingle();
  if (duplicate) return res.status(409).json({ error: { code: 'DUPLICATE_TRANSACTION_REFERENCE', message: 'رقم العملية مستخدم مسبقًا.' } });
  const { data, error } = await supabaseAdmin.from('wallet_topup_requests').update({ transaction_reference: reference, verification_source: 'pending_review', updated_at: new Date().toISOString() }).eq('id', request.id).eq('user_id', req.user!.id).eq('status', 'pending').select('id,method_code,submitted_amount,submitted_currency,expected_usd,transaction_reference,status,verification_source,created_at,updated_at').single();
  if (error) return res.status(400).json({ error: { code: 'TOPUP_VERIFY_SUBMISSION_FAILED', message: error.message } });
  if (request.submitted_currency === 'USDT') return res.json({ data, message: 'تم إرسال الدفع إلى OxaPay. سيضاف الرصيد تلقائيًا بعد وصول Webhook المؤكد.' });
  const { data: method } = await supabaseAdmin.from('wallet_topup_methods').select('address').eq('code', request.method_code).single();
  const verification = request.method_code === 'syriatel_cash'
    ? await verifySyriatel(Number(request.submitted_amount), reference, request.created_at)
    : await verifyShamCash(Number(request.submitted_amount), request.submitted_currency, reference, String(method?.address ?? ''), request.created_at);
  if (!verification.ok) {
    if (verification.reason === 'not_configured') return res.status(503).json({ error: { code: 'SYRIA_PROVIDER_NOT_CONFIGURED', message: 'مزود التحقق غير مهيأ حاليًا.' }, data });
    return res.status(409).json({ error: { code: 'TOPUP_NOT_VERIFIED', message: 'لم يتم العثور على عملية مطابقة. تأكد من الرقم والمبلغ ثم حاول مجددًا.', data } });
  }
  await supabaseAdmin.from('wallet_topup_requests').update({ provider_reference: verification.externalRef ?? reference, provider_payload: verification.raw ?? {}, verification_source: 'api_syria', updated_at: new Date().toISOString() }).eq('id', request.id).eq('status', 'pending');
  const { data: approval, error: approvalError } = await supabaseAdmin.rpc('approve_wallet_topup', { p_request_id: request.id, p_admin_id: null, p_admin_note: 'Automatically verified by API Syria' });
  const result = Array.isArray(approval) ? approval[0] : approval;
  if (approvalError || !result?.success) return res.status(503).json({ error: { code: 'TOPUP_AUTO_APPROVAL_FAILED', message: 'تم التحقق من العملية لكن تعذر تحديث الرصيد. حاول تحديث الصفحة.' } });
  await supabaseAdmin.from('wallet_topup_requests').update({ verified_at: new Date().toISOString() }).eq('id', request.id);
  return res.json({ data: { ...data, status: 'verified', verification_source: 'api_syria' }, message: 'تم التحقق وإضافة الرصيد تلقائيًا.' });
});

router.get('/access', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('subscriptions').select('status,expires_at,proxy_products(feature_flags),subscription_plans(feature_flags)').eq('user_id', req.user!.id).eq('status', 'active').gt('expires_at', new Date().toISOString());
  if (error) return res.status(500).json({ error: { code: 'ACCESS_UNAVAILABLE', message: 'Could not load feature access' } });
  const access = { advanced: false, sim: false, vpn: false, mock_location: false };
  for (const row of data ?? []) for (const flags of [row.proxy_products, row.subscription_plans]) for (const [key, value] of Object.entries((flags as { feature_flags?: Record<string, boolean> } | null)?.feature_flags ?? {})) if (key in access && value === true) access[key as keyof typeof access] = true;
  const { data: grants } = await supabaseAdmin.from('user_feature_grants').select('feature_key,enabled,expires_at').eq('user_id', req.user!.id).eq('enabled', true);
  for (const grant of grants ?? []) if ((!grant.expires_at || new Date(grant.expires_at) > new Date()) && grant.feature_key in access) access[grant.feature_key as keyof typeof access] = true;
  return res.json({ data: access });
});

router.post('/subscription-plans/:id/activate', async (req, res) => {
  const { data, error } = await supabaseAdmin.rpc('purchase_subscription_plan', { p_user_id: req.user!.id, p_plan_id: req.params.id });
  if (error) return res.status(500).json({ error: { code: 'PLAN_PURCHASE_FAILED', message: error.message } });
  const result = Array.isArray(data) ? data[0] : data;
  if (!result?.success) return res.status(409).json({ error: { code: 'PLAN_PURCHASE_REJECTED', message: result?.message ?? 'Could not activate plan' }, data: result });
  return res.status(201).json({ data: result });
});

export default router;
