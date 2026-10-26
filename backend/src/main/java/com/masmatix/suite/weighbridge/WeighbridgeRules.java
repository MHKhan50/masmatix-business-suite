package com.masmatix.suite.weighbridge;

/** Net weight from the two weighings. frontend/src/lib/services.ts mirrors netKg. */
public final class WeighbridgeRules {

    /** Default bridge limit; a real install sets this from the bridge's plate. */
    public static final int MAX_GROSS_KG = 60_000;

    private WeighbridgeRules() {}

    /** Gross is the loaded vehicle, tare the empty one. Throws with a message the operator can act on. */
    public static int netKg(int grossKg, int tareKg) {
        if (tareKg <= 0) {
            throw new IllegalArgumentException("tare must be more than 0 kg");
        }
        if (grossKg > MAX_GROSS_KG) {
            throw new IllegalArgumentException("gross " + grossKg + " kg is over the bridge limit of " + MAX_GROSS_KG + " kg");
        }
        if (grossKg <= tareKg) {
            throw new IllegalArgumentException("gross must be more than tare (were the weighings swapped?)");
        }
        return grossKg - tareKg;
    }

    /** WB-010245 -> WB-010246. */
    public static String nextSlip(String last) {
        int n = 0;
        if (last != null && last.matches("WB-\\d{6}")) {
            n = Integer.parseInt(last.substring(3));
        }
        return String.format("WB-%06d", n + 1);
    }
}
