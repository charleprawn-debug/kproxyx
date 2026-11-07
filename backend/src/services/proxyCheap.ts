import config from '../config';
import logger from '../utils/logger';

type Json = Record<string, unknown>;
type RequestOptions = { method?: string; headers?: Record<string, string>; body?: string; signal?: AbortSignal };
export type CheapService = { id: string; label: string; plans?: { id: string; label: string }[] };
export type CheapProxy = { id: string; status: string; protocol: string; host: string; port: number; username: string; password: string; expiresAt?: string; location?: Json };

const asObject = (value: unknown): Json => value && typeof value === 'object' ? value as Json : {};
const asArray = (value: unknown) => Array.isArray(value) ? value : [];
const textOf = (value: unknown): string => (typeof value === 'string' ? value.trim() : typeof value === 'number' && Number.isFinite(value) ? String(value) : '');

/** Collects every readable detail (code, message, field/parameter names) from a provider error value. */
const collectErrorParts = (value: unknown, depth = 0): string[] => {
  if (depth > 4 || value === null || value === undefined) return [];
  const direct = textOf(value);
  if (direct) return [direct];
  if (Array.isArray(value)) return value.flatMap((item) => collectErrorParts(item, depth + 1));
  if (typeof value === 'object') {
    const item = value as Record<string, unknown>;
    const keys = ['code', 'message', 'detail', 'title', 'field', 'fieldName', 'path', 'property', 'parameter', 'parameters', 'violations', 'fieldErrors', 'errors', 'error'];
    return keys.flatMap((key) => collectErrorParts(item[key], depth + 1));
  }
  return [];
};

const unique = (values: string[]) => [...new Set(values.filter(Boolean))];

/** Backwards compatible helper: readable text of the provider `errors` value. */
export const providerErrorMessage = (value: unknown): string => unique(collectErrorParts(value)).join(' ');

const isEmptyErrors = (value: unknown) =>
  value === undefined || value === null || value === false || value === '' ||
  (Array.isArray(value) && value.length === 0) ||
  (typeof value === 'object' && !Array.isArray(value) && Object.keys(value as object).length === 0);

export class ProxyCheapApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly body: unknown, public readonly raw: string) {
    super(message);
    this.name = 'ProxyCheapApiError';
  }
  /** True for provider-side validation rejections (nothing was created at the provider). */
  get isValidation() { return this.status === 400 || this.status === 422; }
}

/**
 * Builds a precise message from ANY error shape: `errors: [...]`, Spring style
 * `{status,error,message,path}`, `{message}`, plain text or an empty body, so the real
 * provider reason is never reduced to a bare "request failed (400)".
 */
export const describeProviderFailure = (body: unknown, status: number, raw = ''): string => {
  const obj = asObject(body);
  const parts = unique([
    ...collectErrorParts(obj.errors),
    ...collectErrorParts(obj.message),
    ...collectErrorParts(obj.detail),
    ...collectErrorParts(obj.title),
    ...collectErrorParts(obj.violations),
    ...collectErrorParts(obj.fieldErrors),
    ...collectErrorParts(typeof obj.error === 'string' && obj.error !== 'Bad Request' ? obj.error : obj.error && typeof obj.error === 'object' ? obj.error : undefined),
  ]);
  if (parts.length) return parts.join(' ');
  const snippet = raw.trim().slice(0, 300);
  return `Proxy-Cheap request failed (${status})${snippet ? `: ${snippet}` : ' with an empty response'}`;
};

/** Removes undefined, null and blank-string values (recursively): the provider rejects them as NOT_BLANK. */
export const pruneBlank = <T>(value: T): T => {
  if (Array.isArray(value)) return value.map((item) => pruneBlank(item)).filter((item) => item !== undefined) as unknown as T;
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      if (item === undefined || item === null) continue;
      if (typeof item === 'string' && !item.trim()) continue;
      const cleaned = pruneBlank(item);
      if (cleaned && typeof cleaned === 'object' && !Array.isArray(cleaned) && Object.keys(cleaned as object).length === 0) continue;
      out[key] = cleaned;
    }
    return out as T;
  }
  return value;
};

