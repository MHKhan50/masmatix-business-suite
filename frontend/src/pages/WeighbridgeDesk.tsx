import { useState, type FormEvent } from "react";
import { openFile } from "../lib/api";
import { useDesk } from "../lib/desk";
import { isoDate } from "../lib/rules";
import { netKg } from "../lib/services";

interface Slip { id: number; slipNumber: string; vehicleNumber: string; material: string }
interface Result { slip: Slip; netKg: number }
interface Day { slips: number; netKgByMaterial: Record<string, number>; totalNetKg: number }

const MATERIALS = ["WHEAT", "COTTON", "YARN", "MAIZE", "OTHER"];
const kg = (n: number) => `${n.toLocaleString("en-GB")} kg`;

export default function WeighbridgeDesk() {
  const today = isoDate(new Date());
  const { data, error, act } = useDesk<Day>(`/api/weighbridge/desk/day?date=${today}`);
  const [form, setForm] = useState({ vehicleNumber: "", material: "WHEAT", supplierName: "", grossKg: "", tareKg: "" });
  const [last, setLast] = useState<Result | null>(null);
  const preview = form.grossKg && form.tareKg ? netKg(Number(form.grossKg), Number(form.tareKg)) : null;
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  async function weigh(ev: FormEvent) {
    ev.preventDefault();
    const r = await act<Result>("/api/weighbridge/desk/weigh", { ...form, grossKg: Number(form.grossKg), tareKg: Number(form.tareKg) });
    if (r) setLast(r);
  }

  return (
    <section>
      <h1>Weighbridge</h1>
      <div className="till">
        <form className="card form" onSubmit={weigh}>
          <label><span>Vehicle</span><input value={form.vehicleNumber} onChange={set("vehicleNumber")} required /></label>
          <label><span>Material</span>
            <select value={form.material} onChange={set("material")}>{MATERIALS.map((m) => <option key={m}>{m}</option>)}</select>
          </label>
          <label><span>Supplier</span><input value={form.supplierName} onChange={set("supplierName")} /></label>
          <label><span>Gross kg (loaded)</span><input type="number" value={form.grossKg} onChange={set("grossKg")} required /></label>
          <label><span>Tare kg (empty)</span><input type="number" value={form.tareKg} onChange={set("tareKg")} required /></label>
          {preview != null && (typeof preview === "number" ? <p><strong>Net {kg(preview)}</strong></p> : <p className="error">{preview}</p>)}
          <button type="submit" disabled={typeof preview !== "number"}>Save slip</button>
          {last && (
            <p>
              Saved {last.slip.slipNumber} · {last.slip.vehicleNumber} · net {kg(last.netKg)}
              <br />
              <button type="button" className="ghost" onClick={() => openFile(`/api/weighbridge/desk/slips/${last.slip.id}/slip.pdf`)}>Print slip</button>
            </p>
          )}
          {error && <p className="error">{error}</p>}
        </form>
        <aside className="card pay">
          <h2>Today: {data?.slips ?? 0} slip(s)</h2>
          {data && Object.entries(data.netKgByMaterial).map(([m, n]) => <p key={m}>{m.toLowerCase()}: {kg(n)}</p>)}
          {data && <p><strong>Total net {kg(data.totalNetKg)}</strong></p>}
        </aside>
      </div>
      <p className="muted">Weights are typed in. A link to the bridge's indicator is not part of this demo.</p>
    </section>
  );
}
