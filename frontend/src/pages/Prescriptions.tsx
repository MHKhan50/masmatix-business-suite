import { useEffect, useState, type FormEvent } from "react";
import { api, openFile, type Page, type Session } from "../lib/api";

interface Patient { id: number; fullName: string; dateOfBirth: string; allergies: string | null }
interface Medicine { id: number; name: string; strength: string | null; form: string | null }
interface Item { medicineId: string; dose: string; frequency: string; durationDays: string; instructions: string }
interface Prescription {
  id: number; prescriptionNumber: string; patientName: string; prescriber: string; issuedAt: string;
  allergyOverrideReason: string | null; items: { medicineName: string }[];
}

const blank: Item = { medicineId: "", dose: "", frequency: "", durationDays: "7", instructions: "" };

export default function Prescriptions({ session }: { session: Session }) {
  const canWrite = session.role !== "STAFF";
  const [patients, setPatients] = useState<Patient[]>([]);
  const [medicines, setMedicines] = useState<Medicine[]>([]);
  const [list, setList] = useState<Prescription[]>([]);
  const [patientId, setPatientId] = useState("");
  const [items, setItems] = useState<Item[]>([{ ...blank }]);
  const [notes, setNotes] = useState("");
  const [override, setOverride] = useState("");
  const [message, setMessage] = useState("");
  const [failed, setFailed] = useState(false);

  const load = () => api<Prescription[]>("/api/clinic/prescriptions").then(setList).catch((e: Error) => setMessage(e.message));
  useEffect(() => {
    api<Page<Patient>>("/api/clinic/patients?size=200").then((p) => setPatients(p.content));
    api<Page<Medicine>>("/api/clinic/medicines?size=200").then((p) => setMedicines(p.content));
    load();
  }, []);

  const patient = patients.find((p) => String(p.id) === patientId);
  const setItem = (i: number, patch: Partial<Item>) =>
    setItems((all) => all.map((it, j) => (j === i ? { ...it, ...patch } : it)));

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    try {
      const rx = await api<Prescription>("/api/clinic/prescriptions", {
        method: "POST",
        body: JSON.stringify({
          patientId: Number(patientId), notes: notes || null, allergyOverrideReason: override || null,
          items: items.map((it) => ({ ...it, medicineId: Number(it.medicineId), durationDays: Number(it.durationDays) })),
        }),
      });
      setMessage(`Saved ${rx.prescriptionNumber}`);
      setFailed(false);
      setItems([{ ...blank }]);
      setNotes("");
      setOverride("");
      load();
      openFile(`/api/clinic/prescriptions/${rx.id}/print.pdf`);
    } catch (e) {
      setMessage((e as Error).message);
      setFailed(true);
    }
  }

  return (
    <section>
      <h1>Prescriptions</h1>
      {canWrite && (
        <form className="card" onSubmit={submit}>
          <div className="form">
            <label><span>Patient</span>
              <select value={patientId} onChange={(e) => setPatientId(e.target.value)} required>
                <option value="">Choose</option>
                {patients.map((p) => <option key={p.id} value={p.id}>{p.fullName} ({p.dateOfBirth})</option>)}
              </select>
            </label>
            <p className={patient?.allergies ? "error" : "muted"}>
              Allergies: {patient ? patient.allergies || "none recorded" : "-"}
            </p>
          </div>
          {items.map((it, i) => (
            <div className="form" key={i}>
              <label><span>Medicine</span>
                <select value={it.medicineId} onChange={(e) => setItem(i, { medicineId: e.target.value })} required>
                  <option value="">Choose</option>
                  {medicines.map((m) => <option key={m.id} value={m.id}>{m.name} {m.strength ?? ""}</option>)}
                </select>
              </label>
              <label><span>Dose</span><input value={it.dose} onChange={(e) => setItem(i, { dose: e.target.value })} required /></label>
              <label><span>Frequency</span><input value={it.frequency} onChange={(e) => setItem(i, { frequency: e.target.value })} required /></label>
              <label><span>Days</span><input type="number" min={1} max={365} value={it.durationDays} onChange={(e) => setItem(i, { durationDays: e.target.value })} /></label>
              <label><span>Instructions</span><input value={it.instructions} onChange={(e) => setItem(i, { instructions: e.target.value })} /></label>
            </div>
          ))}
          <div className="form">
            <label><span>Notes</span><input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
            <label><span>Allergy override reason (only if warned)</span><input value={override} onChange={(e) => setOverride(e.target.value)} /></label>
          </div>
          <div className="actions">
            {items.length < 10 && <button type="button" className="ghost" onClick={() => setItems([...items, { ...blank }])}>Add medicine</button>}
            <button type="submit">Save and print</button>
          </div>
        </form>
      )}
      {message && <p className={failed ? "error" : "muted"}>{message}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>Number</th><th>Date</th><th>Patient</th><th>Medicines</th><th>Prescriber</th><th /></tr></thead>
          <tbody>
            {list.map((p) => (
              <tr key={p.id}>
                <td>{p.prescriptionNumber}{p.allergyOverrideReason ? " ⚠" : ""}</td>
                <td>{new Date(p.issuedAt).toLocaleDateString("en-GB")}</td>
                <td>{p.patientName}</td>
                <td>{p.items.map((i) => i.medicineName).join(", ")}</td>
                <td>{p.prescriber}</td>
                <td><button className="link" onClick={() => openFile(`/api/clinic/prescriptions/${p.id}/print.pdf`)}>Print</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
