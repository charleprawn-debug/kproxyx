import config from '../config';

type Json = Record<string, unknown>;
type Verification = { ok: boolean; reason?: string; externalRef?: string; raw?: Json };

const normalize = (value: unknown) => String(value ?? '').trim().replace(/[\s-]/g, '').toUpperCase();
const decimal = (value: unknown): number | null => {
  const parsed = Number(String(value ?? '').replace(/,/g, '').trim());
  return Number.isFinite(parsed) ? parsed : null;
};
const dateValue = (tx: Json) => tx.date ?? tx.created_at ?? tx.timestamp ?? tx.datetime;

async function request(params: Record<string, string>): Promise<{ ok: boolean; body?: Json; reason?: string }> {
  if (!config.payments.syria.token) return { ok: false, reason: 'not_configured' };
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.payments.syria.timeoutMs);
  try {
    const url = new URL(config.payments.syria.baseUrl);
    Object.entries(params).forEach(([key, value]) => url.searchParams.set(key, value));
    const response = await fetch(url, { headers: { 'X-Api-Key': config.payments.syria.token, Accept: 'application/json', 'User-Agent': 'ProxyPlatform-PaymentVerifier/1.0' }, signal: controller.signal });
    const body = await response.json() as Json;
    if (!response.ok || body.success !== true) return { ok: false, reason: 'provider_error' };
    return { ok: true, body };
  } catch {
    return { ok: false, reason: 'network_error' };
  } finally { clearTimeout(timeout); }
}

export async function verifySyriatel(amount: number, reference: string, createdAt: string): Promise<Verification> {
  const query = config.payments.syria.query.trim();
  if (!query) return { ok: false, reason: 'not_configured' };
  const result = await request({ resource: 'syriatel', action: 'find_tx', tx: normalize(reference), gsm: query, period: '7' });
  if (!result.ok) return { ok: false, reason: result.reason };
  const payload = (result.body?.data ?? {}) as Json;
  if (payload.found !== true || typeof payload.transaction !== 'object' || !payload.transaction) return { ok: false, reason: 'not_found' };
  const tx = payload.transaction as Json;
  const txAmount = decimal(tx.amount ?? tx.net);
  const txRef = normalize(tx.transaction_no ?? tx.tx ?? tx.id);
  if (txAmount !== Math.trunc(amount) || txRef !== normalize(reference)) return { ok: false, reason: 'amount_or_reference_mismatch' };
  if (!withinWindow(dateValue(tx), createdAt)) return { ok: false, reason: 'outside_time_window' };
  return { ok: true, externalRef: txRef, raw: { source: 'api_syria', transaction: tx } };
}

export async function verifyShamCash(amount: number, currency: string, reference: string, address: string, createdAt: string): Promise<Verification> {
  if (!address.trim()) return { ok: false, reason: 'not_configured' };
  const result = await request({ resource: 'shamcash', action: 'find_tx', tx: normalize(reference), account_address: address.trim() });
  if (!result.ok) return { ok: false, reason: result.reason };
  const payload = (result.body?.data ?? {}) as Json;
  if (payload.found !== true || typeof payload.transaction !== 'object' || !payload.transaction) return { ok: false, reason: 'not_found' };
  const tx = payload.transaction as Json;
  const txAmount = decimal(tx.amount ?? tx.value);
  const txCurrency = normalize(tx.currency ?? tx.currency_code);
  const txRef = normalize(tx.tran_id ?? tx.transaction_no ?? tx.tx);
  if (txAmount !== amount || txCurrency !== normalize(currency) || txRef !== normalize(reference)) return { ok: false, reason: 'amount_currency_or_reference_mismatch' };
  if (!withinWindow(dateValue(tx), createdAt)) return { ok: false, reason: 'outside_time_window' };
  return { ok: true, externalRef: txRef, raw: { source: 'api_syria', transaction: tx } };
}

function withinWindow(value: unknown, createdAt: string): boolean {
  if (!value) return true;
  const txTime = Date.parse(String(value).replace(' ', 'T'));
  const createdTime = Date.parse(createdAt);
  if (!Number.isFinite(txTime) || !Number.isFinite(createdTime)) return true;
  const diff = Math.abs(txTime - createdTime) / 60000;
  return diff <= 360;
}
