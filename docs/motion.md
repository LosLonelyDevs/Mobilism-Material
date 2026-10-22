# Expressive motion decisions

The theme uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`. Shared controls live in `core:designsystem`; feature screens do not define ad hoc animation durations.

| Interaction | Motion | Purpose |
| --- | --- | --- |
| Navigate forward/back | Short shared-axis translation and fade; opposite direction on back | Preserve screen hierarchy and continuity |
| Primary action | Native Expressive Button shape morph and M3 state layer | Confirm touch without moving the task |
| Release/theme selection | Small press scale using `fastSpatialSpec`, native state layer | Direct press feedback |
| Drawer and download sheet | Material navigation drawer and modal sheet transitions | Keep familiar navigation and dismissal behavior |
| Waiting | Contained Expressive loading indicator plus live-region text | Show progress without inventing a percentage |
| Appearance change | Immediate semantic color/font update | Show the selected appearance without a separate staged preview |

Spatial movement uses the theme's spring specs; opacity uses effects specs. The common focus outline provides a visible keyboard focus state. Buttons and icon controls keep at least 48 dp touch targets, and field/password error text remains readable independently of accent colors.

The app's Reduce motion switch or Android animator duration scale set to zero chooses an instant MotionScheme, removes route translation/fades and press scaling, and replaces the animated loading indicator with text. Native ripple/state feedback remains. Native component internals also depend on platform and library animation behavior and need device checks.

## Pending checks

Compilation and interaction validation were deferred by request. Before calling this migration ready, check forward/back interruption, drawer/sheet gestures, font scaling, TalkBack announcements, keyboard focus, disabled animations, and contrast on a real device. No such checks are claimed by this source milestone.
