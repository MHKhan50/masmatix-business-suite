import { useState } from "react";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Student { id: number; fullName: string; batchName: string; phone: string | null; feePaidUntil: string }
interface Overdue { student: Student; monthsOwed: number; amountOwed: number | null }
interface Receipt { receiptNumber: string; student: string; batch: string; months: number; total: number; paidFrom: string; paidUntil: string }

export default function CoachingFees() {
  const { data, error, act } = useDesk<Overdue[]>("/api/coaching/fees/overdue");
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  async function pay(id: number, months: number) {
    setReceipt(await act<Receipt>(`/api/coaching/fees/students/${id}/pay`, { months }));
  }

  return (
    <section>
      <h1>Coaching fees</h1>
      <div className="till">
        <div className="card">
          <h2>Fee overdue</h2>
          {data?.length === 0 && <p className="muted">Every student is paid up</p>}
          <div className="table-wrap">
            <table>
              <thead><tr><th>Student</th><th>Batch</th><th>Paid until</th><th>Months owed</th><th>Owed</th><th /></tr></thead>
              <tbody>
                {data?.map((r) => (
                  <tr key={r.student.id}>
                    <td>{r.student.fullName}<br /><span className="muted">{r.student.phone}</span></td>
                    <td>{r.student.batchName}</td>
                    <td>{r.student.feePaidUntil}</td>
                    <td>{r.monthsOwed}</td>
                    <td>{r.amountOwed == null ? "-" : money(r.amountOwed)}</td>
                    <td><button className="link" onClick={() => pay(r.student.id, r.monthsOwed)}>Take {r.monthsOwed} month(s)</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {error && <p className="error">{error}</p>}
          <p className="muted">A missed month is still owed, so a payment extends from the old paid-until date.</p>
        </div>
        {receipt && (
          <aside className="card pay">
            <h2>Fee receipt {receipt.receiptNumber}</h2>
            <p>{receipt.student} · {receipt.batch}</p>
            <p>{receipt.months} month(s): {receipt.paidFrom} to {receipt.paidUntil}</p>
            <p><strong>{money(receipt.total)}</strong></p>
            <button className="ghost" onClick={() => window.print()}>Print</button>
          </aside>
        )}
      </div>
    </section>
  );
}
