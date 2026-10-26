import { useCallback, useEffect, useState } from "react";
import { api } from "./api";

/** Loads one list for a desk screen and runs actions that refresh it, keeping the last error for display. */
export function useDesk<T>(url: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");

  const refresh = useCallback(() => {
    if (!url) return Promise.resolve();
    return api<T>(url).then(setData).catch((e: Error) => setError(e.message));
  }, [url]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function act<R>(path: string, body?: unknown): Promise<R | null> {
    try {
      const r = await api<R>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
      setError("");
      await refresh();
      return r;
    } catch (e) {
      setError((e as Error).message);
      return null;
    }
  }

  return { data, error, setError, refresh, act };
}
