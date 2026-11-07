-- ============================================================================
-- PROXY PLATFORM - COMPLETE DATABASE SCHEMA
-- Database: Supabase PostgreSQL
-- Version: 1.0.0
-- ============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enable RLS
-- Managed Supabase controls server parameters; no ALTER SYSTEM is required here.

-- ============================================================================
-- PART 1: CORE TABLES
-- ============================================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    auth_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    full_name VARCHAR(255),
    avatar_url TEXT,
    country_code VARCHAR(2),
    language VARCHAR(10) DEFAULT 'en',
    role VARCHAR(50) DEFAULT 'user', -- user, admin, moderator
    status VARCHAR(50) DEFAULT 'active', -- active, banned, suspended
    
    -- KYC/Verification
    is_email_verified BOOLEAN DEFAULT FALSE,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    is_phone_verified BOOLEAN DEFAULT FALSE,
    phone_verified_at TIMESTAMP WITH TIME ZONE,
    
    -- Account settings
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    notification_preferences JSONB DEFAULT '{"email": true, "push": true, "sms": false}',
    privacy_settings JSONB DEFAULT '{"profile_public": false, "show_referrals": false}',
    
    -- Metadata
    last_login_at TIMESTAMP WITH TIME ZONE,
    login_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$'),
    CONSTRAINT phone_format CHECK (phone IS NULL OR phone ~* '^\+?[1-9]\d{1,14}$')
);

-- User sessions (device tracking)
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    device_model VARCHAR(255),
    os_type VARCHAR(50), -- android, ios, web
    os_version VARCHAR(50),
    app_version VARCHAR(50),
    ip_address INET,
    user_agent TEXT,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

