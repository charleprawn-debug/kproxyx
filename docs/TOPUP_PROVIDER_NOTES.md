# Provider integration notes

- Reference repository: https://github.com/jude3432/pot.git
- API Syria reference integration uses `https://apisyria.com/api/v1`, header `X-Api-Key`, and query parameters:
  - Syriatel transaction: `resource=syriatel`, `action=find_tx`, `tx`, `gsm`, `period`
  - ShamCash transaction: `resource=shamcash`, `action=find_tx`, `tx`, `account_address`
  - ShamCash history: `resource=shamcash`, `action=logs`, `account_address`
- Reference verifies exact amount, currency for ShamCash, transaction reference, and a bounded timestamp window before approval.
- Reference OxaPay integration uses `https://api.oxapay.com/v1`, header `merchant_api_key`, endpoint `POST /payment/white-label`, fields including `pay_currency=USDT`, `network`, `amount`, `currency`, `callback_url`, `order_id`, and returns `track_id`, `address`, `pay_amount`.
- OxaPay webhook signatures in the reference are HMAC-SHA512 over the raw request body using the merchant API key.
- Official OxaPay documentation pages returned HTTP 403 from the sandbox fetch, so implementation follows the existing reference integration and keeps provider credentials server-side.
