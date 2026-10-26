package com.masmatix.suite.lab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Order status flow and result flags against the test's normal range ("4.0-11.0", "< 5.7", "> 60"). */
public final class LabRules {

    public enum Flag { LOW, NORMAL, HIGH, NOT_NUMERIC }

    private static final Pattern RANGE = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern BELOW = Pattern.compile("^\\s*<\\s*=?\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern ABOVE = Pattern.compile("^\\s*>\\s*=?\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern NUMBER = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)");

    private LabRules() {}

    public static Flag flag(String result, String normalRange) {
        if (result == null || normalRange == null) {
            return Flag.NOT_NUMERIC;
        }
        Matcher n = NUMBER.matcher(result);
        if (!n.find()) {
            return Flag.NOT_NUMERIC;
        }
        double v = Double.parseDouble(n.group(1));
        Matcher m = RANGE.matcher(normalRange);
        if (m.find()) {
            double lo = Double.parseDouble(m.group(1));
            double hi = Double.parseDouble(m.group(2));
            return v < lo ? Flag.LOW : v > hi ? Flag.HIGH : Flag.NORMAL;
        }
        m = BELOW.matcher(normalRange);
        if (m.find()) {
            return v > Double.parseDouble(m.group(1)) ? Flag.HIGH : Flag.NORMAL;
        }
        m = ABOVE.matcher(normalRange);
        if (m.find()) {
            return v < Double.parseDouble(m.group(1)) ? Flag.LOW : Flag.NORMAL;
        }
        return Flag.NOT_NUMERIC;
    }

    /** COLLECTED -> IN_PROGRESS -> REPORTED. A reported result is final; a correction needs a new order. */
    public static boolean canMove(String from, String to) {
        String f = from == null ? "COLLECTED" : from;
        return switch (to) {
            case "IN_PROGRESS" -> f.equals("COLLECTED");
            case "REPORTED" -> f.equals("COLLECTED") || f.equals("IN_PROGRESS");
            default -> false;
        };
    }
}
