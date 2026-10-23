import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, openFile, type Page } from "../lib/api";
import { money } from "../lib/cart";
import { isoDate, renewedExpiry } from "../lib/rules";

interface Result { allowed: boolean; memberCode: string; memberName: string | null; expiresOn: string | null; daysLeft: number; reason: string }
interface Member { id: number; memberCode: string; fullName: string; expiresOn: string | null; frozen: boolean }
interface Plan { id: number; name: string; months: number; fee: number }
interface Payment { id: number; memberName: string; newExpiry: string; amount: number }
interface Entry { id: number; memberCode: string; memberName: string | null; checkedAt: string; allowed: boolean; reason: string }

export default function GymDesk() {
  const [code, setCode] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [expiring, setExpiring] = useState<Member[]>([]);
  const [log, setLog] = useState<Entry[]>([]);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [method, setMethod] = useState<"CASH" | "CARD">("CASH");
  const [paid, setPaid] = useState<Payment | null>(null);
  const [message, setMessage] = useState("");
  const codeRef = useRef<HTMLInputElement>(null);
  const today = isoDate(new Date());

  const refresh = () => {
    api<Page<Member>>("/api/gym/members?size=200").then((p) => setMembers(p.content));
    api<Member[]>("/api/gym/expiring?days=7").then(setExpiring);
    api<Entry[]>("/api/gym/check-ins").then(setLog);
  };
  useEffect(() => {
    api<Page<Plan>>("/api/gym/plans").then((p) => setPlans(p.content));
    refresh();
  }, []);

  async function check(ev: FormEvent) {
    ev.preventDefault();
    try {
      setResult(await api<Result>("/api/gym/check-in", { method: "POST", body: JSON.stringify({ memberCode: code }) }));
      setCode("");
      refresh();
    } catch (e) {
      setMessage((e as Error).message);
    }
    codeRef.current?.focus();
  }

  const member = members.find((m) => String(m.id) === memberId);
  const plan = plans.find((p) => String(p.id) === planId);

  async function renew(ev: FormEvent) {
    ev.preventDefault();
    try {
      const p = await api<Payment>(`/api/gym/members/${memberId}/renew`, {
        method: "POST",
        body: JSON.stringify({ planId: Number(planId), method }),
      });
      setPaid(p);
      setMessage("");
      refresh();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <h1>Gym front desk</h1>
      <div className="till">
        <div>
          <form onSubmit={check} className="scan">
            <input ref={codeRef} autoFocus placeholder="Scan or type member code, then Enter (sample: GYM-0001)"
              value={code} onChange={(e) => setCode(e.target.value)} />
            <button type="submit">Check in</button>
          </form>
          {result && (
            <div className={`card gate ${result.allowed ? "ok" : "stop"}`}>
              <strong>{result.allowed ? "ENTRY OK" : "ENTRY BLOCKED"}</strong>
              <p>{result.memberName ?? result.memberCode} · {result.reason}
                {result.allowed && result.daysLeft > 0 ? ` · ${result.daysLeft} days left` : ""}</p>
            </div>
          )}
          <h2>Today</h2>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Time</th><th>Member</th><th>Result</th></tr></thead>
              <tbody>
                {log.map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.checkedAt).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}</td>
                    <td>{e.memberName ?? e.memberCode}</td>
                    <td className={e.allowed ? "" : "error"}>{e.allowed ? "in" : e.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <aside className="card pay">
          <h2>Take fees</h2>
          <form onSubmit={renew} className="stack">
            <label><span>Member</span>
              <select value={memberId} onChange={(e) => setMemberId(e.target.value)} required>
                <option value="">Choose</option>
                {members.map((m) => <option key={m.id} value={m.id}>{m.memberCode} {m.fullName}</option>)}
              </select>
            </label>
            <label><span>Plan</span>
              <select value={planId} onChange={(e) => setPlanId(e.target.value)} required>
                <option value="">Choose</option>
                {plans.map((p) => <option key={p.id} value={p.id}>{p.name} · {money(p.fee)}</option>)}
              </select>
            </label>
            <div className="seg">
              {(["CASH", "CARD"] as const).map((m) => (
                <button type="button" key={m} className={method === m ? "" : "ghost"} onClick={() => setMethod(m)}>{m}</button>
              ))}
            </div>
            {member && plan && (
              <p className="muted">
                Expiry {member.expiresOn ?? "none"} → {renewedExpiry(member.expiresOn, today, plan.months)}
              </p>
            )}
            <button type="submit" disabled={!member || !plan}>Take {plan ? money(plan.fee) : "payment"}</button>
          </form>
          {paid && (
            <div className="done">
              <p>{paid.memberName}: paid {money(paid.amount)}, valid to {paid.newExpiry}</p>
              <button className="ghost" onClick={() => openFile(`/api/gym/payments/${paid.id}/receipt.pdf`)}>Print receipt</button>
            </div>
          )}
          {message && <p className="error">{message}</p>}
          <h2>Ending in 7 days</h2>
          {expiring.length === 0 && <p className="muted">None</p>}
          {expiring.map((m) => <p key={m.id}>{m.fullName} · {m.expiresOn}</p>)}
        </aside>
      </div>
    </section>
  );
}
