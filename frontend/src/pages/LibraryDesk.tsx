import { useState, type FormEvent } from "react";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Loan { id: number; accessionNumber: string; borrower: string; dueOn: string; fine: number | null }
interface Overdue { loan: Loan; title: string; fineSoFar: number }

export default function LibraryDesk() {
  const { data, error, act } = useDesk<Overdue[]>("/api/library/desk/overdue");
  const [accession, setAccession] = useState("");
  const [borrower, setBorrower] = useState("");
  const [loanId, setLoanId] = useState("");
  const [note, setNote] = useState("");

  async function issue(ev: FormEvent) {
    ev.preventDefault();
    const l = await act<Loan>("/api/library/desk/issue", { accessionNumber: accession, borrower });
    if (l) setNote(`Loan ${l.id}: ${l.accessionNumber} to ${l.borrower}, due ${l.dueOn}`);
  }

  async function giveBack(id: string) {
    const l = await act<Loan>(`/api/library/desk/loans/${id}/return`);
    if (l) setNote(`Returned ${l.accessionNumber}. Fine ${money(l.fine ?? 0)}`);
  }

  return (
    <section>
      <h1>Library desk</h1>
      <div className="till">
        <div>
          <form className="card form" onSubmit={issue}>
            <h2>Issue</h2>
            <label><span>Accession number</span><input value={accession} onChange={(e) => setAccession(e.target.value)} required /></label>
            <label><span>Borrower</span><input value={borrower} onChange={(e) => setBorrower(e.target.value)} required /></label>
            <button type="submit">Issue for 14 days</button>
          </form>
          <div className="card form">
            <h2>Return</h2>
            <label><span>Loan number</span><input value={loanId} onChange={(e) => setLoanId(e.target.value)} /></label>
            <button disabled={!loanId} onClick={() => giveBack(loanId)}>Return and work out fine</button>
          </div>
          {note && <p className="muted">{note}</p>}
          {error && <p className="error">{error}</p>}
          <p className="muted">Fine 0.20 a day after the due date, at most 10.00 a loan.</p>
        </div>
        <aside className="card pay">
          <h2>Overdue</h2>
          {data?.length === 0 && <p className="muted">Nothing overdue</p>}
          {data?.map((r) => (
            <div key={r.loan.id} className="slot">
              <strong>{r.title}</strong> · {r.loan.borrower}<br />
              <span className="muted">Loan {r.loan.id} · due {r.loan.dueOn} · fine so far {money(r.fineSoFar)}</span>
              <button className="link" onClick={() => giveBack(String(r.loan.id))}>Return</button>
            </div>
          ))}
        </aside>
      </div>
    </section>
  );
}
