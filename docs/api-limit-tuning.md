# API limit tuning

Start with this conservative profile if clients still generate too much traffic. These are starting values, not a measured server capacity. All durations are milliseconds.

| Group | Field | Value |
| --- | --- | ---: |
| API reads | Request spacing | 1,000 |
| API reads | Concurrent reads | 1 |
| API reads | Retry delay | 2,000 |
| API reads | Initial cooldown | 300,000 |
| Search | Request spacing | 5,000 |
| Search | Concurrent categories | 1 |
| Search | Requests per category | 3 |
| Search | Cache lifetime | 900,000 |
| Search | Failure cooldown | 300,000 |
| Discussions | First-page cache | 300,000 |
| Messages | Check interval | 300,000 |
| Member profiles | Lookup spacing | 3,000 |

Leave other fields blank initially. Blank fields use the client defaults shown as placeholders. These values trade faster loading and broader search coverage for fewer requests. Cached discussions can lag five minutes; searches can lag fifteen minutes. Foreground message checks run roughly every five minutes, with background checks subject to Android scheduling.

Limits apply per installation, not across the server's whole population. Check request counts by endpoint, active installations, response latency, and 429/5xx rates after rollout. The app respects server `Retry-After` instructions; changing User-Agent does not remove rate limits. See [HTTP 429 semantics](https://www.rfc-editor.org/rfc/rfc6585.html#section-4).

This profile is guidance only. Deploying the dashboard does not apply it to live configuration.
