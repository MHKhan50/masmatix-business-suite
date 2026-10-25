// Same rules as backend ImeiRules, BakeryRules and ExchangeRules, so screens preview what the API will decide.
import { addDays, addMonths } from "./rules";

/** Luhn check digit for the first 14 digits of an IMEI. */
export function imeiCheckDigit(first14: string): number {
  if (!/^\d{14}$/.test(first14)) throw new Error("need exactly 14 digits");
  let sum = 0;
  for (let i = 0; i < 14; i++) {
    let d = Number(first14[13 - i]);
    if (i % 2 === 0) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return (10 - (sum % 10)) % 10;
}

export function cleanImei(raw: string): string {
  return raw.replace(/[\s-]/g, "");
}

export function imeiValid(raw: string): boolean {
  const imei = cleanImei(raw);
  return /^\d{15}$/.test(imei) && imeiCheckDigit(imei.slice(0, 14)) === Number(imei[14]);
}

/** Last day of cover: 12 months from 2026-08-02 ends on 2027-08-01. */
export function warrantyEnd(soldOn: string | null, months: number | null): string | null {
  if (!soldOn || !months || months <= 0) return null;
  return addDays(addMonths(soldOn, months), -1);
}

export type ExpiryStatus = "EXPIRED" | "LAST_DAY" | "TOMORROW" | "OK";

export function expiryStatus(expiresOn: string, today: string): ExpiryStatus {
  if (today > expiresOn) return "EXPIRED";
  if (today === expiresOn) return "LAST_DAY";
  return addDays(today, 1) === expiresOn ? "TOMORROW" : "OK";
}

export const EXCHANGE_WINDOW_DAYS = 30;

/** New price minus the price paid, in pounds to 2 dp: positive means the customer pays more. */
export function exchangeDifference(paid: number, newPrice: number): number {
  return Math.round((newPrice - paid) * 100) / 100;
}
