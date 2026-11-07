import http from 'http';
import type { AddressInfo } from 'net';

const mockConfig = { proxyCheap: { baseUrl: '', apiKey: 'k', apiSecret: 's', timeoutMs: 3000 } };
jest.mock('../config', () => ({ __esModule: true, default: mockConfig }));

import { proxyCheapService } from './proxyCheap';
import { buildProxyCheapExecutePayload, buildProxyCheapLocations, buildProxyCheapOrderPayload, validateProxyCheapLocation, parseProxyCheapLocation } from './proxyCheapLocations';

/**
 * Strict stand-in for the provider. It reproduces the behaviours seen in production:
 *  - any blank string is rejected with errors:[{code:'NOT_BLANK'}]
 *  - `basic` rejects unknown properties with a Spring style 400 that has NO `errors` key
 *  - `bandwidth` is sold per GB and needs `traffic`
 *  - unknown regions answer NO_SUCH_REGION
 */
const setups: Record<string, unknown> = {
  basic: { countries: ['US'], regions: { US: [{ id: 'Virginia', label: 'Virginia', isps: ['isp-1'] }] }, cities: { US: [] }, isps: { US: [{ id: 'isp-1', label: 'ISP 1' }] } },
  premium: { countries: ['US'], regions: { US: [{ id: 'Virginia', label: 'Virginia', isps: ['isp-1'] }] }, cities: { US: [] }, isps: { US: [{ id: 'isp-1', label: 'ISP 1' }] } },
  dedicated: { countries: ['US'], regions: { US: [{ id: 'Virginia', label: 'Virginia', isps: ['isp-1'] }] }, cities: { US: [] }, isps: { US: [{ id: 'isp-1', label: 'ISP 1' }] } },
  bandwidth: { countries: ['US'], regions: { US: [] }, cities: { US: [] }, isps: { US: [{ id: 'isp-1', label: 'ISP 1' }] } },
};
const calls: { path: string; body: any }[] = [];
const findBlank = (value: any, path = ''): string | undefined => {
  if (typeof value === 'string' && !value.trim()) return path;
  if (value && typeof value === 'object') for (const [k, v] of Object.entries(value)) { const hit = findBlank(v, path ? `${path}.${k}` : k); if (hit) return hit; }
  return undefined;
};
const send = (res: http.ServerResponse, status: number, body: unknown) => { res.statusCode = status; res.setHeader('content-type', 'application/json'); res.end(JSON.stringify(body)); };
const server = http.createServer((req, res) => {
  let raw = '';
  req.on('data', (c) => { raw += c; });
  req.on('end', () => {
    const body = raw ? JSON.parse(raw) : {};
    const path = req.url ?? '';
    calls.push({ path, body });
    if (req.headers['x-api-key'] !== 'k' || req.headers['x-api-secret'] !== 's') return send(res, 401, { errors: [{ code: 'UNAUTHORIZED' }] });
    if (path === '/order') return send(res, 200, { services: [{ id: 'static-residential-ipv4', label: 'Static', plans: ['basic', 'premium', 'dedicated'].map((id) => ({ id, label: id })) }] });
    const m = path.match(/^\/order\/([^/]+)(?:\/(price|execute))?$/);
    if (m && !m[2]) return send(res, 200, setups[body.planId] ?? setups.basic);
    if (m && m[2]) {
      const blank = findBlank(body);
      if (blank) return send(res, 400, { errors: [{ code: 'NOT_BLANK', parameters: [blank] }] });
      if (body.planId === 'basic' && (body.traffic !== undefined)) return send(res, 400, { timestamp: 'x', status: 400, error: 'Bad Request', path });
      if (body.planId === 'bandwidth' && body.traffic === undefined) return send(res, 400, { errors: [{ code: 'NOT_NULL', parameters: ['traffic'] }] });
      if (body.region && !(setups[body.planId] as any).regions.US.some((r: any) => r.id === body.region)) return send(res, 400, { errors: [{ code: 'NO_SUCH_REGION', parameters: [body.region] }] });
      if (m[2] === 'price') return send(res, 200, { finalPrice: 3.39 });
      if (!body.autoExtend || typeof body.autoExtend.isEnabled !== 'boolean') return send(res, 400, { errors: [{ code: 'NOT_NULL', parameters: ['autoExtend'] }] });
      return send(res, 200, { id: 'order-1' });
    }
    return send(res, 404, {});
  });
});

