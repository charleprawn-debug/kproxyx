import config from '../config';

type Json = Record<string, unknown>;
type RequestOptions = { method?: string; headers?: Record<string, string>; body?: string; signal?: AbortSignal };
export type SellerProxy = { id: string; orderId?: string; protocol: string; host: string; port: number; username: string; password: string; expiresAt?: string; location?: Json };
const obj = (v: unknown): Json => v && typeof v === 'object' ? v as Json : {};
const arr = (v: unknown) => Array.isArray(v) ? v : [];

class ProxySellerService {
  private url(path: string) { return `${config.proxySeller.baseUrl.replace(/\/$/, '')}/${encodeURIComponent(config.proxySeller.apiKey)}${path}`; }
  private async request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    if (!config.proxySeller.apiKey) throw new Error('Proxy-Seller is not configured');
    const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), config.proxySeller.timeoutMs);
    try {
      const response = await fetch(this.url(path), { ...init, signal: controller.signal, headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...(init.headers ?? {}) } });
      const body = obj(JSON.parse(await response.text()));
      const rawErrors = body.errors;
      const errors = (Array.isArray(rawErrors) ? rawErrors : Object.values(obj(rawErrors))).flatMap((e) => Array.isArray(e) ? e : [e]).map((e) => typeof e === 'string' ? e : obj(e).message).filter(Boolean).join('; ');
      if (!response.ok || body.status === 'error' || errors) {
        const message = errors || `Proxy-Seller request failed (${response.status})`;
        if (message.toLowerCase().includes('make order with resident first')) throw new Error('حساب Proxy-Seller لا يحتوي على باقة Residential مفعّلة. يجب إنشاء باقة Resident من لوحة المزود أولًا، لأن API العام لا يشتري هذه الباقة.');
        throw new Error(message);
      }
      return body as T;
    } finally { clearTimeout(timer); }
  }
  async reference(type = 'isp'): Promise<Json> { return this.request<Json>(`/reference/list/${encodeURIComponent(type)}`); }
  async residentialPackage(): Promise<Json> { return this.request<Json>('/resident/package'); }
  async residentialGeo(): Promise<Json> { return this.request<Json>('/resident/geo'); }
  async residentialLists(): Promise<Json> { return this.request<Json>('/resident/lists'); }
  async createResidentialList(payload: Json): Promise<Json> { return this.request<Json>('/resident/list/add', { method: 'POST', body: JSON.stringify(payload) }); }
  async renameResidentialList(payload: Json): Promise<Json> { return this.request<Json>('/resident/list/rename', { method: 'POST', body: JSON.stringify(payload) }); }
  async rotateResidentialList(payload: Json): Promise<Json> { return this.request<Json>('/resident/list/rotation', { method: 'POST', body: JSON.stringify(payload) }); }
  async deleteResidentialList(payload: Json): Promise<Json> { return this.request<Json>('/resident/list/delete', { method: 'DELETE', body: JSON.stringify(payload) }); }
  async calculate(payload: Json): Promise<Json> { return this.request<Json>('/order/calc', { method: 'POST', body: JSON.stringify(payload) }); }
  async make(payload: Json): Promise<Json> { return this.request<Json>('/order/make', { method: 'POST', body: JSON.stringify(payload) }); }
  async proxies(type = 'isp', orderId?: string): Promise<SellerProxy[]> {
    const query = orderId ? `?orderId=${encodeURIComponent(orderId)}` : '';
    const body = await this.request<Json>(`/proxy/list/${encodeURIComponent(type)}${query}`);
    const data = obj(body.data); const items = arr(data.items);
    return items.map((item) => { const x = obj(item); return { id: String(x.id ?? x.ip ?? ''), orderId: String(x.order_id ?? ''), protocol: String(x.protocol ?? 'HTTP').toLowerCase(), host: String(x.ip_only ?? x.ip ?? ''), port: Number(x.port_http ?? x.port_socks), username: String(x.login ?? ''), password: String(x.password ?? ''), expiresAt: typeof x.date_end === 'string' ? x.date_end : undefined, location: { country: x.country, countryCode: x.country_alpha3 } }; }).filter((x) => x.id && x.host && Number.isInteger(x.port) && x.port > 0);
  }
  async waitForProxy(type: string, orderId: string): Promise<SellerProxy> {
    for (let attempt = 0; attempt < 24; attempt += 1) { const list = await this.proxies(type, orderId); if (list[0]) return list[0]; await new Promise((resolve) => setTimeout(resolve, 2500)); }
    throw new Error('Proxy-Seller order is still provisioning');
  }
}
export const proxySellerService = new ProxySellerService();
export default proxySellerService;
