# Daily usage endpoint

Vercel functions for anonymous daily app usage, backed by Neon Postgres. The app submits reports here. Signed-in dashboard users can see daily counts and device breakdowns. Open the deployment root to access the dashboard.

Completed-day endpoint counters share that same report. App usage shows request totals, outcomes, and average response-header time for the selected UTC day or range. This panel stays hidden until the verified stable catalog contains v0.1.24 or later, then displays its minimum version. See [request traffic](../../docs/request-traffic.md) for batching, coverage, and limitations.

The usage service has its own Vercel project and Neon database. Set `DAILY_USAGE_ENDPOINT` to the private build configuration value for the HTTPS reporting URL.

The Actions build setting supplies future release builds. Keep server credentials in the deployment environment and local overrides outside Git.

## Verify Android contributions

Apply `schema.sql` before enabling verification. Set these values in the service environment:

| Setting | Value |
|---|---|
| `ANDROID_RELEASE_CERT_SHA256` | The 64-digit SHA-256 signing certificate fingerprint from a known official release. Check it with `apksigner verify --print-certs`. |
| `APP_ATTESTATION_SECRET` | A separate random server secret of at least 32 characters. |
| `APP_WRITE_POLICY` | Defaults to `observe` so older installs keep contributing during rollout. Set `enforce` to require verified writes. |

In observe mode, existing clients can contribute. Filter the function logs for `app-write` to compare verified, unsigned, and denied requests. These are request counts. Switch to enforcement after checking coverage on supported devices. Invalid signed requests never contribute. An unsigned Following lookup still gets its cache results in either mode.

The official APK can enroll from GitHub, Obtainium, or another installer. Enrollment checks Android hardware key attestation, the release signer, and locked verified boot. Locked GrapheneOS devices use the project's signed list of official boot keys. The app needs no Play services or Mobilism credentials for enrollment. Keys rotate when their 24-hour tickets expire. Repeated signed requests count once. Keep the same ticket secret across function instances. [Android attestation](https://developer.android.com/privacy-and-security/security-key-attestation) · [GrapheneOS compatibility](https://grapheneos.org/articles/attestation-compatibility-guide)

## Shared Latest Apps cache

The cache accepts up to 20 public release summaries from Apps > Latest or exact matches from a Following search. Each record holds the app name, release title, version, topic ID, posting time, and uploader. An older upload cannot replace a newer release. Contributions respect the app's usage-sharing opt-out.

The public `GET` snapshot holds the newest 500 apps by posting date. Following has another 500 slots. Apps people requested in the last 24 hours get first pick. Hits and misses both count. Yesterday's counts carry less weight as today progresses. The most recent request breaks ties. Other releases use spare slots in posting order.

A Following lookup accepts up to 512 names and returns up to 500 matches. Larger libraries can still read the public snapshot and match locally. Only a first-page Latest read refreshes the feed. Entries outside the feed need their own sighting within six hours to answer a lookup. Requests never extend verification. Android uses its normal Mobilism search budget when it cannot confirm a version.

The Cache page counts named lookups as they arrive, including the current partial UTC day. Most followed apps ranks by requesting installations per UTC day and shows total lookups, including repeat checks. Most missed apps counts requests for absent names. Filtered and stale lookups count toward the total but do not increase misses. Historical totals include only recorded outcomes. The inventory count and versions show the current snapshot. Each installation generates a separate receipt per app and day. Receipts expire separately from the daily totals and contain no account or app name. Apply `schema.sql` before deploying.

An entry expires 24 hours after its last sighting. It can disappear sooner when two comparable first-page reads at least ten minutes apart both show its old neighbors with the topic missing between them. Scrolling off the page is insufficient. A later sighting restores it. Expiry and removal use existing feed reads.

## Dashboard sign-in

The overview links to each page the user can access. **App usage** lives at `/app-usage`. **Following cache** at `/following-cache` shows hit rates, most followed apps, cache misses, and the pull-to-refresh switch. Admins have access automatically. Grant other accounts this page from Users. **App controls** at `/app-controls` manages notices and API thresholds. See [Remote app controls](../../docs/remote-controls.md) and [component notes](../../docs/dashboard-components.md). Apply `schema.sql` before deployment.

The Overview’s App usage card shows today’s client share by app version. Each card uses its destination’s page permission.

In App usage, open Report dates and choose a day. Choose a second day for an inclusive range, then select Apply. Dates use UTC and cover the last 365 days. A day shows reporting clients. A range shows the sum of daily reports. The busiest day's client count is the minimum possible number of distinct installations in the range. The total reports give the maximum. Daily IDs cannot link an installation across days.

