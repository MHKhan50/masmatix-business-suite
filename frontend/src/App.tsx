import { useState, type FormEvent } from "react";
import { BrowserRouter, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { api, loadSession, saveSession, type Session } from "./lib/api";
import { generatedSystems, productEntity } from "./lib/fields";
import Audit from "./pages/Audit";
import CrudPage from "./pages/CrudPage";
import Sales from "./pages/Sales";
import Till from "./pages/Till";

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
          <p className="group">Shop POS</p>
          <NavLink to="/pos/till">Till</NavLink>
          <NavLink to="/pos/products">Products</NavLink>
          <NavLink to="/pos/sales">Sales</NavLink>
          {generatedSystems.map((sys) =>
            sys.entities.map((e) => (
              <NavLink key={e.api} to={`/${sys.system}/${e.path}`}>{e.label}</NavLink>
            )),
          )}
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
