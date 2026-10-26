import { useState, type FormEvent } from "react";
import { api } from "../lib/api";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Entry { id: number; employeeCode: string; minutesLate: number; fineAmount: number }
interface Payslip {
  employee: { fullName: string; employeeCode: string }; month: string; daysPresent: number; minutesLate: number;
  fines: number; overtimeMinutes: number; overtimePay: number; salary: number; net: number;
}

export default function PayrollDesk() {
  const { error, setError, act } = useDesk<null>(null);
  const [code, setCode] = useState("");
  const [time, setTime] = useState("09:00");
  const [entry, setEntry] = useState<Entry | null>(null);
  const [ot, setOt] = useState("");
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const [slip, setSlip] = useState<Payslip | null>(null);

  async function checkIn(ev: FormEvent) {
    ev.preventDefault();
    setEntry(await act<Entry>("/api/payroll/desk/check-in", { employeeCode: code, checkIn: time }));
  }

  async function payslip() {
    try {
      setSlip(await api<Payslip>(`/api/payroll/desk/payslip?employeeCode=${encodeURIComponent(code)}&month=${month}`));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <section>
      <h1>Attendance and pay</h1>
      <div className="till">
        <div>
          <form className="card form" onSubmit={checkIn}>
            <label><span>Employee code</span><input value={code} onChange={(e) => setCode(e.target.value)} required /></label>
            <label><span>Check-in</span><input type="time" value={time} onChange={(e) => setTime(e.target.value)} /></label>
            <button type="submit">Check in</button>
            {entry && <p>{entry.employeeCode}: {entry.minutesLate} min late · fine {money(entry.fineAmount)}</p>}
            {entry && (
              <div className="actions">
                <input type="number" placeholder="overtime minutes" value={ot} onChange={(e) => setOt(e.target.value)} />
                <button type="button" className="ghost" onClick={() => act(`/api/payroll/desk/attendance/${entry.id}/overtime`, { minutes: Number(ot) })}>Save overtime</button>
              </div>
            )}
          </form>
          {error && <p className="error">{error}</p>}
          <p className="muted">Sample rules: 10 minutes' grace; after that every late minute is fined at the per-minute rate (salary / 26 days / 8 hours), overtime at 1.5 times. Check local employment law before use.</p>
        </div>
        <aside className="card pay">
          <h2>Pay summary</h2>
          <label><span>Month</span><input type="month" value={month} onChange={(e) => setMonth(e.target.value)} /></label>
          <button disabled={!code} onClick={payslip}>Show for {code || "employee"}</button>
          {slip && (
            <>
              <p>{slip.employee.fullName} · {slip.month} · {slip.daysPresent} day(s)</p>
              <p>Salary {money(slip.salary)}</p>
              <p>Late fines ({slip.minutesLate} min) − {money(slip.fines)}</p>
              <p>Overtime ({slip.overtimeMinutes} min) + {money(slip.overtimePay)}</p>
              <p><strong>Net {money(slip.net)}</strong></p>
            </>
          )}
        </aside>
      </div>
    </section>
  );
}
