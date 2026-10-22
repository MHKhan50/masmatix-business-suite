import { useEffect, useState } from "react";
import { api, openFile, type Page, type Session } from "../lib/api";
import { money } from "../lib/cart";

interface Sale {
  id: number;
  saleNumber: string;
  soldAt: string;
  cashier: string;
  paymentMethod: string;
  total: number;
}

interface Daily {
  date: string;
  sales: number;
  subtotal: number;
  tax: number;
  total: number;
  cash: number;
  card: number;
}

export default function Sales({ session }: { session: Session }) {
  const [sales, setSales] = useState<Sale[]>([]);
  const [daily, setDaily] = useState<Daily | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Page<Sale>>("/api/pos/sales").then((p) => setSales(p.content)).catch((e: Error) => setError(e.message));
    if (session.role !== "STAFF") {
      api<Daily>("/api/pos/reports/daily").then(setDaily).catch((e: Error) => setError(e.message));
    }
  }, [session.role]);

  return (
    <section>
      <h1>Sales</h1>
      {error && <p className="error">{error}</p>}
      {daily && (
        <div className="stats">
          <div><span>Sales today</span><strong>{daily.sales}</strong></div>
          <div><span>Takings</span><strong>{money(daily.total)}</strong></div>
          <div><span>Tax</span><strong>{money(daily.tax)}</strong></div>
          <div><span>Cash / card</span><strong>{money(daily.cash)} / {money(daily.card)}</strong></div>
        </div>
      )}
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Number</th><th>Time</th><th>Cashier</th><th>Paid by</th><th>Total</th><th /></tr>
          </thead>
          <tbody>
            {sales.map((s) => (
              <tr key={s.id}>
                <td>{s.saleNumber}</td>
                <td>{new Date(s.soldAt).toLocaleString("en-GB")}</td>
                <td>{s.cashier}</td>
                <td>{s.paymentMethod}</td>
                <td>{money(s.total)}</td>
                <td><button className="link" onClick={() => openFile(`/api/pos/sales/${s.id}/receipt.pdf`)}>Receipt</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
