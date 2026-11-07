import { Router } from 'express';
import crypto from 'crypto';
import { requireAuth } from '../middleware/auth';
import { supabaseAdmin } from '../utils/supabase';
import { ezProxyService } from '../services/ezProxy';
import { proxyCheapService } from '../services/proxyCheap';
import { proxySellerService } from '../services/proxySeller';
import { encryptionService } from '../utils/encryption';
import { buildProxyCheapExecutePayload, buildProxyCheapLocations, buildProxyCheapOrderPayload, friendlyProxyCheapError, normalizeProxyCheapPlanId, parseProxyCheapLocation, ProxyCheapLocationError, validateProxyCheapLocation } from '../services/proxyCheapLocations';

const router = Router();
const money = (value: string | number) => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error('Invalid provider price');
  return Math.round(parsed * 100) / 100;
};

async function providerOptions(protocol = 'socks5') {
  const [plans, locations] = await Promise.all([
    ezProxyService.plans(),
    ezProxyService.locations({ protocol }),
  ]);
  const available = locations.filter((location) => location.status === undefined || location.status === 'available');
  if (!available.length) throw new Error('لا توجد مواقع متاحة لهذا البروتوكول حاليًا.');
  return {
    protocol,
    plans: plans.map((plan) => ({ id: plan.id, name: plan.name, duration_hours: plan.duration_hours, price: money(plan.price), currency: 'USD' })),
    locations: available.map((location) => ({
      id: location.id,
      country: location.country,
      state: location.state,
      isp: location.isp,
      protocols: location.protocols,
      status: location.status ?? 'available',
    })),
  };
}

const providerCatalog = [
  { id: 'ezproxy', number: 1, name: 'سيرفر 1', description: 'المزود الأساسي الحالي', kind: 'ezproxy' },
  { id: 'proxy-cheap', number: 2, name: 'سيرفر 2', description: 'Proxy-Cheap — أسعار مباشرة من المزود', kind: 'proxy-cheap' },
];
const firstObject = (value: unknown): Record<string, unknown> => value && typeof value === 'object' ? value as Record<string, unknown> : {};
const list = (value: unknown) => Array.isArray(value) ? value : [];
const optionObject = (value: unknown): Record<string, unknown> => typeof value === 'string' ? { code: value, id: value, name: value, label: value } : firstObject(value);
const providerPrice = (body: Record<string, unknown>) => {
  const data = firstObject(body.data);
  const parsed = Number(data.finalPrice ?? data.finalPriceInCurrency ?? data.total ?? data.totalPrice ?? data.total_price ?? body.finalPrice ?? body.finalPriceInCurrency ?? body.total ?? body.totalPrice);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error('المزود لم يرجع سعرًا صالحًا.');
  return money(parsed);
};
const geoLocations = (geo: unknown) => list(geo).flatMap((country) => {
  const c = optionObject(country); const code = String(c.code ?? c.country ?? '');
  return [{ id: code, country: String(c.name ?? code), state: '', isp: '', protocols: { http: true, socks5: true } }, ...list(c.regions).flatMap((region) => {
    const r = optionObject(region);
    return [{ id: `${code}|${String(r.name ?? r.code ?? '')}`, country: String(c.name ?? code), state: String(r.name ?? r.code ?? ''), isp: '', protocols: { http: true, socks5: true } }, ...list(r.cities).map((city) => { const x = optionObject(city); return { id: `${code}|${String(r.name ?? r.code ?? '')}|${String(x.name ?? x.code ?? '')}`, country: String(c.name ?? code), state: String(r.name ?? r.code ?? ''), isp: String(x.name ?? x.code ?? ''), protocols: { http: true, socks5: true } }; })];
  })];
});

