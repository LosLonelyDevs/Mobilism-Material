import { useEffect, useRef, useState } from 'react';
import { Action, Button, Card, Chip, Heading, Help, HStack, Icon, Link, Modal, Switch, TextPairing, Tooltip, VStack } from './ui';
import { audienceSummary } from '../public/notice-editor.js';

const dateLabel = value => new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const scheduleLabel = notice => [notice.startsAt ? `From ${dateLabel(notice.startsAt)}` : '', notice.endsAt ? `Until ${dateLabel(notice.endsAt)}` : ''].filter(Boolean).join(' · ') || 'Any time';

function NoticePreview({ notice, catalog, onEdit, onEnable, onTest, busy }) {
  const audience = audienceSummary(notice.audience, catalog.versions);
  return <Card style={{ borderRadius: 20 }}><article className="notice-preview">
    <div className="notice-preview-heading"><div><Chip size="small" label={{ notice: 'Notice', update: 'Update reminder', required: 'Required update' }[notice.mode]} /><Heading level={2}>{notice.title}</Heading></div><Action icon="edit" label={`Edit ${notice.title}`} disabled={busy} onClick={onEdit} /></div>
    <p className="notice-preview-message">{notice.message}{notice.linkUrl && <> <Link href={notice.linkUrl} target="_blank" rel="noopener noreferrer">{notice.linkText}</Link></>}</p>
    <div className="notice-preview-details"><Tooltip content={audience}><span className="notice-summary">{audience}</span></Tooltip><span>{scheduleLabel(notice)}</span>
      <HStack gap={8} style={{ flexWrap: 'wrap' }}><Chip size="small" label={notice.mode === 'required' ? 'Until updated' : { once: 'Once', launch: 'Each app launch', always: 'Each foreground visit' }[notice.frequency]} />{notice.deviceNotification && <Chip size="small" label="Device notification" />}</HStack>
    </div>
    <div className="notice-preview-actions"><Switch label={notice.enabled ? 'Enabled' : 'Disabled'} checked={notice.enabled} disabled={busy} onChange={event => onEnable(event.target.checked)} /><Action icon="eye" label={`Test ${notice.title}`} onClick={onTest} disabled={busy} /></div>
  </article></Card>;
}

export default function NoticeWorkspace({ config, catalog, usernames, freshNotice, Editor, onSave, onTest, onDirtyChange, busy }) {
  const [editor, setEditor] = useState(null), [confirmDelete, setConfirmDelete] = useState(false);
  const heading = useRef(null), returnToList = useRef(false);
  const original = editor?.id ? config.notices.find(notice => notice.id === editor.id) : null;
  const dirty = !!editor && (!original || JSON.stringify(editor.notice) !== JSON.stringify(original) || editor.position !== config.notices.indexOf(original));
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!editor && returnToList.current) {
      heading.current?.focus();
      heading.current?.scrollIntoView({ block: 'start' });
      returnToList.current = false;
    }
  }, [editor]);
  function close() { returnToList.current = true; setEditor(null); setConfirmDelete(false); }
  function save() {
    const notice = { ...editor.notice, enabled: original?.enabled ?? false };
    const notices = config.notices.filter(item => item.id !== editor.id);
    notices.splice(Math.min(editor.position, notices.length), 0, notice);
    onSave(notices, close);
  }
  if (editor) return <section className="notice-edit-view">
    <div className="workspace-heading"><HStack gap={10}><Action icon="back" label="Cancel and return to notices" onClick={close} disabled={busy} /><Heading level={2}>{original ? 'Edit notice' : 'Create notice'}</Heading></HStack><Action icon="eye" label="Test this notice" onClick={() => onTest({ ...editor.notice, enabled: true })} disabled={busy} /></div>
    <Editor notice={editor.notice} onChange={notice => setEditor(old => ({ ...old, notice }))} usernames={usernames} catalog={catalog} />
    <footer className="notice-edit-actions"><HStack gap={8}>{original && <><Action icon="trash" label="Delete notice" disabled={busy} onClick={() => setConfirmDelete(true)} /><Action icon="up" label="Move notice up" disabled={busy || editor.position === 0} onClick={() => setEditor(old => ({ ...old, position: old.position - 1 }))} /><Action icon="down" label="Move notice down" disabled={busy || editor.position >= config.notices.length - 1} onClick={() => setEditor(old => ({ ...old, position: old.position + 1 }))} /></>}</HStack>
      <HStack gap={8}><Button disabled={busy} onClick={close}>Cancel</Button><Button variant="filled" disabled={busy || !dirty} loading={busy} onClick={save}>{original ? 'Save notice' : 'Create notice'}</Button></HStack>
    </footer>
    <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title="Delete this notice?" size="small" footer={<><Button onClick={() => setConfirmDelete(false)}>Keep notice</Button><Button variant="danger" disabled={busy} onClick={() => onSave(config.notices.filter(notice => notice.id !== editor.id), close)}>Delete notice</Button></>}>This removes the notice from the app on its next successful check.</Modal>
  </section>;
  return <>
    <div className="workspace-heading" ref={heading} tabIndex={-1}><HStack gap={6}><TextPairing title="In-app notices" /><Help label="Notice priority">Required updates take priority. Other notices appear in the order shown. New notices stay disabled until you enable them here.</Help></HStack><Button disabled={busy || config.notices.length >= 10} onClick={() => setEditor({ id: null, notice: freshNotice(), position: config.notices.length })}><Icon name="plus" size={17} /> Add notice</Button></div>
    {config.notices.length ? <VStack gap={16} style={{ alignItems: 'stretch' }}>{config.notices.map((notice, index) => <NoticePreview key={notice.id} notice={notice} catalog={catalog} busy={busy}
      onEdit={() => setEditor({ id: notice.id, notice: structuredClone(notice), position: index })} onTest={() => onTest(notice)}
      onEnable={enabled => onSave(config.notices.map(item => item.id === notice.id ? { ...item, enabled } : item))} />)}</VStack> :
      <Card style={{ borderStyle: 'dashed', borderRadius: 24 }}><div className="empty-notices"><span className="destination-icon"><Icon name="controls" size={26} /></span><Heading level={2}>No notices yet</Heading><Button variant="filled" onClick={() => setEditor({ id: null, notice: freshNotice(), position: 0 })}>Create a notice</Button></div></Card>}
  </>;
}
