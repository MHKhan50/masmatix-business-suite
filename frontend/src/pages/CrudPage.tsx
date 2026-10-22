import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, openFile, type Page, type Session } from "../lib/api";
import { labelOf, type EntityDef, type FieldDef } from "../lib/fields";

type Row = Record<string, unknown> & { id?: number };

function inputFor(f: FieldDef, value: unknown, onChange: (v: unknown) => void) {
  if (f.type === "bool") {
    return <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />;
  }
  if (f.type === "enum") {
    return (
      <select value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value || null)}>
        <option value="">-</option>
        {f.values?.map((v) => <option key={v}>{v}</option>)}
      </select>
    );
  }
  if (f.type === "text") {
    return <textarea value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value)} />;
  }
  const htmlTypes: Record<string, string> = { int: "number", decimal: "number", date: "date", email: "email", phone: "tel" };
  const type = htmlTypes[f.type] ?? "text";
  return (
    <input
      type={type}
      step={f.type === "decimal" ? "0.01" : undefined}
      required={f.required}
      value={value === null || value === undefined ? "" : String(value)}
      onChange={(e) => {
        const v = e.target.value;
        onChange(v === "" ? null : f.type === "int" || f.type === "decimal" ? Number(v) : v);
      }}
    />
  );
}

export default function CrudPage({ entity, session }: { entity: EntityDef; session: Session }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [editing, setEditing] = useState<Row | null>(null);
  const [error, setError] = useState("");
  const canWrite = session.role !== "STAFF";
  const canDelete = session.role === "ADMIN";
  const shown = entity.fields.slice(0, 6);

  const load = useCallback(async () => {
    try {
      setRows((await api<Page<Row>>(`${entity.api}?size=200`)).content);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [entity.api]);

  useEffect(() => {
    setEditing(null);
    setError("");
    void load();
  }, [load]);

  async function save(ev: FormEvent) {
    ev.preventDefault();
    if (!editing) return;
    try {
      const url = editing.id ? `${entity.api}/${editing.id}` : entity.api;
      await api(url, { method: editing.id ? "PUT" : "POST", body: JSON.stringify(editing) });
      setEditing(null);
      setError("");
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function remove(id: number) {
    if (!confirm("Delete this record?")) return;
    try {
      await api(`${entity.api}/${id}`, { method: "DELETE" });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <section>
      <header className="page-head">
        <h1>{entity.label}</h1>
        <div className="actions">
          <button className="ghost" onClick={() => openFile(`${entity.api}/export.csv`, `${entity.path}.csv`)}>
            Export CSV
          </button>
          {canWrite && <button onClick={() => setEditing({})}>Add</button>}
        </div>
      </header>
      {error && <p className="error">{error}</p>}

      {editing && (
        <form className="card form" onSubmit={save}>
          {entity.fields.map((f) => (
            <label key={f.name}>
              <span>{labelOf(f.name)}{f.required ? " *" : ""}</span>
              {inputFor(f, editing[f.name], (v) => setEditing({ ...editing, [f.name]: v }))}
            </label>
          ))}
          <div className="actions">
            <button type="submit">Save</button>
            <button type="button" className="ghost" onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </form>
      )}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th>
              {shown.map((f) => <th key={f.name}>{labelOf(f.name)}</th>)}
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                {shown.map((f) => (
                  <td key={f.name}>{f.type === "bool" ? (r[f.name] ? "Yes" : "No") : String(r[f.name] ?? "")}</td>
                ))}
                <td className="row-actions">
                  {canWrite && <button className="link" onClick={() => setEditing(r)}>Edit</button>}
                  {canDelete && <button className="link danger" onClick={() => remove(r.id!)}>Delete</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
