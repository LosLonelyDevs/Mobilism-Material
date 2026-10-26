# Connection and Premium troubleshooting

## Share logs

1. Reproduce the problem.
2. Open **Settings > Diagnostic logs > Export logs**. The login screen has the same button if sign-in fails.
3. Share the exported text file with the issue report. Include what failed, the approximate time, and whether switching between Wi-Fi and mobile data changed it.

Basic diagnostics record automatically. Files rotate at 256 KiB, keeping at most two files. Nothing uploads automatically. Exports contain timestamps, app version, Android API level, operation names, timing, HTTP status, failure categories, retries, and session renewal outcomes. They exclude usernames, passwords, tokens, URLs, request bodies, comments, and response text.

## Connection reports

The [reported thread](https://forum.mobilism.org/viewtopic.php?t=6437968) has two reports without error details. During investigation on October 25, direct requests from the development machine received HTTP 403 Cloudflare challenges from both the forum and app API, while a browser loaded the forum. This is a plausible cause, not confirmation of the reporters' failures.

The Android emulator loaded the same thread through the app repository and decoded comment 12726994 correctly. No general API outage reproduced there. The reporters' exact failures still need logs from their devices.

Browsing and search now allow connection recovery across server addresses and retry one transient server failure. Comment posts, likes, registration, and password recovery are not automatically retried. Cloudflare challenges are reported separately from server outages, DNS failures, blocked network access, and TLS failures. Browser verification does not automatically authorize the app's separate connection.

## MegaDebrid sessions

MegaDebrid sign-in now saves credentials and the current token together in Android Keystore-backed encrypted storage. Expired sessions renew automatically, and the rejected operation retries once. A temporary renewal failure preserves saved credentials. Removing the account removes the encrypted credentials too.

The [MegaDebrid API](https://www.mega-debrid.eu/index.php?page=api) says a new sign-in invalidates the previous token. Tokens are reused until rejected. Rejected credentials or Premium access stop automatic sign-in attempts until the user signs in again, avoiding repeated failed logins.

Accounts saved by older versions contain only a temporary token. Use **Sign in again** once to enable renewal, without removing the account. Invalid passwords and expired subscriptions still need user action.
