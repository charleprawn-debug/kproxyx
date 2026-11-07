import express, { ErrorRequestHandler } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import config from './config';
import { testSupabaseConnection } from './utils/supabase';
import authRoutes from './routes/auth';
import productRoutes from './routes/products';
import meRoutes from './routes/me';
import adminRoutes from './routes/admin';
import paymentRoutes from './routes/payments';

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1);
  app.use(helmet());
  app.use(cors({ origin: (origin, callback) => !origin || config.corsOrigins.includes(origin) ? callback(null, true) : callback(new Error('CORS policy violation')), credentials: true }));
  app.use('/api/v1/payments/oxapay/webhook', express.raw({ type: 'application/json', limit: '256kb' }));
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: true, limit: '1mb' }));
  app.use('/admin', express.static('admin'));
  if (config.env !== 'test') app.use(morgan('combined', { skip: (req) => req.path === '/livez' }));
  app.use('/api', rateLimit({ windowMs: config.rateLimit.windowMs, max: config.rateLimit.max, standardHeaders: true, legacyHeaders: false }));
  app.get('/livez', (_req, res) => res.json({ status: 'ok' }));
  app.get('/readyz', async (_req, res) => { const ready = await testSupabaseConnection(); return res.status(ready ? 200 : 503).json({ status: ready ? 'ready' : 'not_ready' }); });
  app.get('/api/health', (_req, res) => res.json({ status: 'ok', version: '1.0.0', environment: config.env, timestamp: new Date().toISOString() }));
  app.use('/api/v1/auth', authRoutes);
  app.use('/api/v1/products', productRoutes);
  app.use('/api/v1/me', meRoutes);
  app.use('/api/v1/admin', adminRoutes);
  app.use('/api/v1/payments', paymentRoutes);
  app.get('/admin', (_req, res) => res.sendFile('index.html', { root: 'admin' }));
  app.use((_req, res) => res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Endpoint not found' } }));
  const errors: ErrorRequestHandler = (error, _req, res, _next) => res.status(500).json({ error: { code: 'INTERNAL_ERROR', message: config.env === 'development' ? error.message : 'Internal server error' } });
  app.use(errors);
  return app;
}
export async function startServer() { const app = createApp(); return app.listen(config.port, () => console.log(`Proxy Platform API listening on port ${config.port}`)); }
if (require.main === module) startServer().catch((error) => { console.error(error); process.exit(1); });
export default createApp();
