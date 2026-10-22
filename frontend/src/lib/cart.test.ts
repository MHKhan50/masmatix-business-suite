import { describe, expect, it } from "vitest";
import { addToCart, cartTotals, setQty, type CartLine } from "./cart";

const milk = { barcode: "2000000000015", name: "Milk", unitPrice: 1.45, taxRate: 0, stockQty: 40 };
const cola = { barcode: "2000000000084", name: "Cola", unitPrice: 0.9, taxRate: 20, stockQty: 60 };

describe("cart maths matches the backend PricingCalculator", () => {
  it("gives the same totals as the API test (5.60 + 0.54 = 6.14)", () => {
    let cart: CartLine[] = [];
    cart = addToCart(cart, milk);
    cart = addToCart(cart, milk);
    cart = addToCart(cart, cola);
    cart = setQty(cart, cola.barcode, 3);
    expect(cartTotals(cart)).toEqual({ subtotal: 5.6, tax: 0.54, total: 6.14 });
  });

  it("rounds tax half-up per line (0.99 at 20% -> 0.20)", () => {
    const cart = addToCart([], { ...cola, unitPrice: 0.99 });
    expect(cartTotals(cart).tax).toBe(0.2);
  });

  it("never lets quantity go above stock", () => {
    const cart = setQty(addToCart([], { ...cola, stockQty: 2 }), cola.barcode, 5);
    expect(cart[0].qty).toBe(2);
  });

  it("removes a line when quantity is set to zero", () => {
    expect(setQty(addToCart([], milk), milk.barcode, 0)).toHaveLength(0);
  });

  it("ignores out-of-stock products", () => {
    expect(addToCart([], { ...milk, stockQty: 0 })).toHaveLength(0);
  });
});
