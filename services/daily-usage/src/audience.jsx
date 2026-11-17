import { useState } from 'react';
import { Action, Button, Chip, Help, HStack, InputField, Modal, MultiTextInput, Notice, SelectField, Switch, Tabs } from './ui';
import { listOperators, numberOperators, ruleFields, validateAudience, matchesAudience } from '../public/audience.js';
import { isVersionRule, selectedVersions, versionOperators, versionRule } from '../public/notice-editor.js';

export function newRule(field = 'username') {
  return { type: 'rule', field, operator: ['username', 'versionName'].includes(field) ? 'in' : 'eq',
    ...(['username', 'versionName'].includes(field) ? { values: [] } : { value: field === 'signedIn' ? true : 1 }) };
}
function countRules(group) { return 1 + (group.conditions?.reduce((n, child) => n + countRules(child), 0) || 0); }

export function VersionPicker({ versions, value, multiple = false, label = 'App version', ...props }) {
  const selected = multiple ? value : [value];
  const missing = selected.filter(name => name && !versions.some(row => row.version === name));
  const options = [...versions.map(row => ({ value: row.version, label: row.version, subtitle: row.prerelease ? 'Pre-release' : undefined })),
    ...missing.map(name => ({ value: name, label: name, subtitle: 'Not in the release catalog' }))];
  return <SelectField label={label} searchable multiple={multiple} placeholder="Choose a version" options={options} value={value} {...props} />;
}

function Rule({ rule, onChange, onRemove, usernames, catalog }) {
  const version = isVersionRule(rule);
  const versions = selectedVersions(rule, catalog.versions);
  const versionOperator = rule.operator === 'eq' ? 'in' : rule.operator;
  const multipleVersions = ['in', 'notIn'].includes(versionOperator);
  const operators = version ? versionOperators.filter(([op]) => catalog.comparisonsAvailable || ['in', 'notIn', versionOperator].includes(op)) : rule.field === 'username' ? listOperators : numberOperators;
  function changeVersion(operator, selected) { onChange(versionRule(operator, selected, catalog.versions)); }
  const availableNames = usernames.filter(name => !rule.values?.includes(name));
  return <div className="rule-row">
    <div className="rule-fields"><SelectField label="Condition" options={ruleFields.filter(([field]) => field !== 'versionCode')} value={version ? 'versionName' : rule.field} onChange={field => onChange(newRule(field))} />
      {rule.field !== 'signedIn' && <SelectField label="Match" options={operators} value={version ? versionOperator : rule.operator} onChange={operator => version ? changeVersion(operator, versions.length ? versions : catalog.versions.slice(0, 1).map(row => row.version)) : onChange({ ...rule, operator })} help={version ? 'Versions come from published GitHub releases. Comparisons follow release order. New releases appear automatically.' : undefined} />}
      {version ? <div className="rule-values"><VersionPicker versions={catalog.versions} label={multipleVersions ? 'Versions' : 'Version'} multiple={multipleVersions} value={multipleVersions ? versions : versions[0] || ''} disabled={!catalog.versions.length} onChange={value => changeVersion(versionOperator, multipleVersions ? value : [value])} />
        {rule.field === 'versionCode' && !versions.length && <Notice variant="warning">This saved condition targets a release unavailable in this catalog.</Notice>}
      </div> : rule.field === 'username' ? <div className="rule-values"><div className="field-label"><span>Usernames</span><Help label="Adding selections">Enter a value, then press Enter or Tab. Add as many as needed. Usernames match the signed-in Mobilism account.</Help></div>
        <MultiTextInput values={rule.values} onChange={values => onChange({ ...rule, values })} label="Selected usernames" placeholder="Add username" fullWidth maxTags={100} />
        {availableNames.length > 0 && <SelectField label="Add saved usernames" multiple searchable options={availableNames.map(n => [n, n])} value={[]} onChange={selected => onChange({ ...rule, values: [...new Set([...rule.values, ...selected])] })} />}
      </div> : rule.field === 'signedIn' ? <SelectField label="Value" options={[[true, 'Signed in'], [false, 'Signed out']].map(([v, l]) => [String(v), l])} value={String(rule.value)} onChange={value => onChange({ ...rule, value: value === 'true' })} /> :
        <InputField label="Android API" type="number" min={1} max={100} value={rule.value} onChange={e => onChange({ ...rule, value: e.target.value === '' ? '' : Number(e.target.value) })} />}
    </div><Action label="Remove condition" icon="close" onClick={onRemove} />
  </div>;
}
function Group({ group, depth = 0, onChange, onRemove, usernames, room, catalog }) {
  function change(index, value) { onChange({ ...group, conditions: group.conditions.map((old, i) => i === index ? value : old) }); }
  return <section className={`rule-group depth-${depth}`}>
    <div className="rule-group-heading"><div className="group-mode"><SelectField label={depth ? 'Nested group' : 'Match rules'} help="ALL requires every condition. ANY requires at least one. Nested groups combine both." value={group.type} options={[['all', 'ALL · AND'], ['any', 'ANY · OR']]} onChange={type => onChange({ ...group, type })} /></div>{onRemove && <Action icon="close" label="Remove group" onClick={onRemove} />}</div>
    <div className="rule-list">{group.conditions.map((child, index) => <div key={index}>{index > 0 && <span className="rule-join">{group.type === 'all' ? 'AND' : 'OR'}</span>}{child.type === 'rule' ?
      <Rule rule={child} onChange={value => change(index, value)} onRemove={() => onChange({ ...group, conditions: group.conditions.filter((_, i) => i !== index) })} usernames={usernames} catalog={catalog} /> :
      <Group group={child} depth={depth + 1} onChange={value => change(index, value)} onRemove={() => onChange({ ...group, conditions: group.conditions.filter((_, i) => i !== index) })} usernames={usernames} room={room} catalog={catalog} />}</div>)}</div>
    <HStack gap={8} style={{ flexWrap: 'wrap' }}><Button size="sm" disabled={!room || group.conditions.length >= 12} onClick={() => onChange({ ...group, conditions: [...group.conditions, newRule()] })}>+ Condition</Button>
      {depth < 2 && <Button size="sm" disabled={room < 2 || group.conditions.length >= 12} onClick={() => onChange({ ...group, conditions: [...group.conditions, { type: 'any', conditions: [newRule('versionName')] }] })}>+ Group</Button>}</HStack>
  </section>;
}

