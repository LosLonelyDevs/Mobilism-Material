# Daily usage endpoint

Vercel functions for anonymous daily app usage, backed by Neon Postgres. `POST /api/ping` accepts reports. `GET /api/stats` returns protected daily counts and app/device breakdowns. Nothing is deployed or enabled by adding this directory.

## Deploy

1. Create a Neon database and run [schema.sql](schema.sql) in its SQL editor. Use a dedicated database for these reports.
2. Import this repository into Vercel. Set the project root to `services/daily-usage`, Framework Preset to **Other**, and Node.js to **22.x**. Leave build and output directory overrides unset. The committed lockfile installs the function dependencies.
3. Set these Vercel environment variables for Production:

   | Variable | Value |
   | --- | --- |
   | `DATABASE_URL` | Neon connection string, including `sslmode=require` |
   | `STATS_TOKEN` | Random secret of at least 32 characters for reading statistics |
   | `CRON_SECRET` | A separate random secret of at least 32 characters for cleanup |

4. Deploy. The production `/api/ping` route must accept unauthenticated app requests. Keep `/api/stats` and `/api/cleanup` protected by their bearer tokens. Use Vercel Firewall rate limits on `/api/ping` to limit abuse; do not put a shared secret in the APK.
5. Set the GitHub repository **Actions variable** `DAILY_USAGE_ENDPOINT` to `https://YOUR-PROJECT.vercel.app/api/ping`. The release and manual APK workflows pass it to Gradle. Local builds use the same environment variable:

   ```sh
   DAILY_USAGE_ENDPOINT=https://YOUR-PROJECT.vercel.app/api/ping bash ./gradlew :app:assembleRelease
   ```

The endpoint is compiled into the APK. Setting the variable does not change installed apps. An empty variable disables reporting; debug builds never send reports. No app version is changed by this setup.

The daily Vercel cron calls `/api/cleanup` with `CRON_SECRET`. It deletes receipts older than the retry window and aggregates older than 365 UTC dates. Check that the cron is enabled after deployment. Use separate databases and tokens for preview deployments.

## Read counts

```sh
curl --fail --silent --show-error \
  -H "Authorization: Bearer $STATS_TOKEN" \
  'https://YOUR-PROJECT.vercel.app/api/stats?from=2026-11-01&to=2026-11-04'
```

The default range is the last 30 UTC dates. Up to 365 dates are allowed. `daily` includes zero-count dates; `breakdown` groups by app version/code, Android API level, manufacturer, and model. Counts are decimal strings so large integers remain exact. Dates can gain late reports for seven days.

## What a report means

- One report per installation per active UTC date. Opening or keeping the app visible records activity, including midnight rollover. Background update checks alone do not count as activity.
- A random UUID is created for each date. The exact payload is saved before scheduling and reused after process death, app updates, offline retries, and opt-out/re-enable within that date. The first observed app/device details represent that date.
- WorkManager queues unique work per date and requires a network connection. Failed delivery uses exponential backoff. A report is complete only after the endpoint acknowledges its date and UUID. Reports expire after seven days.
- The database's `(day, ping_id)` primary key and aggregate update run in one atomic SQL statement. Concurrent retries, changed metadata on a replay, and lost acknowledgements cannot add another count.
- Clearing app data or reinstalling can count an installation again that day. Several devices belonging to one person count separately. Opt-outs, missing connectivity, incorrect device clocks, and Android scheduling affect totals. This is an estimate of active installations, not a count of unique people.

## Data handling

Reports contain only schema version, UTC date, random daily UUID, app version/code, Android API level, manufacturer, and model. There are no account details, message contents, browsing activity, advertising IDs, Android IDs, serial numbers, location, or permanent installation IDs. Unknown fields are rejected.

Daily UUIDs are stored separately from aggregate dimensions, for eight UTC dates including today. Aggregates are retained for 365 dates. The service does not read or store IP addresses, user agents, or raw request bodies in its database or application logs. Vercel and network providers still receive network metadata; review their logging and retention settings before enabling production collection.

Settings > Anonymous usage disables future reports and cancels queued work. A request already received by the endpoint may finish. Local daily receipts are retained through the retry window to avoid duplicates after re-enabling. Reports are not linked to accounts, so contributions cannot be selectively removed by account.

The public endpoint accepts only small, validated JSON payloads. Counts are not proof of authentic devices: a modified client can generate new IDs. Keep statistics tokens server-side and rotate them independently of app releases.

## Verify

```sh
cd services/daily-usage
npm ci --ignore-scripts
npm test
```

Tests use an embedded Postgres engine to exercise the real schema and SQL, including concurrent duplicate requests, transaction rollback, metadata changes on retries, retention, validation, and protected statistics. Android tests cover persisted daily IDs, process recreation, concurrent preparation, acknowledgement, storage failure, date rollover, and expiry.
