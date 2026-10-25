package com.masmatix.suite.bakery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure shelf-life rules and first-expiry-first-out allocation. frontend/src/lib/shops.ts mirrors expiry status. */
public final class BakeryRules {

    public enum Status { EXPIRED, LAST_DAY, TOMORROW, OK }

    /** A batch with stock left, already sorted by expiry (oldest first). */
    public record Stock(long batchId, String batchCode, LocalDate expiresOn, int quantity) {}

    public record Take(long batchId, String batchCode, int quantity) {}

    private BakeryRules() {}

    /** Baked on 24 Oct with a 3-day shelf life: may be sold up to and including 27 Oct. */
    public static LocalDate expiresOn(LocalDate bakedOn, int shelfLifeDays) {
        if (shelfLifeDays < 1) {
            throw new IllegalArgumentException("shelf life must be at least 1 day");
        }
        return bakedOn.plusDays(shelfLifeDays);
    }

    public static Status status(LocalDate expiresOn, LocalDate today) {
        if (today.isAfter(expiresOn)) {
            return Status.EXPIRED;
        }
        if (today.isEqual(expiresOn)) {
            return Status.LAST_DAY;
        }
        return today.plusDays(1).isEqual(expiresOn) ? Status.TOMORROW : Status.OK;
    }

    /**
     * Takes the quantity from the batches that expire first, skipping expired ones.
     * Throws if fresh stock is not enough, so nothing is half-sold.
     */
    public static List<Take> allocate(List<Stock> oldestFirst, int quantity, LocalDate today) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        List<Take> takes = new ArrayList<>();
        int left = quantity;
        for (Stock s : oldestFirst) {
            if (left == 0) {
                break;
            }
            if (s.quantity() <= 0 || status(s.expiresOn(), today) == Status.EXPIRED) {
                continue;
            }
            int n = Math.min(left, s.quantity());
            takes.add(new Take(s.batchId(), s.batchCode(), n));
            left -= n;
        }
        if (left > 0) {
            throw new IllegalStateException("Only " + (quantity - left) + " fresh in stock");
        }
        return takes;
    }
}
