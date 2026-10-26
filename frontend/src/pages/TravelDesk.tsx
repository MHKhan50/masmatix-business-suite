import { useState } from "react";
import { useDesk } from "../lib/desk";

interface Booking { id: number; reference: string; travellerName: string; tripType: string; travelOn: string }
interface Check { booking: Booking; daysLeft: number; missing: string[]; ready: boolean }

export default function TravelDesk() {
  const [onlyRemind, setOnlyRemind] = useState(true);
  const { data, error } = useDesk<Check[]>(onlyRemind ? "/api/travel/desk/remind" : "/api/travel/desk/upcoming");

  return (
    <section>
      <h1>Travel documents</h1>
      <div className="seg">
        <button className={onlyRemind ? "" : "ghost"} onClick={() => setOnlyRemind(true)}>Call list (14 days)</button>
        <button className={onlyRemind ? "ghost" : ""} onClick={() => setOnlyRemind(false)}>All upcoming</button>
      </div>
      {data?.length === 0 && <p className="muted">Nothing to chase</p>}
      {data?.map((c) => (
        <div key={c.booking.id} className={`card slot ${c.ready ? "" : "cancelled"}`}>
          <strong>{c.booking.reference}</strong> · {c.booking.travellerName} · {c.booking.tripType.toLowerCase()} on {c.booking.travelOn}
          <span className="muted"> ({c.daysLeft} days)</span>
          <br />
          {c.ready ? <span>All documents and payment in</span> : <span>{c.missing.join(" · ")}</span>}
        </div>
      ))}
      {error && <p className="error">{error}</p>}
      <p className="muted">Flights need no visa from the agency. Reminders are a list to call; no messages are sent.</p>
    </section>
  );
}
