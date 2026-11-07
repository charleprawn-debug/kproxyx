import { Request, Response, NextFunction } from 'express';
import { supabaseAdmin } from '../utils/supabase';

declare global { namespace Express { interface Request { user?: { id: string; authId: string; email?: string; role: string }; } } }
export async function requireAuth(req: Request, res: Response, next: NextFunction) {
  const header = req.header('authorization');
  if (!header?.startsWith('Bearer ')) return res.status(401).json({ error: { code: 'UNAUTHORIZED', message: 'Bearer token required' } });
  const { data: { user }, error } = await supabaseAdmin.auth.getUser(header.slice(7));
  if (error || !user) return res.status(401).json({ error: { code: 'UNAUTHORIZED', message: 'Invalid or expired token' } });
  const profile = await supabaseAdmin.from('users').select('id,auth_id,email,role').eq('auth_id', user.id).maybeSingle();
  if (profile.error || !profile.data) return res.status(403).json({ error: { code: 'PROFILE_REQUIRED', message: 'User profile not found' } });
  req.user = { id: profile.data.id, authId: user.id, email: profile.data.email, role: profile.data.role };
  next();
}
export function requireRole(...roles: string[]) { return (req: Request, res: Response, next: NextFunction) => { if (!req.user || !roles.includes(req.user.role)) return res.status(403).json({ error: { code: 'FORBIDDEN', message: 'Insufficient permissions' } }); next(); }; }