beforeAll(async () => { await new Promise<void>((r) => server.listen(0, '127.0.0.1', r)); mockConfig.proxyCheap.baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`; });
afterAll(async () => { await new Promise((r) => server.close(r)); });
beforeEach(() => { calls.length = 0; jest.spyOn(console, 'warn').mockImplementation(() => undefined); });
afterEach(() => jest.restoreAllMocks());

const flow = async (planId: string, trafficGb: unknown = 1) => {
  const setup = await proxyCheapService.setup('static-residential-ipv4', planId) as Record<string, unknown>;
  const location = buildProxyCheapLocations(setup).find((l) => l.state === 'Virginia' && l.isp === 'ISP 1');
  validateProxyCheapLocation(setup, parseProxyCheapLocation(location?.id));
  const payload = buildProxyCheapOrderPayload({ locationId: location?.id, planId, periodMonths: 1 });
  const quoted = await proxyCheapService.quote('static-residential-ipv4', payload, trafficGb);
  return proxyCheapService.execute('static-residential-ipv4', buildProxyCheapExecutePayload(quoted.payload));
};

describe('Proxy-Cheap checkout against a strict provider', () => {
  it.each(['basic', 'premium', 'dedicated'])('completes the full flow for the %s plan', async (plan) => {
    await expect(flow(plan)).resolves.toEqual({ id: 'order-1' });
    const execute = calls.find((c) => c.path.endsWith('/execute'))!.body;
    expect(execute).toMatchObject({ planId: plan, country: 'US', region: 'Virginia', ispId: 'isp-1', autoExtend: { isEnabled: false } });
    expect(findBlank(execute)).toBeUndefined();
  });

  it('reproduces the old NOT_BLANK failure when couponCode is sent empty (regression guard)', async () => {
    await expect(proxyCheapService.execute('static-residential-ipv4', { planId: 'premium', autoExtend: { isEnabled: false }, couponCode: '' } as any)).resolves.toEqual({ id: 'order-1' });
    const sent = calls.find((c) => c.path.endsWith('/execute'))!.body;
    expect(sent).not.toHaveProperty('couponCode');
  });

  it('adds traffic only for plans that need it (quote negotiation)', async () => {
    const quoted = await proxyCheapService.quote('static-residential-ipv4', { planId: 'bandwidth', quantity: 1 }, 1);
    expect(quoted.payload).toMatchObject({ traffic: 1 });
    const basic = await proxyCheapService.quote('static-residential-ipv4', { planId: 'basic', quantity: 1 }, 1);
    expect(basic.payload).not.toHaveProperty('traffic');
  });

  it('surfaces the real provider reason for errors without an `errors` key', async () => {
    await expect(proxyCheapService.price('static-residential-ipv4', { planId: 'basic', traffic: 1 })).rejects.toThrow(/request failed \(400\)|Bad Request|\/order/);
  });

  it('names the offending field for NOT_BLANK-style errors', async () => {
    await expect(proxyCheapService.execute('static-residential-ipv4', { planId: 'premium', autoExtend: {} } as any)).rejects.toThrow(/NOT_NULL autoExtend/);
  });

  it('does not retry the quote on non-validation errors', async () => {
    mockConfig.proxyCheap.apiKey = 'bad';
    await expect(proxyCheapService.quote('static-residential-ipv4', { planId: 'premium' })).rejects.toThrow(/UNAUTHORIZED/);
    expect(calls.filter((c) => c.path.endsWith('/price'))).toHaveLength(1);
    mockConfig.proxyCheap.apiKey = 'k';
  });

  it('treats a successful response with an empty errors array as success', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({ ok: true, status: 200, text: async () => JSON.stringify({ errors: [], id: 'x' }) } as Response);
    await expect(proxyCheapService.execute('s', { a: 1 })).resolves.toEqual({ errors: [], id: 'x' });
  });
});
