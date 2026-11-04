# Member profiles

Tap a username in releases, comments, discussions, or messages to open its native profile. Profile links in formatted content use the same screen.

Administrator, Moderator, and Developer badges come from the forum's rank and group fields. Biography text cannot grant a badge. Other ranks and groups, including Major Android Releaser, keep their forum labels.

Telegram and WhatsApp buttons open the profile's original contact URL in the installed app or browser. Image-only contact links work too. Opening a link does not automatically join a channel.

The screen uses one identity panel, wrapping badges, flat details, and the app's Expressive navigation and button motion. Reduced motion follows Appearance settings. If the forum requires verification, complete its check inside the app and return to the native profile.

Parser tests cover role metadata, contributor ranks, contacts, invalid pages, and username URL encoding. Android UI tests cover username navigation and contact buttons at phone width. Those UI tests require a device run; compilation alone does not verify their appearance.
