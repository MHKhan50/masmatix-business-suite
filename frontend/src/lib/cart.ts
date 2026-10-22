// Same money rule as backend PricingCalculator: tax rounded half-up per line, then summed.
// Work in integer pence to avoid floating point drift.

export interface CartLine {
  barcode: string;
  name: string;
  unitPrice: number; // before tax, e.g. 0.9
  taxRate: number; // percent, e.g. 20
  qty: number;
  stockQty: number;
}

export interface Totals {
  subtotal: number;
  tax: number;
  total: number;
}

const toPence = (v: number) => Math.round(v * 100);

/** Half-up rounding of (a * b / 10000) for non-negative integers. */
function taxPence(netPence: number, ratePercent: number): number {
  const rateBasis = Math.round(ratePercent * 100); // 20.00% -> 2000
  return Math.floor((netPence * rateBasis + 5000) / 10000);
}

export function lineTotals(line: CartLine): { net: number; tax: number } {
  const net = toPence(line.unitPrice) * line.qty;
  return { net, tax: taxPence(net, line.taxRate) };
}

export function cartTotals(lines: CartLine[]): Totals {
  let sub = 0;
  let tax = 0;
  for (const l of lines) {
    const t = lineTotals(l);
    sub += t.net;
    tax += t.tax;
  }
  return { subtotal: sub / 100, tax: tax / 100, total: (sub + tax) / 100 };
}

/** Add a scanned product, or bump its quantity if it is already in the cart. */
export function addToCart(lines: CartLine[], item: Omit<CartLine, "qty">): CartLine[] {
  const existing = lines.find((l) => l.barcode === item.barcode);
  if (existing) {
    return lines.map((l) => (l.barcode === item.barcode ? { ...l, qty: Math.min(l.qty + 1, l.stockQty) } : l));
  }
  return item.stockQty > 0 ? [...lines, { ...item, qty: 1 }] : lines;
}

export function setQty(lines: CartLine[], barcode: string, qty: number): CartLine[] {
  if (qty <= 0) return lines.filter((l) => l.barcode !== barcode);
  return lines.map((l) => (l.barcode === barcode ? { ...l, qty: Math.min(qty, l.stockQty) } : l));
}

export const money = (v: number, currency = "GBP") =>
  new Intl.NumberFormat("en-GB", { style: "currency", currency }).format(v);
