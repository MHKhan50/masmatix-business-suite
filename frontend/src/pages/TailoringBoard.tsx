import { openFile } from "../lib/api";
import { money } from "../lib/cart";
import { useDesk } from "../lib/desk";

interface Order { id: number; orderNumber: string; customerName: string; garment: string; deliveryOn: string | null; status: string | null }
interface Row { order: Order; balanceDue: number; nextStage: string | null }

export default function TailoringBoard() {
  const { data, error, act } = useDesk<Row[]>("/api/tailoring/desk/board");

  return (
    <section>
      <h1>Tailoring orders</h1>
      <div className="card">
        {data?.length === 0 && <p className="muted">No open orders</p>}
        <div className="table-wrap">
          <table>
            <thead><tr><th>Order</th><th>Customer</th><th>Garment</th><th>Delivery</th><th>Stage</th><th>Balance</th><th /></tr></thead>
            <tbody>
              {data?.map((r) => (
                <tr key={r.order.id}>
                  <td>{r.order.orderNumber}</td>
                  <td>{r.order.customerName}</td>
                  <td>{r.order.garment}</td>
                  <td>{r.order.deliveryOn ?? "-"}</td>
                  <td>{r.order.status ?? "CUTTING"}</td>
                  <td>{money(r.balanceDue)}</td>
                  <td className="row-actions">
                    {r.nextStage && <button className="link" onClick={() => act(`/api/tailoring/desk/orders/${r.order.id}/next`)}>To {r.nextStage.toLowerCase()}</button>}
                    {r.order.status === "READY" && (
                      <button className="link" onClick={() => act(`/api/tailoring/desk/orders/${r.order.id}/deliver`, { payment: r.balanceDue })}>
                        Deliver, take {money(r.balanceDue)}
                      </button>
                    )}
                    <button className="link" onClick={() => openFile(`/api/tailoring/desk/orders/${r.order.id}/slip.pdf`)}>Slip</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {error && <p className="error">{error}</p>}
      </div>
      <p className="muted">Cutting, stitching, trial, ready. An order is handed over only when the balance is paid.</p>
    </section>
  );
}
