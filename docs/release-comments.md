# Release comments

Signed-in users can post a comment or reply to an existing comment on an Apps, Games, or Books release. Threads sort by the latest activity anywhere in their reply chain. Replies appear once beneath their parent, oldest first. Editing, deletion, and attachments remain pending.

## Flow

- The Comments section has Write a comment and Refresh actions. Guests get Sign in to comment and return to the release after signing in.
- Each comment has Reply. The composer shows the quoted author and text; Remove reply keeps the draft and turns it into a normal comment. Reply targets survive closing the sheet and saved-state restoration.
- Tap a comment's author row to collapse or reopen its body and all replies beneath it. Collapse state survives saved-state restoration.
- Quotes already represented by a parent comment are omitted from the reply body. Other quotes show a preview with Show quote to expand the formatted text. Duplicate API and inline quotes appear once. Plain URLs become clickable links; named links keep their destination.
- An expanded modal sheet shows the release title, account name, multiline field, character count, Close, and Post comment. Comments are public. The app limits a draft to 10,000 characters.
- Closing the sheet keeps the draft for that release. Rotation and saved-state restoration keep it too. Leaving the release or changing accounts ends that draft's lifetime.
- Posting disables editing, dismissal, and repeat submission. A confirmed comment ID clears the draft, closes the sheet, shows success, adds the comment, and reloads page one.
- A connection failure or unclear response keeps the draft and asks the user to check comments before trying again. The app never retries a post automatically. Restoring an interrupted submission shows the same guidance.
- A failed comment refresh cannot turn a confirmed post into a failed submission. The accepted comment stays visible with a separate refresh error.
- Session expiry follows the app's account boundary: sign-in replaces the release screen and clears its draft. It does not silently resend anything.

## M3E UI

The composer uses Material 3's modal bottom sheet, semantic theme colors, rounded text field, existing expressive buttons, and contained loading indicator. Post comment is the single primary action. Labels stay visible, controls use at least 48 dp touch targets, and the sheet scrolls above the keyboard. Existing theme and reduced-motion settings apply.

The sheet uses Material's default maximum width on larger windows. Hierarchy comes from `headlineSmall`, `titleSmall`, and `bodyMedium`; shapes and colors come from `MaterialTheme`. No custom animation timing or fixed palette is added.

## API and privacy

The legacy contract is `POST release/comment`, form fields `id` and `comment`, authenticated with `X-Auth-Token`. Replies also send the selected comment's ID as `msg_id`. Success requires a positive `response.comment_id`. The request uses the existing API origin, bounded responses, and disabled redirects and connection retries.

Drafts are not sent until Post comment is tapped. Diagnostics do not log comment bodies or tokens. The immediate local display escapes HTML and is replaced by the server's formatted comment when the refreshed page includes it.

Posting tests use intercepted API responses. No live forum comment is published by the test suite.

The API returns a flat list with quoted reply text. Threading uses quoted post IDs when available, otherwise the author and normalized quote text. Replies whose parents have not loaded stay beneath an Earlier comment placeholder containing the quoted text. Loading the parent replaces that placeholder. Repeated text without a post ID is matched to the latest earlier comment from that author.
