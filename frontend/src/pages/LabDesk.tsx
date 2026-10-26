import { useState } from "react";
import { openFile } from "../lib/api";
import { useDesk } from "../lib/desk";

interface Order { id: number; orderNumber: string; patientName: string; collectedOn: string; status: string | null }
interface Row { order: Order; testName: string; normalRange: string | null; flag: string | null }

export default function LabDesk() {
  const { data, error, act } = useDesk<Row[]>("/api/lab/desk/worklist");
  const [results, setResults] = useState<Record<number, string>>({});
  const [done, setDone] = useState<Row | null>(null);

  async function report(id: number) {
    const r = await act<Row>(`/api/lab/desk/orders/${id}/result`, { result: results[id] ?? "" });
    if (r) setDone(r);
  }

  return (
    <section>
      <h1>Lab work list</h1>
      <div className="card">
        {data?.length === 0 && <p className="muted">No samples waiting</p>}
        <div className="table-wrap">
          <table>
            <thead><tr><th>Order</th><th>Patient</th><th>Test</th><th>Normal range</th><th>Status</th><th>Result</th><th /></tr></thead>
            <tbody>
              {data?.map((r) => (
                <tr key={r.order.id}>
                  <td>{r.order.orderNumber}<br /><span className="muted">{r.order.collectedOn}</span></td>
                  <td>{r.order.patientName}</td>
                  <td>{r.testName}</td>
                  <td>{r.normalRange}</td>
                  <td>
                    {r.order.status ?? "COLLECTED"}
                    {(r.order.status ?? "COLLECTED") === "COLLECTED" && (
                      <button className="link" onClick={() => act(`/api/lab/desk/orders/${r.order.id}/start`)}>Start</button>
                    )}
                  </td>
                  <td><input value={results[r.order.id] ?? ""} onChange={(e) => setResults({ ...results, [r.order.id]: e.target.value })} /></td>
                  <td><button className="link" disabled={!results[r.order.id]} onClick={() => report(r.order.id)}>Report</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {error && <p className="error">{error}</p>}
      </div>
      {done && (
        <p className="card">
          {done.order.orderNumber}: {done.testName} reported{done.flag && done.flag !== "NOT_NUMERIC" ? `, ${done.flag}` : ""}.{" "}
          <button className="ghost" onClick={() => openFile(`/api/lab/desk/orders/${done.order.id}/report.pdf`)}>Print report</button>
        </p>
      )}
      <p className="muted">A reported result is final here; a correction needs a new order. Sample software, not a medical device.</p>
    </section>
  );
}
