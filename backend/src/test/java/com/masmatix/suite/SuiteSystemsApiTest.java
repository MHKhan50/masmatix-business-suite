package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masmatix.suite.generated.clinic.MedicineRepository;
import com.masmatix.suite.generated.clinic.PatientRepository;
import com.masmatix.suite.generated.gym.MembershipPlanRepository;
import com.masmatix.suite.generated.hotel.RoomRepository;
import com.masmatix.suite.generated.salon.StylistRepository;
import com.masmatix.suite.generated.salon.TreatmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end API tests for the salon, clinic, gym and hotel systems, using the invented seed data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuiteSystemsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired StylistRepository stylists;
    @Autowired TreatmentRepository treatments;
    @Autowired PatientRepository patients;
    @Autowired MedicineRepository medicines;
    @Autowired MembershipPlanRepository plans;
    @Autowired RoomRepository rooms;

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private ResultActions postJson(String url, String user, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------------- salon

    private Long stylistId(String name) {
        return stylists.findAll().stream().filter(s -> s.getFullName().contains(name)).findFirst().orElseThrow().getId();
    }

    private Long treatmentId(String name) {
        return treatments.findAll().stream().filter(t -> t.getName().startsWith(name)).findFirst().orElseThrow().getId();
    }

    private Map<String, Object> salonBooking(Long stylist, Long treatment, String startsAt) {
        return Map.of("stylistId", stylist, "treatmentId", treatment, "clientName", "Sample Client", "startsAt", startsAt);
    }

    @Test
    void salonPreventsDoubleBookingButAllowsBackToBack() throws Exception {
        Long amna = stylistId("Amna");
        Long cut = treatmentId("Cut and blow-dry"); // 45 minutes
        String day = today().plusDays(3).toString();

        JsonNode first = read(postJson("/api/salon/appointments", "staff", salonBooking(amna, cut, day + "T10:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED")));
        assertThat(first.get("endsAt").asText()).startsWith(day + "T10:45");
        postJson("/api/salon/appointments", "staff", salonBooking(amna, cut, day + "T10:30"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/salon/appointments", "staff", salonBooking(amna, cut, day + "T10:45"))
                .andExpect(status().isCreated());
        // A different stylist is free at the same time.
        postJson("/api/salon/appointments", "staff", salonBooking(stylistId("Ben"), cut, day + "T10:30"))
                .andExpect(status().isCreated());

        JsonNode free = read(mvc.perform(get("/api/salon/availability").header("Authorization", token("staff"))
                .param("date", day).param("stylistId", amna.toString()).param("treatmentId", cut.toString())));
        assertThat(free.toString()).contains("\"09:15\"", "\"11:30\"").doesNotContain("\"10:00\"", "\"10:30\"", "\"11:00\"");
    }

    @Test
    void salonRejectsBadTimesAndFreesCancelledSlots() throws Exception {
        Long chloe = stylistId("Chloe");
        Long massage = treatmentId("Back massage"); // 60 minutes
        String day = today().plusDays(4).toString();

        postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, day + "T08:30"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, day + "T17:30"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, day + "T11:10"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, today().minusDays(1) + "T11:00"))
                .andExpect(status().isUnprocessableEntity());

        JsonNode booked = read(postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, day + "T14:00"))
                .andExpect(status().isCreated()));
        postJson("/api/salon/appointments/" + booked.get("id").asLong() + "/cancel", "staff", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        postJson("/api/salon/appointments", "staff", salonBooking(chloe, massage, day + "T14:00"))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- clinic

    private Long patientId(String name) {
        return patients.findAll().stream().filter(p -> p.getFullName().equals(name)).findFirst().orElseThrow().getId();
    }

    private Long medicineId(String name) {
        return medicines.findAll().stream().filter(m -> m.getName().startsWith(name)).findFirst().orElseThrow().getId();
    }

    private Map<String, Object> item(Long medicine) {
        return Map.of("medicineId", medicine, "dose", "1 capsule", "frequency", "three times a day", "durationDays", 5);
    }

    @Test
    void allergyBlocksPrescriptionUntilOverrideReasonGiven() throws Exception {
        Long allergic = patientId("Sample Patient One"); // allergies: Penicillin
        Long amoxicillin = medicineId("Amoxicillin");    // ingredient: penicillin

        postJson("/api/clinic/prescriptions", "manager", Map.of("patientId", allergic, "items", List.of(item(amoxicillin))))
                .andExpect(status().isUnprocessableEntity());

        JsonNode rx = read(postJson("/api/clinic/prescriptions", "manager", Map.of("patientId", allergic,
                        "allergyOverrideReason", "Sample test: tolerance confirmed", "items", List.of(item(amoxicillin))))
                .andExpect(status().isCreated()));
        assertThat(rx.get("prescriptionNumber").asText()).startsWith("RX");
        assertThat(rx.get("allergyOverrideReason").asText()).contains("tolerance");
    }

    @Test
    void prescriptionPrintsAndOnlyPrescribersCanWrite() throws Exception {
        Long patient = patientId("Sample Patient Two");
        Long paracetamol = medicineId("Paracetamol");
        Map<String, Object> body = Map.of("patientId", patient, "items", List.of(item(paracetamol)));

        postJson("/api/clinic/prescriptions", "staff", body).andExpect(status().isForbidden());
        postJson("/api/clinic/prescriptions", "manager", Map.of("patientId", patient, "items", List.of()))
                .andExpect(status().isBadRequest());

        JsonNode rx = read(postJson("/api/clinic/prescriptions", "manager", body).andExpect(status().isCreated()));
        assertThat(rx.get("items").get(0).get("medicineName").asText()).startsWith("Paracetamol");
        assertThat(rx.get("allergyOverrideReason").isNull()).isTrue();

        byte[] pdf = mvc.perform(get("/api/clinic/prescriptions/" + rx.get("id").asLong() + "/print.pdf")
                        .header("Authorization", token("staff")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void sameMedicineTwiceIsRejected() throws Exception {
        Long paracetamol = medicineId("Paracetamol");
        postJson("/api/clinic/prescriptions", "manager", Map.of("patientId", patientId("Sample Patient Two"),
                        "items", List.of(item(paracetamol), item(paracetamol))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- gym

    private Long planId(String name) {
        return plans.findAll().stream().filter(p -> p.getName().startsWith(name)).findFirst().orElseThrow().getId();
    }

    private JsonNode checkIn(String code) throws Exception {
        return read(postJson("/api/gym/check-in", "staff", Map.of("memberCode", code)).andExpect(status().isOk()));
    }

    @Test
    void expiredMemberIsBlockedUntilFeesArePaid() throws Exception {
        JsonNode member = read(postJson("/api/gym/members", "manager", Map.of(
                "memberCode", "TEST-LAPSED", "fullName", "Sample Lapsed Member",
                "expiresOn", today().minusDays(3).toString())).andExpect(status().isCreated()));

        JsonNode denied = checkIn("test-lapsed");
        assertThat(denied.get("allowed").asBoolean()).isFalse();
        assertThat(denied.get("reason").asText()).startsWith("Expired on");

        JsonNode paid = read(postJson("/api/gym/members/" + member.get("id").asLong() + "/renew", "staff",
                Map.of("planId", planId("Monthly"), "method", "CASH")).andExpect(status().isCreated()));
        // Paid after expiry, so the month starts today.
        assertThat(paid.get("newExpiry").asText()).isEqualTo(today().plusMonths(1).toString());
        assertThat(paid.get("amount").decimalValue()).isEqualByComparingTo("29.99");

        assertThat(checkIn("TEST-LAPSED").get("allowed").asBoolean()).isTrue();

        byte[] pdf = mvc.perform(get("/api/gym/payments/" + paid.get("id").asLong() + "/receipt.pdf")
                        .header("Authorization", token("staff")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void frozenAndUnknownMembersAreBlockedAndEveryAttemptIsLogged() throws Exception {
        assertThat(checkIn("GYM-0003").get("reason").asText()).isEqualTo("Membership is frozen");
        assertThat(checkIn("NOBODY-99").get("reason").asText()).isEqualTo("Unknown member code");
        JsonNode log = read(mvc.perform(get("/api/gym/check-ins").header("Authorization", token("staff"))));
        assertThat(log.toString()).contains("GYM-0003", "NOBODY-99");
    }

    @Test
    void earlyRenewalExtendsFromCurrentExpiry() throws Exception {
        LocalDate expiry = today().plusDays(10);
        JsonNode member = read(postJson("/api/gym/members", "manager", Map.of(
                "memberCode", "TEST-EARLY", "fullName", "Sample Early Payer",
                "expiresOn", expiry.toString())).andExpect(status().isCreated()));
        JsonNode paid = read(postJson("/api/gym/members/" + member.get("id").asLong() + "/renew", "staff",
                Map.of("planId", planId("Quarterly"), "method", "CARD")).andExpect(status().isCreated()));
        assertThat(paid.get("newExpiry").asText()).isEqualTo(expiry.plusMonths(3).toString());
    }

    // ---------------------------------------------------------------- hotel

    private Long roomId(String number) {
        return rooms.findAll().stream().filter(r -> r.getRoomNumber().equals(number)).findFirst().orElseThrow().getId();
    }

    private Map<String, Object> stay(Long room, int guests, LocalDate in, LocalDate out) {
        return Map.of("roomId", room, "guestName", "Sample Guest", "guests", guests,
                "checkIn", in.toString(), "checkOut", out.toString());
    }

    @Test
    void hotelRoomCannotBeSoldTwiceForTheSameNight() throws Exception {
        Long double102 = roomId("102"); // 85.00 a night, sleeps 2
        LocalDate d = today().plusDays(10);

        postJson("/api/hotel/bookings", "staff", stay(double102, 2, d, d.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(170.00))
                .andExpect(jsonPath("$.status").value("RESERVED"));
        postJson("/api/hotel/bookings", "staff", stay(double102, 2, d.plusDays(1), d.plusDays(3)))
                .andExpect(status().isUnprocessableEntity());
        // Arrives on the day the first guest leaves.
        postJson("/api/hotel/bookings", "staff", stay(double102, 1, d.plusDays(2), d.plusDays(3)))
                .andExpect(status().isCreated());
        postJson("/api/hotel/bookings", "staff", stay(double102, 3, d.plusDays(5), d.plusDays(6)))
                .andExpect(status().isUnprocessableEntity());

        JsonNode free = read(mvc.perform(get("/api/hotel/availability").header("Authorization", token("staff"))
                .param("from", d.toString()).param("to", d.plusDays(2).toString()).param("guests", "2")));
        assertThat(free.toString()).doesNotContain("\"roomNumber\":\"102\"").contains("\"roomNumber\":\"103\"")
                .doesNotContain("\"roomNumber\":\"101\""); // 101 sleeps one
    }

    @Test
    void roomNumberIsUniqueAndRateHasAFloor() throws Exception {
        postJson("/api/hotel/rooms", "manager", Map.of("roomNumber", "101", "nightlyRate", 70, "maxGuests", 1))
                .andExpect(status().isConflict());
        postJson("/api/hotel/rooms", "manager", Map.of("roomNumber", "301", "nightlyRate", 0.14, "maxGuests", 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkInCheckOutAndInvoice() throws Exception {
        Long family = roomId("201");
        JsonNode future = read(postJson("/api/hotel/bookings", "staff",
                stay(family, 3, today().plusDays(20), today().plusDays(21))).andExpect(status().isCreated()));
        postJson("/api/hotel/bookings/" + future.get("id").asLong() + "/check-in", "staff", Map.of())
                .andExpect(status().isUnprocessableEntity());

        JsonNode tonight = read(postJson("/api/hotel/bookings", "staff",
                stay(family, 4, today(), today().plusDays(1))).andExpect(status().isCreated()));
        long id = tonight.get("id").asLong();
        postJson("/api/hotel/bookings/" + id + "/check-out", "staff", Map.of()).andExpect(status().isUnprocessableEntity());
        postJson("/api/hotel/bookings/" + id + "/check-in", "staff", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_IN"));
        postJson("/api/hotel/bookings/" + id + "/cancel", "manager", Map.of()).andExpect(status().isUnprocessableEntity());
        postJson("/api/hotel/bookings/" + id + "/check-out", "staff", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHECKED_OUT"));

        byte[] pdf = mvc.perform(get("/api/hotel/bookings/" + id + "/invoice.pdf").header("Authorization", token("staff")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }
}
