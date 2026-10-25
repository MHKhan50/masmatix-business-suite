import { useEffect, useState, type FormEvent } from "react";
import { api } from "../lib/api";
import { cleanImei, imeiValid } from "../lib/shops";

interface Handset { id: number; imei: string; brand: string; model: string; soldOn: string | null; warrantyMonths: number | null; customerPhone: string | null }
interface Claim { id: number; imei: string; fault: string; receivedOn: string; status: string }
interface Lookup { imei: string; checkDigitValid: boolean; handset: Handset | null; warrantyEndsOn: string | null; inWarranty: boolean; daysLeft: number; claims: Claim[] }
interface Ending { handset: Handset; warrantyEndsOn: string; daysLeft: number }

export default function ImeiDesk() {
  const [imei, setImei] = useState("");
  const [found, setFound] = useState<Lookup | null>(null);
  const [fault, setFault] = useState("");
  const [ending, setEnding] = useState<Ending[]>([]);
  const [message, setMessage] = useState("");

  useEffect(() => { api<Ending[]>("/api/imei/warranty-ending?days=30").then(setEnding); }, []);

  const digits = cleanImei(imei);
  const hint = digits.length === 0 ? "" : digits.length !== 15 ? `${digits.length} of 15 digits`
    : imeiValid(digits) ? "Check digit OK" : "Check digit wrong: the number was probably mistyped";

  async function look(ev: FormEvent) {
    ev.preventDefault();
    try {
      setFound(await api<Lookup>(`/api/imei/lookup/${digits}`));
      setMessage("");
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function openClaim(ev: FormEvent) {
    ev.preventDefault();
    try {
      await api<Claim>("/api/imei/claims/open", { method: "POST", body: JSON.stringify({ imei: digits, fault }) });
      setFault("");
      setMessage("");
      setFound(await api<Lookup>(`/api/imei/lookup/${digits}`));
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <h1>IMEI and warranty desk</h1>
      <div className="till">
        <div>
          <form onSubmit={look} className="scan">
            <input autoFocus placeholder="Type or scan the 15-digit IMEI (*#06# on the phone). Sample: 000000000000018"
              value={imei} onChange={(e) => setImei(e.target.value)} />
            <button type="submit" disabled={!imeiValid(digits)}>Look up</button>
          </form>
          <p className={digits.length === 15 && !imeiValid(digits) ? "error" : "muted"}>{hint}</p>
          {found && !found.handset && <p className="card">Not sold by this shop.</p>}
          {found?.handset && (
            <>
              <div className={`card gate ${found.inWarranty ? "ok" : "stop"}`}>
                <strong>{found.inWarranty ? "IN WARRANTY" : "NO WARRANTY"}</strong>
                <p>{found.handset.brand} {found.handset.model} · sold {found.handset.soldOn ?? "?"}
                  {found.warrantyEndsOn ? ` · cover to ${found.warrantyEndsOn}` : ""}
                  {found.inWarranty ? ` (${found.daysLeft} days left)` : ""}</p>
              </div>
              {found.inWarranty && (
                <form onSubmit={openClaim} className="stack">
                  <label><span>Fault reported by customer</span>
                    <textarea rows={2} value={fault} onChange={(e) => setFault(e.target.value)} required />
                  </label>
                  <button type="submit">Open warranty claim</button>
                </form>
              )}
              <h2>Claims</h2>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Received</th><th>Fault</th><th>Status</th></tr></thead>
                  <tbody>
                    {found.claims.map((c) => <tr key={c.id}><td>{c.receivedOn}</td><td>{c.fault}</td><td>{c.status}</td></tr>)}
                  </tbody>
                </table>
              </div>
            </>
          )}
          {message && <p className="error">{message}</p>}
        </div>
        <aside className="card pay">
          <h2>Cover ending in 30 days</h2>
          {ending.length === 0 && <p className="muted">None</p>}
          {ending.map((e) => (
            <p key={e.handset.id}>{e.handset.model} · {e.warrantyEndsOn}<br />
              <span className="muted">{e.handset.customerPhone ?? "no phone"}</span></p>
          ))}
        </aside>
      </div>
    </section>
  );
}