async function externalProviderOptions(provider: string, requestedServiceId = '', requestedPlanId = '') {
  if (provider === 'proxy-cheap') {
    const services = await proxyCheapService.services();
    const residential = services.find((service) => service.id === (requestedServiceId || 'static-residential-ipv4'));
    if (!residential || residential.id !== 'static-residential-ipv4') throw new Error('منتج Static Residential IPv4 غير متاح في حساب Proxy-Cheap حاليًا.');
    const plans = (residential.plans?.length ? residential.plans : [{ id: '', label: residential.label }]).map((plan) => ({ id: `${residential.id}::${plan.id}`, serviceId: residential.id, providerPlanId: plan.id, name: plan.label, duration_hours: 720, price: 0, currency: 'USD' }));
    // The location catalog (countries / regions / cities / ISPs) is plan specific.
    // Always load it for the exact plan the user selected, and never silently fall
    // back to another plan: that would return locations that the selected plan rejects
    // (NO_SUCH_REGION / NO_SUCH_CITY / NO_SUCH_ISP).
    const requested = normalizeProxyCheapPlanId(requestedPlanId);
    const knownPlanIds = (residential.plans ?? []).map((plan) => plan.id).filter(Boolean);
    if (requested && knownPlanIds.length && !knownPlanIds.includes(requested)) throw new Error('الباقة المختارة غير متاحة لدى المزود حاليًا.');
    const selectedPlan = requested || knownPlanIds[0] || '';
    const setup = await proxyCheapService.setup(residential.id, selectedPlan);
    const s = firstObject(setup);
    const locations = buildProxyCheapLocations(s);
    return { provider, label: 'Proxy-Cheap Residential', protocol: 'http', serviceId: residential.id, plans, selectedPlanId: selectedPlan, locations, setup };
  }
  if (provider === 'proxy-seller') {
    const [packageInfo, geo] = await Promise.all([proxySellerService.residentialPackage(), proxySellerService.residentialGeo()]);
    const packageData = firstObject(packageInfo.data);
    return { provider, label: 'Proxy-Seller Residential', protocol: 'http', residential: true, plans: [{ id: 'current-package', name: `الباقة الحالية — ${(Number(packageData.traffic_left ?? 0) / 1073741824).toFixed(2)} GB متبقية`, duration_hours: 0, price: 0, currency: 'USD' }], locations: geoLocations(geo.data ?? geo), package: packageData, setup: geo };
  }
  return null;
}

/**
 * Builds the Proxy-Cheap price/execute payload and validates the selected location
 * against the setup of the SAME service + plan, so quote and checkout never send a
 * location that belongs to another plan.
 */
async function resolveProxyCheapPayload(body: Record<string, unknown>) {
  const serviceId = String(body.serviceId || 'static-residential-ipv4');
  const planId = normalizeProxyCheapPlanId(body.planId);
  const locationId = body.locationId ?? body.country;
  const setup = firstObject(await proxyCheapService.setup(serviceId, planId));
  validateProxyCheapLocation(setup, parseProxyCheapLocation(locationId));
  return { serviceId, payload: buildProxyCheapOrderPayload({ locationId, planId, periodMonths: body.periodMonths }) };
}
const proxyCheapFailure = (error: unknown, fallback: string) => {
  if (error instanceof ProxyCheapLocationError) return { status: 422, code: 'LOCATION_NOT_AVAILABLE', message: `${error.message} أعد اختيار الموقع ثم حاول مرة أخرى.` };
  return { status: 502, code: 'PROVIDER_UNAVAILABLE', message: error instanceof Error ? friendlyProxyCheapError(error.message) : fallback };
};

