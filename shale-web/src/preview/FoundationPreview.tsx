import { useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ResponsiveShell } from '../shell/ResponsiveShell';
import { activeDestination, foundationUrl } from '../shell/navigation';
import { Button, EntityCard, EntityList, Feedback, Field, MetadataGrid, MetadataRow, NavigationButton, PageHeader, SectionRegion, StatusPill, ToolbarActions } from '../ui/primitives';
import '../ui/foundation.css';
import '../shell/shell.css';
import './preview.css';

export function FoundationPreview() {
  const location = useLocation();
  const path = new URLSearchParams(location.search).get('destination') ?? '/my-shale';
  const destination = activeDestination(path);
  const [theme, setTheme] = useState('light');
  const [selected, setSelected] = useState(true);
  const [contactSelected, setContactSelected] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string>();
  const [feedback, setFeedback] = useState('Example only: values stay in memory and are never saved.');
  const form = useRef<HTMLFormElement>(null);
  const title = destination?.label ?? 'Unavailable destination';
  return <div className="shale-foundation" data-theme={theme}>
    <ResponsiveShell path={path} linkTo={foundationUrl} caption="Foundation preview" identity={<><strong>Synthetic workspace</strong><p>No account connected</p></>}
      utilities={<><label htmlFor="preview-theme">Preview theme</label><select id="preview-theme" value={theme} onChange={event => setTheme(event.target.value)}><option value="light">Light</option><option value="dark">Dark</option></select></>}>
      <section className="foundation-gallery" aria-labelledby="foundation-title">
        <PageHeader eyebrow="Web V2 · Phase 2A" title={title} titleId="foundation-title"
          lede="Synthetic foundation review. No real records, account, API connection, or persistence." />
        {(!destination || !destination.available) && <Feedback kind="unavailable">{title} is unavailable in the web application. This preview adds no functionality for this destination.</Feedback>}
        <div className="foundation-columns">
          <div className="foundation-stack">
            <SectionRegion title="Entity vocabulary">
              <p>Selection is local to this example. The same summaries wrap at every width.</p>
              <EntityList ariaLabel="Synthetic case, contact and organization examples">
                <EntityCard title="Example Case — North Meadow Community Association and the exceptionally long review of shared access agreements"
                  eyebrow="Synthetic case" subtitle="Long names remain readable, with no essential details hidden."
                  selected={selected} onClick={() => setSelected(value => !value)} ariaLabel="Select example case"
                  badges={<><StatusPill color="#176b42">Open</StatusPill><StatusPill color="0xF2CF75FF">Community practice</StatusPill></>}
                  metadata={<MetadataGrid><MetadataRow label="Reference" value="EXAMPLE-0001" /><MetadataRow label="Selected" value={selected ? 'Yes' : 'No'} /></MetadataGrid>} />
                <EntityCard compact title="Example Contact — Alexandra Extended-Family-Name, community liaison for the North Meadow demonstration"
                  eyebrow="Synthetic contact" subtitle="alexandra.long-demonstration-address@example.invalid" selected={contactSelected}
                  onClick={() => setContactSelected(value => !value)} ariaLabel="Select example contact"
                  badges={<StatusPill tone="info">Contact</StatusPill>} />
                <EntityCard compact title="Example Organization — North Meadow Regional Community Partnership for Planning and Accessible Shared Facilities"
                  eyebrow="Synthetic organization" subtitle="An unselected, display-only compact summary."
                  badges={<StatusPill color="bad-color">Neutral fallback</StatusPill>} />
              </EntityList>
            </SectionRegion>
            <SectionRegion title="Status and practice-area indicators" density="compact">
              <p>Stored colors identify data, while labels communicate meaning.</p>
              <div className="foundation-indicators">
                <StatusPill color="#073B59">Pending review</StatusPill><StatusPill color="#FFFFA0">Community practice</StatusPill>
                <StatusPill color="0x7B3FE480">Practice with stored alpha</StatusPill><StatusPill color="#FF0000">Urgent</StatusPill>
                <StatusPill color={null}>No stored color</StatusPill><StatusPill color="invalid">Invalid stored color</StatusPill>
                <StatusPill color="#7B3FE4" variant="badge">Practice-area badge</StatusPill>
              </div>
            </SectionRegion>
          </div>
          <div className="foundation-stack">
            <SectionRegion title="Form and action roles">
              <form className="foundation-form" ref={form} noValidate onSubmit={event => {
                event.preventDefault();
                if (!name.trim()) { setError('Enter an example name to check validation.'); form.current?.querySelector('input')?.focus(); }
                else { setError(undefined); setFeedback('Example validation passed. Nothing was saved or sent.'); }
              }}>
                <Field label="Example name" id="example-name" value={name} onChange={event => { setName(event.target.value); setError(undefined); }}
                  error={error} hint="Synthetic text only. Required for the local validation demonstration." autoComplete="off" />
                <Field label="Example email with validation error" type="email" defaultValue="example.invalid" error="Example error: enter an email address." autoComplete="off" />
                <Field label="Read-only example" value="Review only" readOnly hint="This example cannot be edited." />
                <ToolbarActions><Button purpose="primary" type="submit">Check example</Button><Button onClick={() => { setName(''); setError(undefined); setFeedback('Example reset locally. Nothing was saved.'); }}>Reset example</Button></ToolbarActions>
              </form>
              <Feedback>{feedback}</Feedback>
              <ToolbarActions><Button purpose="ghost" size="small" onClick={() => setFeedback('Ghost action demonstrated locally.')}>Show feedback</Button>
                <Button purpose="danger" disabled>Delete unavailable</Button><NavigationButton to={foundationUrl('/tasks')}>View My Tasks example</NavigationButton></ToolbarActions>
              <p>Danger is disabled: there is no destructive operation in the preview.</p>
            </SectionRegion>
            <SectionRegion title="Content states" density="compact">
              <Feedback kind="loading">Loading example — no request is running.</Feedback>
              <Feedback kind="empty">Empty example — no items to show.</Feedback>
              <Feedback kind="unavailable">Unavailable example — reports are not implemented.</Feedback>
              <Feedback kind="error">Error example — content could not be loaded. No real request failed.</Feedback>
              <Feedback kind="success">Feedback example — local validation only, nothing saved.</Feedback>
            </SectionRegion>
          </div>
        </div>
      </section>
    </ResponsiveShell>
  </div>;
}
