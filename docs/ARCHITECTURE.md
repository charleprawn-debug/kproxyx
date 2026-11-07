# 🏗️ Proxy Platform - System Architecture

## Overview

Proxy Platform is a production-ready, enterprise-grade proxy management system built with:
- **Backend**: Node.js/Express + TypeScript
- **Database**: Supabase (PostgreSQL with RLS)
- **Mobile**: Android with Jetpack Compose
- **Admin**: Node.js/Next.js (future)
- **Infrastructure**: Docker, Kubernetes, GitHub Actions

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT LAYER                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│   ┌──────────────┐          ┌──────────────┐                │
│   │ Android App  │          │ Admin Panel  │                │
│   │ (Kotlin)     │          │ (Next.js)    │                │
│   │              │          │              │                │
│   │ • Dashboard  │          │ • Analytics  │                │
│   │ • Marketplace│          │ • User Mgmt  │                │
│   │ • VPN Engine │          │ • Proxy Mgmt │                │
│   │ • Settings   │          │ • Reports    │                │
│   └──────┬───────┘          └──────┬───────┘                │
│          │                         │                         │
└──────────┼─────────────────────────┼──────────────────────────┘
           │                         │
           │ HTTPS / TLS            │
           │                         │
┌──────────▼─────────────────────────▼──────────────────────────┐
│                    API GATEWAY LAYER                           │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐   │
│  │     Express.js REST API (Node.js)                       │   │
│  │                                                         │   │
│  │  • Authentication & JWT                                │   │
│  │  • Rate Limiting & DDoS Protection                     │   │
│  │  • CORS & Security Headers                             │   │
│  │  • Request Validation                                  │   │
│  │  • Error Handling                                      │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
┌───────────▼─────┐ ┌──────▼──────┐ ┌────▼────────┐
│  BUSINESS LOGIC │ │   EXTERNAL  │ │   MESSAGE   │
│  SERVICES LAYER │ │   SERVICES  │ │   QUEUE     │
├─────────────────┤ ├─────────────┤ ├─────────────┤
│                 │ │             │ │             │
│ • Auth Service  │ │ • Stripe    │ │ • Bull      │
│ • User Service  │ │ • PayPal    │ │ • Email     │
│ • Proxy Service │ │ • Razorpay  │ │ • Webhooks  │
│ • Order Service │ │ • Supabase  │ │ • Scheduler │
│ • Payment Svc   │ │ • SMS (Twilio)              │
│ • Subscription  │ │ • Analytics │ │             │
│ • Analytics Svc │ │ • Logging   │ │             │
│                 │ │             │ │             │
└─────────────────┘ └─────────────┘ └─────────────┘
            │              │              │
            └──────────────┼──────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    DATA LAYER                                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         Supabase / PostgreSQL                            │   │
│  │                                                          │   │
│  │  • Users & Sessions                                     │   │
│  │  • Proxy Products & Inventory                           │   │
│  │  • Subscriptions & Orders                               │   │
│  │  • Payments & Transactions                              │   │
│  │  • Connections & Analytics                              │   │
│  │  • Support Tickets                                      │   │
│  │  • Notifications                                        │   │
│  │                                                          │   │
│  │  Row-Level Security (RLS)                               │   │
│  │  • Users see only their data                            │   │
│  │  • Admins have full access                              │   │
│  │  • Audit logs on all changes                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │  Redis Cache     │  │  Search Engine   │                     │
│  │                  │  │  (Elasticsearch) │                     │
│  │  • Session cache │  │  • Proxy search  │                     │
│  │  • Rate limits   │  │  • Marketplace   │                     │
│  │  • Job queue     │  │  • Analytics     │                     │
│  └──────────────────┘  └──────────────────┘                     │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Detailed Layer Architecture

### 1. Presentation Layer (Client)

