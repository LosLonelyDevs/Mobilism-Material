# Discussions

Native forum browsing follows the original app's discussion and other-forum routes. Apps, Games, and Books keep their category lists. Community includes Announcements, Contests, Chitchat, Services, and Suggestions & Feedback.

Scope: categories, topic lists, search within a category, original posts, paged comments, and signed-in comments or quoted replies. Topic creation stays on the forum website because the original API has no create-topic route. Empty, loading, offline, expired-session, and unconfirmed-post states must stay actionable. Drafts belong to their topic and account.

The legacy DTOs and loaders define request fields and response shapes. `status` marks sticky/announcement topics, not locked topics. The existing release reader and comment components define formatting and quote behavior.

Motion: preserve the app's shared-axis navigation and Material motion scheme for the category-to-topic hierarchy. Reverse navigation mirrors forward navigation. Reduced motion removes movement. Cards, buttons, sheets, typography, and colors use the shared design system. Existing release and message flows stay unchanged.

Verification uses local fixtures and an Android emulator. No test publishes a real forum comment.

Discussion links use the browser chooser and exclude Mobilism's release link handler. The original post and comment reader keep formatting, text selection, safe clickable URLs, images, and collapsible quotes.

## Verification

- JSON tests cover category groups, section feeds, announcement routes, sticky flags, original posts, search result types, unsafe URLs, and malformed responses.
- ViewModel tests cover paging, failed refreshes, stale requests, search, topic-specific drafts, reply targets, guest restrictions, duplicate submits, and uncertain delivery.
- Emulator tests cover authenticated request fields, both forum API families, session expiry, empty and error states, search, posting, return navigation, and browser routing. Light and AMOLED screens were reviewed. Keyboard focus and 150% Compose text were checked, including reopening a draft and reaching the sheet actions.
- New screens use shared expressive controls, theme typography and colors, a bounded reading column, mirrored shared-axis transitions, and reduced-motion behavior. Native Material controls provide state layers. Existing app theme and system-bar behavior stays in place.

The public category endpoints returned HTTP 403 from this host. Live authenticated forum responses and real posting remain unverified. Topic creation, editing/deletion, and unread-topic tracking remain outside this implementation. Drafts survive sheet dismissal and saved-state restoration when the topic is reopened; reopening a killed app starts from the category browser.
