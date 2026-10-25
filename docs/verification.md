# Verification

## Unreleased: Premium, sorting, and book titles

- All 129 unit tests pass, including an opt-in live Mobilism Premium check. Debug and Android test builds pass. Android lint reports zero errors and 35 warnings.
- Mobilism Premium authenticated, loaded the current 19-domain host list, and resolved a supported Rapidgator mirror through both resolver steps. A four-byte range request to the generated URL returned HTTP 206, `application/pdf`, and a valid PDF signature. This verifies one live mirror, not every supported host.
- The emulator suite completed with 27 passing tests and one skipped opt-in multi-provider live test. New checks cover Mobilism sign-in controls, encrypted storage and reload, account removal, alphabetical category rows with stable selection IDs, and separate book title/author/series/format rendering.
- API fixtures cover unsupported hosts, lookalike domains, invalid credentials, expired subscriptions, unsafe URLs, redirects, oversized responses, repeated forms, and the resolver's success response inside an `htmlerror` container.
- Shared A-Z sorting preserves preferred-host priority and release feed ordering. Book parsing covers Mobilism's dotted formats, series numbers, multiple authors, dates, numeric titles, and conservative fallback.

Commands: `:core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`. Set `MOBILISM_PREMIUM_TEST_ENV` to a local `.env` path to opt into the live test. The local file supplies forum and Premium credentials only during testing; it is not bundled in the APK.

Exact-value scans found no local credentials in changed sources or the debug APK. No physical device was changed. Version remains 0.1.13; no commit or push was made.

## 0.1.10

- Debug and Android test builds pass. All 92 unit tests and 14 emulator tests pass; lint reports no errors.
- Provider cards use matching tonal controls and a neutral remove action. The emulator fixture checks the host dropdown and captures the updated cards with sample data.
- Real-Debrid resolved a live supported-host link. Its direct URL returned HTTP 200 for HEAD and HTTP 206 with a valid archive signature for a four-byte range request.
- Real-Debrid reported the original example host as down. Availability checks now exclude reported outages and unverified cached lists while preserving supported catalog hosts missing from the status feed.
- MegaDebrid accepted the saved token and rejected an invalid token. Both the app client and an independent request returned an unexplained unrestriction error for live mirrors. Premium status and successful MegaDebrid resolution remain unverified.

## 0.1.9

- Debug and Android test builds pass. All 87 unit tests pass; lint reports no errors.
- All 13 emulator tests pass, including resolved-link opening, loading state, explicit original-host fallback, and copying without resolving or marking a download.
- Real-Debrid and MegaDebrid credentials authenticated against their live APIs. Both providers refused the sample host link. Successful resolution is covered by API fixtures and emulator tests; a live file download was not verified.
- Authentication failures, expired premium access, host errors, redirects, unsafe download URLs, and responses containing credentials have regression coverage. No physical device was changed.

## 0.1.4

- Debug and signed release builds pass. All 45 tests pass; lint reports no errors.
- Following shows the installed version, latest matching release, and update action. A disposable emulator app verified the installed-version baseline and that the update action clears after an upgrade. The fixture was removed afterward.
- A real AdGuard check posted an update notification. Tapping it opened the release and Back returned to Following. Rechecking after dismissal did not repeat the alert.
- WorkManager completed a background check while the app was offscreen. Android still controls periodic timing.
- Spotify release `6429648` now keeps its author credit and unlocked-feature bullets in Mod info. Description retains the normal Spotify features.
- Feed cards show app names above smaller version/build/mod text. Focused login fields have one native border. Login succeeded and the configured Android Autofill service received a field request.
- Username/password Autofill hints and successful-login commit are wired. Third-party provider save/fill dialogs still need device coverage.
- Theme import is hidden. Release loading uses a centered 96 dp animated indicator and respects reduced motion.

Following checks inspect up to three Apps search pages. Unknown or ambiguous versions are not reported as definite updates. Non-launcher discovery, long-running battery behavior, and large libraries need broader testing. No physical device was changed.

## 0.1.3

Completed on an Android 16 / API 36 emulator:

- Debug and signed release builds pass; 37 regression tests pass; Android lint reports zero errors and 20 advisory warnings.
- Native Apps search returns live artwork and releases. Scrolling a Contacts search exposed 27 distinct titles, beyond the server's ten-item page. Returning from a release preserves the query/results.
- An unmatched query renders the empty state. Disabling the emulator network produces a retryable error; restoring connectivity and retrying returns results.
- My Applications lists installed launcher apps and opens an Apps search from the selected app's label.
- Watch and downloaded marks coexist and persist through process restart and APK replacement. Removal and Undo restore the record.
- A real AdGuard watch finds a newer phone release. A TV variant initially exposed an identity ambiguity; the matching rule and regression test now reject it for the phone watch.
- Moving the watch to the newer release preserves the older downloaded record.
- Manifest version 0.1.3/build 4 and local release signature verified. No physical device was changed.
- Repository signing secrets were provisioned through GitHub's encrypted API flow. A hosted manual release build succeeded and its signing certificate matched the published 0.1.2 APK.

Update checks are manual and search up to three recent Apps result pages per watch. Automatic installed-version matching, background checks/notifications, bulk actions, complete history, and large-library performance remain unimplemented or unverified. Library data stays local to this installation. The Downloaded tab contains user marks, not native download jobs.

## 0.1.2

Completed on an Android 16 / API 36 emulator:

- Debug and signed release builds pass; 27 regression tests pass; Android lint reports zero errors and 20 advisory warnings.
- Latest/Top, searchable category multiselect, Apply/Reset/Cancel, independent Books filters, and persisted Apps selections. Scrolling produced 47 distinct titles across selected categories.
- Aligned Settings card widths, rounded colored actions, expandable supported-host menus, saved-key hiding, and persistent architecture/host preferences.
- Download groups, preferred-host ordering, universal/unlabeled handling, and collapsed alternative architectures. Host buttons are compact and omit debrid subtitles.
- Structured name/version/build/tag title on the live Dual Space release; conservative fallback and numeric app names covered by parser tests.
- Live underlined changelog and premium mod block split into separate collapsible cards. Heading sizes and hierarchy colors remain consistent.
- Read-only comments below Downloads. Scrolling through a real release exposed 26 distinct comment timestamps, beyond the first 20-item page. Live API checks confirmed disjoint pages and the explicit empty-page response.
- Full-screen image rendering with a dark backdrop, double-tap zoom, close/back behavior, next/previous controls, and swipe paging. Unavailable images show an error state. Pinch/pan and accessibility require broader manual coverage.
- Release URL copying verified against the emulator clipboard.
- Manifest version 0.1.2/build 3 and release APK v2 signature verified. No physical device was changed.

Remaining coverage includes the 0.1.1 limitations below, full gesture/accessibility audits, and large image memory profiling. Some old forum image hosts are unavailable.

## 0.1.1

Environment: JDK 21, Android Gradle Plugin 9.4.1, Gradle 9.6.1, compile SDK 37. Runtime checks use an Android 16 / API 36 emulator and the separate migration package.

Completed:

- Debug and signed release compilation; Android lint has no errors.
- Fourteen passing regression tests for image-array parsing, server-capped pages, download extraction, URL safety, deduplication, Mobilism host shortlisting, and domain boundaries.
- Empty-login validation, real sign-in, favourite section, populated Apps feed, release text and artwork.
- Session restoration after reinstall; custom-theme import, font choice, and reduced-motion preferences persist.
- Visual confirmation of real artwork in list cards.
- Apps, Games, Books, and Top feeds load; scrolling through 30 distinct titles verifies pagination beyond the server's ten-item pages.
- Real-Debrid and MegaDebrid host catalogs load with encrypted keys retained after reinstall. Supported RapidGator buttons use Frappé green; unsupported hosts retain normal styling.
- Saved keys hide their inputs. Settings lists supported Mobilism hosts with aliases grouped instead of all provider domains.
- Host buttons launch the device browser; copying and pasting into its address bar reproduces the same URL.
- Versioned debug/release filenames, unsigned labeling, IDE export names, manifest version 0.1.1/build 2, release signature verification, and workflow syntax checks.

Remaining coverage: account registration, password reset delivery, server-side session expiry, full accessibility and physical-device audits. Public host catalogs establish provider support; key validity, account entitlement, and unrestricted downloads are not part of this milestone.

An intermittent server HTTP 522 appeared once during live testing; retry recovered. Network/server availability is external to the client.
