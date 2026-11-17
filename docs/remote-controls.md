# Remote app controls

App controls manage notices, required updates, and API thresholds through the existing Vercel service. No app version changes are needed to edit a published configuration. Older builds without this feature cannot receive it.

## Existing installations

The v0.1.19 API error handler already displays `error.text` from a JSON error response, limited to 240 characters. The API owner can return HTTP 403 with `Content-Type: application/json` and [this response body](update-required-response.json) for blocked old clients. This displays an error on the requested screen. It does not create a global notice or clickable link. An HTML block page or browser challenge will instead show the app's generic connection error.

Existing update checks can also prompt for a newer published version when automatic updates are enabled. Neither option silently installs an APK. Remote mandatory notices require a build containing this feature first.

## Publish a notice

1. Sign in to the [admin overview](https://mobilism-material-usage.vercel.app/) with your password and authenticator.
2. Open [App controls](https://mobilism-material-usage.vercel.app/app-controls), then **Add notice**. Usage reports are separate at `/app-usage`.
3. Set the message, audience rules, and action. Schedule and **Device notification** are optional; device notifications default off.
4. Use **Test this notice**, then choose **Create notice**. The form closes and the saved preview card appears, disabled.
5. Review the card, then switch it to **Enabled**. Use its edit icon for changes; **Save notice** returns to the list.

Only saved cards can enable or disable notices. Those switches save immediately, with confirmation when version targeting is missing. Form changes stay local until saved. Cancel leaves the saved notice unchanged. Reload discards drafts. A stale editor cannot overwrite a newer save. API limits have a separate save button; saving a notice does not publish pending limit changes.

Schedule is a separate collapsible card. Both dates are optional and use your browser's local time; the service stores UTC. Blank dates mean immediate start and no scheduled end. All version targeting belongs in Audience.

Notice actions:

- **Notice only:** Dismissible information, with an optional inline HTTPS link.
- **Ask to update:** Opens the existing update screen. Users can choose Later.
- **Require an update:** Blocks normal navigation while the audience matches. Use an app-version cutoff to stop the notice after updating. Publish the target APK first. Android still controls installation approval.

Required notices take priority. Other notices use their listed order. Repeat options are once, each process launch, or each foreground visit. Editing a notice makes it eligible to appear again; unrelated threshold changes do not. Required notices ignore dismissal settings.

In-app notices hold dismissal for three seconds after rendering. Back, outside taps, and notice actions stay locked during the countdown. Required notices remain blocking afterward. Diagnostic logging records `NoticeDisplay/Rendered` after layout and a foreground frame, and `NoticeDisplay/Dismissed` when dismissed. Logs use a notice fingerprint, with no message text or username, and respect the existing logging setting.

### Device notifications

The optional toggle posts an Android notification on a matching app check. Checks run during foreground refreshes and through a background worker scheduled about every 15 minutes. Android battery management can delay them. This is polling, not instant push, and requires an APK containing this feature.

Enabled state, audience, app version, and schedule apply to both delivery types. Each changed notice alerts once per account, with duplicate suppression across restarts. Notification permission and the **App notices** channel must be enabled. Tapping opens the matching in-app notice; revoked, expired, or mismatched notices cannot bypass targeting. Android controls notification-shade dismissal.

Diagnostics record `DeviceNotification/Posted` only after Android accepts the notification. This does not claim the user opened the notification shade or read it.

## Audience rules

Choose **Everyone** for a notice that applies to all matching installations, including signed-out users. Choose **Selected audience** to select usernames, app versions, and other conditions.

Each group can match **ALL conditions (AND)** or **ANY condition (OR)**. Groups can nest three levels deep. Username fields accept multiple entries and suggest previously configured usernames. Version lists use a searchable multi-select; range comparisons use one release dropdown. This is a configured allowlist, not a directory collected from anonymous usage reports.

| Field | Comparisons |
| --- | --- |
| Username | Is one of, is not one of |
| App version | Is one of, is not one of, less than, at most, greater than, at least |
| Android API level | Equals, below, at most, above, at least |
| Signed in | Signed in, signed out |

For a private test, use an ALL group containing your username and the app version you want to test. Anyone outside that combination is excluded. For separate test populations, use an ANY group with an ALL group for each username/version combination.

The app reads the username from its current Mobilism session. Matching ignores username case and surrounding whitespace. Signing out or switching accounts re-evaluates rules immediately. Username rules never match signed-out users, including exclusion rules. Dismissals are scoped to the account so one user's dismissal does not hide the notice for another.

Schedules apply outside the rule tree with AND. Saved legacy build restrictions migrate into audience conditions without broadening targeting. An empty or invalid custom audience cannot be published. Unknown or malformed rules cause the app to ignore that notice, including mandatory notices.

**Test this notice** checks its enabled state, schedule, and audience against a sample account, app version, and Android API. Build numbers resolve automatically from the chosen release. Username fields contain account names, never passwords or session tokens.

The protected release catalog reads published GitHub releases and verifies each tag's Android version metadata. Results cache for ten minutes. Comparisons use verified version codes already supported by existing APKs; no patch-to-build formula is assumed. Missing metadata disables new comparisons. A failed refresh retains the last verified catalog with a warning.

Publishing a changed, enabled notice without version targeting requires confirmation. This includes OR branches that can match without a version condition. Disabled drafts and unrelated API-limit edits do not trigger it. Notice headers update with a compact summary as you edit.

Readable usernames stay in the protected configuration. The public payload substitutes normalized username hashes; the app matches locally without sending credentials or usernames to Vercel. Hashes are identifiers, not authentication. Notice contents remain public.

The public protocol is schema 2. Clients that only understand schema 1 reject it instead of ignoring targeting and showing a restricted notice to everyone. Stored schema 1 notices migrate to an explicit Everyone audience. Old dashboard editors cannot overwrite schema 2 configuration.

## Test on your account

1. Add your signed-in Mobilism username to a Username rule.
2. Add the intended app version in the same ALL group.
3. Set **Each foreground visit** while testing. Enable and publish the notice.
4. In the app, open **Settings > Updates > Check app notices**.
5. Disable the notice and publish again when finished.

Manual notice checks run at most once every 15 seconds. Changing notice text also makes a previously dismissed notice eligible again. The test APK retains its version, so select that version when testing. Publish an actual newer APK before requiring an update for general users.

Links appear after the message with the configured link text. They open in the user's browser. Message text is plain text, not HTML. Notices, links, schedules, and thresholds are public configuration. Never put credentials or private information in them.

## API thresholds

| Control | App default | Allowed range |
| --- | ---: | ---: |
| Minimum search spacing | 3,000 ms | 1,000 to 60,000 ms |
| Search cache lifetime | 300,000 ms | 0 to 3,600,000 ms |
| Failed search cooldown | 60,000 ms | 60,000 to 3,600,000 ms |
| Search requests per category | 6 | 1 to 30 |
| Concurrent category searches | 4 | 1 to 4 |
| Read retry delay | 500 ms | 0 to 5,000 ms |
| Minimum read spacing | 250 ms | 100 to 60,000 ms |
| Concurrent reads per endpoint | 3 | 1 to 3 |
| Initial failed read cooldown | 60,000 ms | 60,000 to 3,600,000 ms |
| Maximum failed read cooldown | 900,000 ms | 60,000 to 3,600,000 ms |
| Discussion category cache lifetime | 300,000 ms | 0 to 3,600,000 ms |
| Discussion first-page cache lifetime | 60,000 ms | 0 to 3,600,000 ms |
| Minimum message check interval | 60,000 ms | 60,000 to 3,600,000 ms |
| Maximum failed message check cooldown | 900,000 ms | 60,000 to 3,600,000 ms |
| Minimum member lookup spacing | 1,000 ms | 1,000 to 60,000 ms |
| Member identity cache lifetime | 3,600,000 ms | 60,000 to 86,400,000 ms |
| Missing member cache lifetime | 3,600,000 ms | 60,000 to 86,400,000 ms |
| Initial failed member lookup cooldown | 60,000 ms | 60,000 to 3,600,000 ms |
| Maximum failed member lookup cooldown | 900,000 ms | 60,000 to 3,600,000 ms |
| Minimum debrid refresh interval | 900,000 ms | 60,000 to 3,600,000 ms |

Open **API controls > Limits** to edit thresholds. Blank fields remove the override. **Use app defaults** clears all threshold overrides in the draft; **Save API limits** applies them. Server `Retry-After` delays remain authoritative even when local limits change.

For a starting profile that reduces request volume, see [API limit tuning](api-limit-tuning.md).

Failure cooldowns double after repeated failures, up to the configured maximum. The effective maximum is never below the initial cooldown. Message checks use their normal interval as the initial cooldown. Background message work keeps Android's 15-minute minimum scheduling interval; the policy also applies to foreground checks. These controls preserve the request deduplication and refresh behavior introduced in 0.1.21.

The app keeps its hardcoded defaults. Missing or invalid fields fall back individually. Failed requests, invalid documents, and expired configuration discard remote values and notices. Remote values are never cached on disk. A cold start begins with defaults.

Open apps check every five minutes. Schedules update within 30 seconds while foregrounded. Configuration expires after 15 minutes without a successful refresh. Foregrounding checks freshness without hammering the service. Required notices include **Refresh notice**, limited to one attempt per 15 seconds. An outage releases the notice gate and restores defaults.

Remote controls work independently of anonymous usage reporting and the user's optional update alerts. They use the origin from `DAILY_USAGE_ENDPOINT`, requesting `/api/app-config`. Builds without that endpoint keep their defaults and show no remote notices.

## Deploy the service

Run the idempotent `services/daily-usage/schema.sql` against the existing database. It adds the singleton `app_control` table without changing usage or sign-in records. Deploy `services/daily-usage` to its existing `mobilism-material-usage` Vercel project.

The initial configuration contains no notices or threshold overrides. `/api/control-config` requires the existing admin session. Writes also require a matching Origin and JSON content type. `/api/app-config` exposes only the validated public configuration. Both endpoints disable response caching.

## User-Agent

Forum API requests and embedded verification browsers use the same User-Agent. The browser identity is followed by `MobilismMaterial/<versionName> (Android; build <versionCode>)`. If the browser identity is unavailable, the app identifier remains.

The identifier comes from the installed package. It does not need another hardcoded version string. Server rules can match it to distinguish builds. User-Agent is client-supplied identification, not authentication.

`app/build.gradle.kts` is the version source. It generates package metadata and `BuildConfig.VERSION_NAME` / `VERSION_CODE`; the same `versionName` selects `CHANGELOG.md` notes. Header prefixes never replace the real version/build suffix. See the [request header example](request-headers.http).

Open **API controls > User-Agent** to create a rule. Choose published app versions, or explicitly select all versions, including future releases. Enter a prefix and review the complete header preview. New rules save disabled; enable them from their saved card. The first enabled, matching rule wins.

**Before every request** is the default. Use it when a server needs to distinguish a new app from clients sending `okhttp/4.12.0`. The optional **After an HTTP response** condition activates on a selected 403, 406, or 426 response, then changes later requests only. It does not set the server's response status, retry the failed request, bypass authentication, or remove rate limits. A 426 response means a protocol upgrade is required; changing User-Agent does not perform that upgrade. See [HTTP status semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5).

Response-triggered activation lasts only in memory. Rule edits, removal, expiry, failed config checks, and process restarts reset it. The shipped header returns whenever no valid rule applies. Config is checked on the existing schedule; headers do not create another polling loop.

Rules apply to Mobilism API and forum calls, plus newly opened verification browsers. They do not alter config checks, usage reporting, downloads, or debrid headers. Existing installations need an APK containing this support before remote header rules can affect them.