#### Android App (Kotlin + Jetpack Compose)
```
MainActivity
├── Navigation Graph
│   ├── HomeScreen
│   │   ├── ConnectionStatus
│   │   ├── ProxySelector
│   │   ├── QuickStats
│   │   └── Recommendations
│   ├── MarketplaceScreen
│   │   ├── ProxyList
│   │   ├── Filters & Search
│   │   ├── ProxyDetails
│   │   └── Checkout
│   ├── MyProxiesScreen
│   │   ├── SubscriptionList
│   │   ├── ProxyCard
│   │   ├── ConnectionHistory
│   │   └── Settings
│   ├── ConnectionScreen
│   │   ├── VPN Settings
│   │   ├── App Routing
│   │   ├── Kill Switch
│   │   └── Diagnostics
│   ├── OrdersScreen
│   │   ├── OrderHistory
│   │   ├── InvoiceList
│   │   └── PaymentStatus
│   ├── AccountScreen
│   │   ├── Profile
│   │   ├── Subscription
│   │   ├── Preferences
│   │   └── Support
│   └── AdminScreen (if admin)
│       ├── Dashboard
│       ├── UserManagement
│       ├── ProxyManagement
│       └── Analytics

VPN Service
├── ProxyVpnService
│   ├── Connection Engine
│   ├── Protocol Handler (SOCKS5, HTTP, HTTPS)
│   ├── Kill Switch
│   ├── DNS Protection
│   └── IPv6 Handling

Foreground Service
├── ConnectionMonitor
│   ├── Network State
│   ├── Connection Health
│   ├── Auto-reconnect
│   └── Data Usage Tracking

Background Workers
├── ProxyHealthCheck
├── SubscriptionReminder
├── AutoSync
└── NotificationHandler
```

#### Admin Dashboard (Next.js)
```
Dashboard
├── Analytics Dashboard
│   ├── Revenue Charts
│   ├── User Metrics
│   ├── Connection Stats
│   └── Proxy Health
├── User Management
│   ├── User List
│   ├── User Details
│   ├── Ban/Suspend
│   └── Notifications
├── Proxy Management
│   ├── Inventory
│   ├── Health Monitoring
│   ├── Bulk Import
│   └── Pricing
├── Payment Management
│   ├── Transactions
│   ├── Disputes
│   ├── Refunds
│   └── Provider Settings
├── Support Center
│   ├── Ticket Queue
│   ├── Chat System
│   └── Knowledge Base
└── System Settings
    ├── Feature Flags
    ├── Configuration
    ├── Security Audit
    └── Logs
```

---

### 2. API Gateway Layer

#### Express.js Application Structure
```
Express App
├── Middleware Stack
│   ├── Helmet (Security Headers)
│   ├── CORS (Cross-Origin)
│   ├── Morgan (Logging)
│   ├── Rate Limiter
│   ├── Body Parser
│   ├── Auth Middleware
│   └── Error Handler
│
├── Authentication Routes (/auth)
│   ├── POST /register
│   ├── POST /login
│   ├── POST /refresh
│   ├── POST /logout
│   └── Password Recovery
│
├── User Routes (/users)
│   ├── GET /profile
│   ├── PUT /profile
│   ├── GET /devices
│   ├── DELETE /devices/:id
│   └── Settings Management
│
├── Proxy Routes (/proxies)
│   ├── GET / (marketplace)
│   ├── GET /:id
│   ├── POST /:id/test
│   ├── GET /user/mine
│   └── Favorites Management
│
├── Order Routes (/orders)
│   ├── GET /
│   ├── POST / (create order)
│   ├── GET /:id
│   └── Invoice Download
│
├── Payment Routes (/payments)
│   ├── POST /create
│   ├── POST /webhook
│   └── GET /status
│
├── Subscription Routes (/subscriptions)
│   ├── GET /
│   ├── POST /
│   ├── PUT /:id
│   └── DELETE /:id
│
├── Admin Routes (/admin)
│   ├── Dashboard Stats
│   ├── User Management
│   ├── Proxy Management
│   ├── Analytics
│   └── System Configuration
│
└── Health & Status
    ├── GET /health
    ├── GET /status
    └── GET /metrics
```

