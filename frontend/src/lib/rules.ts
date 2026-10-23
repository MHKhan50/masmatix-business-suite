// Same rules as the backend StayRules and MembershipRules, so screens preview what the API will return.

/** Local date as YYYY-MM-DD (not UTC, so late evening does not jump to tomorrow). */
export function isoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function parse(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function format(d: Date): string {
  return d.toISOString().slice(0, 10);
}

export function addDays(iso: string, days: number): string {
  const d = parse(iso);
  d.setUTCDate(d.getUTCDate() + days);
  return format(d);
}

/** Java LocalDate.plusMonths: clamps to the last day of a shorter month. */
export function addMonths(iso: string, months: number): string {
  const d = parse(iso);
  const day = d.getUTCDate();
  d.setUTCDate(1);
  d.setUTCMonth(d.getUTCMonth() + months);
  const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
  d.setUTCDate(Math.min(day, last));
  return format(d);
}

export function nights(checkIn: string, checkOut: string): number {
  return Math.round((parse(checkOut).getTime() - parse(checkIn).getTime()) / 86_400_000);
}

/** Rate x nights in pence, rounded half-up, like BigDecimal setScale(2, HALF_UP). */
export function stayTotal(nightlyRate: number, n: number): number {
  return Math.round(nightlyRate * 100 * n + 1e-9) / 100;
}

/** Early payment extends from the current expiry; late payment starts today. */
export function renewedExpiry(currentExpiry: string | null, today: string, months: number): string {
  const base = !currentExpiry || currentExpiry < today ? today : currentExpiry;
  return addMonths(base, months);
}
