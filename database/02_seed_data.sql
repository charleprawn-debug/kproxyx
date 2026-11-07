-- ============================================================================
-- PROXY PLATFORM - SEED DATA
-- ============================================================================

-- 1. Insert Sample Proxy Providers
INSERT INTO proxy_providers (id, name, description, provider_type, is_active)
VALUES 
    ('550e8400-e29b-41d4-a716-446655440001'::UUID, 'Premium Proxy Corp', 'High-speed residential proxies', 'api', true),
    ('550e8400-e29b-41d4-a716-446655440002'::UUID, 'DataCenter Proxies Inc', 'Fast datacenter proxies', 'api', true),
    ('550e8400-e29b-41d4-a716-446655440003'::UUID, 'Mobile Proxy Services', 'Real mobile proxies', 'api', true)
ON CONFLICT DO NOTHING;

-- 2. Insert Sample Products
INSERT INTO proxy_products (
    id, provider_id, name, description, protocol, country_code, country_name, 
    city, ip_type, speed_mbps, avg_ping_ms, uptime_percentage,
    price_daily, price_weekly, price_monthly, price_yearly,
    traffic_limit_gb, device_limit, category, is_active, is_featured
)
VALUES 
    -- USA Proxies
    ('550e8400-e29b-41d4-a716-446655550001'::UUID, '550e8400-e29b-41d4-a716-446655440001'::UUID,
     'USA Residential Premium', 'Fast residential proxies from USA', 'socks5', 'US', 'United States',
     'New York', 'residential', 100, 45, 99.9,
     2.99, 14.99, 49.99, 499.99, 100, 3, 'premium', true, true),

    ('550e8400-e29b-41d4-a716-446655550002'::UUID, '550e8400-e29b-41d4-a716-446655440001'::UUID,
     'USA DataCenter', 'High-speed datacenter proxies', 'https', 'US', 'United States',
     'Los Angeles', 'datacenter', 250, 20, 99.95,
     1.99, 9.99, 29.99, 299.99, NULL, 5, 'basic', true, false),

    -- Germany Proxies
    ('550e8400-e29b-41d4-a716-446655550003'::UUID, '550e8400-e29b-41d4-a716-446655440002'::UUID,
     'Germany Residential', 'European residential proxies', 'socks5', 'DE', 'Germany',
     'Berlin', 'residential', 80, 55, 99.8,
     3.49, 16.99, 54.99, 549.99, 150, 2, 'premium', true, false),

    -- UK Proxies
    ('550e8400-e29b-41d4-a716-446655550004'::UUID, '550e8400-e29b-41d4-a716-446655440001'::UUID,
     'UK Fast Residential', 'UK-based residential proxies', 'https', 'GB', 'United Kingdom',
     'London', 'residential', 120, 40, 99.85,
     2.99, 14.99, 49.99, 499.99, 200, 4, 'premium', true, true),

    -- Japan Proxies
    ('550e8400-e29b-41d4-a716-446655550005'::UUID, '550e8400-e29b-41d4-a716-446655440003'::UUID,
     'Japan Mobile Proxies', 'Real mobile proxies in Japan', 'socks5', 'JP', 'Japan',
     'Tokyo', 'mobile', 60, 65, 99.5,
     4.99, 23.99, 74.99, 749.99, 50, 1, 'premium', true, false),

    -- Canada Budget
    ('550e8400-e29b-41d4-a716-446655550006'::UUID, '550e8400-e29b-41d4-a716-446655440002'::UUID,
     'Canada Budget Proxies', 'Budget-friendly proxies', 'http', 'CA', 'Canada',
     'Toronto', 'datacenter', 50, 30, 98.9,
     0.99, 4.99, 14.99, 149.99, 50, 1, 'basic', true, false)
ON CONFLICT DO NOTHING;

