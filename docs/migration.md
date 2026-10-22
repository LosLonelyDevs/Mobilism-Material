# Migration scope

## Approach

Use the decoded app as a behavioral and protocol reference. Reimplement cohesive screens in Kotlin, with a separate data layer and design system. Decompiled Java is not directly buildable source and can omit later smali patches; check the patched behavior when porting each capability.

The first slice is a standalone Gradle application. It does not inject Compose into the old APK or link the old dex files. This avoids combining duplicate AndroidX classes and obsolete dependencies. Android can mix Compose with existing Views in a source project; the release body already uses a native TextView inside Compose for formatted, selectable forum text.

The migration identity `cum.ahfu.mobi.material` permits a separate install when installation is authorized. A future replacement of the old package requires signing continuity and an explicit data-migration plan.

## Current source boundary

| Existing flow | Rewrite scope |
| --- | --- |
| Login / register / reset password / guest | Native screens and API calls authored; credentials never compiled in |
| Favourite section | Apps, Games, Books selection, persisted locally |
| Main drawer and Home / Top | Same navigation pattern, three sections, paged release lists |
| Release detail | Native text bridge, image gallery, browser and download links |
| Appearance | Frappé default; fonts, versioned custom-theme import, reduced motion |
| Premium / debrid / native downloads | Not ported; links open in a browser |
| Search / categories / discussion feeds | Not ported |
| Replies / messages / watchlists / history | Not ported |
| Existing session and preferences | Not migrated; separate app storage |
| Legacy updater / installers / notifications | Not ported |

## Data boundary

`MobilismApi` maps the legacy `https://app.mobilism.org/api-new/` protocol: form authentication, `X-Auth-Token`, release lists and release details. JSON success/error envelopes and the release field mappings are based on the local legacy code. No live login or API integration checks were run for this source milestone.

The repository owns authentication state and clears an expired session. Username and tokens are encrypted with an Android Keystore key in app-private storage. Passwords only remain in the current login/register screen and request. Backup is disabled. No shared developer credentials or debrid keys are embedded.

Guest sessions have no auth token. Server access limits still apply. A challenge page is reported as a service rejection; the client does not attempt to bypass it. Token refresh and any additional server-side session requirements remain migration work.

HTTP redirects are not followed by the authenticated API client. Artwork uses a separate image loader without authentication headers. Forum HTML is rendered as text with allowed formatting; only HTTP(S) links are actionable and no JavaScript runs.

## Next implementation slices

1. Compile the source, then validate sign-in, guest mode, session restoration/expiry, feed paging, and details against the real service when verification is authorized.
2. Port search, category selection, and discussion/comment navigation without changing their task flow.
3. Port download state, premium/debrid accounts, background work, notifications, and file handling behind interfaces independent of Compose.
4. Port remaining account features and saved data, then decide on package/signing migration after feature parity.

For each slice, keep the current navigation and actions, replace its UI with shared components, and move business rules into the data/domain layer. Do not replace unported features with fabricated content.

## Status

This milestone is source only. No Gradle compilation, tests, installation, accessibility audit, visual check, or runtime validation has been performed. The previously rebuilt legacy APK is a separate local artifact and does not contain this rewrite.

Old Git history and tracked work-in-progress are retained in a local `archive/pre-material-rewrite` recovery branch. Fresh `main` contains only the source rewrite, project metadata, and supplied license. Do not publish the recovery branch or use `git push --all`; it includes the legacy repository history.
