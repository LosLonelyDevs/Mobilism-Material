# Verification

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
