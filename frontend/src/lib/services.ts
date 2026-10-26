// Same rules as backend LibraryRules, TailoringRules, WeighbridgeRules and TravelRules, so screens preview what the API will decide.

export const LIBRARY_FINE_PER_DAY = 0.2;
export const LIBRARY_FINE_CAP = 10;
export const MAX_GROSS_KG = 60_000;

function daysBetween(fromIso: string, toIso: string): number {
  return Math.round((Date.parse(`${toIso}T00:00:00Z`) - Date.parse(`${fromIso}T00:00:00Z`)) / 86_400_000);
}

/** Returned on the due date: 0. Each later day adds 0.20, up to 10.00. */
export function libraryFine(dueOn: string, returnedOn: string): number {
  const late = daysBetween(dueOn, returnedOn);
  if (late <= 0) return 0;
  return Math.min(LIBRARY_FINE_CAP, Math.round(late * LIBRARY_FINE_PER_DAY * 100) / 100);
}

export function balanceDue(price: number | null, advancePaid: number | null): number {
  return Math.max(0, Math.round(((price ?? 0) - (advancePaid ?? 0)) * 100) / 100);
}

/** Net kg, or an error message the operator can act on. */
export function netKg(grossKg: number, tareKg: number): number | string {
  if (!(tareKg > 0)) return "tare must be more than 0 kg";
  if (grossKg > MAX_GROSS_KG) return `gross ${grossKg} kg is over the bridge limit of ${MAX_GROSS_KG} kg`;
  if (grossKg <= tareKg) return "gross must be more than tare (were the weighings swapped?)";
  return grossKg - tareKg;
}

export function travelMissing(tripType: string, passportReceived: boolean | null, visaStatus: string | null,
                              amount: number | null, paid: number | null): string[] {
  const out: string[] = [];
  if (passportReceived !== true) out.push("Passport not received");
  if (tripType !== "FLIGHT") {
    if (visaStatus === "REFUSED") out.push("Visa refused");
    else if (visaStatus !== "APPROVED") out.push("Visa not approved yet");
  }
  const p = paid ?? 0;
  if (amount != null && p < amount) out.push(`Balance due ${(amount - p).toFixed(2)}`);
  return out;
}