---

### 3. Business Logic Layer (Services)

#### Service Architecture
```
Service Layer
│
├── Authentication Service
│   ├── User Registration
│   ├── JWT Token Generation
│   ├── Token Validation & Refresh
│   ├── 2FA Setup
│   └── Password Management
│
├── User Service
│   ├── Profile Management
│   ├── Device Tracking
│   ├── Session Management
│   ├── Notification Preferences
│   └── Account Deletion
│
├── Proxy Service
│   ├── Proxy Inventory Management
│   ├── Health Monitoring
│   ├── Status Updates
│   ├── Assignment Logic
│   └── Credential Encryption
│
├── Marketplace Service
│   ├── Product Listing
│   ├── Search & Filtering
│   ├── Pricing Calculations
│   ├── Availability Check
│   └── Recommendations
│
├── Order Service
│   ├── Order Creation
│   ├── Order Status Tracking
│   ├── Invoice Generation
│   ├── Refund Processing
│   └── Order Analytics
│
├── Payment Service
│   ├── Payment Provider Abstraction
│   ├── Transaction Processing
│   ├── Webhook Handling
│   ├── Reconciliation
│   └── Fraud Detection
│
├── Subscription Service
│   ├── Subscription Creation
│   ├── Auto-renewal Management
│   ├── Expiration Handling
│   ├── Upgrade/Downgrade
│   └── Cancellation
│
├── Connection Service
│   ├── Session Tracking
│   ├── Usage Monitoring
│   ├── Statistics Collection
│   └── Anomaly Detection
│
├── Notification Service
│   ├── Email Notifications
│   ├── Push Notifications
│   ├── SMS (optional)
│   ├── Template Management
│   └── Delivery Tracking
│
├── Analytics Service
│   ├── Event Tracking
│   ├── Metrics Aggregation
│   ├── Report Generation
│   └── Insights Generation
│
├── Support Service
│   ├── Ticket Management
│   ├── Message Threading
│   ├── Category Routing
│   └── SLA Tracking
│
└── Admin Service
    ├── User Administration
    ├── Proxy Management
    ├── System Configuration
    ├── Audit Logging
    └── Export/Import
```

---

### 4. Data Layer Architecture

#### Database Schema Zones
```
Supabase PostgreSQL
│
├── User Zone
│   ├── users
│   ├── user_sessions
│   ├── user_devices
│   └── user_preferences
│
├── Proxy Zone
│   ├── proxy_providers
│   ├── proxy_products
│   ├── proxies
│   ├── proxy_health_checks
│   ├── proxy_user_settings
│   └── proxy_assignments
│
├── Commerce Zone
│   ├── products
│   ├── orders
│   ├── order_items
│   ├── payments
│   ├── subscriptions
│   ├── coupons
│   └── referrals
│
├── Operations Zone
│   ├── connection_sessions
│   ├── analytics_events
│   ├── notifications
│   ├── support_tickets
│   ├── support_messages
│   └── audit_logs
│
├── Configuration Zone
│   ├── app_config
│   ├── proxy_providers_config
│   ├── payment_settings
│   └── feature_flags
│
└── Admin Zone
    ├── admin_users
    ├── admin_permissions
    ├── admin_audit_logs
    └── system_logs
```

#### Row-Level Security (RLS) Policies
```
users
├── Users see only themselves
└── Admins see all

subscriptions
├── Users see own subscriptions
├── Admins see all
└── Service role can create

orders
├── Users see own orders
├── Admins see all
└── Payments trigger creation

connection_sessions
├── Users see own sessions
└── Analytics aggregates

notifications
├── Users see own notifications
├── Admins manage all
└── System can insert

support_tickets
├── Users see own tickets
├── Admins see assigned tickets
└── Can reply to own tickets
```

