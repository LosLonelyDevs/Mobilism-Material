# AGENTS entrypoint
This tracked file is a lightweight pointer.
## Source of truth
If `.agents/AGENTS.md` exists locally, read and follow it first. That private
file is the authoritative instruction set for this repository.
## Fallback behavior
If `.agents/AGENTS.md` is missing, use `README.md`, `package.json` scripts, and
inline code comments as the baseline guidance.

## Project writing
Use the owner's voice for README/docs, release notes, commit/PR text, and comments.
Apply the `write` skill when available. Keep it short, direct, and practical.
Lead with the change or result. Keep evidence beside the claim.
Code comments should explain a non-obvious reason, not repeat the code.
Do not use em dashes, en dashes, corporate filler, or invented slang.
Maintain release notes in `CHANGELOG.md`; the release workflow publishes that text.
Keep sensitive details out of release notes.
Keep new changelog entries within 40 words, including Unreleased. Version headings
and bullet markers do not count. `:app:testDebugUnitTest` enforces the limit.
Published entries through 0.1.16 keep their existing text.
Omit settings descriptions when the label is clear. This applies to settings
controls anywhere in the app, including Following. Never write a paragraph or
stack descriptions under a control. When a description is needed, use
`SettingsSubtitle` and keep it to one visible line, at most 10 words. A longer
description requires the owner's explicit approval for that specific instance.
Check the rendered text before shipping. Shorten or remove any new copy that
wraps. Describe Premium indicators with checkmarks so the copy works across themes.
Keep the About description, sourced from README's second paragraph, within 25
words and use a neutral perspective.
Keep the saved landing page separate from current browsing. Fresh installs open
Apps > Home; preserve an existing saved section until a landing page is chosen.

## Android strings
Put all new or updated user-facing app text in the owning module's
`res/values/strings.xml`. When editing an Android source or layout file, move
its existing hardcoded English UI text into string resources in the same change.
Include accessibility labels, errors, and notifications. Use placeholders for
dynamic text and plurals for counts. Preserve user content and stable IDs.

## Request volume and app controls
Create Android HTTP clients, URL connections, and WebViews through `AppHttp`.
It supplies the configured User-Agent and installed app identity on every request.
Preserve request pacing, bounded pagination, caches, failure cooldowns, and server
`Retry-After`. Reuse release metadata already loaded; never fetch each row's details
just to display view counts. Following defaults to five due apps per batch.
There is no five-app follow cap. Batch size, per-app interval, and attempt spacing use
validated remote limits with shipped defaults; the user's background schedule stays separate.
App controls live in `services/daily-usage`. Keep Android defaults and dashboard
validation aligned. Follow `docs/remote-controls.md` and `docs/api-limit-tuning.md`;
deploying code must not silently change production notices or thresholds.
Request totals piggyback on the existing daily usage report. Keep endpoint groups
allowlisted, payloads bounded, daily batch IDs immutable, and uploads deduplicated.
Preserve the usage opt-out and v0.1.24 dashboard gate. See `docs/request-traffic.md`.
