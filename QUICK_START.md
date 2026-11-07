# 🚀 Proxy Platform - Quick Start Guide

## English ⬇️ | العربية ⬆️

---

## ⚡ 5-Minute Quick Start (English)

### 1. Get the Files
```bash
# Extract ZIP
unzip ProxyPlatform.zip
cd ProxyPlatform
```

### 2. Create Environment Files
```bash
cp .env.example .env
cp backend/.env.example backend/.env
```

### 3. Setup Supabase
- Go to https://supabase.com
- Create new project
- Open SQL Editor
- Copy ALL from `database/01_schema.sql` → Paste → **Run**
- Copy ALL from `database/02_seed_data.sql` → Paste → **Run**
- Get your credentials:
  - Project URL → `SUPABASE_URL` in `.env`
  - Anon Key → `SUPABASE_ANON_KEY` in `.env`
  - Service Role Key → `SUPABASE_SERVICE_ROLE_KEY` in `.env`

### 4. Generate Secrets
```bash
# Terminal/PowerShell
node -e "console.log('JWT_SECRET=' + require('crypto').randomBytes(32).toString('hex'))"
node -e "console.log('ENCRYPTION_KEY=' + require('crypto').randomBytes(32).toString('hex'))"

# Copy output to backend/.env
```

### 5. Start Backend
```bash
cd backend
npm install
npm run dev

# Should show: 🚀 Server running on port 3000
```

### 6. Build Android App
```bash
cd android
./gradlew assembleDebug

# APK ready: app/build/outputs/apk/debug/app-debug.apk
```

### 7. Test It
```bash
# Terminal 1: Backend running (npm run dev)
# Terminal 2: Test API
curl http://localhost:3000/api/health

# Should return:
# {"status":"ok","version":"1.0.0",...}
```

---

## 📁 Where to Put Things

### Environment Variables
```
.env                          ← Root environment
backend/.env                  ← Backend config
SUPABASE_URL=https://...
SUPABASE_ANON_KEY=eyJ...
JWT_SECRET=<generated>
ENCRYPTION_KEY=<generated>
STRIPE_SECRET_KEY=sk_test_...
```

### Database SQL Files
```
database/01_schema.sql        ← Copy to Supabase SQL Editor
database/02_seed_data.sql     ← Run in Supabase SQL Editor
```

### API Base URL (Development)
```
Android: http://10.0.2.2:3000     ← From Android emulator
iOS: http://localhost:3000         ← From iOS simulator
Web: http://localhost:3000         ← From browser
```

### API Base URL (Production)
```
All: https://api.proxyplatform.com
```

---

## 🔧 Important Files

| File | Purpose |
|------|---------|
| `README.md` | Complete documentation |
| `SETUP_GUIDE.md` | Detailed setup instructions |
| `DATABASE_SETUP.md` | Database configuration |
| `docs/ARCHITECTURE.md` | System architecture |
| `.env.example` | Environment template |
| `backend/.env.example` | Backend config template |
| `database/01_schema.sql` | Database schema (run first) |
| `database/02_seed_data.sql` | Sample data (run second) |
| `docker-compose.yml` | Docker development setup |
| `.github/workflows/build.yml` | GitHub Actions CI/CD |

---

## 📱 Android APK Build Locations

- **Debug**: `android/app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `android/app/build/outputs/apk/release/app-release.apk`
- **Bundle**: `android/app/build/outputs/bundle/release/app-release.aab`

---

## ✅ Verification

### Backend Running?
```bash
curl http://localhost:3000/api/health
# Response: 200 OK with status
```

### Database Connected?
```bash
# In backend logs, should see:
# "✅ Supabase connection successful"
```

### Can Register User?
```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test@123","fullName":"Test"}'

# Response: 201 Created with user data
```

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| "Cannot find module" | Run `npm install` in backend/ |
| "Port 3000 in use" | Kill process: `lsof -i :3000` → `kill -9 <PID>` |
| "API not responding" | Check .env has SUPABASE_URL |
| "APK build fails" | Run `./gradlew clean` then rebuild |
| "Supabase connection fails" | Verify SUPABASE_URL and keys are correct |

---

## 📊 Project Structure

```
ProxyPlatform/
├── backend/                   ← Node.js API server
│   ├── src/                  ← Source code
│   ├── .env.example          ← Config template
│   └── Dockerfile            ← Docker image
│
├── android/                   ← Android app
│   ├── app/                  ← Main app module
│   ├── build.gradle.kts      ← Build config
│   └── AndroidManifest.xml   ← Permissions
│
├── database/                  ← Database files
│   ├── 01_schema.sql         ← Tables, indexes, functions
│   └── 02_seed_data.sql      ← Sample data
│
├── docs/                      ← Documentation
│   └── ARCHITECTURE.md        ← System design
│
├── .github/workflows/         ← GitHub Actions
│   └── build.yml             ← CI/CD pipeline
│
├── docker-compose.yml         ← Dev environment
└── README.md                  ← Full documentation
```

---

## 🔐 Security Checklist

- [ ] Never commit `.env` file
- [ ] Generate strong JWT_SECRET (32+ bytes)
- [ ] Generate strong ENCRYPTION_KEY (32 bytes)
- [ ] Use HTTPS in production
- [ ] Enable Row-Level Security (RLS) on Supabase
- [ ] Setup payment webhooks correctly
- [ ] Configure CORS properly
- [ ] Use environment variables for secrets
- [ ] Enable API rate limiting
- [ ] Setup SSL certificate

---

---

# ⚡ البدء السريع (العربية)

### 1️⃣ استخراج الملفات
```bash
unzip ProxyPlatform.zip
cd ProxyPlatform
```

### 2️⃣ إنشاء ملفات البيئة
```bash
cp .env.example .env
cp backend/.env.example backend/.env
```

### 3️⃣ إعداد Supabase
- اذهب إلى https://supabase.com
- أنشئ مشروعًا جديدًا
- افتح **SQL Editor**
- انسخ كل محتوى `database/01_schema.sql` → الصق → اضغط **Run**
- انسخ كل محتوى `database/02_seed_data.sql` → الصق → اضغط **Run**
- احصل على بيانات الاعتماد:
  - Project URL → `SUPABASE_URL`
  - Anon Key → `SUPABASE_ANON_KEY`
  - Service Role Key → `SUPABASE_SERVICE_ROLE_KEY`

### 4️⃣ توليد المفاتيح السرية
```bash
node -e "console.log('JWT_SECRET=' + require('crypto').randomBytes(32).toString('hex'))"
node -e "console.log('ENCRYPTION_KEY=' + require('crypto').randomBytes(32).toString('hex'))"

