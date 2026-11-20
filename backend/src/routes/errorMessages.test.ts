import express from 'express';
import request from 'supertest';

const mockState = {
  method: { code: 'bank', currency: 'SYP', address: '123', min_amount: '2500.00', exchange_rate: '100', is_enabled: true },
  insertError: null as null | { code: string; message: string },
  notifications: [{ id: 'n-1', title: 'العنوان الذي كتبه المدير', message: 'تعذر تحميل الإشعارات.', category: 'system' }],
};

jest.mock('../config', () => ({ __esModule: true, default: {
  encryption: { key: 'test-encryption-key-32-bytes-long', algorithm: 'aes-256-cbc' },
  ezProxy: { baseUrl: 'http://127.0.0.1', token: 'test', timeoutMs: 100 },
  proxyCheap: { baseUrl: 'http://127.0.0.1', apiKey: 'test', apiSecret: 'test', timeoutMs: 100 },
  proxySeller: { baseUrl: 'http://127.0.0.1', apiKey: 'test', timeoutMs: 100 },
  logging: { level: 'info' },
} }));

jest.mock('../middleware/auth', () => ({
  requireAuth: (req: any, _res: any, next: any) => { req.user = { id: 'user-1', role: 'admin' }; next(); },
  requireRole: () => (_req: any, _res: any, next: any) => next(),
}));

jest.mock('../utils/supabase', () => {
  const from = (table: string) => {
    const chain: any = {};
    for (const method of ['select', 'eq', 'order', 'limit', 'insert']) chain[method] = () => chain;
    chain.single = async () => table === 'wallet_topup_methods'
      ? { data: mockState.method, error: null }
      : { data: null, error: mockState.insertError };
    chain.then = (resolve: any, reject: any) => Promise.resolve({ data: table === 'notifications' ? mockState.notifications : [], error: null }).then(resolve, reject);
    return chain;
  };
  return { supabaseAdmin: { from }, supabaseClient: {} };
});

import authRouter from './auth';
import meRouter from './me';
import adminRouter from './admin';
import productsRouter from './products';
import { ezProxyService } from '../services/ezProxy';
import { proxyCheapService } from '../services/proxyCheap';

const app = express();
app.use(express.json());
app.use((_req, res, next) => { res.vary('Origin'); next(); });
app.use('/auth', authRouter);
app.use('/me', meRouter);
app.use('/admin', adminRouter);
app.use('/products', productsRouter);

const registration = { email: 'user@example.test', password: 'password123' };
const arabicDeviceMessage = 'لا يمكن إنشاء الحساب دون التحقق من الجهاز.';
const englishDeviceMessage = 'An account cannot be created without verifying the device.';

beforeEach(() => {
  mockState.method = { code: 'bank', currency: 'SYP', address: '123', min_amount: '2500.00', exchange_rate: '100', is_enabled: true };
  mockState.insertError = null;
});
afterEach(() => jest.restoreAllMocks());