-- Proxy Providers (abstraction for multiple proxy vendors)
CREATE TABLE IF NOT EXISTS proxy_providers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    provider_type VARCHAR(50), -- api, manual, csv
    api_endpoint TEXT,
    api_key_encrypted TEXT,
    api_secret_encrypted TEXT,
    config JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT TRUE,
    balance DECIMAL(12, 2),
    last_sync_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Proxy Products (what's sold in the marketplace)
CREATE TABLE IF NOT EXISTS proxy_products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_id UUID REFERENCES proxy_providers(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    protocol VARCHAR(50) NOT NULL, -- http, https, socks5
    country_code VARCHAR(2),
    country_name VARCHAR(100),
    city VARCHAR(100),
    ip_type VARCHAR(50), -- residential, datacenter, mobile
    
    -- Specifications
    speed_mbps INTEGER, -- expected speed
    avg_ping_ms INTEGER,
    uptime_percentage DECIMAL(5, 2),
    concurrent_connections INTEGER,
    rotating BOOLEAN DEFAULT FALSE,
    
    -- Pricing
    price_daily DECIMAL(10, 2),
    price_weekly DECIMAL(10, 2),
    price_monthly DECIMAL(10, 2),
    price_quarterly DECIMAL(10, 2),
    price_yearly DECIMAL(10, 2),
    
    -- Limits
    traffic_limit_gb INTEGER, -- NULL = unlimited
    device_limit INTEGER DEFAULT 1,
    simultaneous_connections INTEGER DEFAULT 1,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_featured BOOLEAN DEFAULT FALSE,
    stock_available INTEGER DEFAULT 0,
    stock_total INTEGER DEFAULT 0,
    
    -- Metadata
    category VARCHAR(50), -- premium, basic, business
    tags JSONB DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Proxy Inventory (actual proxy instances)
CREATE TABLE IF NOT EXISTS proxies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES proxy_products(id) ON DELETE RESTRICT,
    provider_id UUID REFERENCES proxy_providers(id),
    provider_reference_id VARCHAR(255), -- ID from external provider
    
    ip_address INET NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(255),
    password_encrypted TEXT, -- Encrypted with pgcrypto
    
    -- Current status
    status VARCHAR(50) DEFAULT 'available', -- available, assigned, expired, disabled, offline
    assignment_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP WITH TIME ZONE,
    
    -- Health info
    last_check_at TIMESTAMP WITH TIME ZONE,
    health_score DECIMAL(3, 1) DEFAULT 5.0, -- 0-5
    last_online_at TIMESTAMP WITH TIME ZONE,
    connection_failure_count INTEGER DEFAULT 0,
    
    -- Dates
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT proxy_unique_address UNIQUE(ip_address, port, product_id)
);

-- Proxy health metrics (historical tracking)
CREATE TABLE IF NOT EXISTS proxy_health_checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    proxy_id UUID NOT NULL REFERENCES proxies(id) ON DELETE CASCADE,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    is_online BOOLEAN,
    response_time_ms INTEGER,
    ping_ms INTEGER,
    dns_resolution_ms INTEGER,
    
    download_speed_kbps INTEGER,
    upload_speed_kbps INTEGER,
    
    http_status_code INTEGER,
    error_message TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- PART 2: SUBSCRIPTION & ASSIGNMENT
-- ============================================================================

-- Subscriptions (user's purchased proxy subscriptions)
CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proxy_id UUID REFERENCES proxies(id) ON DELETE SET NULL,
    product_id UUID NOT NULL REFERENCES proxy_products(id) ON DELETE RESTRICT,
    
    -- Subscription details
    status VARCHAR(50) DEFAULT 'active', -- active, expired, cancelled, suspended
    duration_type VARCHAR(50) NOT NULL, -- daily, weekly, monthly, quarterly, yearly
    
    -- Dates
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    will_renew BOOLEAN DEFAULT TRUE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    
    -- Usage
    traffic_used_gb DECIMAL(10, 2) DEFAULT 0,
    connection_count INTEGER DEFAULT 0,
    last_connected_at TIMESTAMP WITH TIME ZONE,
    
    -- Renewal info
    auto_renew BOOLEAN DEFAULT TRUE,
    renewal_order_id UUID,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Connection sessions (track each proxy usage)
CREATE TABLE IF NOT EXISTS connection_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    proxy_id UUID REFERENCES proxies(id) ON DELETE SET NULL,
    device_id VARCHAR(255),
    
    -- Connection metadata
    protocol VARCHAR(50), -- http, https, socks5
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds INTEGER,
    
    -- Statistics
    bytes_uploaded BIGINT DEFAULT 0,
    bytes_downloaded BIGINT DEFAULT 0,
    packet_loss_percentage DECIMAL(5, 2),
    avg_latency_ms INTEGER,
    connection_type VARCHAR(50), -- wifi, mobile, wired
    
    -- Status
    status VARCHAR(50) DEFAULT 'connected', -- connected, disconnected, failed, timeout
    disconnect_reason VARCHAR(255),
    error_code VARCHAR(50),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- PART 3: ORDERS & PAYMENTS
-- ============================================================================

-- Products (for purchasing)
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    proxy_product_id UUID REFERENCES proxy_products(id) ON DELETE CASCADE,
    
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sku VARCHAR(100) NOT NULL UNIQUE,
    
    base_price DECIMAL(10, 2) NOT NULL,
    tax_percentage DECIMAL(5, 2) DEFAULT 0,
    discount_percentage DECIMAL(5, 2) DEFAULT 0,
    
    is_active BOOLEAN DEFAULT TRUE,
    category VARCHAR(100),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Orders
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- Order info
    order_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) DEFAULT 'pending', -- pending, processing, paid, completed, cancelled, refunded
    
    -- Amounts
    subtotal DECIMAL(10, 2) NOT NULL,
    tax_amount DECIMAL(10, 2) DEFAULT 0,
    discount_amount DECIMAL(10, 2) DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL,
    
    -- Payment
    payment_method VARCHAR(50), -- card, wallet, crypto, bank_transfer
    payment_provider VARCHAR(50), -- stripe, paypal, razorpay, etc
    payment_id VARCHAR(255), -- External payment ID
    payment_status VARCHAR(50) DEFAULT 'pending',
    
    -- Dates
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    paid_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Metadata
    coupon_id UUID,
    notes TEXT
);

-- Order items (line items in an order)
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    proxy_product_id UUID NOT NULL REFERENCES proxy_products(id),
    
    duration_type VARCHAR(50) NOT NULL, -- daily, weekly, monthly, quarterly, yearly
    quantity INTEGER DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    
    -- Subscription created from this item
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Payments (payment transactions)
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    payment_provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    
    status VARCHAR(50) DEFAULT 'pending', -- pending, success, failed, refunded
    payment_method VARCHAR(50),
    
    metadata JSONB DEFAULT '{}',
    error_message TEXT,
    
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- PART 4: PROMOTIONS & REFERRALS
-- ============================================================================

