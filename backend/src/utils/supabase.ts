import { createClient, SupabaseClient } from '@supabase/supabase-js';
import config from '../config';
export const supabaseClient: SupabaseClient = createClient(config.supabase.url, config.supabase.anonKey, { auth: { persistSession: false, autoRefreshToken: false } });
export const supabaseAdmin: SupabaseClient = createClient(config.supabase.url, config.supabase.serviceRoleKey, { auth: { persistSession: false, autoRefreshToken: false } });
export async function testSupabaseConnection() { const { error } = await supabaseAdmin.from('proxy_products').select('id').limit(1); return !error; }
