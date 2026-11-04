# App updates

Settings opens the Updates submenu. Automatic checks default to on and preserve an existing choice to turn them off.

Automatic checks run when the app starts in a new process, at most once every 12 hours. Returning to an open app or rotating the screen does not trigger another check. Failed attempts count toward that limit. No periodic background checks are scheduled for Mobilism Material updates.

When a newer stable release is available, an in-app dialog offers Download. That action opens Updates and starts the download immediately. Later dismisses the dialog for that version. A newer version can prompt again. Cached release details survive restarts.

Manual checks work even when automatic checks are off. Download shows progress and supports cancellation. Downloads survive navigation and rotation. A verified saved download can be reused after restarting. If download metadata is unavailable, View release opens GitHub instead.

The installer flow comes from GAYERFyTV. Downloads stay on HTTPS and approved release hosts, with bounded size and SHA-256 verification when GitHub supplies a checksum. Before installation, the app checks the package name, advertised version, newer version code, Android compatibility, and signing key. Android still asks for installation confirmation. If install permission is needed, the flow resumes after returning from Android settings.

What's new shows the installed version's notes from `CHANGELOG.md`, including on the first launch with this feature. Dismissal is saved per version. It appears before an available-update dialog so the dialogs never stack.

Existing Android update notifications remain available when permission and the update channel are enabled. The in-app dialog does not require notification permission. Turning automatic checks off also cancels the app update notification.

These checks use the public GitHub release API and send no Mobilism login credentials. They are separate from update checks for apps in Following.
