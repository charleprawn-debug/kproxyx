# 🗄️ Proxy Platform - Database Setup Guide for Supabase

## Quick Start - Supabase SQL Editor

### Step 1: Get to SQL Editor
1. Go to your Supabase project dashboard
2. Click on **SQL Editor** in the left sidebar
3. Click **New Query**

### Step 2: Execute Schema (Part 1)
1. Open `database/01_schema.sql` from project folder
2. Copy ALL content
3. Paste into Supabase SQL Editor
4. Click **Run** button
5. Wait for completion (~30 seconds)

### Step 3: Execute Seed Data (Part 2)
1. Open `database/02_seed_data.sql`
2. Copy ALL content
3. Create NEW query in SQL Editor
4. Paste content
5. Click **Run** button
6. Wait for completion (~5 seconds)

### Step 4: Verify Installation
Create new query and run:
```sql
-- Check tables created
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Should return 21 tables including:
-- users, proxies, proxy_products, orders, subscriptions, etc.
```

### Step 5: Verify Data Inserted
```sql
-- Check data
SELECT COUNT(*) as users_count FROM users;
SELECT COUNT(*) as proxies_count FROM proxies;
SELECT COUNT(*) as products_count FROM proxy_products;
SELECT COUNT(*) as coupons_count FROM coupons;

-- Should show sample data inserted
```

---

## Alternative Method: Using psql CLI

If you have PostgreSQL installed locally:

```bash
# 1. Get connection string from Supabase
# Go to: Project Settings → Database → Connection String → URI
# Copy the full connection string

# 2. Set environment variable
export DATABASE_URL="postgresql://postgres:[PASSWORD]@[HOST]:[PORT]/postgres"

# 3. Run schema
psql "$DATABASE_URL" < database/01_schema.sql

# 4. Run seed data
psql "$DATABASE_URL" < database/02_seed_data.sql

# 5. Verify
psql "$DATABASE_URL" -c "SELECT COUNT(*) FROM users;"
```

---

## Troubleshooting

### Error: "insufficient privilege"
**Solution**: Make sure you're using the `postgres` user role, not a service role.

### Error: "permission denied"
**Solution**: Check Row-Level Security policies aren't blocking writes. Temporarily disable RLS:
```sql
ALTER TABLE users DISABLE ROW LEVEL SECURITY;
-- Run migrations
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
```

### Error: "UUID extension not found"
**Solution**: The extension should be created automatically. If not:
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

### Tables created but no data
**Solution**: Run seed data script again:
```bash
psql "$DATABASE_URL" < database/02_seed_data.sql
```

---

## After Setup

### 1. Enable Row-Level Security
In SQL Editor, verify RLS is enabled:
```sql
-- Check RLS status
SELECT tablename, rowsecurity 
FROM pg_tables 
WHERE schemaname = 'public' 
AND rowsecurity = true 
LIMIT 5;
```

### 2. Configure Supabase Auth
1. Go to **Auth Settings**
2. Enable **Email provider**
3. Set redirect URL to your app domain
4. Save

### 3. Test Connection from App
```bash
# Update backend/.env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-key
SUPABASE_SERVICE_ROLE_KEY=your-key

# Start backend
cd backend && npm run dev

# Test
curl http://localhost:3000/api/health
# Should return 200 OK
```

---

## Database Schema Summary

### Core Tables (21 total)
- **users**: User accounts & profiles
- **user_sessions**: Device & session tracking
- **proxy_providers**: External proxy providers
- **proxy_products**: Products for sale
- **proxies**: Actual proxy instances
- **proxy_health_checks**: Health metrics history
- **subscriptions**: User subscriptions
- **connection_sessions**: Connection tracking
- **products**: Purchasable products
- **orders**: User orders
- **order_items**: Line items
- **payments**: Payment transactions
- **coupons**: Discount codes
- **referrals**: Referral program
- **favorites**: User favorites
- **proxy_user_settings**: Per-proxy settings
- **support_tickets**: Support tickets
- **support_messages**: Support messages
- **notifications**: User notifications
- **admin_users**: Admin accounts
- **audit_logs**: Audit trail
- **analytics_events**: Analytics data
- **app_config**: App settings

### Views (3 total)
- `v_user_dashboard` - Dashboard summary
- `v_active_subscriptions` - Active subscriptions
- `v_proxy_health_summary` - Proxy health stats

### Functions (4 total)
- `get_current_user()` - Get authenticated user
- `user_owns_subscription()` - Check subscription ownership
- `get_active_subscriptions_count()` - Count active subs
- `renew_subscription()` - Renew a subscription

---

## Important Environment Variables

```bash
# After setup, add to backend/.env:

# Supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=<service-role-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>

# JWT (generate with: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
JWT_SECRET=your-secret-here
JWT_REFRESH_SECRET=your-refresh-secret
JWT_EXPIRATION=24h
JWT_REFRESH_EXPIRATION=7d

# Encryption (generate with: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
ENCRYPTION_KEY=your-encryption-key
ENCRYPTION_ALGORITHM=aes-256-cbc
```

---

## Testing Database Connection

```bash
# In backend directory
npm install

# Test connection
npx ts-node -e "
import { testSupabaseConnection } from './src/utils/supabase';
testSupabaseConnection().then(result => {
  console.log('Connection test:', result ? 'SUCCESS' : 'FAILED');
  process.exit(result ? 0 : 1);
});
"
```

---

## Backup & Recovery

### Automatic Backups
Supabase automatically backs up daily. No action needed.

### Manual Backup
```bash
# Export schema and data
pg_dump "postgresql://user:password@host/db" > backup.sql

# Restore from backup
psql "postgresql://user:password@host/db" < backup.sql
```

### Point-in-Time Recovery
1. Go to Supabase Dashboard
2. Click **Backups** in sidebar
3. Scroll through backup history
4. Click **Restore** next to desired backup

---

## Next Steps

1. ✅ Database initialized
2. ✅ Schema created
3. ✅ Seed data inserted
4. Now: [Configure Backend](./SETUP_GUIDE.md#step-5-update-environment-variables)
5. Then: [Build Android App](./SETUP_GUIDE.md#step-7-build--run-android-app)
6. Finally: [Deploy to Production](./SETUP_GUIDE.md#step-10-deploy-to-production)

---

## Support

If you encounter issues:

1. Check Supabase project status page
2. Review SQL error message
3. Verify connection string
4. Check firewall rules allow connection
5. Create GitHub issue with error log

---

**Database Version**: 1.0.0  
**Supabase Compatible**: v2.38+  
**PostgreSQL Version**: 13+
