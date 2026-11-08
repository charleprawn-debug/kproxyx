import { supabaseAdmin } from '../utils/supabase';
import encryptionService from '../utils/encryption';

type StoredSettings = Record<string, string>;
export type PaymentSettings = {
  oxapay: { apiBase: string; merchantKey: string; webhookUrl: string; feePaidByPayer: boolean };
  syria: { apiBase: string; token: string; query: string };
};

const keys = ['payment_oxapay_api_base', 'payment_oxapay_merchant_key', 'payment_oxapay_webhook_url', 'payment_oxapay_fee_paid_by_payer', 'payment_syria_api_base', 'payment_syria_token', 'payment_syria_query'];

async function read(): Promise<StoredSettings> {
  const { data, error } = await supabaseAdmin.from('app_settings').select('key,value').in('key', keys);
  if (error) return {};
  const values: StoredSettings = {};
  for (const row of data ?? []) {
    try { values[row.key] = encryptionService.decrypt(row.value); } catch { values[row.key] = row.value; }
  }
  return values;
}

export async function getPaymentSettings(): Promise<PaymentSettings> {
  const values = await read();
  return {
    oxapay: {
      apiBase: (values.payment_oxapay_api_base ?? 'https://api.oxapay.com/v1').replace(/\/$/, ''),
      merchantKey: values.payment_oxapay_merchant_key ?? '',
      webhookUrl: values.payment_oxapay_webhook_url ?? '',
      feePaidByPayer: values.payment_oxapay_fee_paid_by_payer ? ['true', '1', 'yes'].includes(values.payment_oxapay_fee_paid_by_payer.toLowerCase()) : false,
    },
    syria: {
      apiBase: values.payment_syria_api_base ?? '',
      token: values.payment_syria_token ?? '',
      query: values.payment_syria_query ?? '',
    },
  };
}

export async function savePaymentSettings(input: Record<string, string>): Promise<void> {
  const allowed = new Set(keys);
  const rows = Object.entries(input).filter(([key, value]) => allowed.has(key) && typeof value === 'string').map(([key, value]) => ({ key, value: encryptionService.encrypt(value.trim()), updated_at: new Date().toISOString() }));
  if (!rows.length) return;
  const { error } = await supabaseAdmin.from('app_settings').upsert(rows, { onConflict: 'key' });
  if (error) throw error;
}

export async function paymentSettingsStatus() {
  const settings = await getPaymentSettings();
  return {
    oxapay: { configured: Boolean(settings.oxapay.merchantKey && settings.oxapay.webhookUrl), apiBase: settings.oxapay.apiBase, webhookUrl: settings.oxapay.webhookUrl, feePaidByPayer: settings.oxapay.feePaidByPayer, merchantKey: settings.oxapay.merchantKey ? `••••${settings.oxapay.merchantKey.slice(-4)}` : '' },
    syria: { configured: Boolean(settings.syria.apiBase && settings.syria.token && settings.syria.query), apiBase: settings.syria.apiBase, query: settings.syria.query, token: settings.syria.token ? `••••${settings.syria.token.slice(-4)}` : '' },
  };
}
