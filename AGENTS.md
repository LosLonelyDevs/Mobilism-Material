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
For sensitive changes, use exactly `- various bug fixes and improvements` as the
default changelog bullet. Keep sensitive details out of release notes.
Keep new changelog entries within 40 words, including Unreleased. Version headings
and bullet markers do not count. `:app:testDebugUnitTest` enforces the limit.
Published entries through 0.1.16 keep their existing text.
Use `SettingsSubtitle` for Settings section subtitles: shared bodyMedium sizing,
at most 10 words, one sentence, and two lines. Describe Premium indicators with
checkmarks so the copy works across themes.
Keep the About description, sourced from README's second paragraph, within 25
words and use a neutral perspective.
Keep the saved landing page separate from current browsing. Fresh installs open
Apps > Home; preserve an existing saved section until a landing page is chosen.

## Request volume and app controls
Preserve request pacing, bounded pagination, caches, failure cooldowns, and server
`Retry-After`. Reuse release metadata already loaded; never fetch each row's details
just to display view counts. Following defaults to five due apps per batch.
There is no five-app follow cap. Batch size, per-app interval, and attempt spacing use
validated remote limits with shipped defaults; the user's background schedule stays separate.
App controls live in `services/daily-usage`. Keep Android defaults and dashboard
validation aligned. Follow `docs/remote-controls.md` and `docs/api-limit-tuning.md`;
deploying code must not silently change production notices or thresholds.
