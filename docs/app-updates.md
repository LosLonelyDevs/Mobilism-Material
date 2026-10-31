# App updates

Settings and About have a Check for updates button and an Update notifications toggle. Notifications default to on.

Automatic checks run when the app starts in a new process, at most once every 12 hours. Returning to an open app or rotating the screen does not trigger another check. Failed attempts count toward that limit. No periodic background checks are scheduled for Mobilism Material updates.

Manual checks run whenever requested, including when notifications are off or an automatic check ran recently. A newer stable GitHub release shows Download update. The button opens the release APK in your browser; installation stays under your control. If the APK is unavailable, View release opens the GitHub release page instead.

Notifications appear once per newer version and open its GitHub release page. Android notification permission and the Mobilism Material updates channel must be enabled. The app requests permission when an update is available; Allow notifications in Settings or About can enable it later. Turning the toggle off cancels the app update notification.

These checks use the public GitHub release API and send no Mobilism login credentials. They are separate from update checks for apps in Following.
