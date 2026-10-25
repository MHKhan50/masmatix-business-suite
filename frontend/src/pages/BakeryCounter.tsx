import { useEffect, useState, type FormEvent } from "react";
import { api, type Page, type Session } from "../lib/api";
import { money } from "../lib/cart";

interface Item { id: number; name: string; price: number; shelfLifeDays: number }
interface Batch { id: number; batchCode: string; itemName: string; quantity: number; expiresOn: string }
interface Row { batch: Batch; status: "EXPIRED" | "LAST_DAY" | "TOMORROW" | "OK" }
interface Sold { itemName: string; quantity: number; total: number; fromBatches: { batchCode: string; quantity: number }[] }

const label: Record<Row["status"], string> = { EXPIRED: "Expired", LAST_DAY: "Last day", TOMORROW: "Ends tomorrow", OK: "OK" };

export default function BakeryCounter({ session }: { session: Session }) {
  const [items, setItems] = useState<Item[]>([]);
  const [itemId, setItemId] = useState("");
  const [qty, setQty] = useState("1");
  const [rows, setRows] = useState<Row[]>([]);
  const [sold, setSold] = useState<Sold | null>(null);
  const [message, setMessage] = useState("");
  const canWriteOff = session.role !== "STAFF";

  const refresh = () => api<Row[]>("/api/bakery/expiry").then(setRows);
  useEffect(() => {
    api<Page<Item>>("/api/bakery/items?size=200").then((p) => setItems(p.content));
    refresh();
  }, []);

  const item = items.find((i) => String(i.id) === itemId);

  async function post<T>(url: string, body?: unknown): Promise<T | null> {
    try {
      const r = await api<T>(url, { method: "POST", body: body ? JSON.stringify(body) : undefined });
      setMessage("");
      refresh();
      return r;
    } catch (e) {
      setMessage((e as Error).message);
      return null;
    }
  }

  async function sell(ev: FormEvent) {
    ev.preventDefault();
    setSold(await post<Sold>("/api/bakery/sell", { itemId: Number(itemId), quantity: Number(qty) }));
  }

  async function bake() {
    const b = await post<Batch>("/api/bakery/bake", { itemId: Number(itemId), quantity: Number(qty) });
    if (b) setMessage(`Batch ${b.batchCode}: ${b.quantity} x ${b.itemName}, sell by ${b.expiresOn}`);
  }

  return (
    <section>
      <h1>Bakery counter</h1>
      <div className="till">
        <div>
          <form onSubmit={sell} className="card form">
            <label><span>Item</span>
              <select value={itemId} onChange={(e) => setItemId(e.target.value)} required>
                <option value="">Choose</option>
                {items.map((i) => <option key={i.id} value={i.id}>{i.name} · {money(i.price)}</option>)}
              </select>
            </label>
            <label><span>Quantity</span><input type="number" min={1} value={qty} onChange={(e) => setQty(e.target.value)} /></label>
            <div className="actions">
              <button type="submit" disabled={!item}>Sell {item ? money(item.price * Number(qty || 0)) : ""}</button>
              <button type="button" className="ghost" disabled={!item} onClick={bake}>Record new batch</button>
            </div>
          </form>
          {sold && (
            <p className="card">
              Sold {sold.quantity} x {sold.itemName} for {money(sold.total)} from{" "}
              {sold.fromBatches.map((b) => `${b.batchCode} (${b.quantity})`).join(", ")}
            </p>
          )}
          {message && <p className={message.startsWith("Batch") ? "muted" : "error"}>{message}</p>}
          <p className="muted">Sales take the batch that expires first. Expired batches are never sold.</p>
        </div>
        <aside className="card pay">
          <h2>Expiry alerts</h2>
          {rows.length === 0 && <p className="muted">Nothing expired or ending soon</p>}
          {rows.map((r) => (
            <div key={r.batch.id} className={`slot ${r.status === "EXPIRED" ? "cancelled" : ""}`}>
              <strong>{label[r.status]}</strong> · {r.batch.itemName}<br />
              <span className="muted">{r.batch.batchCode} · {r.batch.quantity} left · {r.batch.expiresOn}</span>
              {canWriteOff && (
                <button className="link danger" onClick={() => post(`/api/bakery/batches/${r.batch.id}/write-off`)}>Write off</button>
              )}
            </div>
          ))}
        </aside>
      </div>
    </section>
  );
}
