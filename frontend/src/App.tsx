import { useState, type FormEvent } from "react";
import { BrowserRouter, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { api, loadSession, saveSession, type Session } from "./lib/api";
import { generatedSystems, gymMemberEntity, handsetEntity, productEntity } from "./lib/fields";
import Audit from "./pages/Audit";
import CoachingFees from "./pages/CoachingFees";
import ConstructionCosts from "./pages/ConstructionCosts";
import HousingDesk from "./pages/HousingDesk";
import LabDesk from "./pages/LabDesk";
import HearingCalendar from "./pages/HearingCalendar";
import LibraryDesk from "./pages/LibraryDesk";
import LoanDesk from "./pages/LoanDesk";
import PayrollDesk from "./pages/PayrollDesk";
import TailoringBoard from "./pages/TailoringBoard";
import TravelDesk from "./pages/TravelDesk";
import WeighbridgeDesk from "./pages/WeighbridgeDesk";
import WholesaleLoading from "./pages/WholesaleLoading";
import BakeryCounter from "./pages/BakeryCounter";
import CrudPage from "./pages/CrudPage";
import GarmentTill from "./pages/GarmentTill";
import GymDesk from "./pages/GymDesk";
import HotelDesk from "./pages/HotelDesk";
import ImeiDesk from "./pages/ImeiDesk";
import Prescriptions from "./pages/Prescriptions";
import Sales from "./pages/Sales";
import SalonDiary from "./pages/SalonDiary";
import Till from "./pages/Till";

/** Hand-written screens per system, listed above that system's generated record screens. */
const handWritten: Record<string, { path: string; label: string }[]> = {
  pos: [{ path: "till", label: "Till" }, { path: "products", label: "Products" }, { path: "sales", label: "Sales" }],
  salon: [{ path: "diary", label: "Diary" }],
  clinic: [{ path: "prescriptions", label: "Prescriptions" }],
  gym: [{ path: "desk", label: "Front desk" }, { path: "members", label: "Members" }],
  hotel: [{ path: "desk", label: "Front desk" }],
  garment: [{ path: "till", label: "Till and exchanges" }],
  imei: [{ path: "desk", label: "IMEI desk" }, { path: "handsets", label: "Handsets" }],
  bakery: [{ path: "counter", label: "Counter and expiry" }],
  coaching: [{ path: "fees", label: "Fees and overdue" }],
  construction: [{ path: "costs", label: "Site costs" }],
  housing: [{ path: "desk", label: "Overdue and transfers" }],
  lab: [{ path: "worklist", label: "Work list and reports" }],
  lawfirm: [{ path: "calendar", label: "Hearing calendar" }],
  library: [{ path: "desk", label: "Issue and return" }],
  microfinance: [{ path: "desk", label: "Loan desk" }],
  payroll: [{ path: "desk", label: "Attendance and pay" }],
  tailoring: [{ path: "board", label: "Order board" }],
  travel: [{ path: "desk", label: "Document checklist" }],
  weighbridge: [{ path: "desk", label: "Weigh and slip" }],
  wholesale: [{ path: "loading", label: "Loading list" }],
};
const order = Object.keys(handWritten);
const systems = [...generatedSystems].sort(
  (a, b) => (order.indexOf(a.system) + 1 || 99) - (order.indexOf(b.system) + 1 || 99),
);

function Login({ onLogin }: { onLogin: (s: Session) => void }) {
  const [username, setUsername] = useState("staff");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function submit(ev: FormEvent) {
    ev.preventDefault();
    try {
      const s = await api<Session>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      saveSession(s);
      onLogin(s);
    } catch {
      setError("Wrong username or password");
    }
  }

  return (
    <main className="login">
      <form className="card" onSubmit={submit}>
        <h1>Masmatix Business Suite</h1>
        <p className="muted">Demo users: admin, manager, staff</p>
        <label><span>Username</span><input value={username} onChange={(e) => setUsername(e.target.value)} /></label>
        <label><span>Password</span><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <button type="submit">Sign in</button>
      </form>
    </main>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(loadSession());
  if (!session) return <Login onLogin={setSession} />;

  const logout = () => {
    saveSession(null);
    setSession(null);
  };

  return (
    <BrowserRouter>
      <div className="shell">
        <nav>
          <div className="brand">Masmatix<span>Business Suite</span></div>
          {systems.map((sys) => (
            <div key={sys.system}>
              <p className="group">{sys.title}</p>
              {(handWritten[sys.system] ?? []).map((h) => (
                <NavLink key={h.path} to={`/${sys.system}/${h.path}`}>{h.label}</NavLink>
              ))}
              {sys.entities.map((e) => (
                <NavLink key={e.api} to={`/${sys.system}/${e.path}`}>{e.label}</NavLink>
              ))}
            </div>
          ))}
          {session.role === "ADMIN" && (
            <>
              <p className="group">Admin</p>
              <NavLink to="/audit">Audit log</NavLink>
            </>
          )}
          <div className="who">
            {session.username} · {session.role.toLowerCase()}
            <button className="link" onClick={logout}>Sign out</button>
          </div>
        </nav>
        <main>
          <Routes>
            <Route path="/pos/till" element={<Till />} />
            <Route path="/pos/products" element={<CrudPage entity={productEntity} session={session} />} />
            <Route path="/pos/sales" element={<Sales session={session} />} />
            <Route path="/salon/diary" element={<SalonDiary />} />
            <Route path="/clinic/prescriptions" element={<Prescriptions session={session} />} />
            <Route path="/gym/desk" element={<GymDesk />} />
            <Route path="/gym/members" element={<CrudPage entity={gymMemberEntity} session={session} />} />
            <Route path="/hotel/desk" element={<HotelDesk session={session} />} />
            <Route path="/garment/till" element={<GarmentTill />} />
            <Route path="/imei/desk" element={<ImeiDesk />} />
            <Route path="/imei/handsets" element={<CrudPage entity={handsetEntity} session={session} />} />
            <Route path="/bakery/counter" element={<BakeryCounter session={session} />} />
            <Route path="/coaching/fees" element={<CoachingFees />} />
            <Route path="/construction/costs" element={<ConstructionCosts />} />
            <Route path="/housing/desk" element={<HousingDesk session={session} />} />
            <Route path="/lab/worklist" element={<LabDesk />} />
            <Route path="/lawfirm/calendar" element={<HearingCalendar />} />
            <Route path="/library/desk" element={<LibraryDesk />} />
            <Route path="/microfinance/desk" element={<LoanDesk />} />
            <Route path="/payroll/desk" element={<PayrollDesk />} />
            <Route path="/tailoring/board" element={<TailoringBoard />} />
            <Route path="/travel/desk" element={<TravelDesk />} />
            <Route path="/weighbridge/desk" element={<WeighbridgeDesk />} />
            <Route path="/wholesale/loading" element={<WholesaleLoading />} />
            {generatedSystems.map((sys) =>
              sys.entities.map((e) => (
                <Route key={e.api} path={`/${sys.system}/${e.path}`} element={<CrudPage entity={e} session={session} />} />
              )),
            )}
            <Route path="/audit" element={<Audit />} />
            <Route path="*" element={<Navigate to="/pos/till" replace />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
