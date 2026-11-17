# Dashboard components

The dashboard has three routes: `/` for the overview, `/app-usage` for the existing usage report, and `/app-controls` for notices and API limits.

App Controls uses AHFUCollapsibleCard, Card, Button, IconButton, TextInput, TextArea, Select, MultiTextInput, DateTimePicker, Switch, Chip, Tooltip, Notice, TabGroup, Modal, Heading, TextPairing, Skeleton, Icon, Box, HStack, and VStack. The shared shell also uses AHFULink and Text. Buttons use AHFUSpinner while loading. DateTimePicker uses DropdownSelector for calendar and time selections.

Library review:

| Component | Current implementation |
| --- | --- |
| Date/time picker | AHFUDateTimePicker in the optional Schedule card |
| Duration input | Now available in the source library; API limits still use bounded TextInput fields |
| Nested AND/OR rule builder | Source RuleBuilder still needs configurable operators and custom value editors for username/version lists. The dashboard keeps its existing AHFU composition. |
| Application navigation | Configurable Navigation is now available; the dashboard retains its existing navigation links |
| Metrics and empty states | Metric and EmptyState are now available; existing overview compositions remain |

The existing components also needed accessibility adaptations: modal focus trapping, keyboard select/tab navigation, closed-card focus exclusion, input labels, remove-button names, and bounded touch tooltips. These changes are local to the vendored components. See [the adaptation notes](../services/daily-usage/src/ahfu/README.md).

The latest date picker, button, select, tooltip, popup positioning, and motion updates are included. Inputs share a 12px corner radius. Notice headers show a live, single-line audience summary with a tooltip.

Tables, code blocks, palette controls, filter panels, and multistep dialogs have no role in this controls form. Their absence is intentional; they are not missing from the library.
