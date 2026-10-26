package com.masmatix.suite.wholesale;

import java.time.LocalDate;

/** Route days and the order flow BOOKED -> LOADED -> DELIVERED (or BOOKED -> CANCELLED). */
public final class WholesaleRules {

    private WholesaleRules() {}

    /** "MON" matches a Monday. */
    public static boolean runsOn(String routeDay, LocalDate date) {
        return routeDay != null && date.getDayOfWeek().name().startsWith(routeDay);
    }

    public static boolean canMove(String from, String to) {
        String f = from == null ? "BOOKED" : from;
        return switch (to) {
            case "LOADED", "CANCELLED" -> f.equals("BOOKED");
            case "DELIVERED" -> f.equals("LOADED");
            default -> false;
        };
    }
}
