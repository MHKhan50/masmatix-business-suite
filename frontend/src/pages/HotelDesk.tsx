import { useEffect, useState, type FormEvent } from "react";
import { api, openFile, type Page, type Session } from "../lib/api";
import { money } from "../lib/cart";
import { addDays, isoDate, nights, stayTotal } from "../lib/rules";

interface Room { id: number; roomNumber: string; roomType: string | null; nightlyRate: number; maxGuests: number }
interface Booking {
  id: number; bookingNumber: string; roomNumber: string; guestName: string; guests: number;
  checkIn: string; checkOut: string; total: number; status: string;
}
interface FrontDesk { arrivals: Booking[]; inHouse: Booking[] }

export default function HotelDesk({ session }: { session: Session }) {
  const today = isoDate(new Date());
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(addDays(today, 1));
  const [guests, setGuests] = useState(1);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [roomId, setRoomId] = useState("");
  const [guestName, setGuestName] = useState("");
  const [guestPhone, setGuestPhone] = useState("");
  const [desk, setDesk] = useState<FrontDesk>({ arrivals: [], inHouse: [] });
  const [recent, setRecent] = useState<Booking[]>([]);
  const [message, setMessage] = useState("");

  const n = nights(from, to);
  const loadDesk = () => {
    api<FrontDesk>("/api/hotel/front-desk").then(setDesk).catch((e: Error) => setMessage(e.message));
    api<Page<Booking>>("/api/hotel/bookings").then((p) => setRecent(p.content));
  };
  useEffect(loadDesk, []);

  useEffect(() => {
    setRoomId("");
    if (n < 1) return setRooms([]);
    api<Room[]>(`/api/hotel/availability?from=${from}&to=${to}&guests=${guests}`)
      .then(setRooms)
      .catch((e: Error) => setMessage(e.message));
  }, [from, to, guests, n, recent]);

  async function book(ev: FormEvent) {
    ev.preventDefault();
    try {
      const b = await api<Booking>("/api/hotel/bookings", {
        method: "POST",
        body: JSON.stringify({ roomId: Number(roomId), guestName, guestPhone: guestPhone || null, guests, checkIn: from, checkOut: to }),
      });
      setMessage(`Booked ${b.bookingNumber}, room ${b.roomNumber}, ${money(b.total)}`);
      setGuestName("");
      setGuestPhone("");
      loadDesk();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  async function act(id: number, action: "check-in" | "check-out" | "cancel") {
    try {
      await api(`/api/hotel/bookings/${id}/${action}`, { method: "POST" });
      loadDesk();
    } catch (e) {
      setMessage((e as Error).message);
    }
  }

  const room = rooms.find((r) => String(r.id) === roomId);

  return (
    <section>
      <h1>Hotel front desk</h1>
      <form className="card form" onSubmit={book}>
        <label><span>Arrive</span><input type="date" min={today} value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label><span>Depart</span><input type="date" min={addDays(from, 1)} value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <label><span>Guests</span><input type="number" min={1} max={8} value={guests} onChange={(e) => setGuests(Number(e.target.value))} /></label>
        <label><span>Free rooms ({rooms.length})</span>
          <select value={roomId} onChange={(e) => setRoomId(e.target.value)} required>
            <option value="">{n < 1 ? "Depart after arrive" : "Choose"}</option>
            {rooms.map((r) => <option key={r.id} value={r.id}>{r.roomNumber} {r.roomType ?? ""} · {money(r.nightlyRate)}/night</option>)}
          </select>
        </label>
        <label><span>Guest name</span><input value={guestName} onChange={(e) => setGuestName(e.target.value)} required /></label>
        <label><span>Phone</span><input value={guestPhone} onChange={(e) => setGuestPhone(e.target.value)} /></label>
        <button type="submit" disabled={!room}>
          {room ? `Book ${n} night${n === 1 ? "" : "s"} · ${money(stayTotal(room.nightlyRate, n))}` : "Book"}
        </button>
      </form>
      {message && <p className="muted">{message}</p>}

      <div className="diary">
        <div className="card">
          <h2>Arriving today</h2>
          {desk.arrivals.length === 0 && <p className="muted">None</p>}
          {desk.arrivals.map((b) => (
            <div key={b.id} className="slot">
              <strong>Room {b.roomNumber}</strong> {b.guestName} · {b.guests} guest(s) · until {b.checkOut}
              <div className="row-actions"><button className="link" onClick={() => act(b.id, "check-in")}>Check in</button></div>
            </div>
          ))}
        </div>
        <div className="card">
          <h2>In house</h2>
          {desk.inHouse.length === 0 && <p className="muted">None</p>}
          {desk.inHouse.map((b) => (
            <div key={b.id} className="slot">
              <strong>Room {b.roomNumber}</strong> {b.guestName} · leaves {b.checkOut}
              <div className="row-actions">
                <button className="link" onClick={() => act(b.id, "check-out")}>Check out</button>
                <button className="link" onClick={() => openFile(`/api/hotel/bookings/${b.id}/invoice.pdf`)}>Invoice</button>
              </div>
            </div>
          ))}
        </div>
      </div>

      <h2>Recent bookings</h2>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Number</th><th>Room</th><th>Guest</th><th>Dates</th><th>Total</th><th>Status</th><th /></tr></thead>
          <tbody>
            {recent.map((b) => (
              <tr key={b.id}>
                <td>{b.bookingNumber}</td><td>{b.roomNumber}</td><td>{b.guestName}</td>
                <td>{b.checkIn} to {b.checkOut}</td><td>{money(b.total)}</td><td>{b.status.toLowerCase().replace("_", " ")}</td>
                <td className="row-actions">
                  <button className="link" onClick={() => openFile(`/api/hotel/bookings/${b.id}/invoice.pdf`)}>Invoice</button>
                  {b.status === "RESERVED" && session.role !== "STAFF" && (
                    <button className="link" onClick={() => act(b.id, "cancel")}>Cancel</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
