process.env.NODE_ENV = 'test';
process.env.SUPABASE_URL = 'https://example.supabase.co';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-service-key';
process.env.JWT_SECRET = 'test-jwt-secret';
process.env.ENCRYPTION_KEY = 'test-encryption-key-32-bytes-long';
import request from 'supertest';
import app from './index';
import config from './config';

describe('health contract', () => {
  it('returns liveness status', async () => {
    const response = await request(app).get('/livez');
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: 'ok' });
  });
  it('returns a consistent 404 envelope', async () => {
    const response = await request(app).get('/not-found');
    expect(response.status).toBe(404);
    expect(response.body.error.code).toBe('NOT_FOUND');
  });
});

describe('language negotiation in the full application', () => {
  it('localizes a fixed API error and retains existing Vary values', async () => {
    const response = await request(app).post('/api/v1/auth/forgot-password')
      .set('Accept-Language', 'en').send({ email: 'invalid' }).expect(400);
    expect(response.body).toEqual({ error: { code: 'VALIDATION_ERROR', message: 'Enter a valid email address.' } });
    expect(response.headers.vary).toMatch(/Origin/i);
    expect(response.headers.vary).toMatch(/Accept-Language/i);
  });

  it('does not weaken authentication or change an existing English auth error', async () => {
    const response = await request(app).get('/api/v1/me/notifications').set('Accept-Language', 'en').expect(401);
    expect(response.body).toEqual({ error: { code: 'UNAUTHORIZED', message: 'Bearer token required' } });
  });

  it.each([
    ['ar', 'نقطة النهاية غير موجودة.'],
    ['en', 'Endpoint not found'],
    ['', 'Endpoint not found'],
  ])('negotiates the 404 without changing its code for %s', async (language, message) => {
    const call = request(app).get('/missing-endpoint');
    if (language) call.set('Accept-Language', language);
    const response = await call.expect(404);
    expect(response.body).toEqual({ error: { code: 'NOT_FOUND', message } });
    expect(response.headers.vary).toMatch(/Origin/i);
    expect(response.headers.vary).toMatch(/Accept-Language/i);
  });

  it('localizes only the generic internal error, never a development error', async () => {
    const sendInvalid = () => request(app).post('/api/v1/auth/login').set('Content-Type', 'application/json').send('{');
    const ar = await sendInvalid().set('Accept-Language', 'ar').expect(500);
    expect(ar.body).toEqual({ error: { code: 'INTERNAL_ERROR', message: 'حدث خطأ داخلي في الخادم.' } });
    expect(ar.headers.vary).toMatch(/Accept-Language/i);
    const en = await sendInvalid().set('Accept-Language', 'en').expect(500);
    const absent = await sendInvalid().expect(500);
    expect(en.body).toEqual({ error: { code: 'INTERNAL_ERROR', message: 'Internal server error' } });
    expect(absent.body).toEqual(en.body);
    const env = config.env;
    try {
      config.env = 'development';
      const development = await sendInvalid().set('Accept-Language', 'ar').expect(500);
      const baseline = await sendInvalid().expect(500);
      expect(development.body).toEqual(baseline.body);
      expect(development.body.error.message).not.toBe('حدث خطأ داخلي في الخادم.');
    } finally {
      config.env = env;
    }
  });
});
