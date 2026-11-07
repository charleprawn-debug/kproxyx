import dotenv from 'dotenv';
dotenv.config();
const required = ['SUPABASE_URL', 'SUPABASE_ANON_KEY', 'SUPABASE_SERVICE_ROLE_KEY', 'JWT_SECRET', 'ENCRYPTION_KEY'];
const missing = required.filter((key) => !process.env[key]);
if (missing.length && process.env.NODE_ENV !== 'test') throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
const number = (key: string, fallback: number) => {
  const value = Number(process.env[key] ?? fallback);
  if (!Number.isFinite(value)) throw new Error(`${key} must be a number`);
  return value;
};
export const config = {
  env: process.env.NODE_ENV ?? 'development',
  port: number('PORT', 3000),
  apiVersion: process.env.API_VERSION ?? 'v1',
  supabase: { url: process.env.SUPABASE_URL ?? '', anonKey: process.env.SUPABASE_ANON_KEY ?? '', serviceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY ?? '' },
  auth: { jwtSecret: process.env.JWT_SECRET ?? '', jwtRefreshSecret: process.env.JWT_REFRESH_SECRET ?? process.env.JWT_SECRET ?? '' },
  encryption: { key: process.env.ENCRYPTION_KEY ?? '', algorithm: process.env.ENCRYPTION_ALGORITHM ?? 'aes-256-cbc' },
  logging: { level: process.env.LOG_LEVEL ?? 'info' },
  ezProxy: { baseUrl: process.env.EZ_PROXY_API_BASE_URL ?? 'https://api.ez-proxy.net/v1', token: process.env.EZ_PROXY_API_TOKEN ?? '', timeoutMs: number('EZ_PROXY_TIMEOUT_MS', 20000) },
  proxyCheap: { baseUrl: process.env.PROXY_CHEAP_API_BASE_URL ?? 'https://api.proxy-cheap.com/v2', apiKey: process.env.PROXY_CHEAP_API_KEY ?? '', apiSecret: process.env.PROXY_CHEAP_API_SECRET ?? '', timeoutMs: number('PROXY_CHEAP_TIMEOUT_MS', 20000) },
  proxySeller: { baseUrl: process.env.PROXY_SELLER_API_BASE_URL ?? 'https://proxy-seller.com/personal/api/v2', apiKey: process.env.PROXY_SELLER_API_KEY ?? '', timeoutMs: number('PROXY_SELLER_TIMEOUT_MS', 20000) },
  payments: {
    syria: { baseUrl: process.env.SYRIATEL_API_BASE_URL ?? '', token: process.env.SYRIATEL_API_TOKEN ?? '', query: process.env.SYRIATEL_API_QUERY ?? '', timeoutMs: number('SYRIATEL_API_TIMEOUT_MS', number('SYRIA_API_TIMEOUT_MS', 15000)) },
    oxapay: { baseUrl: (process.env.OXAPAY_API_BASE ?? 'https://api.oxapay.com/v1').replace(/\/$/, ''), merchantKey: process.env.OXAPAY_MERCHANT_API_KEY ?? '', webhookUrl: process.env.OXAPAY_WEBHOOK_URL ?? '', timeoutMs: number('OXAPAY_API_TIMEOUT_MS', number('OXAPAY_TIMEOUT_MS', 15000)), feePaidByPayer: ['true', '1', 'yes'].includes((process.env.OXAPAY_FEE_PAID_BY_PAYER ?? 'false').toLowerCase()) },
  },
  corsOrigins: Array.from(new Set((process.env.CORS_ORIGINS ?? 'http://localhost:3001,http://localhost:3002').split(',').map((v) => v.trim()).filter(Boolean).concat('https://uyguhj.onrender.com'))),
  rateLimit: { windowMs: number('RATE_LIMIT_WINDOW_MS', 900000), max: number('RATE_LIMIT_MAX_REQUESTS', 100) },
};
export default config;