#### Indexes for Performance
```
High-Traffic Queries:
├── users (email, auth_id, status)
├── subscriptions (user_id, status, expires_at)
├── proxies (status, product_id, assignment_user_id)
├── orders (user_id, status, created_at)
├── connection_sessions (user_id, started_at, subscription_id)
├── notifications (user_id, is_read, created_at)
└── analytics_events (user_id, event_name, created_at)
```

---

### 5. Proxy Provider Abstraction

```
ProxyProvider Interface
│
├── ProviderA (API-based)
│   ├── Import Proxies
│   ├── Check Balance
│   ├── Assign Proxy
│   ├── Renew Subscription
│   ├── Get Status
│   └── Handle Errors
│
├── ProviderB (CSV-based)
│   ├── Parse CSV
│   ├── Validate Data
│   ├── Bulk Import
│   ├── Manual Renewal
│   ├── Health Checks
│   └── Error Recovery
│
└── ProviderC (Manual)
    ├── Manual Entry
    ├── IP Validation
    ├── Credentials Encryption
    ├── Usage Tracking
    └── Expiration Management

Benefits:
✓ Add new providers without code changes
✓ Hot-swap providers
✓ Redundancy & failover
✓ Abstraction from business logic
```

---

### 6. Payment Provider Abstraction

```
PaymentProvider Interface
│
├── Stripe Adapter
│   ├── Create Payment Intent
│   ├── Webhook Signature Verification
│   ├── Webhook Handlers
│   ├── Refund Processing
│   └── Dispute Handling
│
├── PayPal Adapter
│   ├── Create Payment
│   ├── IPN Listener
│   ├── Refund Request
│   └── Billing Plan
│
├── Razorpay Adapter
│   ├── Order Creation
│   ├── Webhook Handling
│   ├── Refund Processing
│   └── Subscriptions
│
└── Fallback/Manual
    ├── Bank Transfer
    ├── Manual Verification
    ├── Invoice Generation
    └── Status Tracking

Flow:
1. Client initiates → API /create-payment
2. API selects provider
3. API creates payment with provider
4. Client → redirects to provider
5. Provider → webhook to API
6. API verifies webhook signature
7. API credits subscription
8. Client updates UI via polling
```

---

### 7. Android VPN Engine Architecture

```
ProxyVpnService (Android VPNService)
│
├── Connection Manager
│   ├── Protocol Handler
│   │   ├── SOCKS5
│   │   ├── HTTP/HTTPS
│   │   └── Others (extensible)
│   │
│   ├── Network State Machine
│   │   ├── IDLE
│   │   ├── CONNECTING
│   │   ├── CONNECTED
│   │   ├── DISCONNECTING
│   │   ├── DISCONNECTED
│   │   └── ERROR
│   │
│   ├── Auto-Reconnect Logic
│   │   ├── Exponential Backoff
│   │   ├── Max Retries
│   │   ├── Network Change Detection
│   │   └── Timeout Handling
│   │
│   └── Kill Switch
│       ├── Monitor VPN status
│       ├── Block all traffic if down
│       ├── Whitelist system services
│       └── Graceful shutdown
│
├── DNS Protection
│   ├── Custom DNS Options
│   ├── Leak Prevention
│   ├── IPv6 Leak Handling
│   └── DNS Over HTTPS (DoH)
│
├── Traffic Interception
│   ├── All Traffic Mode
│   ├── Selected Apps Mode
│   ├── Excluded Apps Mode
│   └── Package Manager Integration
│
├── Statistics & Monitoring
│   ├── Bytes Up/Down
│   ├── Packet Loss
│   ├── Latency
│   ├── Connection Duration
│   └── Errors & Disconnects
│
└── Security
    ├── Credential Encryption
    ├── Secure Storage (Keystore)
    ├── No Logs of Traffic
    ├── Certificate Pinning
    └── SSL/TLS Validation
```

---

### 8. Caching Strategy