# انسخ القيم إلى backend/.env
```

### 5️⃣ تشغيل الخادم
```bash
cd backend
npm install
npm run dev

# يجب أن يظهر: 🚀 Server running on port 3000
```

### 6️⃣ بناء تطبيق Android
```bash
cd android
./gradlew assembleDebug

# الـ APK جاهز في: app/build/outputs/apk/debug/app-debug.apk
```

### 7️⃣ اختبار النظام
```bash
# المحطة 1: الخادم يعمل (npm run dev)
# المحطة 2: اختبر API
curl http://localhost:3000/api/health

# يجب أن تحصل على:
# {"status":"ok","version":"1.0.0",...}
```

---

## 🗺️ أين تضع الأشياء

### متغيرات البيئة
```
.env                          ← الجذر
backend/.env                  ← تكوين الخادم
SUPABASE_URL=https://...
SUPABASE_ANON_KEY=eyJ...
JWT_SECRET=<قيمة مولدة>
ENCRYPTION_KEY=<قيمة مولدة>
```

### ملفات SQL في Supabase
```
database/01_schema.sql        ← انسخ إلى Supabase SQL Editor
database/02_seed_data.sql     ← شغّل في Supabase SQL Editor
```

### عنوان API (التطوير)
```
Android: http://10.0.2.2:3000
iOS: http://localhost:3000
Web: http://localhost:3000
```

### عنوان API (الإنتاج)
```
جميع الأنظمة: https://api.proxyplatform.com
```

---

## 📱 مواقع بناء Android APK

- **Debug**: `android/app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `android/app/build/outputs/apk/release/app-release.apk`

---

## ✔️ التحقق من التشغيل

### هل الخادم يعمل؟
```bash
curl http://localhost:3000/api/health
# يجب أن يرجع: 200 OK
```

### هل قاعدة البيانات متصلة؟
```bash
# في سجلات الخادم، يجب أن ترى:
# "✅ Supabase connection successful"
```

### هل يمكن تسجيل مستخدم؟
```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test@123","fullName":"Test"}'

# يجب أن يرجع: 201 Created
```

---

## 🆘 حل المشاكل الشائعة

| المشكلة | الحل |
|--------|-----|
| "لا يمكن العثور على الوحدة" | شغّل `npm install` في `backend/` |
| "المنفذ 3000 قيد الاستخدام" | اقتل العملية: `lsof -i :3000` |
| "API لا يستجيب" | تحقق من أن .env يحتوي على SUPABASE_URL |
| "فشل بناء APK" | شغّل `./gradlew clean` ثم أعد البناء |
| "فشل الاتصال بـ Supabase" | تحقق من صحة SUPABASE_URL والمفاتيح |

---

## 📚 الملفات المهمة

| الملف | الغرض |
|------|------|
| `README.md` | التوثيق الكامل |
| `SETUP_GUIDE.md` | تعليمات الإعداد التفصيلية |
| `DATABASE_SETUP.md` | إعداد قاعدة البيانات |
| `docs/ARCHITECTURE.md` | معمارية النظام |
| `database/01_schema.sql` | جداول قاعدة البيانات |
| `database/02_seed_data.sql` | البيانات النموذجية |
| `docker-compose.yml` | إعداد Docker |
| `.github/workflows/build.yml` | GitHub Actions CI/CD |

---

## 🔒 قائمة التحقق الأمنية

- [ ] عدم حفظ ملف `.env`
- [ ] توليد JWT_SECRET قوي (32+ بايت)
- [ ] توليد ENCRYPTION_KEY قوي
- [ ] استخدام HTTPS في الإنتاج
- [ ] تفعيل Row-Level Security (RLS)
- [ ] إعداد Stripe webhooks بشكل صحيح
- [ ] إعداد CORS بشكل صحيح
- [ ] استخدام متغيرات البيئة للأسرار
- [ ] تفعيل Rate Limiting
- [ ] إعداد شهادة SSL

---

## 📞 للمساعدة

1. اقرأ `README.md` للتوثيق الكامل
2. اقرأ `SETUP_GUIDE.md` لخطوات مفصلة
3. اقرأ `docs/ARCHITECTURE.md` لفهم المعمارية
4. اقرأ `DATABASE_SETUP.md` لإعداد قاعدة البيانات

---

**الإصدار**: 1.0.0  
**آخر تحديث**: 2024  
**الحالة**: جاهز للإنتاج ✅
