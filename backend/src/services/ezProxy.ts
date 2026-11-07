import config from '../config';

export type EzPlan = { id: number; name?: { en?: string; ar?: string }; duration_hours: number; price: string | number };
export type EzLocation = { id: number; country?: { code?: string; name?: string }; state?: string; isp?: string; protocols?: { http?: boolean; socks5?: boolean }; status?: string };
export type EzProxy = { id: number; status: string; type: string; protocol: string; host: string; port: number; username?: string; password?: string; url?: string; expires_at?: string; location?: EzLocation; plan?: EzPlan };
type FetchInit = { method?: string; headers?: Record<string, string>; body?: string; signal?: AbortSignal };

class EzProxyService {
  private get baseUrl() {
    return config.ezProxy.baseUrl.replace(/\/$/, '');
  }

  private async request<T>(path: string, init: FetchInit = {}): Promise<T> {
    if (!config.ezProxy.token) throw new Error('EZ Proxy provider is not configured');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.ezProxy.timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...init,
        signal: controller.signal,
        headers: { Accept: 'application/json', 'Content-Type': 'application/json', Authorization: `Bearer ${config.ezProxy.token}`, ...(init.headers ?? {}) },
      });
      const body = await response.json() as { status?: string; code?: string; message?: string; data?: T };
      if (!response.ok || body.status === 'error') throw new Error(body.message || body.code || `EZ Proxy request failed (${response.status})`);
      return body.data as T;
    } finally {
      clearTimeout(timer);
    }
  }

  plans() { return this.request<EzPlan[]>('/proxy/plans'); }
  locations(params: { countryCode?: string; protocol?: string }) {
    const query = new URLSearchParams();
    if (params.countryCode) query.set('country_code', params.countryCode);
    if (params.protocol) query.set('protocol', params.protocol);
    return this.request<EzLocation[]>(`/proxy/locations${query.size ? `?${query}` : ''}`);
  }
  createOrder(input: { clientRequestId: string; locationId: number; planId: number; protocol: 'http' | 'socks5'; username: string; password: string }) {
    return this.request<{ order: { id: number; price: string }; proxy: EzProxy }>('/proxy/orders', {
      method: 'POST',
      body: JSON.stringify({ client_request_id: input.clientRequestId, location_id: input.locationId, plan_id: input.planId, type: 'normal', protocol: input.protocol, auth: { username: input.username, password: input.password } }),
    });
  }
}

export const ezProxyService = new EzProxyService();
export default ezProxyService;