-- 3. Insert Sample Proxy Instances
INSERT INTO proxies (
    id, product_id, provider_id, ip_address, port, status, 
    health_score, last_check_at, created_at
)
VALUES 
    ('650e8400-e29b-41d4-a716-446655550001'::UUID, '550e8400-e29b-41d4-a716-446655550001'::UUID, 
     '550e8400-e29b-41d4-a716-446655440001'::UUID, '198.51.100.1'::INET, 8080, 'available', 5.0, NOW(), NOW()),

    ('650e8400-e29b-41d4-a716-446655550002'::UUID, '550e8400-e29b-41d4-a716-446655550001'::UUID,
     '550e8400-e29b-41d4-a716-446655440001'::UUID, '198.51.100.2'::INET, 8080, 'available', 4.8, NOW(), NOW()),

    ('650e8400-e29b-41d4-a716-446655550003'::UUID, '550e8400-e29b-41d4-a716-446655550002'::UUID,
     '550e8400-e29b-41d4-a716-446655440002'::UUID, '203.0.113.1'::INET, 9090, 'available', 4.9, NOW(), NOW()),

    ('650e8400-e29b-41d4-a716-446655550004'::UUID, '550e8400-e29b-41d4-a716-446655550003'::UUID,
     '550e8400-e29b-41d4-a716-446655440002'::UUID, '192.0.2.1'::INET, 8080, 'available', 4.7, NOW(), NOW()),

    ('650e8400-e29b-41d4-a716-446655550005'::UUID, '550e8400-e29b-41d4-a716-446655550004'::UUID,
     '550e8400-e29b-41d4-a716-446655440001'::UUID, '198.51.100.10'::INET, 3128, 'available', 5.0, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- 4. Sample Coupons
INSERT INTO coupons (
    id, code, description, discount_type, discount_value, 
    max_discount_amount, minimum_order_amount, usage_limit,
    is_active, valid_from, valid_until
)
VALUES 
    ('750e8400-e29b-41d4-a716-446655550001'::UUID, 'WELCOME20', 
     '20% off first order', 'percentage', 20, 100, 10, 100, true, NOW(), NOW() + INTERVAL '30 days'),

    ('750e8400-e29b-41d4-a716-446655550002'::UUID, 'SAVE10',
     '$10 off any purchase', 'fixed', 10, 10, 0, 1000, true, NOW(), NOW() + INTERVAL '90 days'),

    ('750e8400-e29b-41d4-a716-446655550003'::UUID, 'PREMIUM15',
     '15% off premium proxies', 'percentage', 15, 50, 30, 50, true, NOW(), NOW() + INTERVAL '60 days')
ON CONFLICT DO NOTHING;

-- 5. Feature Flags
INSERT INTO app_config (config_key, config_value, description, is_active)
VALUES 
    ('maintenance_mode', 'false', 'Enable/disable maintenance mode', true),
    ('min_app_version', '"1.0.0"', 'Minimum required app version', true),
    ('max_app_version', '"999.0.0"', 'Maximum app version (deprecation)', true),
    ('feature_flags', '{"referral": true, "coupons": true, "smart_select": true, "auto_proxy_selection": true}', 
     'Feature flags', true),
    ('api_rate_limit', '{"general": 100, "auth": 5, "payment": 20}', 'Rate limits per endpoint', true),
    ('notification_settings', '{"enabled": true, "channels": ["email", "push"], "digest_frequency": "daily"}',
     'Notification configuration', true),
    ('proxy_test_config', '{"timeout_ms": 5000, "test_url": "http://ipinfo.io/json", "health_check_interval_minutes": 60}',
     'Proxy health check configuration', true),
    ('payment_settings', '{"enable_stripe": true, "enable_paypal": true, "enable_razorpay": false, "min_transaction": 0.99}',
     'Payment provider settings', true),
    ('support_settings', '{"email": "support@proxyplatform.com", "response_time_hours": 24, "ticket_categories": ["payment", "proxy", "connection", "account", "technical"]}',
     'Support center configuration', true),
    ('analytics_settings', '{"enabled": true, "track_user_connections": true, "collect_usage_stats": true, "retention_days": 90}',
     'Analytics configuration', true)
ON CONFLICT (config_key) DO NOTHING;

-- 6. Admin Users (optional)
-- Note: These should be created through proper registration flow in production
-- This is just for reference

-- 7. Sample Analytics Events (for dashboard)
INSERT INTO analytics_events (
    user_id, event_name, event_category, properties, created_at
)
SELECT NULL::UUID, 'platform_startup', 'system', '{"version": "1.0.0"}'::JSONB, NOW() - INTERVAL '1 hour'
UNION ALL
SELECT NULL::UUID, 'proxy_listed', 'marketplace', '{"product_count": 6}'::JSONB, NOW() - INTERVAL '30 minutes'
UNION ALL
SELECT NULL::UUID, 'payment_provider_available', 'payment', '{"providers": ["stripe", "paypal"]}'::JSONB, NOW() - INTERVAL '15 minutes'
ON CONFLICT DO NOTHING;

-- 8. Create Index Stats
ANALYZE users;
ANALYZE proxy_products;
ANALYZE proxies;
ANALYZE subscriptions;
ANALYZE orders;

-- 9. View initialization
-- Health summary is a normal view and requires no refresh.

-- Print confirmation
SELECT 'Seed data inserted successfully!' as status,
       (SELECT COUNT(*) FROM proxy_providers) as providers,
       (SELECT COUNT(*) FROM proxy_products) as products,
       (SELECT COUNT(*) FROM proxies) as proxy_instances,
       (SELECT COUNT(*) FROM coupons) as coupons,
       (SELECT COUNT(*) FROM app_config) as config_items;
