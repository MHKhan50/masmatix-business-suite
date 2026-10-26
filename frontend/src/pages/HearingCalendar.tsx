import { useState } from "react";
import { useDesk } from "../lib/desk";

interface Hearing { id: number; caseNumber: string; hearingOn: string; purpose: string | null }
interface Entry { hearing: Hearing; title: string | null; court: string | null; clientName: string | null }
interface Day { date: string; hearings: Entry[] }

export default function HearingCalendar() {
  const [days, setDays] = useState(14);
  const { data, error, act } = useDesk<Day[]>(`/api/lawfirm/desk/calendar?days=${days}`);
  const [outcome, setOutcome] = useState<Record<number, string>>({});
  const [next, setNext] = useState<Record<number, string>>({});

  return (
    <section>
      <h1>Hearing calendar</h1>
      <div className="seg">
        {[7, 14, 30].map((d) => <button key={d} className={d === days ? "" : "ghost"} onClick={() => setDays(d)}>{d} days</button>)}
      </div>
      {data?.length === 0 && <p className="muted">No hearings in this period</p>}
      {data?.map((d) => (
        <div key={d.date} className="card">
          <h2>{d.date}</h2>
          {d.hearings.map((e) => (
            <div key={e.hearing.id} className="slot">
              <strong>{e.hearing.caseNumber}</strong> · {e.title} · {e.court}<br />
              <span className="muted">{e.clientName} · {e.hearing.purpose}</span>
              <div className="actions">
                <input placeholder="Outcome" value={outcome[e.hearing.id] ?? ""} onChange={(ev) => setOutcome({ ...outcome, [e.hearing.id]: ev.target.value })} />
                <input type="date" value={next[e.hearing.id] ?? ""} onChange={(ev) => setNext({ ...next, [e.hearing.id]: ev.target.value })} />
                <button className="link" disabled={!outcome[e.hearing.id] || !next[e.hearing.id]}
                  onClick={() => act(`/api/lawfirm/desk/hearings/${e.hearing.id}/outcome`, { outcome: outcome[e.hearing.id], nextHearingOn: next[e.hearing.id] })}>Adjourn to date</button>
                <button className="link" disabled={!outcome[e.hearing.id]}
                  onClick={() => act(`/api/lawfirm/desk/hearings/${e.hearing.id}/outcome`, { outcome: outcome[e.hearing.id], decided: true })}>Decided</button>
              </div>
            </div>
          ))}
        </div>
      ))}
      {error && <p className="error">{error}</p>}
      <p className="muted">Outcomes are recorded on or after the hearing day. Closed or decided cases take no new dates.</p>
    </section>
  );
}
