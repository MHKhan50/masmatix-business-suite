import { useEffect, useState } from "react";
import { api, type Page } from "../lib/api";

interface AuditRow {
  id: number;
  at: string;
  username: string;
  action: string;
  entity: string;
  entityId: number | null;
  detail: string | null;
}

export default function Audit() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  useEffect(() => {
    api<Page<AuditRow>>("/api/audit").then((p) => setRows(p.content)).catch(() => setRows([]));
  }, []);
  return (
    <section>
      <h1>Audit log</h1>
      <div className="table-wrap">
        <table>
          <thead><tr><th>When</th><th>User</th><th>Action</th><th>Record</th><th>Detail</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{new Date(r.at).toLocaleString("en-GB")}</td>
                <td>{r.username}</td>
                <td>{r.action}</td>
                <td>{r.entity} {r.entityId ?? ""}</td>
                <td>{r.detail ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
