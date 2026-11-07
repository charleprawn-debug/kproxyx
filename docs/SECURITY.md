# Security Boundary and Release Checklist

The API keeps the Supabase service-role key on the server. The Android client must receive only a public API URL and user-scoped access tokens. The service-role key must never be placed in Android resources, Gradle properties committed to Git, or client logs.

The API validates required startup secrets, limits JSON request bodies to 1 MB, applies Helmet headers, restricts CORS to configured origins, applies rate limiting to `/api`, and uses separate liveness and readiness endpoints. Logs must not include Authorization headers or refresh tokens.

Before production release, the team must complete the following items:

1. Apply and review the database schema in a disposable Supabase project.
2. Add RLS tests for anonymous, authenticated user A, authenticated user B, admin, and service-role access.
3. Add order, payment, webhook signature, idempotency, and inventory-allocation tests before enabling purchases.
4. Replace development CORS origins, configure HTTPS, and move secrets to a managed secret store.
5. Review Supabase Auth email verification, password recovery, session lifetime, and account deletion flows.
6. Perform dependency, container, SAST, secret, and DAST scans in staging.
7. Design and independently review any TUN/VPN transport, DNS policy, IPv6 behavior, kill switch, and reconnect semantics before advertising device-wide VPN functionality.
8. Create a privacy policy, abuse process, data-retention policy, and Google Play Data Safety declaration.

The current Android application is an MVP marketplace shell. It does not implement device-wide VPN tunneling or payment fulfillment.
