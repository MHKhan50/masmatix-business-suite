package com.masmatix.suite.clinic;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Compares a patient's recorded allergies (free text, e.g. "Penicillin, nuts") with a
 * medicine's name and ingredient. A simple text check: it supports the prescriber and
 * does not replace a clinical drug-interaction database.
 */
public final class AllergyCheck {

    private AllergyCheck() {}

    public static List<String> allergyTerms(String allergies) {
        if (allergies == null) {
            return List.of();
        }
        return Arrays.stream(allergies.toLowerCase(Locale.ROOT).split("[,;/\\n]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 3 && !s.equals("none") && !s.equals("nkda"))
                .toList();
    }

    /** The first recorded allergy that appears in any of the medicine's terms. */
    public static Optional<String> firstMatch(String allergies, String... medicineTerms) {
        for (String allergy : allergyTerms(allergies)) {
            for (String term : medicineTerms) {
                if (term != null && term.toLowerCase(Locale.ROOT).contains(allergy)) {
                    return Optional.of(allergy);
                }
            }
        }
        return Optional.empty();
    }
}
