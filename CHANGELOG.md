# Changelog

## 0.1.2

- Format release names separately from explicit version/build markers and tags; preserve ambiguous titles intact.
- Add read-only paginated comments below Downloads and a full-screen image viewer with zoom, pan, and paging.
- Reduce release heading and host-button sizes; remove debrid subtitles from download buttons.
- Recognize underlined changelog headings and common premium mod blocks without losing post formatting.
- Expose the newest-first feed as Latest beside Top.
- Add a filter sheet with searchable multiselect categories, Apply and Reset controls, and per-section preferences.
- Merge selected categories in order across infinite-scroll pages, with duplicate protection and retry-safe cursors.
- Align debrid cards with Appearance, add colorful account status and actions, and show supported hosts in an expandable menu.
- Use Frappé's broader palette through semantic theme roles across feeds, navigation, settings, and release sections.
- Right-align the active category chip and clear icon button.
- Split releases into collapsible Description, What's new, Mod info, and Downloads cards when present.
- Group download buttons by CPU architecture; remember preferred architecture and favorite hosts, with other architectures collapsed and preferred hosts listed first.
- Add a release overflow menu with Copy release URL and Open in browser actions.

## 0.1.1

- Build the Compose rewrite with Material 3 Expressive, Catppuccin Frappé, Geist, persistent appearance settings, and encrypted login sessions.
- Load list thumbnails from release images and turn download URLs into host-name browser buttons with adjacent copy actions.
- Continue feed pagination when the server returns fewer items than requested.
- Add encrypted Real-Debrid and MegaDebrid key settings, supported-host highlighting, and regression coverage for image/link parsing and domain matching.
- Hide saved-key inputs and shortlist provider support against Mobilism's own file-host directory.
