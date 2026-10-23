import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, type Page } from "../lib/api";
import { money } from "../lib/cart";
import { isoDate } from "../lib/rules";

interface Stylist { id: number; fullName: string; active: boolean | null }
interface Treatment { id: number; name: string; durationMinutes: number; price: number }
interface Appointment {
  id: number; stylistId: number; treatmentName: string; clientName: string;
  startsAt: string; endsAt: string; price: number; status: "BOOKED" | "COMPLETED" | "CANCELLED";
}

const hm = (iso: string) => iso.slice(11, 16);

export default function SalonDiary() {
  const [date, setDate] = useState(isoDate(new Date()));
  const [stylists, setStylists] = useState<Stylist[]>([]);
  const [treatments, setTreatments] = useState<Treatment[]>([]);
  const [diary, setDiary] = useState<Appointment[]>([]);
  const [stylistId, setStylistId] = useState("");
  const [treatmentId, setTreatmentId] = useState("");
  const [slots, setSlots] = useState<string[]>([]);
  const [time, setTime] = useState("");
  const [client, setClient] = useState("");
  const [phone, setPhone] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    api<Page<Stylist>>("/api/salon/stylists").then((p) => setStylists(p.content.filter((s) => s.active !== false)));
    api<Page<Treatment>>("/api/salon/treatments").then((p) => setTreatments(p.content));
  }, []);

  const loadDiary = useCallback(() => {
    api<Appointment[]>(`/api/salon/appointments?date=${date}`).then(setDiary).catch((e: Error) => setMessage(e.message));
  }, [date]);
  useEffect(loadDiary, [loadDiary]);

  useEffect(() => {
    setTime("");
    if (!stylistId || !treatmentId) return setSlots([]);
    api<string[]>(`/api/salon/availability?date=${date}&stylistId=${stylistId}&treatmentId=${treatmentId}`)
      .then(setSlots)
      .catch((e: Error) => setMessage(e.message));
  }, [date, stylistId, treatmentId, diary]);

  async function book(ev: FormEvent) {
    ev.preventDefault();
    try {
      await api("/api/salon/appointments", {
        method: "POST",
        body: JSON.stringify({
          stylistId: Number(stylistId), treatmentId: Number(treatmentId),
          clientName: client, clientPhone: phone || null, startsAt: `${date}T${time}`,
        }),
      });
      setMessage(`Booked ${client} at ${time}`);
      setClient("");
      setPhone("");
      loadDiary();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function act(id: number, action: "cancel" | "complete") {
    try {
      await api(`/api/salon/appointments/${id}/${action}`, { method: "POST" });
      loadDiary();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Salon diary</h1>
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>

      <form className="card form" onSubmit={book}>
        <label><span>Stylist</span>
          <select value={stylistId} onChange={(e) => setStylistId(e.target.value)} required>
            <option value="">Choose</option>
            {stylists.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
          </select>
        </label>
        <label><span>Treatment</span>
          <select value={treatmentId} onChange={(e) => setTreatmentId(e.target.value)} required>
            <option value="">Choose</option>
            {treatments.map((t) => <option key={t.id} value={t.id}>{t.name} · {t.durationMinutes} min · {money(t.price)}</option>)}
          </select>
        </label>
        <label><span>Free start times</span>
          <select value={time} onChange={(e) => setTime(e.target.value)} required disabled={slots.length === 0}>
            <option value="">{stylistId && treatmentId ? `${slots.length} free` : "Pick stylist and treatment"}</option>
            {slots.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label><span>Client name</span><input value={client} onChange={(e) => setClient(e.target.value)} required /></label>
        <label><span>Phone</span><input value={phone} onChange={(e) => setPhone(e.target.value)} /></label>
        <button type="submit" disabled={!time}>Book</button>
      </form>
      {message && <p className="muted">{message}</p>}

      <div className="diary">
        {stylists.map((s) => (
          <div key={s.id} className="card">
            <h2>{s.fullName}</h2>
            {diary.filter((a) => a.stylistId === s.id).map((a) => (
              <div key={a.id} className={`slot ${a.status.toLowerCase()}`}>
                <strong>{hm(a.startsAt)} to {hm(a.endsAt)}</strong> {a.clientName}
                <div className="muted">{a.treatmentName} · {money(a.price)} · {a.status.toLowerCase()}</div>
                {a.status === "BOOKED" && (
                  <div className="row-actions">
                    <button className="link" onClick={() => act(a.id, "complete")}>Done</button>
                    <button className="link" onClick={() => act(a.id, "cancel")}>Cancel</button>
                  </div>
                )}
              </div>
            ))}
            {diary.every((a) => a.stylistId !== s.id) && <p className="muted">No bookings</p>}
          </div>
        ))}
      </div>
    </section>
  );
}