async function purchaseExternal(req: any, res: any, provider: string) {
  const body = firstObject(req.body); const requestId = typeof body.clientRequestId === 'string' && body.clientRequestId ? body.clientRequestId : crypto.randomUUID();
  const username = typeof body.username === 'string' ? body.username : ''; const password = typeof body.password === 'string' ? body.password : '';
  const country = String(body.country ?? body.locationId ?? ''); const planId = normalizeProxyCheapPlanId(body.planId);
  if (!country || !planId) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'اختر الدولة والمدة قبل المتابعة.' } });
  const { data: existing } = await supabaseAdmin.from('marketplace_proxy_orders').select('id,status,amount,provider_proxy_id').eq('client_request_id', requestId).eq('user_id', req.user.id).maybeSingle();
  if (existing?.status === 'completed') { const { data: saved } = await supabaseAdmin.from('user_proxy_credentials').select('*').eq('order_id', existing.id).single(); return res.json({ data: saved ? formatSavedProxy(saved, encryptionService.decrypt(saved.password_encrypted)) : existing }); }
  const sellerLocation = country.split('|');
  let proxyCheapServiceId = 'static-residential-ipv4';
  let payload: Record<string, unknown>;
  if (provider === 'proxy-cheap') {
    try { const resolved = await resolveProxyCheapPayload({ ...body, locationId: body.locationId ?? country, planId }); proxyCheapServiceId = resolved.serviceId; payload = resolved.payload; }
    catch (error) { const failure = proxyCheapFailure(error, 'تعذر التحقق من الموقع لدى المزود.'); return res.status(failure.status).json({ error: { code: failure.code, message: failure.message } }); }
  } else payload = { title: String(body.title ?? `Proxy Platform ${new Date().toISOString().slice(0, 10)}`).slice(0, 80), whitelist: '', geo: { country: sellerLocation[0] ?? '', region: sellerLocation[1] ?? '', city: sellerLocation[2] ?? '', isp: sellerLocation[3] ?? '' }, export: { ports: Math.min(1000, Math.max(1, Number(body.ports ?? 1))), ext: 'csv' }, rotation: Number(body.rotation ?? -1) };
  let price = 0;
  try { if (provider === 'proxy-cheap') { const quoted = await proxyCheapService.quote(proxyCheapServiceId, payload, body.trafficGb ?? 1); payload = quoted.payload; price = providerPrice(quoted.response); } }
  catch (error) { const failure = proxyCheapFailure(error, 'تعذر الحصول على السعر من المزود.'); return res.status(failure.status).json({ error: { code: failure.code, message: failure.message } }); }
  const orderId = existing?.id ?? crypto.randomUUID();
  if (!existing) {
    const { error } = await supabaseAdmin.from('marketplace_proxy_orders').insert({ id: orderId, user_id: req.user.id, product_id: null, provider_code: provider, client_request_id: requestId, status: 'pending', protocol: String(body.protocol ?? 'http').toLowerCase(), plan_id: 0, location_id: 0, amount: price, metadata: { source: 'external_provider', provider, request: payload } });
    if (error) return res.status(409).json({ error: { code: 'CLIENT_REQUEST_ID_CONFLICT', message: 'هذا الطلب قيد المعالجة أو تم استخدامه سابقًا.' } });
  }
  const { data: reserved, error: reserveError } = await supabaseAdmin.rpc('reserve_proxy_purchase', { p_user_id: req.user.id, p_amount: price, p_reference_id: orderId, p_description: `Proxy purchase (${provider})` });
  const reserve = Array.isArray(reserved) ? reserved[0] : reserved;
  if (reserveError || !reserve?.success) { await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'failed', error_message: reserve?.message ?? reserveError?.message ?? 'Insufficient wallet balance' }).eq('id', orderId); return res.status(409).json({ error: { code: 'INSUFFICIENT_BALANCE', message: 'رصيد المحفظة غير كافٍ لإتمام الشراء.', data: { balance: reserve?.new_balance ?? null, required: price } } }); }
  try {
    const result = provider === 'proxy-cheap'
      ? await proxyCheapService.execute(proxyCheapServiceId, buildProxyCheapExecutePayload(payload))
      : await proxySellerService.createResidentialList(payload);
    const data = firstObject(result.data); const providerOrderId = String(data.id ?? data.orderId ?? data.order_id ?? result.id ?? '');
    if (!providerOrderId) throw new Error('لم يرجع المزود رقم الطلب/القائمة.');
    const proxy = provider === 'proxy-cheap' ? await proxyCheapService.waitForProxy(providerOrderId) : { id: providerOrderId, status: 'active', protocol: 'http', host: sellerLocation[0] === 'US' ? 'us.res.proxy-seller.com' : 'res.proxy-seller.com', port: 10000, username: String(data.login ?? ''), password: String(data.password ?? ''), expiresAt: undefined, location: payload.geo as Record<string, unknown> };
    const proxyPassword = proxy.password || password;
    const { error: saveError } = await supabaseAdmin.from('user_proxy_credentials').insert({ user_id: req.user.id, order_id: orderId, provider_proxy_id: String(proxy.id), product_id: null, protocol: proxy.protocol, host: proxy.host, port: proxy.port, username: proxy.username || username, password_encrypted: encryptionService.encrypt(proxyPassword), proxy_url: `${proxy.protocol}://${proxy.username ? `${encodeURIComponent(proxy.username)}:${encodeURIComponent(proxyPassword)}@` : ''}${proxy.host}:${proxy.port}`, expires_at: proxy.expiresAt ?? null, location: proxy.location ?? {} });
    if (saveError) throw saveError;
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'completed', provider_order_id: providerOrderId, provider_proxy_id: String(proxy.id), completed_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq('id', orderId);
    return res.status(201).json({ data: { order_id: orderId, price, balance_after: reserve.new_balance, proxy: { provider_id: String(proxy.id), status: 'active', protocol: proxy.protocol, host: proxy.host, port: proxy.port, username: proxy.username || username, password: proxyPassword, url: `${proxy.protocol}://${proxy.host}:${proxy.port}`, expires_at: proxy.expiresAt } } });
  } catch (error) { await supabaseAdmin.rpc('refund_proxy_purchase', { p_user_id: req.user.id, p_amount: price, p_reference_id: orderId, p_description: `Refund failed ${provider} proxy purchase` }); await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'refunded', error_message: error instanceof Error ? error.message : 'Provider purchase failed' }).eq('id', orderId); return res.status(502).json({ error: { code: 'PROXY_PURCHASE_FAILED', message: error instanceof Error ? error.message : 'تعذر إنشاء البروكسي وتمت إعادة الرصيد.' } }); }
}

