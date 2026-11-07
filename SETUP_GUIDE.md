# 🚀 Proxy Platform - Complete Setup & Deployment Guide

## 📖 Table of Contents (جدول المحتويات)

- [English](#english-setup-guide)
- [العربية](#دليل-الإعداد-بالعربية)

---

# English Setup Guide

## Prerequisites

### System Requirements
- **OS**: Linux/macOS/Windows (WSL2)
- **Docker**: v20+
- **Docker Compose**: v2.0+
- **Git**: Latest version
- **Node.js**: v18+ (if not using Docker)
- **Android Studio**: v2023.1.1+ (for Android development)
- **8GB+ RAM** (minimum)
- **20GB+ Disk Space**

### Required Accounts
1. **GitHub** - For code repository & Actions
2. **Supabase** - For database (https://supabase.com)
3. **Stripe** - For payment processing (https://stripe.com)
4. **AWS/GCP** - For infrastructure (optional)

---

## Step 1: Clone & Initial Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/proxy-platform.git
cd ProxyPlatform

# Create environment files
cp .env.example .env
cp backend/.env.example backend/.env

# Initialize git submodules (if any)
git submodule update --init --recursive
```

---

## Step 2: Configure Supabase

### Option A: Using Supabase Cloud (Recommended)

1. Go to https://supabase.com and create account
2. Create new project
3. Go to **Project Settings** → **API**
4. Copy:
   - `Project URL` → `SUPABASE_URL`
   - `anon public key` → `SUPABASE_ANON_KEY`
   - `service_role secret` → `SUPABASE_SERVICE_ROLE_KEY`

5. Go to **SQL Editor**
6. Create new query and run:
   ```bash
   # Paste entire content of database/01_schema.sql
   # Then run database/02_seed_data.sql
   ```

### Option B: Local PostgreSQL

```bash
# Start PostgreSQL with Docker
docker run --name proxy-platform-db \
  -e POSTGRES_DB=proxy_platform \
  -e POSTGRES_PASSWORD=postgres123 \
  -p 5432:5432 \
  -d postgres:15-alpine

# Wait for container to start
sleep 10

# Initialize database
psql -h localhost -U postgres -d proxy_platform < database/01_schema.sql
psql -h localhost -U postgres -d proxy_platform < database/02_seed_data.sql
```

---

## Step 3: Generate Secret Keys

```bash
# Generate JWT Secret
JWT_SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
echo "JWT_SECRET=$JWT_SECRET"

# Generate Encryption Key
ENCRYPTION_KEY=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY"

# Generate Refresh Token Secret
JWT_REFRESH_SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
echo "JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET"

# Copy these to your .env files
```

---

## Step 4: Configure Stripe (or other payment provider)

### Get Stripe Keys

1. Go to https://dashboard.stripe.com
2. Navigate to **Developers** → **API Keys**
3. Copy:
   - `Secret Key` → `STRIPE_SECRET_KEY`
   - `Publishable Key` → `STRIPE_PUBLIC_KEY`

4. Set up webhook:
   - Go to **Webhooks**
   - Click **Add endpoint**
   - URL: `https://your-domain.com/api/v1/payments/webhook`
   - Events: Select `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`
   - Copy signing secret → `STRIPE_WEBHOOK_SECRET`

---

## Step 5: Configure Email (Gmail Example)

### Gmail Setup

1. Enable 2-factor authentication on your Gmail account
2. Generate app password:
   - Go to https://myaccount.google.com/apppasswords
   - Select Mail → Windows Computer
   - Copy generated password

3. Add to `.env`:
   ```
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=your-email@gmail.com
   SMTP_PASSWORD=<app-password-from-google>
   SMTP_FROM_EMAIL=noreply@proxyplatform.com
   ```

---

## Step 6: Update Environment Variables

Edit `.env` and `backend/.env`:

```bash
# .env (Root)
NODE_ENV=development
PORT=3000
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=<service-role-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
JWT_SECRET=<generated-above>
JWT_REFRESH_SECRET=<generated-above>
ENCRYPTION_KEY=<generated-above>
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLIC_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=<app-password>
REDIS_PASSWORD=redis123
FRONTEND_URL=http://localhost:3001
ADMIN_FRONTEND_URL=http://localhost:3002
```

---

## Step 7: Start Development Environment

### Option A: Docker Compose (Recommended)

```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f backend

# Stop services
docker-compose down
```

### Option B: Manual Setup

#### Backend
```bash
cd backend

# Install dependencies
npm install

# Create .env file
cp .env.example .env
# Edit .env with your values

# Start development server
npm run dev

# Server will start at http://localhost:3000
```

#### Redis (if not using Docker)
```bash
# Install Redis (macOS)
brew install redis

# Start Redis
redis-server

# Verify connection
redis-cli ping  # Should return PONG
```

---

## Step 8: Build & Run Android App

### Setup
```bash
cd android

# Create local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Create Android Keystore (for release builds)
keytool -genkey -v -keystore app/keystore/release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias release-key \
  -keypass YOUR_KEY_PASSWORD \
  -storepass YOUR_KEYSTORE_PASSWORD
```

### Build Debug APK
```bash
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk

# Install on emulator/device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK
```bash
# Update gradle.properties with keystore info
./gradlew assembleRelease

# APK location: app/build/outputs/apk/release/app-release.apk
```

---

## Step 9: Test API Endpoints

```bash
# Health check
curl http://localhost:3000/api/health

# Register user
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@12345",
    "fullName": "Test User"
  }'

# Login
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@12345"
  }'

# Get proxies (using token from login)
curl http://localhost:3000/api/v1/proxies \
  -H "Authorization: Bearer <your-token>"
```

---

## Step 10: Deploy to Production

### Option A: Using Docker

```bash
# Build production image
docker build -t proxy-platform-api:latest backend/

# Push to registry (Docker Hub, ECR, etc.)
docker tag proxy-platform-api:latest your-registry/proxy-platform-api:latest
docker push your-registry/proxy-platform-api:latest

# Deploy to server
docker run -d \
  -p 3000:3000 \
  -e NODE_ENV=production \
  -e SUPABASE_URL="$SUPABASE_URL" \
  -e SUPABASE_ANON_KEY="$SUPABASE_ANON_KEY" \
  -e JWT_SECRET="$JWT_SECRET" \
  your-registry/proxy-platform-api:latest
```

### Option B: Kubernetes

```bash
# Create namespace
kubectl create namespace proxy-platform

# Create secrets
kubectl create secret generic proxy-platform-secrets \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --from-literal=encryption-key="$ENCRYPTION_KEY" \
  -n proxy-platform

# Deploy
kubectl apply -f devops/kubernetes/api-deployment.yaml

# Check status
kubectl get pods -n proxy-platform
```

### Option C: Cloud Platform (Heroku/Railway/Render)

```bash
# Using Railway.app
railway link
railway variables set SUPABASE_URL="..."
railway variables set NODE_ENV="production"
railway deploy

# Using Render
git push origin main  # Automatic deployment
```

---

## Step 11: Setup GitHub Actions (CI/CD)

### Create GitHub Secrets

1. Go to your GitHub repository → **Settings** → **Secrets**
2. Add these secrets:
   ```
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=<service-role-key>
   SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
   JWT_SECRET=...
   KEYSTORE_PASSWORD=...
   KEY_ALIAS=release-key
   KEY_PASSWORD=...
   KEYSTORE_FILE=<base64-encoded-jks>
   API_BASE_URL=https://api.proxyplatform.com
   ```

3. Encode keystore file:
   ```bash
   base64 -i android/app/keystore/release.jks | pbcopy
   # Paste into KEYSTORE_FILE secret
   ```

### Trigger Build
```bash
git tag v1.0.0
git push origin v1.0.0

# This triggers GitHub Actions workflow
# Check Actions tab for build progress
```

---

## Step 12: Database Migrations

### Manual Migration
```bash
# Connect to database
psql -h localhost -U postgres -d proxy_platform

# Run SQL files in order
\i database/01_schema.sql
\i database/02_seed_data.sql

# Check results
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM proxy_products;
```

### Using Supabase CLI (Optional)
```bash
# Install Supabase CLI
npm install -g supabase

# Link project
supabase link --project-ref your-project-ref

# Apply migrations
supabase db push

# Pull remote changes
supabase db pull
```

---

## 🐛 Common Issues & Solutions

### Issue: "Cannot connect to Supabase"
**Solution:**
```bash
# Verify credentials
curl $SUPABASE_URL

# Check .env file
cat backend/.env | grep SUPABASE

# Restart services
docker-compose restart backend
```

### Issue: "Port 3000 already in use"
**Solution:**
```bash
# Find process
lsof -i :3000

# Kill process
kill -9 <PID>

# Or use different port
PORT=3001 npm run dev
```

### Issue: "Android build out of disk space"
**Solution:**
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches

# Free up space
docker system prune -a

# Clean build
cd android && ./gradlew clean
```

### Issue: "Stripe webhook not firing"
**Solution:**
```bash
# Verify webhook in Stripe dashboard
# Check webhook URL is correct
# Verify STRIPE_WEBHOOK_SECRET in .env
# Restart backend: docker-compose restart backend

# Test webhook locally with Stripe CLI:
stripe listen --forward-to localhost:3000/api/v1/payments/webhook
```

---

## 📊 Verification Checklist

- [ ] Docker containers running (`docker-compose ps`)
- [ ] API responding (`curl http://localhost:3000/api/health`)
- [ ] Database initialized (`SELECT COUNT(*) FROM users;`)
- [ ] Redis connected (`redis-cli ping`)
- [ ] Supabase credentials valid
- [ ] JWT secrets generated & stored
- [ ] Android emulator/device connected
- [ ] App APK builds successfully
- [ ] Payment provider configured
- [ ] Email SMTP working
- [ ] GitHub Actions secrets added

---

---

# دليل الإعداد بالعربية

## المتطلبات

### متطلبات النظام
- **نظام التشغيل**: Linux/macOS/Windows (WSL2)
- **Docker**: إصدار 20+
- **Git**: آخر إصدار
- **Node.js**: إصدار 18+
- **Android Studio**: إصدار 2023.1.1+
- **RAM**: 8GB على الأقل
- **مساحة التخزين**: 20GB على الأقل

### حسابات مطلوبة
1. **GitHub** - مستودع الكود
2. **Supabase** - قاعدة البيانات (https://supabase.com)
3. **Stripe** - معالجة الدفع (https://stripe.com)

---

## الخطوة 1: استنساخ المشروع

```bash
# استنسخ المستودع
git clone https://github.com/yourusername/proxy-platform.git
cd ProxyPlatform

# إنشاء ملفات البيئة
cp .env.example .env
cp backend/.env.example backend/.env
```

---

## الخطوة 2: إعداد Supabase

### الخيار أ: استخدام Supabase Cloud (الموصى به)

1. اذهب إلى https://supabase.com وأنشئ حسابًا
2. أنشئ مشروعًا جديدًا
3. اذهب إلى **Project Settings** → **API**
4. انسخ:
   - `Project URL` → `SUPABASE_URL`
   - `anon public key` → `SUPABASE_ANON_KEY`
   - `service_role secret` → `SUPABASE_SERVICE_ROLE_KEY`

5. اذهب إلى **SQL Editor** وشغّل:
   ```bash
   # انسخ محتوى database/01_schema.sql بالكامل
   # ثم شغّل database/02_seed_data.sql
   ```

---

## الخطوة 3: توليد المفاتيح السرية

```bash
# توليد JWT Secret
JWT_SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
echo "JWT_SECRET=$JWT_SECRET"

# توليد مفتاح التشفير
ENCRYPTION_KEY=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY"

# انسخها إلى ملفات .env
```

---

## الخطوة 4: إعداد Stripe

1. اذهب إلى https://dashboard.stripe.com
2. انتقل إلى **Developers** → **API Keys**
3. انسخ:
   - `Secret Key` → `STRIPE_SECRET_KEY`
   - `Publishable Key` → `STRIPE_PUBLIC_KEY`

4. إعداد webhook:
   - اذهب إلى **Webhooks**
   - اضغط **Add endpoint**
   - URL: `https://your-domain.com/api/v1/payments/webhook`
   - اختر الأحداث المطلوبة
   - انسخ التوقيع → `STRIPE_WEBHOOK_SECRET`

---

## الخطوة 5: تحديث ملفات البيئة

عدّل `.env` و `backend/.env` بقيمك الخاصة:

```bash
NODE_ENV=development
PORT=3000
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=<service-role-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
JWT_SECRET=<القيمة المولدة>
JWT_REFRESH_SECRET=<القيمة المولدة>
ENCRYPTION_KEY=<القيمة المولدة>
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLIC_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=بريدك@gmail.com
SMTP_PASSWORD=<كلمة المرور>
REDIS_PASSWORD=redis123
FRONTEND_URL=http://localhost:3001
```

---

## الخطوة 6: تشغيل بيئة التطوير

### استخدام Docker Compose (الموصى به)

```bash
# شغّل جميع الخدمات
docker-compose up -d

# تحقق من الحالة
docker-compose ps

# شاهد السجلات
docker-compose logs -f backend

# إيقاف الخدمات
docker-compose down
```

### الإعداد اليدوي

```bash
cd backend

# تثبيت الحزم
npm install

# ابدأ خادم التطوير
npm run dev

# سيبدأ الخادم على http://localhost:3000
```

---

## الخطوة 7: بناء تطبيق Android

```bash
cd android

# بناء APK للتطوير
./gradlew assembleDebug

# سيتم إنشاء: app/build/outputs/apk/debug/app-debug.apk

# تثبيت على المحاكي
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## الخطوة 8: اختبار نقاط نهاية API

```bash
# فحص الحالة
curl http://localhost:3000/api/health

# تسجيل مستخدم جديد
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@12345",
    "fullName": "Test User"
  }'

# تسجيل الدخول
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test@12345"
  }'
```

---

## الخطوة 9: النشر في الإنتاج

### استخدام Docker

```bash
# بناء صورة الإنتاج
docker build -t proxy-platform-api:latest backend/

# نشر على الخادم
docker run -d \
  -p 3000:3000 \
  -e NODE_ENV=production \
  -e SUPABASE_URL="$SUPABASE_URL" \
  proxy-platform-api:latest
```

---

## الخطوة 10: إعداد GitHub Actions

1. اذهب إلى مستودعك → **Settings** → **Secrets**
2. أضف هذه السرار:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
   - `JWT_SECRET`
   - `KEYSTORE_PASSWORD`
   - وغيرها...

3. انشر علامة:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

---

## 📊 قائمة التحقق من الإعداد

- [ ] حاويات Docker قيد التشغيل
- [ ] API يستجيب على `http://localhost:3000/api/health`
- [ ] قاعدة البيانات مهيأة
- [ ] Redis متصل
- [ ] بيانات اعتماد Supabase صحيحة
- [ ] تطبيق Android يبني بنجاح
- [ ] معالج الدفع مُعد
- [ ] البريد الإلكتروني يعمل

---

## 📞 الدعم

- 📧 البريد: support@proxyplatform.com
- 💬 مشاكل GitHub: https://github.com/yourusername/proxy-platform/issues
- 📖 التوثيق: انظر مجلد `/docs`

---

**آخر تحديث**: 2024  
**الإصدار**: 1.0.0
