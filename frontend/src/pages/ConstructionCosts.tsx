import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface SiteCost {
  site: string; materialsByType: Record<string, number>; materials: number;
  labourDays: number; workerDays: number; labour: number; total: number;
}

export default function ConstructionCosts() {
  const { data, error } = useDesk<SiteCost[]>("/api/construction/desk/costs");
  const grand = (data ?? []).reduce((s, c) => s + c.total, 0);

  return (
    <section>
      <h1>Site costs</h1>
      {data && <p className="stats"><strong>{data.length}</strong> site(s) · all sites {money(grand)}</p>}
      {data?.map((c) => (
        <div key={c.site} className="card">
          <h2>{c.site} · {money(c.total)}</h2>
          <div className="table-wrap">
            <table>
              <tbody>
                {Object.entries(c.materialsByType).map(([m, v]) => (
                  <tr key={m}><td>{m.toLowerCase()}</td><td>{money(v)}</td><td>{c.total ? Math.round((v / c.total) * 100) : 0}% of site</td></tr>
                ))}
                <tr><td>labour ({c.labourDays} day(s), {c.workerDays} worker-days)</td><td>{money(c.labour)}</td><td>{c.total ? Math.round((c.labour / c.total) * 100) : 0}% of site</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      ))}
      {error && <p className="error">{error}</p>}
      <p className="muted">Material cost is quantity times unit cost; labour is workers times daily rate, from the records under this system.</p>
    </section>
  );
}
