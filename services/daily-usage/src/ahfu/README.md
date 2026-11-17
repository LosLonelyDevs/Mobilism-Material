# AHFU components

Source components from AHFU Material Components, used directly by the admin frontend. The original license is included in `LICENSE`.

Local adaptations:

- CollapsibleCard observes content resizing, removes closed fields from keyboard navigation, and exposes stable title/subtitle classes for compact summaries.
- Tooltip wraps within the viewport, supports touch and focus, and connects help text to its trigger.
- Select adds labels, keyboard navigation, focus restoration, viewport-aware menus, and accessible remove actions.
- MultiTextInput wraps chips, associates its label, and commits on blur.
- Modal traps and restores focus, renders in a portal, and makes the background inert.
- TabGroup supports arrow keys, Home, End, and a single tab stop.
- Chip labels its remove action. Link supports React children for internal navigation.
- TextInput, TextArea, Select, MultiTextInput, and picker fields share the dashboard's control radius.
- DateTimePicker, DropdownSelector, Button, Select, Tooltip, StateLayer, and popup hooks include the latest library refinements. Picker icons use local SVG paths so an external font is unnecessary.

Theme colors live in `../app.css`. The source library is unchanged.
