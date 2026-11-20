# Request identity

The installed package supplies `versionName` and `versionCode` through `AppUserAgent`. The APK downloader uses the same Gradle values from `BuildConfig`. No production request path should fall back to OkHttp's default identity.

| Request | Identity | Remote prefix |
| --- | --- | --- |
| Mobilism API and forum reads/writes | Browser prefix plus app version/build | Yes |
| Embedded verification and forum forms | Browser prefix plus app version/build | At browser creation |
| Shared images: thumbnails, release images, avatars, comments, guides, library and following | Browser prefix plus app version/build | HTTPS Mobilism hosts only |
| Mobilism forum host list used by Premium | Browser prefix plus app version/build | Yes |
| Premium/debrid providers and external metadata | App version/build | No |
| GitHub update checks | App version/build | No |
| GitHub APK downloads, including redirects | App version/build | No |
| Release files from direct and resolved links | App version/build, without forum cookies or account headers | No |
| Remote configuration | App version/build | No |
| Usage reporting | Existing usage prefix plus app version/build | No |

Links opened in an external browser use that browser's networking settings.

## Browser cookies

Native API and forum requests share WebView's cookie store for their matching HTTPS origin. Response `Set-Cookie` headers update that store before the caller resumes, including rotations and deletions on challenge responses. Updates are flushed to disk. This does not add requests, change retry budgets, or send API tokens to forum pages.

The bridge accepts only `app.mobilism.org` and `forum.mobilism.org`. WebView enforces cookie domain, path, expiry, Secure, and HttpOnly rules. Image clients, Premium providers, downloads, and external links do not use this bridge.

`NativeCookieContinuityTest` uses intercepted responses to verify rotation, deletion, origin isolation, challenge recovery, and persistence across separate instrumentation processes. These checks make no server requests.

### Avatars and badges

The app keeps up to 256 member identities on the device, keyed by username. A known avatar or badge stays available for 30 days. Reading it does not extend that window. Opening a profile updates its badges immediately. Profiles with neither an avatar nor a badge use the configured member cache interval. Coil stores the image bytes in its disk cache.

An avatar supplied by a message or comment can be reused even when the profile lookup fails. Only profile ranks and groups supply badges. Optional lookups stop on a challenge. An explicit visit to a profile or Download Help opens the requested page for verification. The app displays the result directly once that page loads.

## Images

`MobilismApplication` owns Coil's singleton factory. All image components use the same configured loader. This includes images opened at full size and images loaded from saved library entries.

The image client upgrades ordinary HTTP Mobilism image URLs to HTTPS before making a request. Its network interceptor chooses the header again on every redirect. Third-party hosts use the shipped header, without the remote prefix. The client does not share API credentials or cookies.

Response-triggered rules affect subsequent requests. Observing a matching response does not retry that request. Rule removal, invalid or expired configuration, and configuration failures restore the shipped header through the existing policy.

Image support for remote rules starts with v0.1.23. Earlier releases cannot receive this transport fix through configuration alone.

## Verification

`AppUserAgentInterceptorTest` covers response activation, subsequent requests, fallback, and host boundaries. `ServiceUserAgentTest` checks update/config request headers. `AppImageUrlTest` checks HTTPS upgrades and authority boundaries.

`LiveImageLoadingTest` exercises the real application singleton with caches disabled, using the reported release image and both HTTPS and legacy HTTP feed images. Run it with the live probe runner and `-e liveImages true`. The existing recovery probe covers silent API/forum verification and the visible forum dialog.
