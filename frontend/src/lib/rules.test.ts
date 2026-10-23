import { describe, expect, it } from "vitest";
import { addDays, addMonths, nights, renewedExpiry, stayTotal } from "./rules";

// Same numbers as backend BusinessRulesTest.
describe("hotel stay rules match backend StayRules", () => {
  it("counts nights, not days", () => {
    expect(nights("2030-03-04", "2030-03-06")).toBe(2);
    expect(addDays("2030-02-28", 1)).toBe("2030-03-01");
  });
  it("totals rate x nights", () => {
    expect(stayTotal(85, 2)).toBe(170);
  });
});

describe("gym renewal matches backend MembershipRules", () => {
  it("keeps paid days on early renewal", () => {
    expect(renewedExpiry("2030-03-09", "2030-03-04", 1)).toBe("2030-04-09");
  });
  it("starts from today after expiry", () => {
    expect(renewedExpiry("2030-02-12", "2030-03-04", 1)).toBe("2030-04-04");
    expect(renewedExpiry(null, "2030-03-04", 12)).toBe("2031-03-04");
  });
  it("clamps month ends like Java plusMonths", () => {
    expect(addMonths("2030-01-31", 1)).toBe("2030-02-28");
    expect(addMonths("2028-01-31", 1)).toBe("2028-02-29");
  });
});