-- Coupons
CREATE TABLE IF NOT EXISTS coupons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    
    discount_type VARCHAR(50) NOT NULL, -- percentage, fixed
    discount_value DECIMAL(10, 2) NOT NULL,
    max_discount_amount DECIMAL(10, 2),
    
    minimum_order_amount DECIMAL(10, 2),
    applicable_products JSONB DEFAULT '[]', -- empty = all
    
    usage_limit INTEGER, -- NULL = unlimited
    usage_limit_per_user INTEGER DEFAULT 1,
    current_usage INTEGER DEFAULT 0,
    
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    
    valid_from TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    valid_until TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE orders ADD CONSTRAINT orders_coupon_id_fkey FOREIGN KEY (coupon_id) REFERENCES coupons(id);

-- Referral system
CREATE TABLE IF NOT EXISTS referrals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    referrer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referred_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    referral_code VARCHAR(50) NOT NULL UNIQUE,
    
    status VARCHAR(50) DEFAULT 'pending', -- pending, completed, expired
    reward_type VARCHAR(50), -- percentage, fixed, credit
    reward_value DECIMAL(10, 2),
    
    completed_at TIMESTAMP WITH TIME ZONE,
    reward_credited_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE
);

-- ============================================================================
-- PART 5: USER FEATURES
-- ============================================================================

-- Favorites (user's favorite proxies)
CREATE TABLE IF NOT EXISTS favorites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proxy_product_id UUID NOT NULL REFERENCES proxy_products(id) ON DELETE CASCADE,
    
    order_num INTEGER, -- for custom ordering
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(user_id, proxy_product_id)
);

-- Proxy user settings (personal proxy configurations)
CREATE TABLE IF NOT EXISTS proxy_user_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE CASCADE,
    
    -- Display settings
    custom_name VARCHAR(255),
    nickname VARCHAR(100),
    tags JSONB DEFAULT '[]',
    
    -- Connection settings
    auto_reconnect BOOLEAN DEFAULT FALSE,
    timeout_seconds INTEGER DEFAULT 30,
    kill_switch_enabled BOOLEAN DEFAULT FALSE,
    app_routing_mode VARCHAR(50) DEFAULT 'all', -- all, selected, excluded
    
    -- DNS settings
    custom_dns_servers JSONB DEFAULT '[]',
    dns_leak_protection BOOLEAN DEFAULT TRUE,
    
    -- Routing
    excluded_apps JSONB DEFAULT '[]',
    included_apps JSONB DEFAULT '[]',
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- PART 6: SUPPORT & NOTIFICATIONS
-- ============================================================================

-- Support tickets
CREATE TABLE IF NOT EXISTS support_tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    subject VARCHAR(255) NOT NULL,
    category VARCHAR(100), -- payment, proxy, connection, account, technical, other
    priority VARCHAR(50) DEFAULT 'medium', -- low, medium, high, urgent
    
    description TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'open', -- open, in_progress, waiting_user, resolved, closed
    
    assigned_to_admin UUID REFERENCES users(id) ON DELETE SET NULL,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE
);

-- Support ticket messages
CREATE TABLE IF NOT EXISTS support_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sender_type VARCHAR(50) NOT NULL, -- user, admin
    
    message TEXT NOT NULL,
    attachments JSONB DEFAULT '[]',
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    -- Types: proxy_expiring, proxy_expired, connection_lost, payment_success, 
    -- new_offer, maintenance, security_alert, referral_success, subscription_renewed
    
    category VARCHAR(50), -- system, account, proxy, payment, promotion
    
    related_entity_type VARCHAR(50), -- proxy, subscription, order, etc
    related_entity_id UUID,
    
    action_url TEXT,
    
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE
);

-- ============================================================================
-- PART 7: ADMIN & ANALYTICS
-- ============================================================================

-- Admin users (separate from regular users)
CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    permissions JSONB DEFAULT '{}', -- JSON of permissions
    role VARCHAR(50) NOT NULL, -- admin, moderator, support
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit log (for tracking admin actions)
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id UUID REFERENCES admin_users(id) ON DELETE SET NULL,
    
    action VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    
    old_values JSONB,
    new_values JSONB,
    
    ip_address INET,
    user_agent TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Analytics events
CREATE TABLE IF NOT EXISTS analytics_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    event_name VARCHAR(255) NOT NULL,
    -- app_open, purchase, connection_start, connection_end, error, etc
    
    event_category VARCHAR(50),
    event_value DECIMAL(10, 2),
    
    properties JSONB DEFAULT '{}',
    
    device_id VARCHAR(255),
    session_id VARCHAR(255),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- App configuration (feature flags, settings)
