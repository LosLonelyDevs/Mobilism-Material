# Verification

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
