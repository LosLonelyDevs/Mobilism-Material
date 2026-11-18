# Optional text formatting

Developer options has two independent switches. Both default off, and disabling Developer mode disables their effects.

| Switch | Scope | Behavior |
| --- | --- | --- |
| Clean up release text | Description, What's new, Mod info, and download notes | Add paragraph spacing, recognize short headings, and turn repeated dash or symbol markers into hanging bullet lists. |
| Enable Markdown syntax | Release text, forum announcements/discussions, comments, and quoted replies | Render CommonMark headings, emphasis, lists, links, blockquotes, and code alongside existing forum HTML. |

With both enabled, Markdown runs first. Cleanup preserves its structured lists, headings, quotes, and code. Private messages retain their existing rendering.

Cleanup uses authored line breaks. It does not split prose at periods, summarize, remove repeated paragraphs, or rewrite claims. A single attribution such as `- Sir Richard Branson` stays intact. Full link targets, hashes, versions, and code remain available. Short heading detection is heuristic, which is why this stays optional.

With cleanup enabled, the requirements line uses smaller text and a muted secondary color. Section titles use a subtle blend of the body text color and the theme accent. Both follow the active palette.

Markdown uses [CommonMark Java](https://github.com/commonmark/commonmark-java), version 0.32.0, under the BSD 2-Clause license. Its notice ships in `assets/licenses/commonmark-LICENSE.txt`. The final HTML still passes through the app's allowlist. Markdown images become links instead of triggering new image downloads. Tables and other GitHub-specific extensions are not enabled.

These are local display transforms. Original content, outgoing posts, section extraction, download parsing, and network schedules stay unchanged. There are no new API calls or background fetches. Turning either switch off restores the corresponding original display without reloading content.

Formatting skips documents over 200,000 characters or with more than 32 nested elements. This keeps unusual posts readable without expensive transformations.

## Offline emulator previews

`-PofflineFormattingPreview=true` gives debug builds the separate `.formattingpreview` application ID. For fixture instrumentation, also pass `-PofflineFixtureRunner=true` to skip WorkManager startup. The offline screenshots run in an isolated `Application` with local fixtures.

`ForumFormattingPreviewTest` exercises the toggles and native text spans. It captures original, cleanup, Markdown, combined, changelog, and settings views using the app's actual theme, release cards, and text renderer. Preview content is reconstructed from supplied screenshots or synthetic Markdown, not fetched forum pages.