CREATE TABLE IF NOT EXISTS app_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value JSONB NOT NULL,
    
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- PART 8: INDEXES FOR PERFORMANCE
-- ============================================================================

-- Users indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_auth_id ON users(auth_id);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at DESC);

-- Sessions indexes
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_device_id ON user_sessions(device_id);
CREATE INDEX idx_user_sessions_is_active ON user_sessions(is_active);

-- Proxy indexes
CREATE INDEX idx_proxies_product_id ON proxies(product_id);
CREATE INDEX idx_proxies_status ON proxies(status);
CREATE INDEX idx_proxies_assignment_user_id ON proxies(assignment_user_id);
CREATE INDEX idx_proxies_ip_port ON proxies(ip_address, port);
CREATE INDEX idx_proxies_expires_at ON proxies(expires_at);

-- Subscriptions indexes
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_proxy_id ON subscriptions(proxy_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_expires_at ON subscriptions(expires_at);

-- Orders indexes
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);

-- Connections indexes
CREATE INDEX idx_connection_sessions_user_id ON connection_sessions(user_id);
CREATE INDEX idx_connection_sessions_subscription_id ON connection_sessions(subscription_id);
CREATE INDEX idx_connection_sessions_started_at ON connection_sessions(started_at DESC);

-- Notifications indexes
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- Analytics indexes
CREATE INDEX idx_analytics_events_user_id ON analytics_events(user_id);
CREATE INDEX idx_analytics_events_event_name ON analytics_events(event_name);
CREATE INDEX idx_analytics_events_created_at ON analytics_events(created_at DESC);

-- ============================================================================
-- PART 9: VIEWS FOR COMMON QUERIES
-- ============================================================================

-- User dashboard summary
CREATE OR REPLACE VIEW v_user_dashboard AS
SELECT 
    u.id,
    u.email,
    u.full_name,
    (SELECT COUNT(*) FROM subscriptions WHERE user_id = u.id AND status = 'active') as active_subscriptions,
    (SELECT COUNT(*) FROM orders WHERE user_id = u.id AND status = 'paid') as total_orders,
    (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE user_id = u.id AND status = 'paid') as total_spent,
    (SELECT COUNT(*) FROM connection_sessions WHERE user_id = u.id AND DATE(started_at) = CURRENT_DATE) as connections_today,
    u.last_login_at,
    u.created_at
FROM users u
WHERE u.deleted_at IS NULL;

-- Active subscriptions with proxy details
CREATE OR REPLACE VIEW v_active_subscriptions AS
SELECT 
    s.id,
    s.user_id,
    s.proxy_id,
    p.ip_address,
    pp.name as product_name,
    pp.protocol,
    pp.country_code,
    pp.city,
    s.expires_at,
    CASE 
        WHEN s.expires_at < NOW() THEN 'expired'
        WHEN s.expires_at < NOW() + INTERVAL '7 days' THEN 'expiring_soon'
        ELSE 'active'
    END as status
FROM subscriptions s
LEFT JOIN proxies p ON s.proxy_id = p.id
LEFT JOIN proxy_products pp ON s.product_id = pp.id
WHERE s.status = 'active';

-- Proxy health summary
CREATE OR REPLACE VIEW v_proxy_health_summary AS
SELECT 
    p.id,
    p.ip_address,
    p.port,
    pp.country_code,
    pp.protocol,
    p.status,
    p.health_score,
    p.last_check_at,
    (SELECT COUNT(*) FROM proxies WHERE status = 'assigned' AND product_id = p.product_id) as assigned_count,
    (SELECT COUNT(*) FROM proxies WHERE status = 'available' AND product_id = p.product_id) as available_count
FROM proxies p
LEFT JOIN proxy_products pp ON p.product_id = pp.id
WHERE p.deleted_at IS NULL;

-- ============================================================================
-- PART 10: ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE proxy_providers ENABLE ROW LEVEL SECURITY;
ALTER TABLE proxy_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE proxies ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE connection_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE proxy_user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics_events ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- Users RLS
-- ============================================================================

CREATE POLICY "Users can view own profile"
    ON users FOR SELECT
    USING (auth.uid() = auth_id OR auth.jwt() ->> 'role' = 'admin');

CREATE POLICY "Users can update own profile"
    ON users FOR UPDATE
    USING (auth.uid() = auth_id)
    WITH CHECK (auth.uid() = auth_id);

-- ============================================================================
-- Sessions RLS
-- ============================================================================

CREATE POLICY "Users can view own sessions"
    ON user_sessions FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