class ProxyCheapService {
  private get baseUrl() { return config.proxyCheap.baseUrl.replace(/\/$/, ''); }
  private async request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    if (!config.proxyCheap.apiKey || !config.proxyCheap.apiSecret) throw new Error('Proxy-Cheap is not configured');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.proxyCheap.timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, { ...init, signal: controller.signal, headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0 (compatible; KUN-Proxy/1.0)', 'X-Api-Key': config.proxyCheap.apiKey, 'X-Api-Secret': config.proxyCheap.apiSecret, ...(init.headers ?? {}) } });
      const raw = await response.text();
      let body: unknown = {};
      try { body = raw ? JSON.parse(raw) as unknown : {}; } catch { body = { errors: raw }; }
      const errors = asObject(body).errors;
      if (!response.ok || !isEmptyErrors(errors)) {
        const status = response.status || 0;
        const message = describeProviderFailure(body, status, raw);
        logger.warn('Proxy-Cheap request rejected', { method: init.method ?? 'GET', path, status, response: raw.slice(0, 1000), request: init.body });
        throw new ProxyCheapApiError(message, status, body, raw);
      }
      return body as T;
    } finally { clearTimeout(timer); }
  }
  async services(): Promise<CheapService[]> {
    const body = await this.request<Json>('/order');
    return asArray(body.services).map((item) => { const x = asObject(item); return { id: String(x.id ?? ''), label: String(x.label ?? x.id ?? ''), plans: asArray(x.plans).map((p) => { const y = asObject(p); return { id: String(y.id ?? ''), label: String(y.label ?? y.id ?? '') }; }) }; }).filter((x) => x.id);
  }
  async setup(serviceId: string, planId?: string): Promise<Json> {
    const body = await this.request<Json>(`/order/${encodeURIComponent(serviceId)}`, { method: 'POST', body: JSON.stringify({ planId: planId?.trim() || null }) });
    return body;
  }
  async price(serviceId: string, payload: Json): Promise<Json> {
    return this.request<Json>(`/order/${encodeURIComponent(serviceId)}/price`, { method: 'POST', body: JSON.stringify(pruneBlank(payload)) });
  }
  /**
   * Prices an order. A quote has no side effects, so when the provider rejects the minimal
   * payload with a validation error we retry once with `traffic` (needed by plans that are sold
   * per GB). The payload that the provider accepted is returned so that execute() sends exactly it.
   */
  async quote(serviceId: string, base: Json, trafficGb: unknown = 1): Promise<{ payload: Json; response: Json }> {
    const traffic = Number(trafficGb);
    const candidates: Json[] = [pruneBlank(base)];
    if (Number.isFinite(traffic) && traffic > 0) candidates.push({ ...pruneBlank(base), traffic });
    let first: ProxyCheapApiError | undefined;
    for (const candidate of candidates) {
      try {
        return { payload: candidate, response: await this.price(serviceId, candidate) };
      } catch (error) {
        if (!(error instanceof ProxyCheapApiError) || !error.isValidation) throw error;
        first ??= error;
      }
    }
    throw first as ProxyCheapApiError;
  }
  async execute(serviceId: string, payload: Json): Promise<Json> {
    return this.request<Json>(`/order/${encodeURIComponent(serviceId)}/execute`, { method: 'POST', body: JSON.stringify(pruneBlank(payload)) });
  }
  async proxyAction(proxyId: string, action: string, payload: Json = {}): Promise<Json> {
    const paths: Record<string, { method: string; path: string }> = { 'enable-autoextend': { method: 'POST', path: 'auto-extend/enable' }, 'disable-autoextend': { method: 'POST', path: 'auto-extend/disable' }, 'extend-period': { method: 'POST', path: 'extend-period' }, 'buy-bandwidth': { method: 'POST', path: 'buy-bandwidth' }, 'whitelist-ip': { method: 'POST', path: 'whitelist-ip' }, 'change-protocol': { method: 'POST', path: 'change-protocol' }, 'change-authentication': { method: 'POST', path: 'change-authentication-type' }, rotate: { method: 'POST', path: 'rotate-ip' } };
    const target = paths[action]; if (!target) throw new Error('Proxy-Cheap action is not supported');
    return this.request<Json>(`/proxies/${encodeURIComponent(proxyId)}/${target.path}`, { method: target.method, body: JSON.stringify(payload) });
  }
  async proxies(orderId: string): Promise<CheapProxy[]> {
    const body = await this.request<unknown>(`/orders/${encodeURIComponent(orderId)}/proxies`);
    return asArray(body).map((item) => {
      const x = asObject(item); const auth = asObject(x.authentication); const connection = asObject(x.connection);
      const requestedType = String(x.proxyType ?? 'HTTP').toUpperCase();
      const availablePorts = [
        { type: 'SOCKS5', port: Number(connection.socks5Port) },
        { type: 'HTTPS', port: Number(connection.httpsPort) },
        { type: 'HTTP', port: Number(connection.httpPort) },
      ];
      const selectedPort = availablePorts.find((candidate) => candidate.type === requestedType && Number.isInteger(candidate.port) && candidate.port > 0)
        ?? availablePorts.find((candidate) => Number.isInteger(candidate.port) && candidate.port > 0);
      const protocol = (selectedPort?.type ?? requestedType).toLowerCase();
      const port = selectedPort?.port ?? Number.NaN;
      return { id: String(x.id ?? ''), status: String(x.status ?? ''), protocol, host: String(connection.connectIp ?? connection.publicIp ?? ''), port, username: String(auth.username ?? ''), password: String(auth.password ?? ''), expiresAt: typeof x.expiresAt === 'string' ? x.expiresAt : undefined, location: asObject(x.metadata) };
    }).filter((x) => x.id && x.host && Number.isInteger(x.port) && x.port > 0);
  }
  async waitForProxy(orderId: string): Promise<CheapProxy> {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const proxies = await this.proxies(orderId);
      const proxy = proxies.find((x) => x.status.toUpperCase() === 'ACTIVE');
      if (proxy) return proxy;
      if (attempt < 11) await new Promise((resolve) => setTimeout(resolve, 2500));
    }
    throw new Error('Proxy-Cheap order is still provisioning');
  }
}
export const proxyCheapService = new ProxyCheapService();
export default proxyCheapService;
