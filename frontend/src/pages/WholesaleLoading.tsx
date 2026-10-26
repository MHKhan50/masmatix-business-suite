import { useEffect, useState } from "react";
import { api, type Page } from "../lib/api";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";
import { isoDate } from "../lib/rules";

interface Route { id: number; name: string; rider: string | null; routeDay: string | null }
interface Order { id: number; orderNumber: string; shopName: string; amount: number | null; status: string | null }
interface List { route: Route; date: string; routeRunsThatDay: boolean; orders: Order[]; orderCount: number; total: number }

export default function WholesaleLoading() {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [routeId, setRouteId] = useState("");
  const [date, setDate] = useState(isoDate(new Date()));
  useEffect(() => {
    api<Page<Route>>("/api/wholesale/routes?size=200").then((p) => setRoutes(p.content));
  }, []);
  const base = routeId ? `/api/wholesale/desk/routes/${routeId}` : null;
  const { data, error, act } = useDesk<List>(base ? `${base}/loading-list?date=${date}` : null);

  return (
    <section>
      <h1>Loading list</h1>
      <div className="card form">
        <label><span>Route</span>
          <select value={routeId} onChange={(e) => setRouteId(e.target.value)}>
            <option value="">Choose</option>
            {routes.map((r) => <option key={r.id} value={r.id}>{r.name} · {r.routeDay}</option>)}
          </select>
        </label>
        <label><span>Date</span><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      </div>
      {data && (
        <div className="card">
          <h2>{data.route.name} · {data.orderCount} order(s) · {money(data.total)}</h2>
          {!data.routeRunsThatDay && <p className="error">This route runs on {data.route.routeDay}, not on this date.</p>}
          <div className="table-wrap">
            <table>
              <thead><tr><th>Order</th><th>Shop</th><th>Amount</th><th>Status</th><th /></tr></thead>
              <tbody>
                {data.orders.map((o) => (
                  <tr key={o.id}>
                    <td>{o.orderNumber}</td><td>{o.shopName}</td><td>{money(o.amount ?? 0)}</td><td>{o.status}</td>
                    <td>{o.status === "LOADED" && <button className="link" onClick={() => act(`/api/wholesale/desk/orders/${o.id}/status`, { status: "DELIVERED" })}>Delivered</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="actions">
            <button disabled={!data.routeRunsThatDay || data.orderCount === 0} onClick={() => act(`${base}/load?date=${date}`)}>Mark all loaded</button>
            <button className="ghost" onClick={() => window.print()}>Print list</button>
          </div>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
