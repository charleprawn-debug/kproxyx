# Provider marketplace API research

## Proxy-Cheap
Source: https://docs.proxy-cheap.com/

Proxy-Cheap exposes a residential product through `GET /v2/order`, `POST /v2/order/{serviceId}`, `POST /v2/order/{serviceId}/price`, and `POST /v2/order/{serviceId}/execute`. Authentication uses `X-Api-Key` and `X-Api-Secret`. The residential service is `static-residential-ipv4`, with plans such as `basic`, `standard`, and `premium`. The setup request requires a non-empty `planId`; the application loads the service catalog first and uses the first valid plan to retrieve the location catalog, while still showing all plans to the user. Setup returns `countries` as ISO strings and separate `regions`, `cities`, and `isps` maps. The location values must be sent in the exact fields expected by the selected setup; sending a city or region that is not allowed for that country causes a provider validation error.

The price response returns `finalPrice` at the top level, not `data.total`. The execute response returns an order ID, and `GET /orders/{id}/proxies` must be polled until an `ACTIVE` proxy appears. The public API also documents later management operations such as extension, bandwidth purchase, whitelist changes, protocol changes, authentication changes, rotation, and auto-extension.

## Proxy-Seller residential
Sources:

- https://docs.proxy-seller.com/api-v2/residential-proxy
- https://docs.proxy-seller.com/api-v2/residential-proxy/get-package-information
- https://docs.proxy-seller.com/api-v2/residential-proxy/get-all-locations
- https://docs.proxy-seller.com/api-v2/residential-proxy/create-list
- https://docs.proxy-seller.com/api-v1/residential-proxy/connect-and-target-via-login

The current residential API is package/list based. `GET /resident/package` returns the provider package's traffic limit, traffic remaining, expiry, and rotation. `GET /resident/geo` returns the country, region, city, and ISP hierarchy. `POST /resident/list/add` creates a named list inside that existing package with `{title, whitelist, geo, export, rotation}`. The API returns list credentials. Residential connections use `res.proxy-seller.com` (or a region host), port `10000`, and HTTP/SOCKS5; targeting can also be expressed through documented login suffixes.

The public residential section does not document an endpoint that sells selectable GB packages or returns a price for each GB plan. It also does not document a residential purchase/refund flow through `/order/calc` and `/order/make`; those are for the IP-based product types. Therefore the application must not invent residential plan prices. The implemented flow displays the real existing package quota, lets the user select supported GEO and rotation settings, and creates a residential list. Buying or renewing the package remains a provider-dashboard operation unless Proxy-Seller supplies a separate private endpoint.
