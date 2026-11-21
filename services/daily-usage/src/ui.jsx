import { useId } from 'react';
import Button from './ahfu/AHFUButton';
import IconButton from './ahfu/AHFUIconButton';
import Tooltip from './ahfu/AHFUTooltip';
import Select from './ahfu/AHFUSelect';
import TextInput from './ahfu/AHFUTextInput';
import AHFULink from './ahfu/AHFULink';
import AHFUIcon from './ahfu/AHFUIcon';
export { Button, IconButton, Tooltip, TextInput };
export { default as Card } from './ahfu/AHFUCard';
export { default as CollapsibleCard } from './ahfu/AHFUCollapsibleCard';
export { default as DateTimePicker } from './ahfu/AHFUDateTimePicker';
export { default as TextArea } from './ahfu/AHFUTextArea';
export { default as Switch } from './ahfu/AHFUSwitch';
export { default as MultiTextInput } from './ahfu/AHFUMultiTextInput';
export { default as Chip } from './ahfu/AHFUChip';
export { default as Notice } from './ahfu/AHFUNotice';
export { default as Tabs } from './ahfu/AHFUTabGroup';
export { default as Modal } from './ahfu/AHFUModal';
export { default as Heading } from './ahfu/AHFUHeading';
export { default as Text } from './ahfu/AHFUText';
export { default as TextPairing } from './ahfu/AHFUTextPairing';
export function Link(props) { return <AHFULink target="_self" {...props} />; }
export { default as Skeleton } from './ahfu/AHFUSkeleton';
export { default as HStack } from './ahfu/AHFUHStack';
export { default as VStack } from './ahfu/AHFUVStack';
export { default as Box } from './ahfu/AHFUBox';

const paths = {
  edit: 'm15 5 4 4M4 20l4-1L20 7a2 2 0 0 0-4-4L4 15v5Z', back: 'M19 12H5m6-6-6 6 6 6',
  arrow: 'M5 12h14m-6-6 6 6-6 6', plus: 'M12 5v14M5 12h14',
  close: 'm6 6 12 12M6 18 18 6', info: 'M12 11v6M12 7h.01',
  chart: 'M4 4v16h16M8 15v-4m5 4V7m5 8V4',
  cache: 'M4 6c0-4 16-4 16 0s-16 4-16 0Zm0 0v12c0 4 16 4 16 0V6M4 12c0 4 16 4 16 0',
  controls: 'M4 7h7m4 0h5M4 17h3m4 0h9M11 4v6M7 14v6',
  home: 'm3 10 9-7 9 7v10h-6v-6H9v6H3Z',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8M16 3.13a4 4 0 0 1 0 7.75',
  account: 'M20 21v-2a7 7 0 0 0-14 0v2M13 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8',
  refresh: 'M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 12-2l2 7M4 12l2 7a7 7 0 0 0 12-2',
  trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 10v7m4-7v7',
  up: 'm6 14 6-6 6 6', down: 'm6 10 6 6 6-6',
  check: 'm5 12 4 4L19 6', eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z',
};
export function Icon({ name, size = 20 }) {
  return <AHFUIcon size={size}><svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.info} />{name === 'info' && <circle cx="12" cy="12" r="9" />}{name === 'eye' && <circle cx="12" cy="12" r="2" />}</svg></AHFUIcon>;
}
export function Help({ children, label = 'Help' }) {
  return <Tooltip content={children} delay={0}><IconButton aria-label={label} size="sm"><Icon name="info" size={17} /></IconButton></Tooltip>;
}
export function SelectField({ label, help, options, ...props }) {
  const id = useId();
  return <div className="field"><div className="field-label"><span id={id}>{label}</span>{help && <Help label={`About ${label}`}>{help}</Help>}</div><Select labelledBy={id} options={options.map(item => Array.isArray(item) ? { value: item[0], label: item[1] } : item)} {...props} /></div>;
}
export function InputField({ label, help, ...props }) {
  return <div className="field with-help">{help && <div className="input-help"><Help label={`About ${label}`}>{help}</Help></div>}<TextInput label={label} fullWidth {...props} /></div>;
}
export function Action({ icon, label, ...props }) {
  return <Tooltip content={label}><IconButton aria-label={label} {...props}><Icon name={icon} /></IconButton></Tooltip>;
}
