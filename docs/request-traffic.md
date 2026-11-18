# Request totals

Endpoint counters travel in the existing daily usage POST to Vercel. There is no per-request upload, extra reporting timer, or call to Mobilism for telemetry. The app version is unchanged by this work. The dashboard stays hidden until the verified stable release catalog contains v0.1.24 or later, then shows that minimum version.

## Collection and delivery

The native Mobilism API/forum client and Coil's Mobilism image client count network attempts by fixed endpoint group, HTTP method, and outcome. The six outcomes are success, redirect, other 4xx, 429, 5xx, and network failure. Retries and redirects count separately. Cache hits do not count. Duration measures time to response headers or failure, capped at five minutes per attempt; it does not measure body downloads.

Counters persist locally by UTC date and app version. The next daily usage report attaches completed dates from the prior seven days, with at most eight batches. A batch becomes immutable when first attached. Later reports may carry the same batch again; its stable random ID lets the server ignore it. App upgrades keep earlier version buckets separate. WorkManager stores only the report date and loads the durable payload, avoiding its 10 KiB data limit.

Each endpoint/method row holds six totals and a duration sum, capped at one million attempts. A batch has at most 128 rows. The service rejects unknown groups, fields, invalid dates, duplicate rows, and payloads larger than 256 KiB. No URLs, query strings, release IDs, credentials, request bodies, usernames, or permanent installation IDs are collected.

Usage and traffic receipts and aggregates update in one atomic SQL statement. Concurrent requests, lost acknowledgements, and resending a batch in a different daily report cannot double-count it. Usage receipts retain eight UTC dates including today. Traffic receipts retain 14 completed dates, covering carryover plus delivery retry. Both aggregate tables retain 365 UTC dates.

## Coverage and limits

These are anonymous reporting-client counts, not server-wide traffic or real-time logs. Current-day totals arrive on a later foreground daily report. Inactive clients, opt-outs, offline delivery, capped buckets, storage failure, and incorrect clocks can omit data. The service cannot authenticate individual request counts from a public client.

WebViews, external image hosts, third-party download/debrid services, APK downloads, remote configuration, and the usage endpoint itself are excluded. DNS, TLS, and connection failures before an HTTP attempt reaches OkHttp's network interceptor are excluded. Network failures after headers while reading a body remain classified by the received HTTP status.

Settings > Anonymous usage controls both device reports and counters. Disabling clears local counters, strips traffic from queued reports, and cancels pending delivery. Existing daily usage IDs remain for deduplication. An already-started upload can finish. Debug builds and builds without a usage endpoint do not collect counters through the application sink.

## Validation

Core unit tests use a loopback HTTP server for redirect counts, cache exclusion, network failure, and sink failure. App unit tests cover immutable batches, process recreation, upgrades, expiry, bounds, and persistence failure. Service tests use embedded Postgres for atomic updates, duplicate delivery, retention, validation, and release gating. Use the synthetic dashboard preview with `--following-controls` to inspect the future endpoint panel; normal preview keeps it hidden. These checks do not measure production server load.