router.get('/', async (req, res) => {
  const q = supabaseAdmin.from('proxy_products').select('id,name,description,protocol,country_code,country_name,city,ip_type,price_daily,price_weekly,price_monthly,device_limit,stock_available,is_featured,category,tags').eq('is_active', true);
  if (req.query.country) q.eq('country_code', String(req.query.country).toUpperCase());
  if (req.query.protocol) q.eq('protocol', String(req.query.protocol));
  if (req.query.featured === 'true') q.eq('is_featured', true);
  const { data, error } = await q.order('is_featured', { ascending: false }).order('created_at', { ascending: false });
  if (error) return res.status(500).json({ error: { code: 'PRODUCTS_UNAVAILABLE', message: 'Could not load products' } });
  return res.json({ data: data ?? [] });
});

router.get('/providers', requireAuth, (_req, res) => res.json({ data: providerCatalog }));

// Provider-driven marketplace catalog. This endpoint intentionally does not read proxy_products.
router.get('/purchase-options', requireAuth, async (req, res) => {
  const provider = String(req.query.provider ?? 'ezproxy').toLowerCase();
  if (provider !== 'ezproxy') {
    try {
      const data = await externalProviderOptions(provider, String(req.query.serviceId ?? ''), String(req.query.planId ?? ''));
      if (!data) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'المزود غير صالح.' } });
      return res.json({ data });
    } catch (providerError) {
      return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر تحميل خيارات المزود.' } });
    }
  }
  const protocol = String(req.query.protocol ?? 'socks5').toLowerCase();
  if (!['http', 'socks5'].includes(protocol)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'البروتوكول غير صالح.' } });
  try {
    return res.json({ data: await providerOptions(protocol) });
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر تحميل خيارات الشراء.' } });
  }
});

router.get('/:id/purchase-options', requireAuth, async (req, res) => {
  const { data: product, error } = await supabaseAdmin.from('proxy_products').select('id,name,protocol,country_code,country_name,city,is_active').eq('id', req.params.id).eq('is_active', true).maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'PRODUCT_UNAVAILABLE', message: 'Could not load product' } });
  if (!product) return res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Product not found' } });
  try {
    const protocol = product.protocol?.toLowerCase() === 'http' ? 'http' : 'socks5';
    const options = await providerOptions(protocol);
    const locations = (await ezProxyService.locations({ countryCode: product.country_code ?? undefined, protocol })).filter((location) => location.status === undefined || location.status === 'available');
    return res.json({ data: { product, ...options, locations: locations.map((location) => ({ id: location.id, country: location.country, state: location.state, isp: location.isp, protocols: location.protocols })) } });
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر تحميل خيارات الشراء.' } });
  }
});

