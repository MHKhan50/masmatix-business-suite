import { describe, expect, it } from "vitest";
import { exchangeDifference, expiryStatus, imeiCheckDigit, imeiValid, warrantyEnd } from "./shops";

// Same numbers as backend ShopRulesTest.
describe("IMEI rules match backend ImeiRules", () => {
  it("uses the Luhn check digit", () => {
    expect(imeiCheckDigit("49015420323751")).toBe(8);
    expect(imeiValid("490154203237518")).toBe(true);
    expect(imeiValid("49015420 3237518")).toBe(true);
    expect(imeiValid("490154203237517")).toBe(false);
    expect(imeiValid("940154203237518")).toBe(false);
    expect(imeiValid("49015420323751")).toBe(false);
  });
  it("ends cover the day before the same date", () => {
    expect(warrantyEnd("2026-08-02", 12)).toBe("2027-08-01");
    expect(warrantyEnd("2030-01-31", 1)).toBe("2030-02-27");
    expect(warrantyEnd("2030-03-04", 0)).toBeNull();
  });
});

describe("bakery and garment rules match the backend", () => {
  it("marks expiry status", () => {
    expect(expiryStatus("2030-03-03", "2030-03-04")).toBe("EXPIRED");
    expect(expiryStatus("2030-03-04", "2030-03-04")).toBe("LAST_DAY");
    expect(expiryStatus("2030-03-05", "2030-03-04")).toBe("TOMORROW");
    expect(expiryStatus("2030-03-06", "2030-03-04")).toBe("OK");
  });
  it("prices an exchange", () => {
    expect(exchangeDifference(24, 18.5)).toBe(-5.5);
    expect(exchangeDifference(24, 26.5)).toBe(2.5);
  });
});