describe('fixed API error localization', () => {
  it('keeps the legacy Arabic message, status and envelope when no language is sent', async () => {
    const response = await request(app).post('/auth/register').send(registration).expect(400);
    expect(response.body).toEqual({ error: { code: 'DEVICE_ID_REQUIRED', message: arabicDeviceMessage } });
    expect(response.headers.vary).toMatch(/Origin/i);
    expect(response.headers.vary).toMatch(/Accept-Language/i);
  });

  it.each(['en', 'en-US,en;q=0.9', 'fr, en;q=0.6', 'ar;q=0.4,en;q=0.9'])(
    'uses English for %s while keeping the response contract', async (language) => {
      const response = await request(app).post('/auth/register').set('Accept-Language', language).send(registration).expect(400);
      expect(response.body).toEqual({ error: { code: 'DEVICE_ID_REQUIRED', message: englishDeviceMessage } });
      expect(response.headers.vary).toMatch(/Origin/i);
      expect(response.headers.vary).toMatch(/Accept-Language/i);
    },
  );

  it.each(['ar', 'ar,en;q=0.5', 'en;q=0', 'fr', '*'])(
    'keeps Arabic when English is not preferred or not acceptable: %s', async (language) => {
      const response = await request(app).post('/auth/register').set('Accept-Language', language).send(registration).expect(400);
      expect(response.body.error.message).toBe(arabicDeviceMessage);
    },
  );

  it('localizes validation errors in different routers without changing status or code', async () => {
    const product = await request(app).get('/products/purchase-options?protocol=invalid').set('Accept-Language', 'en').expect(400);
    expect(product.body).toEqual({ error: { code: 'VALIDATION_ERROR', message: 'The protocol is invalid.' } });
    const admin = await request(app).post('/admin/broadcasts').set('Accept-Language', 'en').send({}).expect(400);
    expect(admin.body).toEqual({ error: { code: 'INVALID_BROADCAST', message: 'A title and message are required.' } });
    const legacy = await request(app).post('/products/old/purchase-proxy').set('Accept-Language', 'en').expect(410);
    expect(legacy.body).toEqual({ error: { code: 'LEGACY_CHECKOUT_DISABLED', message: 'Use the new purchase catalog.' } });
  });

  it('preserves dynamic amount and currency exactly within the translated fixed template', async () => {
    const call = () => request(app).post('/me/wallet/top-up/requests').send({ methodCode: 'bank', amount: 3 });
    const original = await call().expect(400);
    const english = await call().set('Accept-Language', 'en').expect(400);
    expect(original.body).toEqual({ error: { code: 'TOPUP_MINIMUM_NOT_MET', message: 'الحد الأدنى للشحن هو 2500.00 SYP.' } });
    expect(english.body).toEqual({ error: { code: 'TOPUP_MINIMUM_NOT_MET', message: 'The minimum top-up is 2500.00 SYP.' } });
    expect(english.headers.vary).toMatch(/Accept-Language/i);
  });

  it('does not translate a database error even if it equals a known static message', async () => {
    mockState.method.min_amount = '1';
    mockState.insertError = { code: 'other', message: 'تعذر إنشاء طلب الشحن.' };
    const external = await request(app).post('/me/wallet/top-up/requests').set('Accept-Language', 'en').send({ methodCode: 'bank', amount: 3 }).expect(400);
    expect(external.body.error).toEqual({ code: 'TOPUP_REQUEST_CREATE_FAILED', message: 'تعذر إنشاء طلب الشحن.' });
    mockState.insertError.code = '23505';
    const fixed = await request(app).post('/me/wallet/top-up/requests').set('Accept-Language', 'en').send({ methodCode: 'bank', amount: 3 }).expect(400);
    expect(fixed.body.error).toEqual({ code: 'TOPUP_REQUEST_CREATE_FAILED', message: 'Could not create the top-up request.' });
  });

  it('does not translate notification content or product/provider data', async () => {
    const notifications = await request(app).get('/me/notifications').set('Accept-Language', 'en').expect(200);
    expect(notifications.body.data).toEqual(mockState.notifications);
    const providers = await request(app).get('/products/providers').set('Accept-Language', 'en').expect(200);
    expect(providers.body.data[0]).toMatchObject({ name: 'سيرفر 1', description: 'المزود الأساسي الحالي' });
    const success = await request(app).post('/products/purchase-quote').set('Accept-Language', 'en').send({ provider: 'proxy-seller' }).expect(200);
    expect(success.body.data.message).toBe('Proxy-Seller residential API لا يعيد سعر قائمة منفصلًا؛ السعر مرتبط بالباقة الموجودة.');
  });

  it('translates only an app-generated error, not an identical provider error', async () => {
    const sameText = 'لا توجد مواقع متاحة لهذا البروتوكول حاليًا.';
    const plans = jest.spyOn(ezProxyService, 'plans').mockResolvedValue([]);
    jest.spyOn(ezProxyService, 'locations').mockResolvedValue([]);
    const generated = await request(app).get('/products/purchase-options').set('Accept-Language', 'en').expect(502);
    expect(generated.body.error).toEqual({ code: 'PROVIDER_UNAVAILABLE', message: 'No locations are currently available for this protocol.' });
    plans.mockRejectedValueOnce(new Error(sameText));
    const external = await request(app).get('/products/purchase-options').set('Accept-Language', 'en').expect(502);
    expect(external.body.error).toEqual({ code: 'PROVIDER_UNAVAILABLE', message: sameText });
  });

  it('localizes a fixed internal location code without changing the status', async () => {
    jest.spyOn(proxyCheapService, 'setup').mockResolvedValue({ countries: ['US'] });
    const response = await request(app).post('/products/purchase-quote').set('Accept-Language', 'en').send({ provider: 'proxy-cheap', planId: 'basic', locationId: 'CA' }).expect(422);
    expect(response.body.error).toEqual({ code: 'LOCATION_NOT_AVAILABLE', message: 'The selected country is not available for this plan. Choose a location and try again.' });
  });

  it('leaves previously English errors intact', async () => {
    const response = await request(app).post('/auth/register').set('Accept-Language', 'en').send({}).expect(400);
    expect(response.body).toEqual({ error: { code: 'VALIDATION_ERROR', message: 'Valid email and password of at least 8 characters are required' } });
  });
});
