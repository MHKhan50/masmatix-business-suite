import { useState } from "react";
import type { Session } from "../lib/api";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Instalment { id: number; plotNumber: string; dueOn: string; amount: number }
interface Overdue { instalment: Instalment; daysLate: number; ownerName: string | null }
interface Plot { plotNumber: string; status: string; ownerName: string }

export default function HousingDesk({ session }: { session: Session }) {
  const { data, error, act } = useDesk<Overdue[]>("/api/housing/desk/overdue");
  const [plotId, setPlotId] = useState("");
  const [owner, setOwner] = useState("");
  const [note, setNote] = useState("");
  const canTransfer = session.role !== "STAFF";

  async function change(kind: "allot" | "transfer") {
    const p = await act<Plot>(`/api/housing/desk/plots/${plotId}/${kind}`, { ownerName: owner });
    if (p) setNote(`Plot ${p.plotNumber} is ${p.status.toLowerCase()} to ${p.ownerName}`);
  }

  return (
    <section>
      <h1>Housing society desk</h1>
      <div className="till">
        <div className="card">
          <h2>Overdue instalments</h2>
          {data?.length === 0 && <p className="muted">Nothing overdue</p>}
          {data?.map((r) => (
            <div key={r.instalment.id} className="slot">
              <strong>{r.instalment.plotNumber}</strong> · {r.ownerName ?? "no owner"} · {money(r.instalment.amount)}<br />
              <span className="muted">due {r.instalment.dueOn} · {r.daysLate} day(s) late</span>
              <button className="link" onClick={() => act(`/api/housing/desk/instalments/${r.instalment.id}/pay`)}>Mark paid today</button>
            </div>
          ))}
        </div>
        {canTransfer && (
          <aside className="card pay form">
            <h2>Allot or transfer</h2>
            <label><span>Plot record number</span><input value={plotId} onChange={(e) => setPlotId(e.target.value)} /></label>
            <label><span>New owner</span><input value={owner} onChange={(e) => setOwner(e.target.value)} /></label>
            <div className="actions">
              <button disabled={!plotId || !owner} onClick={() => change("allot")}>Allot</button>
              <button className="ghost" disabled={!plotId || !owner} onClick={() => change("transfer")}>Transfer</button>
            </div>
            <p className="muted">A transfer waits until overdue instalments are paid. Old and new owner go to the audit log.</p>
          </aside>
        )}
      </div>
      {note && <p className="muted">{note}</p>}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
