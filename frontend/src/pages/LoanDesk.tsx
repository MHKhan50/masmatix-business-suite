import { useEffect, useState } from "react";
import { api, type Page } from "../lib/api";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Loan { id: number; loanNumber: string; borrowerName: string; status: string | null }
interface Row { number: number; dueOn: string; amount: number; paidTowards: number; status: string }
interface Statement { loan: Loan; schedule: Row[]; totalRepayable: number; paid: number; dueSoFar: number; arrears: number; remaining: number }

export default function LoanDesk() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [loanId, setLoanId] = useState("");
  const [amount, setAmount] = useState("");
  useEffect(() => {
    api<Page<Loan>>("/api/microfinance/loans?size=200").then((p) => setLoans(p.content));
  }, []);
  const { data: s, error, act } = useDesk<Statement>(loanId ? `/api/microfinance/desk/loans/${loanId}/statement` : null);

  return (
    <section>
      <h1>Loan desk</h1>
      <div className="card form">
        <label><span>Loan</span>
          <select value={loanId} onChange={(e) => setLoanId(e.target.value)}>
            <option value="">Choose</option>
            {loans.map((l) => <option key={l.id} value={l.id}>{l.loanNumber} · {l.borrowerName}</option>)}
          </select>
        </label>
      </div>
      {s && (
        <div className="till">
          <div className="card table-wrap">
            <table>
              <thead><tr><th>#</th><th>Due</th><th>Instalment</th><th>Paid towards</th><th>Status</th></tr></thead>
              <tbody>
                {s.schedule.map((r) => (
                  <tr key={r.number}><td>{r.number}</td><td>{r.dueOn}</td><td>{money(r.amount)}</td><td>{money(r.paidTowards)}</td><td>{r.status.replace("_", " ").toLowerCase()}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
          <aside className="card pay">
            <p>Status: {s.loan.status ?? "ACTIVE"}</p>
            <p>Repayable {money(s.totalRepayable)} · paid {money(s.paid)}</p>
            <p>Due so far {money(s.dueSoFar)}</p>
            <p className={s.arrears > 0 ? "error" : ""}>Arrears {money(s.arrears)}</p>
            <p><strong>Left {money(s.remaining)}</strong></p>
            <label><span>Repayment</span><input type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></label>
            <button disabled={!amount} onClick={() => act(`/api/microfinance/desk/loans/${loanId}/repay`, { amount: Number(amount) })}>Take repayment</button>
            <p className="muted">With arrears, a late fee of 2% of the arrears is recorded on the repayment (sample default).</p>
          </aside>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