Version shares use all reports in the selection. Each report records the version seen on the installation’s first activity that day. The dashboard compares it with the current verified stable release. App versions combine builds, and device details retain individual build numbers. In a range, use the chart slider and **View this day’s breakdown** to see one day’s clients.

1. Read `ADMIN_USERNAME` and `ADMIN_PASSWORD` from the ignored root `.env`.
2. Add `ADMIN_TOTP_SECRET` to an authenticator app as a Base32 setup key: time-based, SHA-1, six digits, 30 seconds. Suggested label: Mobilism usage.
3. Enter the username/password, then the authenticator code. The code screen appears only after valid credentials.

The environment credentials initialize the protected owner account on first sign-in. The owner always has admin access to every page and cannot be deleted or restricted. After a credential change in the UI, use the updated credentials. Environment values do not overwrite an existing account.

**Users** at `/users` is visible only to admins. Create a user to generate a random 20-character password and authenticator setup key. Copy both before closing the dialog. Assign Overview, App usage, and App controls separately. An App controls grant includes editing and publishing. Admin status grants user management; other page grants remain separate. Unassigned navigation stays hidden, and direct routes and APIs reject access.

**My account** at `/account` is available to every signed-in user. Password and authenticator changes use separate forms. Both require the current password and a fresh current authenticator code. A new authenticator also requires a code from its new setup key before saving. Either change revokes all sessions and pending sign-ins for that user. Access changes and deletion also revoke access. Passwords use salted scrypt hashes; authenticator keys use AES-256-GCM encryption.

Five failed passwords or codes within 15 minutes cause a 15-minute server-side lockout. Limits persist in Postgres across refreshes and function restarts; account failures also apply across client addresses. Pending second-factor challenges expire after five minutes. Completed sessions last 12 hours in Secure, HttpOnly, SameSite=Strict cookies. Accepted codes cannot be replayed, including after password or access changes.

The separate bearer `STATS_TOKEN` remains available for scripts. The browser never receives it or stored authenticator secrets. Newly generated credentials appear only during setup. Sign out revokes the current session, including copied cookies.

## Deploy

1. Create a Neon database and run [schema.sql](schema.sql) in its SQL editor. Use a dedicated database for these reports.
2. Import this repository into Vercel. Set the project root to `services/daily-usage`, Framework Preset to **Other**, and Node.js to **22.x**. `vercel.json` builds the Vite UI and serves `dist`. The committed lockfile installs the function dependencies.
3. Set these Vercel environment variables for Production:

   | Variable | Value |
   | --- | --- |
   | `DATABASE_URL` | Neon connection string, including `sslmode=require` |
   | `STATS_TOKEN` | Random secret of at least 32 characters for reading statistics |
   | `CRON_SECRET` | A separate random secret of at least 32 characters for cleanup |
   | `ADMIN_USERNAME` | Random dashboard username, at least four characters |
   | `ADMIN_PASSWORD` | Random dashboard password, at least 24 characters |
   | `ADMIN_TOTP_SECRET` | Random 20-byte secret encoded as 32 Base32 characters |
   | `ADMIN_SESSION_SECRET` | Separate random signing and encryption secret of at least 32 characters |

4. Deploy. Reading stats requires a two-factor dashboard session or its bearer token. Scheduled cleanup uses a separate token. Apply firewall rate limits to app submissions. Keep preview and script fixtures outside production uploads with `.vercelignore`.
5. Set the GitHub repository **Actions variable** `DAILY_USAGE_ENDPOINT` to the HTTPS reporting URL from your deployment configuration. The release and manual APK workflows pass it to Gradle. Local builds use the same environment variable:

   ```sh
   : "${DAILY_USAGE_ENDPOINT:?Set the reporting URL first}"
   bash ./gradlew :app:assembleRelease
   ```

The endpoint is compiled into the APK. Setting the variable does not change installed apps. An empty variable disables reporting; debug builds never send reports. No app version is changed by this setup.

Apply the full idempotent `schema.sql` before deploying user management. It adds user and session tables and binds login challenges to users without changing usage data. Existing dashboard sessions require a fresh sign-in after this upgrade. Keep `ADMIN_SESSION_SECRET` stable: changing it requires re-encrypting stored authenticator keys with the new key first. Keep bootstrap environment credentials configured even after changing the owner's credentials through My account.

The daily Vercel cron runs cleanup with `CRON_SECRET`. It deletes receipts older than the retry window, aggregates older than 365 UTC dates, expired sessions and login challenges, and login limits older than a day. Check that the cron is enabled after deployment. Use separate databases and tokens for preview deployments.