```
Multi-Level Caching
│
├── Redis Cache (Distributed)
│   ├── Session Data (TTL: 24h)
│   ├── User Preferences (TTL: 1h)
│   ├── Proxy List (TTL: 30m)
│   ├── Product Info (TTL: 6h)
│   ├── Health Scores (TTL: 5m)
│   └── Rate Limit Counters (TTL: 1m)
│
├── App Cache (Local)
│   ├── Recently Used Proxies
│   ├── Subscription Info
│   ├── User Profile
│   ├── Favorite Proxies
│   └── Last Connection Data
│
├── Database Query Cache
│   ├── Materialized Views
│   │   ├── v_user_dashboard
│   │   ├── v_active_subscriptions
│   │   └── v_proxy_health_summary
│   │
│   └── Query Result Caching (Redis)
│
└── Browser/Client Cache
    ├── Static Assets (1 year)
    ├── API Responses (5 minutes)
    └── Images (30 days)

Invalidation Triggers:
- Manual: Cache clear endpoint
- Automatic: TTL expiration
- Event-based: Order placed, proxy updated
- Scheduled: Cron jobs for stale data
```

---

### 9. Security Architecture

```
Security Layers
│
├── Transport Security
│   ├── HTTPS/TLS 1.3
│   ├── Certificate Pinning
│   ├── HSTS Headers
│   └── Secure Cookies
│
├── API Security
│   ├── Rate Limiting
│   │   ├── Global: 100 req/min
│   │   ├── Auth: 5 attempts/15min
│   │   └── Payments: 20 req/min
│   │
│   ├── CORS Configuration
│   │   ├── Whitelist Origins
│   │   ├── Restrict Methods
│   │   └── Credentials: true
│   │
│   ├── JWT Authentication
│   │   ├── Signing with HS256
│   │   ├── 24h Expiration
│   │   ├── Refresh Token Rotation
│   │   └── Token Blacklist (optional)
│   │
│   ├── Input Validation
│   │   ├── Joi Schema Validation
│   │   ├── Type Checking
│   │   ├── SQL Injection Prevention
│   │   └── XSS Prevention
│   │
│   └── Request Signing
│       ├── HMAC-SHA256
│       ├── Timestamp Validation
│       └── Replay Attack Prevention
│
├── Database Security
│   ├── Row-Level Security (RLS)
│   │   ├── User → Own data only
│   │   ├── Admin → All data
│   │   └── Service Role → Full access
│   │
│   ├── Encryption
│   │   ├── Proxy passwords (AES-256)
│   │   ├── Sensitive API keys
│   │   └── Payment tokens
│   │
│   ├── Access Control
│   │   ├── Column-level restrictions
│   │   ├── Audit on modifications
│   │   └── Connection pooling with limits
│   │
│   └── Backups
│       ├── Daily snapshots
│       ├── Point-in-time recovery
│       └── Encrypted offsite storage
│
├── Application Security
│   ├── Secrets Management
│   │   ├── Environment variables only
│   │   ├── No hardcoded credentials
│   │   ├── Rotation policies
│   │   └── Audit logging
│   │
│   ├── Code Security
│   │   ├── Static Analysis (ESLint)
│   │   ├── Dependency Scanning
│   │   ├── SAST (SonarQube)
│   │   └── Regular Audits
│   │
│   └── Error Handling
│       ├── Generic error messages
│       ├── Detailed internal logs
│       ├── Error tracking (Sentry)
│       └── Alert on critical errors
│
├── Android Security
│   ├── Secure Storage
│   │   ├── Android Keystore
│   │   ├── Encrypted SharedPreferences
│   │   └── Room DB encryption
│   │
│   ├── Permissions
│   │   ├── Runtime permission checks
│   │   ├── Minimal permissions
│   │   └── Audit logging
│   │
│   ├── Code Obfuscation
│   │   ├── ProGuard/R8
│   │   ├── Resource obfuscation
│   │   └── String encryption
│   │
│   └── Network Security
│       ├── Certificate pinning
│       ├── TLS 1.2+
│       ├── Network security config
│       └── Cleartext disabled
│
└── Infrastructure Security
    ├── Firewall Rules
    ├── WAF (Web Application Firewall)
    ├── DDoS Protection (Cloudflare)
    ├── Intrusion Detection
    ├── Secrets Vault (AWS Secrets Manager)
    └── VPC/Network Isolation
```

