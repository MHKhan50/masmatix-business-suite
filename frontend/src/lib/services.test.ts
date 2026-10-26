import { describe, expect, it } from "vitest";
import { balanceDue, libraryFine, netKg, travelMissing } from "./services";

// Same numbers as backend ServiceRulesTest.
describe("service rules", () => {
  it("library fine starts the day after due and is capped", () => {
    expect(libraryFine("2030-03-04", "2030-03-04")).toBe(0);
    expect(libraryFine("2030-03-04", "2030-03-02")).toBe(0);
    expect(libraryFine("2030-03-04", "2030-03-07")).toBe(0.6);
    expect(libraryFine("2030-03-04", "2030-05-23")).toBe(10);
  });

  it("tailoring balance never goes below zero", () => {
    expect(balanceDue(45, 20)).toBe(25);
    expect(balanceDue(45, null)).toBe(45);
    expect(balanceDue(45, 50)).toBe(0);
  });

  it("weighbridge net weight and operator errors", () => {
    expect(netKg(24850, 9120)).toBe(15730);
    expect(netKg(9120, 24850)).toContain("swapped");
    expect(netKg(60001, 9000)).toContain("limit");
    expect(netKg(9000, 0)).toContain("tare");
  });

  it("travel checklist", () => {
    expect(travelMissing("UMRAH", true, "APPROVED", 1450, 1450)).toEqual([]);
    expect(travelMissing("UMRAH", false, "SUBMITTED", 1450, 500)).toEqual([
      "Passport not received", "Visa not approved yet", "Balance due 950.00",
    ]);
    expect(travelMissing("FLIGHT", true, "NOT_STARTED", 300, 300)).toEqual([]);
    expect(travelMissing("TOUR", true, "REFUSED", null, null)).toEqual(["Visa refused"]);
  });
});
