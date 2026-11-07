# API Contract

Base URL: `http://localhost:3000/api/v1` in local development.

All errors use this envelope:

```json
{"error":{"code":"ERROR_CODE","message":"Human-readable message"}}
```

## Public endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/livez` | Process liveness; no dependency check. |
| GET | `/readyz` | Supabase dependency readiness. |
| GET | `/api/health` | Version and environment information. |
| POST | `/api/v1/auth/register` | Creates a Supabase Auth user and profile. Requires `email`, `password` (minimum 8 characters), and optional `fullName`. |
| POST | `/api/v1/auth/login` | Exchanges email and password for Supabase access and refresh tokens. |
| POST | `/api/v1/auth/refresh` | Rotates a Supabase refresh token. |
| GET | `/api/v1/products` | Lists active proxy products. Supports `country`, `protocol`, and `featured` filters. |
| GET | `/api/v1/products/:id` | Reads one active product. |

## Authenticated endpoints

Send `Authorization: Bearer <access-token>`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/me` | Reads the current user profile. |
| GET | `/api/v1/me/subscriptions` | Reads subscriptions belonging to the current user. |

Payment, order creation, proxy assignment, and VPN control endpoints are intentionally absent until implemented and tested.