export default function Audience({ audience, onChange, usernames, catalog }) {
  return <div className="audience-section"><div className="section-label"><h3>Audience</h3><Help label="About notice audiences">Only matching users see a notice or required update. App version targeting belongs here. An optional schedule limits when it appears. Signed-out users never match a username rule, including exclusions.</Help></div>
    <Tabs variant="fill" value={audience.type === 'everyone' ? 'everyone' : 'rules'} items={[{ key: 'everyone', label: 'Everyone' }, { key: 'rules', label: 'Selected audience' }]} onChange={value => onChange(value === 'everyone' ? { type: 'everyone' } : { type: 'all', conditions: [newRule()] })} />
    {audience.type !== 'everyone' && <Group group={audience.type === 'rule' ? { type: 'all', conditions: [audience] } : audience} onChange={onChange} usernames={usernames} room={24 - countRules(audience)} catalog={catalog} />}
  </div>;
}

export function NoticeTest({ notice, onClose, catalog }) {
  const [context, setContext] = useState({ username: '', versionName: catalog.versions.find(row => !row.prerelease)?.version || catalog.versions[0]?.version || '', androidApi: 36, signedIn: true });
  const [result, setResult] = useState(null);
  function update(key, value) { setContext(old => ({ ...old, [key]: value })); setResult(null); }
  function test() {
    try {
      const audience = validateAudience(notice.audience);
      const versionCode = catalog.versions.find(row => row.version === context.versionName)?.versionCode;
      if (!versionCode) throw new Error('Choose an app version to test.');
      const now = Date.now(), reasons = [];
      if (!notice.enabled) reasons.push('Notice is disabled.');
      if (notice.startsAt && Date.parse(notice.startsAt) > now) reasons.push('Schedule has not started.');
      if (notice.endsAt && Date.parse(notice.endsAt) <= now) reasons.push('Schedule has ended.');
      if (!matchesAudience(audience, { ...context, versionCode, username: context.signedIn ? context.username : '' })) reasons.push('Audience rules do not match.');
      setResult({ matches: reasons.length === 0, text: reasons.join(' ') || 'This user would see this notice.' });
    } catch (error) { setResult({ matches: false, text: error.message }); }
  }
  return <Modal open onClose={onClose} title="Test this notice" size="medium" footer={<Button variant="filled" onClick={test}>Check match</Button>}>
    <div className="form-grid"><InputField label="Username" value={context.username} disabled={!context.signedIn} onChange={e => update('username', e.target.value)} />
      <VersionPicker versions={catalog.versions} value={context.versionName} onChange={value => update('versionName', value)} />
      <InputField label="Android API" type="number" min={1} max={100} value={context.androidApi} onChange={e => update('androidApi', Number(e.target.value))} />
    </div><div className="test-sign-in"><Switch label="Signed in" checked={context.signedIn} onChange={e => update('signedIn', e.target.checked)} /></div>
    {result && <div role="status" className="test-result"><Notice variant={result.matches ? 'success' : 'warning'} title={result.matches ? 'Matches' : 'Does not match'}>{result.text}</Notice></div>}
  </Modal>;
}
