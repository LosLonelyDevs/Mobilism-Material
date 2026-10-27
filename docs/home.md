# Home

Apps, Books, and Games get a Home tab beside Latest and Top. The original Home screen defines the content: new releases, reviews, and recent discussions. Orion Store defines the visual direction: a generous hero pager, fanned artwork, rounded tonal cards, horizontal collections, and expressive category shapes.

Home uses real section-specific feeds. Popular means the API's views ordering, not personal recommendations. Reviews come from the section's review forum when one is available. Each collection loads independently and keeps its own retry action. Category shortcuts open the filtered Latest feed; More actions open the corresponding complete list. Existing feed filters, release pages, and discussions retain their behavior.

Motion uses the shared Material expressive scheme. Tab changes crossfade; the hero follows horizontal paging with subtle artwork parallax. Category shapes round out on press. Reduced motion removes parallax, scale, and shape movement. The hero advances only when the user swipes or selects a page, so text remains stable while reading.

Reuse source: [Orion Store](https://github.com/RookieEnough/Orion-Store), commit `bffea4103b05120904a9e0d1a8f3f0cac594c553`. The polygon morph implementation and hero composition are adapted from `ui/Common.kt` and `ui/StoreScreen.kt`. See [third-party notices](../THIRD_PARTY_NOTICES.md). No Orion catalog data, branding, accounts, downloads, or privileged installer code is included.
