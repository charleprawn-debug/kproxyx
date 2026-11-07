import http from 'http';
import express from 'express';
import request from 'supertest';
import type { AddressInfo } from 'net';

const mockConfig = {
  proxyCheap: { baseUrl: '', apiKey: 'k', apiSecret: 's', timeoutMs: 3000 },
  ezProxy: { baseUrl: 'http://127.0.0.1:1', token: 't', timeoutMs: 100 },
  proxySeller: { baseUrl: 'http://127.0.0.1:1', apiKey: 'k', timeoutMs: 100 },
  encryption: { key: '0'.repeat(64), algorithm: 'aes-256-cbc' },
  logging: { level: 'info' },
};
jest.mock('../config', () => ({ __esModule: true, default: mockConfig, config: mockConfig }));
jest.mock('../middleware/auth', () => ({ requireAuth: (req: any, _res: any, next: any) => { req.user = { id: 'user-1' }; next(); } }));

const mockDb = { orders: [] as any[], credentials: [] as any[], rpc: [] as any[], failExecute: false };
jest.mock('../utils/supabase', () => {
  const table = (name: string) => {
    const chain: any = { _f: {} as Record<string, unknown> };
    chain.select = () => chain;
    chain.eq = (k: string, v: unknown) => { chain._f[k] = v; return chain; };
    chain.maybeSingle = async () => ({ data: name === 'marketplace_proxy_orders' ? mockDb.orders.find((o) => o.client_request_id === chain._f.client_request_id) ?? null : null });
    chain.single = async () => ({ data: null });
    chain.insert = async (row: any) => { (name === 'marketplace_proxy_orders' ? mockDb.orders : mockDb.credentials).push(row); return { error: null }; };
    chain.update = (patch: any) => ({ eq: async (_k: string, id: string) => { const o = mockDb.orders.find((x) => x.id === id); if (o) Object.assign(o, patch); return { error: null }; } });
    return chain;
  };
  return { supabaseAdmin: { from: table, rpc: async (fn: string, args: any) => { mockDb.rpc.push({ fn, args }); return { data: [{ success: true, new_balance: 50 }], error: null }; } } };
});

import router from './products';

const setup = { countries: ['US'], regions: { US: [{ id: 'Virginia', label: 'Virginia', isps: ['isp-1'] }] }, cities: { US: [] }, isps: { US: [{ id: 'isp-1', label: 'ISP 1' }] } };
const hasBlank = (v: any): boolean => (typeof v === 'string' && !v.trim()) || (v && typeof v === 'object' && Object.values(v).some(hasBlank));
const provider = http.createServer((req, res) => {
  let raw = ''; req.on('data', (c) => { raw += c; });
  req.on('end', () => {
    const body = raw ? JSON.parse(raw) : {}; const path = req.url ?? '';
    const send = (s: number, b: unknown) => { res.statusCode = s; res.setHeader('content-type', 'application/json'); res.end(JSON.stringify(b)); };
    if (path === '/order') return send(200, { services: [{ id: 'static-residential-ipv4', label: 'Static', plans: ['basic', 'premium', 'dedicated'].map((id) => ({ id, label: id })) }] });
    if (/^\/order\/[^/]+$/.test(path)) return send(200, setup);
    if (/\/(price|execute)$/.test(path)) {
      if (hasBlank(body)) return send(400, { errors: [{ code: 'NOT_BLANK' }] });
      if (body.planId === 'basic' && body.traffic !== undefined) return send(400, { status: 400, error: 'Bad Request' });
      if (path.endsWith('/price')) return send(200, { finalPrice: 4.5 });
      if (mockDb.failExecute) return send(400, { errors: [{ code: 'NO_SUCH_ISP', parameters: ['isp-1'] }] });
      return send(200, { id: 'ord-9' });
    }
    if (path === '/orders/ord-9/proxies') return send(200, [{ id: 'px-1', status: 'ACTIVE', proxyType: 'HTTP', authentication: { username: 'u', password: 'p' }, connection: { connectIp: '1.2.3.4', httpPort: 8080 }, metadata: { country: 'US' } }]);
    return send(404, {});
  });
});

const app = express(); app.use(express.json()); app.use('/products', router);
beforeAll(async () => { await new Promise<void>((r) => provider.listen(0, '127.0.0.1', r)); mockConfig.proxyCheap.baseUrl = `http://127.0.0.1:${(provider.address() as AddressInfo).port}`; });
afterAll(async () => { await new Promise((r) => provider.close(r)); });
beforeEach(() => { mockDb.orders.length = 0; mockDb.credentials.length = 0; mockDb.rpc.length = 0; mockDb.failExecute = false; jest.spyOn(console, 'warn').mockImplementation(() => undefined); });
afterEach(() => jest.restoreAllMocks());

// Exactly what the Android client sends (MainActivity.purchaseProxy).
const clientBody = (planId: string, locationId: string) => ({ clientRequestId: `req-${planId}-${Math.random()}`, provider: 'proxy-cheap', serviceId: 'static-residential-ipv4', planId, locationId, country: 'US', protocol: 'http', username: '', password: '', periodMonths: 1, trafficGb: 1, rotation: -1 });

describe('POST /products/purchase-proxy (Server 2 / Proxy-Cheap)', () => {
  it.each(['basic', 'premium', 'dedicated'])('buys a %s proxy end to end', async (plan) => {
    const options = await request(app).get(`/products/purchase-options?provider=proxy-cheap&serviceId=static-residential-ipv4&planId=${plan}`).expect(200);
    const location = options.body.data.locations.find((l: any) => l.state === 'Virginia' && l.isp === 'ISP 1');
    const quote = await request(app).post('/products/purchase-quote').send({ provider: 'proxy-cheap', serviceId: 'static-residential-ipv4', planId: plan, locationId: location.id, country: 'US' }).expect(200);
    expect(quote.body.data.price).toBe(4.5);
    const res = await request(app).post('/products/purchase-proxy').send(clientBody(plan, location.id));
    expect(res.status).toBe(201);
    expect(res.body.data.proxy).toMatchObject({ host: '1.2.3.4', port: 8080, protocol: 'http', username: 'u' });
    expect(mockDb.rpc.map((c) => c.fn)).toEqual(['reserve_proxy_purchase']);
    expect(mockDb.rpc[0].args.p_amount).toBe(4.5);
    expect(mockDb.orders[0].status).toBe('completed');
  });

  it('refunds the wallet and shows the exact provider reason when the provider rejects the order', async () => {
    mockDb.failExecute = true;
    const options = await request(app).get('/products/purchase-options?provider=proxy-cheap&serviceId=static-residential-ipv4&planId=premium').expect(200);
    const location = options.body.data.locations.find((l: any) => l.isp === 'ISP 1' && l.state === 'Virginia');
    const res = await request(app).post('/products/purchase-proxy').send(clientBody('premium', location.id));
    expect(res.status).toBe(502);
    expect(res.body.error.message).toContain('NO_SUCH_ISP');
    expect(mockDb.rpc.map((c) => c.fn)).toEqual(['reserve_proxy_purchase', 'refund_proxy_purchase']);
    expect(mockDb.orders[0].status).toBe('refunded');
  });
});