## Read counts

```sh
curl --fail --silent --show-error \
  -H "Authorization: Bearer $STATS_TOKEN" \
  "${STATS_URL:?Set the stats URL first}?from=2026-11-01&to=2026-11-04"
```

The default range is the last 30 UTC dates. Up to 365 dates are allowed. `daily` includes zero-count dates; `breakdown` groups by app version/code, Android API level, manufacturer, and model. Counts are decimal strings so large integers remain exact. Dates can gain late reports for seven days.

Add `group=period` to aggregate device details and endpoint totals across the selected dates. The daily trend still includes one count per day. `latestStable` is the current verified stable version or null when unavailable.

## What a report means

- One report per installation per active UTC date. Opening or keeping the app visible records activity, including midnight rollover. Background update checks alone do not count as activity.
- A random UUID is created for each date. The exact payload is saved before scheduling and reused after process death, app updates, offline retries, and opt-out/re-enable within that date. The first observed app/device details represent that date.
- WorkManager queues unique work per date and requires a network connection. Failed delivery uses exponential backoff. A report is complete only after the endpoint acknowledges its date and UUID. Reports expire after seven days.
- The database's `(day, ping_id)` primary key and aggregate update run in one atomic SQL statement. Concurrent retries, changed metadata on a replay, and lost acknowledgements cannot add another count.
- Clearing app data or reinstalling can count an installation again that day. Several devices belonging to one person count separately. Opt-outs, missing connectivity, incorrect device clocks, and Android scheduling affect totals. This is an estimate of active installations, not a count of unique people.

## Data handling

Reports contain schema version, UTC date, random daily UUID, app version/code, Android API level, manufacturer, and model. The optional requestTraffic field adds completed-day counters by allowlisted endpoint group, method, outcome, and app version, plus summed response-header time. Each daily counter batch has a random retry ID. There are no account details, URLs, release IDs, search terms, message contents, request bodies, advertising IDs, Android IDs, serial numbers, location, or permanent installation IDs. Unknown fields are rejected.

Daily UUIDs are stored separately from aggregate dimensions, for eight UTC dates including today. Aggregates are retained for 365 dates. Usage collection does not read or store IP addresses, user agents, or raw request bodies. Dashboard sign-in stores keyed hashes of client addresses and account identities for throttling. Dashboard accounts store usernames, password hashes, encrypted authenticator keys, roles, page grants, and credential versions. Raw addresses, passwords, and codes are not stored in the database or application logs. Vercel and network providers still receive network metadata; review their logging and retention settings before enabling production collection.

Request retry IDs are stored separately from aggregate dimensions for 14 completed UTC dates, covering seven-day carryover plus seven-day delivery retries. Endpoint aggregates retain 365 UTC dates. Apply the full idempotent schema before deploying the request counters; it adds request_receipts and daily_requests without changing existing data.

Settings > Anonymous usage disables future reports, clears local traffic counters, removes traffic from queued payloads, and cancels queued work. A request already received by the endpoint may finish. Local daily usage receipts are retained through the retry window to avoid duplicates after re-enabling. Reports are not linked to accounts, so contributions cannot be selectively removed by account.

The public endpoint accepts validated JSON up to 256 KiB, at most eight traffic batches and 128 endpoint/method rows per batch. Counts are not proof of authentic devices or requests: a modified client can generate new IDs. Keep statistics tokens server-side and rotate them independently of app releases.

Cache hits and misses use the same daily batch IDs as endpoint totals. Apply the `daily_cache_usage` table in `schema.sql` before deploying ingestion. App usage sums hits and misses across the selected dates and versions before calculating the rate. See [cache counting rules](../../docs/request-traffic.md#coverage-and-limits).

## Verify

Run `npm run build`, then `node scripts/preview.mjs` for a local-only preview with synthetic data and temporary credentials printed in its console. It never connects to production. `--dashboard` skips local sign-in; `--test-auth` uses fixed synthetic credentials for login checks. Both flags exist only in the excluded preview script. `PREVIEW_PORT` overrides the local port. Run `npm run typecheck` and `npm test` before deploying.

```sh
cd services/daily-usage
npm ci --ignore-scripts
npm test
```

Tests use an embedded Postgres engine to exercise the real schema and SQL, including concurrent duplicate requests, transaction rollback, metadata changes on retries, retention, validation, and protected statistics. Android tests cover persisted daily IDs, process recreation, concurrent preparation, acknowledgement, storage failure, date rollover, and expiry.
