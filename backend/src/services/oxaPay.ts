import crypto from 'crypto';
import config from '../config';

export type OxaPayment = { trackId: string; orderId: string; address: string; payAmount: number; network: string; raw: Record<string, unknown> };

const networkName = (network: string) => {
  const key = network.trim().toLowerCase();
  if (['trc20', 'trc-20', 'tron', 'tron network'].includes(key)) return 'TRC20';
  if (['bep20', 'bep-20', 'bsc', 'bsc network', 'binance smart chain'].includes(key)) return 'BEP20';
  return null;
};

export function isConfigured(): boolean {
  try { return Boolean(config.payments.oxapay.merchantKey && new URL(config.payments.oxapay.webhookUrl).protocol === 'https:'); } catch { return false; }
}

export function validSignature(rawBody: Buffer, signature: string | undefined): boolean {
  if (!config.payments.oxapay.merchantKey || !signature) return false;
  const expected = crypto.createHmac('sha512', config.payments.oxapay.merchantKey).update(rawBody).digest('hex');
  const received = Buffer.from(signature.trim());
  const calculated = Buffer.from(expected);
  return received.length === calculated.length && crypto.timingSafeEqual(calculated, received);
}

export async function createWhiteLabelPayment(amount: number, network: string, orderId: string): Promise<OxaPayment> {
  if (!isConfigured()) throw new Error('OxaPay is not configured');
  const providerNetwork = networkName(network);
  if (!providerNetwork || !Number.isFinite(amount) || amount <= 0) throw new Error('Invalid OxaPay payment data');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.payments.oxapay.timeoutMs);
  try {
    const response = await fetch(`${config.payments.oxapay.baseUrl}/payment/white-label`, {
      method: 'POST',
      headers: { merchant_api_key: config.payments.oxapay.merchantKey, 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ pay_currency: 'USDT', network: providerNetwork, amount: Number(amount.toFixed(8)), currency: 'USDT', lifetime: 120, fee_paid_by_payer: config.payments.oxapay.feePaidByPayer ? 1 : 0, under_paid_coverage: 0, auto_withdrawal: false, callback_url: config.payments.oxapay.webhookUrl, order_id: orderId, description: `Proxy Platform wallet top-up ${orderId}` }),
      signal: controller.signal,
    });
    const body = await response.json() as Record<string, unknown>;
    const data = (body.data ?? {}) as Record<string, unknown>;
    const status = Number(body.status ?? response.status);
    const trackId = String(data.track_id ?? '').trim();
    const address = String(data.address ?? '').trim();
    const payAmount = Number(data.pay_amount);
    if (!response.ok || status >= 400 || !trackId || !address || !Number.isFinite(payAmount)) throw new Error('OxaPay did not return a valid payment');
    return { trackId, orderId, address, payAmount, network: providerNetwork, raw: body };
  } finally { clearTimeout(timeout); }
}