---

### 10. Deployment Architecture

```
Production Environment
│
├── Frontend (CDN)
│   ├── Vercel / Netlify
│   ├── Global Edge Caching
│   ├── DDoS Protection
│   └── Auto-scaling
│
├── API Servers (Kubernetes)
│   ├── Load Balancer (NGINX)
│   ├── Pod Autoscaling
│   ├── Health Checks
│   ├── Rolling Updates
│   └── Service Mesh (optional: Istio)
│
├── Database (Managed Service)
│   ├── Supabase (PostgreSQL)
│   ├── Automated Backups
│   ├── Read Replicas
│   ├── Point-in-time Recovery
│   └── Connection Pooling
│
├── Cache Layer
│   ├── Redis Cluster
│   ├── Multi-region
│   ├── Replication
│   └── Backup & Recovery
│
├── Message Queue
│   ├── Bull (Redis-based)
│   ├── Job Workers
│   ├── Scheduled Tasks
│   └── Dead Letter Queue
│
├── Monitoring & Observability
│   ├── Application Monitoring (New Relic)
│   ├── Error Tracking (Sentry)
│   ├── Logging (ELK Stack)
│   ├── Metrics (Prometheus)
│   ├── Alerting (PagerDuty)
│   └── APM Tracing
│
├── CI/CD Pipeline
│   ├── GitHub Actions
│   ├── Build Automation
│   ├── Test Automation
│   ├── Code Quality Checks
│   ├── Security Scanning
│   └── Automated Deployment
│
└── Backup & Disaster Recovery
    ├── Database Backups (hourly)
    ├── Code Repository Backups
    ├── Disaster Recovery Plan
    ├── RTO: 2 hours
    └── RPO: 1 hour
```

---

## Technology Stack Summary

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | Kotlin, Jetpack Compose, MVVM | Android mobile app |
| **Admin** | Next.js, React, TypeScript | Admin dashboard |
| **Backend** | Node.js, Express, TypeScript | REST API |
| **Database** | Supabase, PostgreSQL | Data persistence |
| **Cache** | Redis | Session, cache, queue |
| **Auth** | JWT, Supabase Auth | Authentication |
| **Payment** | Stripe, PayPal, Razorpay | Payment processing |
| **Email** | SendGrid/SMTP | Email delivery |
| **Storage** | AWS S3 | File storage |
| **Hosting** | Docker, Kubernetes, AWS/GCP | Infrastructure |
| **CI/CD** | GitHub Actions | Automation |
| **Monitoring** | Sentry, New Relic | Observability |
| **VPN** | Android VPNService | Proxy routing |

---

## Scalability Considerations

### Horizontal Scaling
- Stateless API servers (add more instances)
- Database read replicas
- Redis cluster for distributed cache
- CDN for static assets

### Vertical Scaling
- Increase server resources
- Database performance tuning
- Query optimization
- Connection pooling

### Performance Optimization
- Database indexing
- Query result caching
- Lazy loading
- Pagination
- Compression (Gzip)
- Image optimization
- Code splitting

---

## Future Enhancements

1. **Microservices**: Split into separate services (Auth, Payment, Analytics)
2. **GraphQL**: Add GraphQL layer alongside REST
3. **Real-time**: WebSocket support for live stats
4. **iOS App**: Kotlin Multiplatform Mobile (KMM)
5. **Web Dashboard**: Full-featured user dashboard
6. **AI/ML**: Smart proxy selection, anomaly detection
7. **Blockchain**: Payment alternatives, transparency
8. **Multi-region**: Geographic redundancy

---

**Document Version**: 1.0.0  
**Last Updated**: 2024  
**Status**: Production-Ready
