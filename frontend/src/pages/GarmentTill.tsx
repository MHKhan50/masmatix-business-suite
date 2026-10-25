import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, openFile } from "../lib/api";
import { money } from "../lib/cart";
import { EXCHANGE_WINDOW_DAYS, exchangeDifference } from "../lib/shops";

interface Garment { id: number; styleCode: string; name: string; sizeLabel: string | null; colour: string | null; price: number; stock: number | null }
interface Line { item: Garment; qty: number }
interface SaleLine { styleCode: string; name: string; sizeLabel: string | null; unitPrice: number; qty: number; exchangedQty: number }
interface Sale { id: number; receiptNumber: string; total: number; changeDue: number | null; soldOn: string; lines: SaleLine[] }
interface ExchangeResult { returnedName: string; newName: string; difference: number; returnedToStock: boolean }

const REASONS = ["SIZE", "COLOUR", "FAULT", "OTHER"] as const;

export default function GarmentTill() {
  const [code, setCode] = useState("");
  const [cart, setCart] = useState<Line[]>([]);
  const [method, setMethod] = useState<"CASH" | "CARD">("CASH");
  const [tendered, setTendered] = useState("");
  const [sale, setSale] = useState<Sale | null>(null);
  const [lowStock, setLowStock] = useState<Garment[]>([]);
  const [message, setMessage] = useState("");
  const [receiptNo, setReceiptNo] = useState("");
  const [found, setFound] = useState<Sale | null>(null);
  const [returned, setReturned] = useState("");
  const [newItem, setNewItem] = useState<Garment | null>(null);
  const [newCode, setNewCode] = useState("");
  const [reason, setReason] = useState<(typeof REASONS)[number]>("SIZE");
  const [swap, setSwap] = useState<ExchangeResult | null>(null);
  const codeRef = useRef<HTMLInputElement>(null);

  const refresh = () => api<Garment[]>("/api/garment/low-stock?max=3").then(setLowStock);
  useEffect(() => { refresh(); }, []);

  const total = Math.round(cart.reduce((t, l) => t + l.item.price * 100 * l.qty, 0)) / 100;

  async function scan(ev: FormEvent) {
    ev.preventDefault();
    try {
      const g = await api<Garment>(`/api/garment/lookup/${encodeURIComponent(code.trim())}`);
      const at = cart.findIndex((l) => l.item.id === g.id);
      const have = at >= 0 ? cart[at].qty : 0;
      if (have + 1 > (g.stock ?? 0)) {
        setMessage(`Only ${g.stock ?? 0} in stock`);
      } else {
        setMessage("");
        setCart(at >= 0 ? cart.map((l, i) => (i === at ? { ...l, qty: l.qty + 1 } : l)) : [...cart, { item: g, qty: 1 }]);
      }
    } catch (e) {
      setMessage((e as Error).message);
    }
    setCode("");
    codeRef.current?.focus();
  }

  async function pay() {
    try {
      const s = await api<Sale>("/api/garment/sales", {
        method: "POST",
        body: JSON.stringify({
          items: cart.map((l) => ({ styleCode: l.item.styleCode, qty: l.qty })),
          paymentMethod: method,
          amountTendered: method === "CASH" ? Number(tendered) : null,
        }),
      });
      setSale(s);
      setCart([]);
      setTendered("");
      setMessage("");
      refresh();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function findReceipt(ev: FormEvent) {
    ev.preventDefault();
    setSwap(null);
    try {
      const s = await api<Sale>(`/api/garment/sales/by-receipt/${encodeURIComponent(receiptNo.trim())}`);
      setFound(s);
      setReturned(s.lines.find((l) => l.qty > l.exchangedQty)?.styleCode ?? "");
      setMessage("");
    } catch (e) {
      setFound(null);
      setMessage((e as Error).message);
    }
  }

  async function lookNew(c: string) {
    setNewCode(c);
    setNewItem(null);
    if (c.trim().length < 3) return;
    try {
      setNewItem(await api<Garment>(`/api/garment/lookup/${encodeURIComponent(c.trim())}`));
    } catch {
      /* not a style code yet */
    }
  }

  async function exchange(ev: FormEvent) {
    ev.preventDefault();
    if (!found) return;
    try {
      setSwap(await api<ExchangeResult>("/api/garment/exchange", {
        method: "POST",
        body: JSON.stringify({ receiptNumber: found.receiptNumber, returnedStyleCode: returned, newStyleCode: newCode, reason }),
      }));
      setFound(null);
      setReceiptNo("");
      setNewCode("");
      setNewItem(null);
      setMessage("");
      refresh();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  const paidLine = found?.lines.find((l) => l.styleCode === returned);
  const preview = paidLine && newItem ? exchangeDifference(paidLine.unitPrice, newItem.price) : null;

  return (
    <section>
      <h1>Garment till</h1>
      <div className="till">
        <div>
          <form onSubmit={scan} className="scan">
            <input ref={codeRef} autoFocus placeholder="Scan or type style code, then Enter (sample: KRT-101-M-BLU)"
              value={code} onChange={(e) => setCode(e.target.value)} />
            <button type="submit">Add</button>
          </form>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Item</th><th>Size</th><th>Qty</th><th>Price</th><th /></tr></thead>
              <tbody>
                {cart.map((l) => (
                  <tr key={l.item.id}>
                    <td>{l.item.name} {l.item.colour ?? ""}</td>
                    <td>{l.item.sizeLabel}</td>
                    <td>{l.qty}</td>
                    <td>{money(l.item.price * l.qty)}</td>
                    <td className="row-actions">
                      <button className="link danger" onClick={() => setCart((c) => c.filter((x) => x !== l))}>Remove</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2>Exchange (within {EXCHANGE_WINDOW_DAYS} days)</h2>
          <form onSubmit={findReceipt} className="scan">
            <input placeholder="Receipt number, e.g. G20261025-00001" value={receiptNo} onChange={(e) => setReceiptNo(e.target.value)} />
            <button type="submit" className="ghost">Find</button>
          </form>
          {found && (
            <form onSubmit={exchange} className="card form">
              <label><span>Item coming back</span>
                <select value={returned} onChange={(e) => setReturned(e.target.value)} required>
                  {found.lines.filter((l) => l.qty > l.exchangedQty).map((l) => (
                    <option key={l.styleCode} value={l.styleCode}>{l.name} {l.sizeLabel} · {money(l.unitPrice)}</option>
                  ))}
                </select>
              </label>
              <label><span>New style code</span><input value={newCode} onChange={(e) => lookNew(e.target.value)} required /></label>
              <label><span>Reason</span>
                <select value={reason} onChange={(e) => setReason(e.target.value as (typeof REASONS)[number])}>
                  {REASONS.map((r) => <option key={r}>{r}</option>)}
                </select>
              </label>
              <p className="muted">
                {preview === null ? "Enter the new style code" : preview > 0 ? `Customer pays ${money(preview)}`
                  : preview < 0 ? `Refund ${money(-preview)}` : "Even swap"}
                {reason === "FAULT" ? " · faulty item will not go back on the shelf" : ""}
              </p>
              <button type="submit" disabled={!returned || !newItem}>Exchange</button>
            </form>
          )}
          {swap && (
            <p className="card">
              {swap.returnedName} swapped for {swap.newName}.{" "}
              {swap.difference > 0 ? `Take ${money(swap.difference)}.` : swap.difference < 0 ? `Refund ${money(-swap.difference)}.` : "No money changes hands."}
            </p>
          )}
        </div>

        <aside className="card pay">
          <dl><dt className="big">Total</dt><dd className="big">{money(total)}</dd></dl>
          <p className="muted">Prices include VAT</p>
          <div className="seg">
            {(["CASH", "CARD"] as const).map((m) => (
              <button type="button" key={m} className={method === m ? "" : "ghost"} onClick={() => setMethod(m)}>{m}</button>
            ))}
          </div>
          {method === "CASH" && (
            <label><span>Cash tendered</span><input type="number" step="0.01" value={tendered} onChange={(e) => setTendered(e.target.value)} /></label>
          )}
          <button onClick={pay} disabled={cart.length === 0 || (method === "CASH" && Number(tendered) < total)}>Take payment</button>
          {sale && (
            <div className="done">
              <p>{sale.receiptNumber}: {money(sale.total)}{sale.changeDue != null ? `, change ${money(sale.changeDue)}` : ""}</p>
              <button className="ghost" onClick={() => openFile(`/api/garment/sales/${sale.id}/receipt.pdf`)}>Print receipt</button>
            </div>
          )}
          {message && <p className="error">{message}</p>}
          <h2>Low stock (3 or fewer)</h2>
          {lowStock.length === 0 && <p className="muted">None</p>}
          {lowStock.map((g) => <p key={g.id}>{g.styleCode} · {g.stock ?? 0} left</p>)}
        </aside>
      </div>
    </section>
  );
}
