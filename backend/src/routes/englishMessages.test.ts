import express from 'express';
import request from 'supertest';

const mockState: {
  profile: any; authUser: any; login: any; refresh: any; registration: any;
  tables: Record<string, any>; rpcs: Record<string, any>;
} = { profile: null, authUser: null, login: null, refresh: null, registration: null, tables: {}, rpcs: {} };

const mockSignIn = jest.fn(async () => mockState.login);
const mockRefresh = jest.fn(async () => mockState.refresh);
const mockGetUser = jest.fn(async () => mockState.authUser);
const mockCreateUser = jest.fn(async () => mockState.registration);

jest.mock('../config', () => ({ __esModule: true, default: {
  encryption: { key: 'test-encryption-key-32-bytes-long', algorithm: 'aes-256-cbc' },
  ezProxy: { baseUrl: 'http://127.0.0.1', token: 'test', timeoutMs: 100 },
  proxyCheap: { baseUrl: 'http://127.0.0.1', apiKey: 'test', apiSecret: 'test', timeoutMs: 100 },
  proxySeller: { baseUrl: 'http://127.0.0.1', apiKey: 'test', timeoutMs: 100 },
  logging: { level: 'info' },
} }));

jest.mock('../utils/supabase', () => {
  const from = (table: string) => {
    const chain: any = {};
    for (const method of ['select', 'eq', 'in', 'limit', 'order', 'insert', 'update', 'delete', 'upsert', 'gt', 'range']) chain[method] = () => chain;
    const result = () => mockState.tables[table] ?? (table === 'users'
      ? { data: mockState.profile, error: null } : { data: null, error: null });
    chain.single = async () => result();
    chain.maybeSingle = async () => result();
    chain.then = (resolve: any, reject: any) => Promise.resolve(result()).then(resolve, reject);
    return chain;
  };
  return {
    supabaseAdmin: { from, rpc: async (name: string) => mockState.rpcs[name] ?? { data: null, error: null },
      auth: { getUser: mockGetUser, admin: { createUser: mockCreateUser, deleteUser: jest.fn() } } },
    supabaseClient: { auth: { signInWithPassword: mockSignIn, refreshSession: mockRefresh } },
  };
});

import authRouter from './auth';
import adminRouter from './admin';
import meRouter from './me';
import productsRouter from './products';
import { ezProxyService } from '../services/ezProxy';

const app = express();
app.use(express.json());
app.use((_req, res, next) => { res.vary('Origin'); next(); });
app.use('/auth', authRouter);
app.use('/admin', adminRouter);
app.use('/me', meRouter);
app.use('/products', productsRouter);

const token = 'Bearer test-token';
const englishLogin = 'Email or password is incorrect';
const arabicLogin = 'البريد الإلكتروني أو كلمة المرور غير صحيحة.';

beforeEach(() => {
  jest.clearAllMocks();
  mockState.profile = { id: 'user-1', auth_id: 'auth-1', email: 'user@example.test', role: 'admin' };
  mockState.authUser = { data: { user: { id: 'auth-1' } }, error: null };
  mockState.login = { data: { session: null }, error: { message: 'provider failure' } };
  mockState.refresh = { data: { session: null }, error: { message: 'provider failure' } };
  mockState.registration = { data: { user: null }, error: null };
  mockState.tables = {};
  mockState.rpcs = {};
});
afterEach(() => jest.restoreAllMocks());