router.post('/purchase-quote', requireAuth, async (req, res) => {
  const provider = String(req.body?.provider ?? '').toLowerCase();
  if (provider === 'proxy-seller') return res.json({ data: { provider, price: null, price_available: false, message: 'Proxy-Seller residential API لا يعيد سعر قائمة منفصلًا؛ السعر مرتبط بالباقة الموجودة.' } });
  if (provider !== 'proxy-cheap') return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'المزود لا يحتاج إلى تسعير خارجي.' } });
  try {
    const { serviceId, payload } = await resolveProxyCheapPayload(firstObject(req.body));
    const price = providerPrice((await proxyCheapService.quote(serviceId, payload)).response);
    return res.json({ data: { provider, price, currency: 'USD', price_available: true } });
  } catch (error) { const failure = proxyCheapFailure(error, 'تعذر حساب السعر.'); return res.status(failure.status).json({ error: { code: failure.code, message: failure.message } }); }
});

// Provider-direct checkout. The selected location contains the country, state and ISP.
router.post('/purchase-proxy', requireAuth, async (req, res) => {
  const requestedProvider = String(req.body?.provider ?? 'ezproxy').toLowerCase();
  if (requestedProvider === 'proxy-cheap' || requestedProvider === 'proxy-seller') return purchaseExternal(req, res, requestedProvider);
  const { planId, locationId, protocol, username, password, clientRequestId } = req.body as Record<string, unknown>;
  const parsedPlanId = Number(planId);
  const parsedLocationId = Number(locationId);
  const selectedProtocol = String(protocol ?? 'socks5').toLowerCase();
  const requestId = typeof clientRequestId === 'string' && clientRequestId.length > 0 ? clientRequestId : crypto.randomUUID();
  if (!Number.isInteger(parsedPlanId) || !Number.isInteger(parsedLocationId) || !['http', 'socks5'].includes(selectedProtocol)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'خيار الشراء غير صالح.' } });
  if (typeof username !== 'string' || !/^[A-Za-z0-9._-]{3,64}$/.test(username)) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'اسم المستخدم يجب أن يكون بين 3 و64 محرفًا.' } });
  if (typeof password !== 'string' || password.length < 6 || password.length > 128) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'كلمة المرور يجب أن تكون بين 6 و128 محرفًا.' } });

  const { data: existing } = await supabaseAdmin.from('marketplace_proxy_orders').select('id,status,amount,provider_proxy_id').eq('client_request_id', requestId).eq('user_id', req.user!.id).maybeSingle();
  if (existing?.status === 'completed') {
    const { data: saved } = await supabaseAdmin.from('user_proxy_credentials').select('*').eq('order_id', existing.id).single();
    return res.status(200).json({ data: saved ? formatSavedProxy(saved, encryptionService.decrypt(saved.password_encrypted)) : existing });
  }

  let planPrice = 0;
  try {
    const [plans, locations] = await Promise.all([ezProxyService.plans(), ezProxyService.locations({ protocol: selectedProtocol })]);
    const selectedPlan = plans.find((plan) => plan.id === parsedPlanId);
    const selectedLocation = locations.find((location) => location.id === parsedLocationId && (location.status === undefined || location.status === 'available'));
    if (!selectedPlan || !selectedLocation) return res.status(409).json({ error: { code: 'OPTION_UNAVAILABLE', message: 'الخطة أو الموقع لم يعد متاحًا. حدّث الخيارات وحاول مجددًا.' } });
    planPrice = money(selectedPlan.price);
  } catch (providerError) {
    return res.status(502).json({ error: { code: 'PROVIDER_UNAVAILABLE', message: providerError instanceof Error ? providerError.message : 'تعذر التحقق من السعر.' } });
  }

  const orderId = existing?.id ?? crypto.randomUUID();
  if (!existing) {
    const { error: insertError } = await supabaseAdmin.from('marketplace_proxy_orders').insert({ id: orderId, user_id: req.user!.id, product_id: null, client_request_id: requestId, status: 'pending', protocol: selectedProtocol, plan_id: parsedPlanId, location_id: parsedLocationId, amount: planPrice, metadata: { source: 'provider_catalog' } });
    if (insertError) return res.status(409).json({ error: { code: 'CLIENT_REQUEST_ID_CONFLICT', message: 'هذا الطلب قيد المعالجة أو تم استخدامه سابقًا.' } });
  }

  const { data: reserved, error: reserveError } = await supabaseAdmin.rpc('reserve_proxy_purchase', { p_user_id: req.user!.id, p_amount: planPrice, p_reference_id: orderId, p_description: 'Provider proxy purchase' });
  const reserve = Array.isArray(reserved) ? reserved[0] : reserved;
  if (reserveError || !reserve?.success) {
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'failed', error_message: reserve?.message ?? reserveError?.message ?? 'Insufficient wallet balance' }).eq('id', orderId);
    return res.status(409).json({ error: { code: 'INSUFFICIENT_BALANCE', message: 'رصيد المحفظة غير كافٍ لإتمام الشراء.', data: { balance: reserve?.new_balance ?? null, required: planPrice } } });
  }

  try {
    const result = await ezProxyService.createOrder({ clientRequestId: requestId, locationId: parsedLocationId, planId: parsedPlanId, protocol: selectedProtocol as 'http' | 'socks5', username, password });
    const proxy = result.proxy;
    const { error: saveError } = await supabaseAdmin.from('user_proxy_credentials').insert({ user_id: req.user!.id, order_id: orderId, provider_proxy_id: String(proxy.id), product_id: null, protocol: proxy.protocol, host: proxy.host.trim(), port: proxy.port, username: proxy.username ?? username, password_encrypted: encryptionService.encrypt(proxy.password ?? password), proxy_url: proxy.url?.trim() ?? null, expires_at: proxy.expires_at ?? null, location: proxy.location ?? {} });
    if (saveError) throw saveError;
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'completed', provider_order_id: String(result.order.id), provider_proxy_id: String(proxy.id), completed_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq('id', orderId);
    return res.status(201).json({ data: { order_id: orderId, price: planPrice, balance_after: reserve.new_balance, proxy: formatProxy(proxy, proxy.password ?? password) } });
  } catch (providerError) {
    await supabaseAdmin.rpc('refund_proxy_purchase', { p_user_id: req.user!.id, p_amount: planPrice, p_reference_id: orderId, p_description: 'Refund failed proxy purchase' });
    await supabaseAdmin.from('marketplace_proxy_orders').update({ status: 'refunded', error_message: providerError instanceof Error ? providerError.message : 'Provider purchase failed' }).eq('id', orderId);
    return res.status(502).json({ error: { code: 'PROXY_PURCHASE_FAILED', message: providerError instanceof Error ? providerError.message : 'تعذر إنشاء البروكسي وتمت إعادة الرصيد.' } });
  }
});

