export interface Session {
  token: string;
  username: string;
  role: "ADMIN" | "MANAGER" | "STAFF";
}

const KEY = "suite.session";

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(s: Session | null): void {
  try {
    if (s) sessionStorage.setItem(KEY, JSON.stringify(s));
    else sessionStorage.removeItem(KEY);
  } catch {
    /* private mode: session lives in memory only */
  }
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const s = loadSession();
  const headers = new Headers(init.headers);
  if (s) headers.set("Authorization", `Bearer ${s.token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const res = await fetch(path, { ...init, headers });
  if (res.status === 401 && s) {
    saveSession(null);
    window.location.assign("/");
  }
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const pd = await res.json();
      msg = [pd.detail, ...(pd.errors ?? [])].filter(Boolean).join(" · ") || msg;
    } catch {
      /* no JSON body */
    }
    throw new ApiError(res.status, msg);
  }
  return res;
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await request(path, init);
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

/** Fetch a file with the auth header and open or save it. */
export async function openFile(path: string, download?: string): Promise<void> {
  const blob = await (await request(path)).blob();
  const url = URL.createObjectURL(blob);
  if (download) {
    const a = document.createElement("a");
    a.href = url;
    a.download = download;
    a.click();
  } else {
    window.open(url, "_blank");
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export interface Page<T> {
  content: T[];
  totalElements: number;
}