describe('originally English application messages', () => {
  it.each([
    [undefined, englishLogin], ['en', englishLogin], ['fr', englishLogin], ['*', englishLogin],
    ['ar;q=0,*;q=1', englishLogin], ['en;q=0.9,ar;q=0.2', englishLogin],
    ['ar', arabicLogin], ['AR-sa', arabicLogin], ['ar,en;q=0.5', arabicLogin],
    ['fr, ar;q=0.5', arabicLogin], ['en;q=0.2,ar;q=0.9', arabicLogin],
  ])('negotiates login explicitly for %s', async (language, message) => {
    const call = request(app).post('/auth/login').send({ email: '  USER@EXAMPLE.TEST ', password: 'wrong' });
    if (language) call.set('Accept-Language', language);
    const response = await call.expect(401);
    expect(response.body).toEqual({ error: { code: 'INVALID_CREDENTIALS', message } });
    expect(response.headers.vary).toMatch(/Origin/i);
    expect(response.headers.vary).toMatch(/Accept-Language/i);
    expect(mockSignIn).toHaveBeenCalledWith({ email: 'user@example.test', password: 'wrong' });
  });

  it('keeps successful login and refresh data and credentials unchanged', async () => {
    mockState.profile = null;
    const session = { access_token: 'access', refresh_token: 'refresh', expires_at: 12345 };
    mockState.login = { data: { user: { id: 'auth-1' }, session }, error: null };
    mockState.refresh = { data: { session }, error: null };
    const login = await request(app).post('/auth/login').set('Accept-Language', 'ar').send({ email: 'u@example.test', password: 'password123' }).expect(200);
    expect(login.body).toEqual({ data: { accessToken: 'access', refreshToken: 'refresh', expiresAt: 12345 } });
    const refresh = await request(app).post('/auth/refresh').set('Accept-Language', 'ar').send({ refreshToken: 'refresh' }).expect(200);
    expect(refresh.body).toEqual(login.body);
    expect(mockRefresh).toHaveBeenCalledWith({ refresh_token: 'refresh' });
  });

  it.each([['ar', 'رمز تحديث الجلسة غير صالح.'], ['en', 'Refresh token is invalid'], ['', 'Refresh token is invalid']])(
    'preserves refresh failure status and code for %s', async (language, message) => {
      const call = request(app).post('/auth/refresh').send({ refreshToken: 'bad' });
      if (language) call.set('Accept-Language', language);
      const response = await call.expect(401);
      expect(response.body).toEqual({ error: { code: 'REFRESH_FAILED', message } });
      expect(response.headers.vary).toMatch(/Accept-Language/i);
    },
  );

  it('keeps middleware authentication and role enforcement intact', async () => {
    const missing = await request(app).get('/me/notifications').set('Accept-Language', 'ar').expect(401);
    expect(missing.body).toEqual({ error: { code: 'UNAUTHORIZED', message: 'رمز المصادقة Bearer مطلوب.' } });
    expect(mockGetUser).not.toHaveBeenCalled();
    mockState.authUser = { data: { user: null }, error: { message: 'bad' } };
    const invalid = await request(app).get('/me/notifications').set('Authorization', token).set('Accept-Language', 'ar').expect(401);
    expect(invalid.body.error).toEqual({ code: 'UNAUTHORIZED', message: 'رمز المصادقة غير صالح أو منتهي الصلاحية.' });
    expect(mockGetUser).toHaveBeenCalledWith('test-token');
    mockState.authUser = { data: { user: { id: 'auth-1' } }, error: null };
    mockState.profile = null;
    const noProfile = await request(app).get('/me/notifications').set('Authorization', token).set('Accept-Language', 'ar').expect(403);
    expect(noProfile.body.error).toEqual({ code: 'PROFILE_REQUIRED', message: 'ملف المستخدم غير موجود.' });
    mockState.profile = { id: 'user-1', auth_id: 'auth-1', role: 'user' };
    const forbidden = await request(app).get('/admin/plans').set('Authorization', token).set('Accept-Language', 'ar').expect(403);
    expect(forbidden.body.error).toEqual({ code: 'FORBIDDEN', message: 'الصلاحيات غير كافية.' });
    expect(forbidden.headers.vary).toMatch(/Accept-Language/i);
  });

  it.each([
    ['ar', 'يجب إدخال بريد إلكتروني صالح وكلمة مرور من 8 محارف على الأقل.'],
    ['en', 'Valid email and password of at least 8 characters are required'],
    ['', 'Valid email and password of at least 8 characters are required'],
  ])('localizes registration validation without changing the envelope for %s', async (language, message) => {
    const call = request(app).post('/auth/register').send({});
    if (language) call.set('Accept-Language', language);
    const response = await call.expect(400);
    expect(response.body).toEqual({ error: { code: 'VALIDATION_ERROR', message } });
    expect(response.headers.vary).toMatch(/Accept-Language/i);
    expect(mockCreateUser).not.toHaveBeenCalled();
  });

  it('translates fallback literals but never a provider message with the same spelling', async () => {
    const registration = { email: 'u@example.test', password: 'password123', deviceId: 'device-id-1234' };
    const fallback = await request(app).post('/auth/register').set('Accept-Language', 'ar').send(registration).expect(400);
    expect(fallback.body.error).toEqual({ code: 'REGISTRATION_FAILED', message: 'تعذر إنشاء المستخدم.' });
    mockState.registration.error = { message: 'Could not create user' };
    const external = await request(app).post('/auth/register').set('Accept-Language', 'ar').send(registration).expect(400);
    expect(external.body.error).toEqual({ code: 'REGISTRATION_FAILED', message: 'Could not create user' });
  });

  it('preserves database messages and response data while translating only a fallback', async () => {
    mockState.rpcs.purchase_subscription_plan = { data: { success: false, detail: 42 }, error: null };
    const call = () => request(app).post('/me/subscription-plans/p1/activate').set('Authorization', token).set('Accept-Language', 'ar');
    const fallback = await call().expect(409);
    expect(fallback.body).toEqual({ error: { code: 'PLAN_PURCHASE_REJECTED', message: 'تعذر تفعيل الباقة.' }, data: { success: false, detail: 42 } });
    mockState.rpcs.purchase_subscription_plan.data.message = 'Could not activate plan';
    const external = await call().expect(409);
    expect(external.body).toEqual({ error: { code: 'PLAN_PURCHASE_REJECTED', message: 'Could not activate plan' }, data: { success: false, detail: 42, message: 'Could not activate plan' } });
  });

  it('preserves administrator-written notification text, even if it matches a translation key', async () => {
    const notifications = [{ id: 'n1', title: 'Email or password is incorrect', message: 'Could not activate plan' }];
    mockState.tables.notifications = { data: notifications, error: null };
    const response = await request(app).get('/me/notifications').set('Authorization', token).set('Accept-Language', 'ar').expect(200);
    expect(response.body).toEqual({ data: notifications });
  });

  it('translates fixed administrative and product errors across routes', async () => {
    const admin = await request(app).patch('/admin/users/u1').set('Authorization', token).set('Accept-Language', 'ar').send({}).expect(400);
    expect(admin.body.error).toEqual({ code: 'VALIDATION_ERROR', message: 'يجب إدخال حالة أو دور صالح.' });
    const product = await request(app).get('/products/unknown').set('Accept-Language', 'ar').expect(404);
    expect(product.body.error).toEqual({ code: 'NOT_FOUND', message: 'المنتج غير موجود.' });
    const legacy = await request(app).post('/me/wallet/top-up').set('Authorization', token).set('Accept-Language', 'ar').expect(409);
    expect(legacy.body.error).toEqual({ code: 'TOP_UP_NOT_AVAILABLE', message: 'طرق شحن المحفظة غير متاحة بعد.' });
  });

  it.each([
    ['ar', 'يجب إدخال حالة أو دور صالح.', 'المنتج غير موجود.', 'طرق شحن المحفظة غير متاحة بعد.', 'رمز المصادقة Bearer مطلوب.'],
    ['en', 'Valid status or role is required', 'Product not found', 'Wallet top-up methods are not available yet', 'Bearer token required'],
    ['', 'Valid status or role is required', 'Product not found', 'Wallet top-up methods are not available yet', 'Bearer token required'],
  ])('preserves every route contract for language %s', async (language, adminMessage, productMessage, walletMessage, authMessage) => {
    const admin = request(app).patch('/admin/users/u1').set('Authorization', token).send({});
    const product = request(app).get('/products/unknown');
    const wallet = request(app).post('/me/wallet/top-up').set('Authorization', token);
    const auth = request(app).get('/me/notifications');
    if (language) for (const call of [admin, product, wallet, auth]) call.set('Accept-Language', language);
    const responses = await Promise.all([admin.expect(400), product.expect(404), wallet.expect(409), auth.expect(401)]);
    expect(responses.map((response) => response.body.error)).toEqual([
      { code: 'VALIDATION_ERROR', message: adminMessage },
      { code: 'NOT_FOUND', message: productMessage },
      { code: 'TOP_UP_NOT_AVAILABLE', message: walletMessage },
      { code: 'UNAUTHORIZED', message: authMessage },
    ]);
    for (const response of responses) expect(response.headers.vary).toMatch(/Accept-Language/i);
  });

  it('does not translate a database error identical to an administrative fallback', async () => {
    const call = () => request(app).post('/admin/users/u1/wallet').set('Authorization', token).set('Accept-Language', 'ar').send({ amount: 2 });
    const fallback = await call().expect(400);
    expect(fallback.body.error).toEqual({ code: 'WALLET_UNAVAILABLE', message: 'المحفظة غير متاحة.' });
    mockState.rpcs.ensure_wallet = { data: null, error: { message: 'Wallet unavailable' } };
    const external = await call().expect(400);
    expect(external.body.error).toEqual({ code: 'WALLET_UNAVAILABLE', message: 'Wallet unavailable' });
  });

  it('distinguishes an app-generated invalid price from identical provider text', async () => {
    const plans = jest.spyOn(ezProxyService, 'plans').mockResolvedValue([{ id: 1, name: 'test', duration_hours: 1, price: 'bad' }] as any);
    jest.spyOn(ezProxyService, 'locations').mockResolvedValue([{ id: 1, status: 'available' }] as any);
    const call = () => request(app).get('/products/purchase-options').set('Authorization', token).set('Accept-Language', 'ar');
    const generated = await call().expect(502);
    expect(generated.body.error).toEqual({ code: 'PROVIDER_UNAVAILABLE', message: 'سعر المزود غير صالح.' });
    plans.mockRejectedValueOnce(new Error('Invalid provider price'));
    const external = await call().expect(502);
    expect(external.body.error).toEqual({ code: 'PROVIDER_UNAVAILABLE', message: 'Invalid provider price' });
  });
});