// Legacy product checkout remains available for existing clients.
router.post('/:id/purchase-proxy', requireAuth, async (req, res) => {
  return res.status(410).json({ error: { code: 'LEGACY_CHECKOUT_DISABLED', message: 'استخدم كتالوج الشراء الجديد.' } });
});

router.get('/:id', async (req, res) => {
  const { data, error } = await supabaseAdmin.from('proxy_products').select('*').eq('id', req.params.id).eq('is_active', true).maybeSingle();
  if (error) return res.status(500).json({ error: { code: 'PRODUCT_UNAVAILABLE', message: 'Could not load product' } });
  if (!data) return res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Product not found' } });
  return res.json({ data });
});

function formatProxy(proxy: { id: number; status: string; protocol: string; host: string; port: number; username?: string; password?: string; url?: string; expires_at?: string }, password: string) {
  return { provider_id: String(proxy.id), status: proxy.status, protocol: proxy.protocol, host: proxy.host.trim(), port: proxy.port, username: proxy.username, password, url: proxy.url?.trim(), expires_at: proxy.expires_at };
}
function formatSavedProxy(row: Record<string, unknown>, password: string) {
  return { order_id: row.order_id, status: row.status, protocol: row.protocol, host: row.host, port: row.port, username: row.username, password, url: row.proxy_url, expires_at: row.expires_at };
}

export default router;
