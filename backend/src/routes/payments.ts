import { Router } from 'express';
import { supabaseAdmin } from '../utils/supabase';
import { validSignature } from '../services/oxaPay';

const router = Router();

router.post('/oxapay/webhook', async (req, res) => {
  const rawBody = Buffer.isBuffer(req.body) ? req.body : Buffer.from(JSON.stringify(req.body ?? {}));
  const signature = String(req.header('x-oxapay-signature') ?? req.header('hmac') ?? '').trim();
  if (!validSignature(rawBody, signature)) return res.status(401).json({ error: 'invalid_signature' });
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(rawBody.toString('utf8')) as Record<string, unknown>; } catch { return res.status(400).json({ error: 'invalid_json' }); }
  const data = (payload.data ?? payload) as Record<string, unknown>;
  const orderId = String(data.order_id ?? payload.order_id ?? '').trim();
  const trackId = String(data.track_id ?? payload.track_id ?? '').trim();
  const status = String(data.status ?? data.payment_status ?? payload.status ?? '').trim().toLowerCase();
  if (!orderId && !trackId) return res.status(400).json({ error: 'missing_payment_reference' });
  if (!['paid', 'completed'].includes(status)) return res.status(200).json({ accepted: true, processed: false });

  const query = supabaseAdmin.from('wallet_topup_requests').select('id,status,expected_usd,provider_pay_amount,provider_network,provider_payment_id,provider_order_id');
  const { data: rows, error } = await (trackId ? query.eq('provider_payment_id', trackId) : query.eq('provider_order_id', orderId)).limit(1);
  if (error) return res.status(500).json({ error: 'lookup_failed' });
  const request = rows?.[0];
  if (!request) return res.status(404).json({ error: 'payment_not_found' });
  if (request.status === 'verified') return res.status(200).json({ accepted: true, processed: true, duplicate: true });
  const received = Number(data.pay_amount ?? data.amount ?? data.received_amount);
  const expected = Number(request.provider_pay_amount ?? request.expected_usd);
  if (!Number.isFinite(received) || !Number.isFinite(expected) || received + 1e-8 < expected) return res.status(409).json({ error: 'underpaid' });

  const { error: updateError } = await supabaseAdmin.from('wallet_topup_requests').update({ provider_payload: payload, provider_reference: trackId || orderId, verification_source: 'oxapay_webhook', updated_at: new Date().toISOString() }).eq('id', request.id).eq('status', 'pending');
  if (updateError) return res.status(500).json({ error: 'update_failed' });
  const { data: approval, error: approvalError } = await supabaseAdmin.rpc('approve_wallet_topup', { p_request_id: request.id, p_admin_id: null, p_admin_note: 'Automatically verified by OxaPay webhook' });
  const result = Array.isArray(approval) ? approval[0] : approval;
  if (approvalError || !result?.success) return res.status(500).json({ error: 'approval_failed' });
  await supabaseAdmin.from('wallet_topup_requests').update({ verified_at: new Date().toISOString() }).eq('id', request.id);
  return res.status(200).json({ accepted: true, processed: true });
});

export default router;