-- ============================================================================
-- Subscriptions RLS
-- ============================================================================

CREATE POLICY "Users can view own subscriptions"
    ON subscriptions FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

CREATE POLICY "Admin can view all subscriptions"
    ON subscriptions FOR SELECT
    USING (auth.jwt() ->> 'role' = 'admin');

-- ============================================================================
-- Orders RLS
-- ============================================================================

CREATE POLICY "Users can view own orders"
    ON orders FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

CREATE POLICY "Users can insert own orders"
    ON orders FOR INSERT
    WITH CHECK (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

-- ============================================================================
-- Connection Sessions RLS
-- ============================================================================

CREATE POLICY "Users can view own connections"
    ON connection_sessions FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

-- ============================================================================
-- Notifications RLS
-- ============================================================================

CREATE POLICY "Users can view own notifications"
    ON notifications FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

CREATE POLICY "Users can update own notifications"
    ON notifications FOR UPDATE
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

-- ============================================================================
-- Support Tickets RLS
-- ============================================================================

CREATE POLICY "Users can view own tickets"
    ON support_tickets FOR SELECT
    USING (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()) OR auth.jwt() ->> 'role' = 'admin');

CREATE POLICY "Users can create tickets"
    ON support_tickets FOR INSERT
    WITH CHECK (user_id = (SELECT id FROM users WHERE auth_id = auth.uid()));

-- ============================================================================
-- PART 11: HELPER FUNCTIONS
-- ============================================================================

-- Function to get current user
CREATE OR REPLACE FUNCTION get_current_user()
RETURNS UUID AS $$
  SELECT id FROM users WHERE auth_id = auth.uid() LIMIT 1;
$$ LANGUAGE SQL SECURITY DEFINER;

-- Function to check if user owns subscription
CREATE OR REPLACE FUNCTION user_owns_subscription(sub_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS(
    SELECT 1 FROM subscriptions 
    WHERE id = sub_id 
    AND user_id = (SELECT id FROM users WHERE auth_id = auth.uid())
  );
$$ LANGUAGE SQL SECURITY DEFINER;

-- Function to get user's active subscriptions count
CREATE OR REPLACE FUNCTION get_active_subscriptions_count()
RETURNS INTEGER AS $$
  SELECT COUNT(*) FROM subscriptions 
  WHERE user_id = (SELECT id FROM users WHERE auth_id = auth.uid())
  AND status = 'active'
  AND expires_at > NOW();
$$ LANGUAGE SQL;

-- Function to renew subscription
CREATE OR REPLACE FUNCTION renew_subscription(sub_id UUID)
RETURNS TABLE(success BOOLEAN, message TEXT) AS $$
DECLARE
    v_subscription subscriptions;
    v_days_to_add INTEGER;
BEGIN
    SELECT * INTO v_subscription FROM subscriptions WHERE id = sub_id;
    
    IF NOT FOUND THEN
        RETURN QUERY SELECT FALSE, 'Subscription not found'::TEXT;
        RETURN;
    END IF;
    
    -- Calculate days based on duration_type
    v_days_to_add := CASE 
        WHEN v_subscription.duration_type = 'daily' THEN 1
        WHEN v_subscription.duration_type = 'weekly' THEN 7
        WHEN v_subscription.duration_type = 'monthly' THEN 30
        WHEN v_subscription.duration_type = 'quarterly' THEN 90
        WHEN v_subscription.duration_type = 'yearly' THEN 365
        ELSE 30
    END;
    
    UPDATE subscriptions 
    SET expires_at = NOW() + (v_days_to_add || ' days')::INTERVAL,
        updated_at = NOW(),
        will_renew = TRUE
    WHERE id = sub_id;
    
    RETURN QUERY SELECT TRUE, 'Subscription renewed successfully'::TEXT;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- PART 12: INITIAL DATA (Optional)
-- ============================================================================

-- Insert default app configuration
INSERT INTO app_config (config_key, config_value, description, is_active) 
VALUES 
    ('app_name', '"Proxy Platform"', 'Application name', TRUE),
    ('maintenance_mode', 'false', 'Enable maintenance mode', TRUE),
    ('min_app_version', '"1.0.0"', 'Minimum required app version', TRUE),
    ('feature_flags', '{"referral": true, "coupons": true, "smart_select": true}', 'Feature flags', TRUE),
    ('support_email', '"support@proxyplatform.com"', 'Support email address', TRUE),
    ('default_currency', '"USD"', 'Default currency', TRUE)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
