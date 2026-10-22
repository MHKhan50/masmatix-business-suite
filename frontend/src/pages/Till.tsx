import { useMemo, useRef, useState, type FormEvent } from "react";
import { api, openFile } from "../lib/api";
import { addToCart, cartTotals, lineTotals, money, setQty, type CartLine } from "../lib/cart";

interface Product {
  barcode: string;
  name: string;
  unitPrice: number;
  taxRate: number;
  stockQty: number;
}

interface Sale {
  id: number;
  saleNumber: string;
  total: number;
  changeDue: number | null;
}

export default function Till() {
  const [cart, setCart] = useState<CartLine[]>([]);
  const [code, setCode] = useState("");
  const [method, setMethod] = useState<"CASH" | "CARD">("CASH");
  const [tendered, setTendered] = useState("");
  const [message, setMessage] = useState("");
  const [lastSale, setLastSale] = useState<Sale | null>(null);
  const scanRef = useRef<HTMLInputElement>(null);
  const totals = useMemo(() => cartTotals(cart), [cart]);

  // A USB barcode scanner acts like a keyboard: it types the digits and presses Enter.
  async function scan(ev: FormEvent) {
    ev.preventDefault();
    const barcode = code.trim();
    setCode("");
    if (!barcode) return;
    try {
      const p = await api<Product>(`/api/pos/products/barcode/${encodeURIComponent(barcode)}`);
      if (p.stockQty <= 0) setMessage(`${p.name} is out of stock`);
      else setMessage("");
      setCart((c) => addToCart(c, p));
    } catch (e) {
      setMessage((e as Error).message);
    }
    scanRef.current?.focus();
  }

  async function checkout() {
    try {
      const sale = await api<Sale>("/api/pos/sales", {
        method: "POST",
        body: JSON.stringify({
          paymentMethod: method,
          amountTendered: method === "CASH" ? Number(tendered) : null,
          items: cart.map((l) => ({ barcode: l.barcode, qty: l.qty })),
        }),
      });
      setLastSale(sale);
      setCart([]);
      setTendered("");
      setMessage("");
    } catch (e) {
      setMessage((e as Error).message);
    }
    scanRef.current?.focus();
  }

  const cashShort = method === "CASH" && (tendered === "" || Number(tendered) < totals.total);

  return (
    <section className="till">
      <div>
        <h1>Till</h1>
        <form onSubmit={scan} className="scan">
          <input
            ref={scanRef}
            autoFocus
            inputMode="numeric"
            placeholder="Scan or type a barcode, then Enter"
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
          <button type="submit">Add</button>
        </form>
        {message && <p className="error">{message}</p>}
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Item</th><th>Qty</th><th>Price</th><th>Line</th></tr>
            </thead>
            <tbody>
              {cart.map((l) => (
                <tr key={l.barcode}>
                  <td>{l.name}</td>
                  <td>
                    <input
                      className="qty"
                      type="number"
                      min={0}
                      max={l.stockQty}
                      value={l.qty}
                      onChange={(e) => setCart((c) => setQty(c, l.barcode, Number(e.target.value)))}
                    />
                  </td>
                  <td>{money(l.unitPrice)}{l.taxRate > 0 ? ` +${l.taxRate}%` : ""}</td>
                  <td>{money(lineTotals(l).net / 100)}</td>
                </tr>
              ))}
              {cart.length === 0 && (
                <tr><td colSpan={4} className="muted">Cart is empty. Sample barcode: 2000000000084</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <aside className="card pay">
        <dl>
          <dt>Subtotal</dt><dd>{money(totals.subtotal)}</dd>
          <dt>Tax</dt><dd>{money(totals.tax)}</dd>
          <dt className="big">Total</dt><dd className="big">{money(totals.total)}</dd>
        </dl>
        <div className="seg">
          {(["CASH", "CARD"] as const).map((m) => (
            <button key={m} className={method === m ? "" : "ghost"} onClick={() => setMethod(m)}>{m}</button>
          ))}
        </div>
        {method === "CASH" && (
          <label>
            <span>Cash tendered</span>
            <input type="number" step="0.01" value={tendered} onChange={(e) => setTendered(e.target.value)} />
          </label>
        )}
        <button disabled={cart.length === 0 || cashShort} onClick={checkout}>Complete sale</button>
        {lastSale && (
          <div className="done">
            <p>
              {lastSale.saleNumber}: {money(lastSale.total)}
              {lastSale.changeDue !== null && <> · change {money(lastSale.changeDue)}</>}
            </p>
            <button className="ghost" onClick={() => openFile(`/api/pos/sales/${lastSale.id}/receipt.pdf`)}>
              Print receipt
            </button>
          </div>
        )}
      </aside>
    </section>
  );
}
