# Message alerts

Message notifications are enabled by default. Android asks for notification permission after sign-in. Settings includes a Message notifications toggle and an Allow notifications action when Android blocks alerts.

The app checks the Inbox every minute while open. WorkManager checks every 15 minutes in the background when a network is available. Android can delay background checks to save battery. These are polling alerts, not server push notifications.

Unread incoming messages alert once per message and account. Local read history suppresses alerts for messages already opened. Opening a conversation clears its message notifications. Signing out or disabling alerts cancels background checks and removes active alerts.

Tapping a notification opens its conversation. Notifications from another account cannot open that account's messages. Message previews use Android's private visibility setting, with generic text on the public lock screen.

Checks only read the Inbox. They do not send replies or mark messages read. If Android blocks a notification, the message remains eligible for a later check.
