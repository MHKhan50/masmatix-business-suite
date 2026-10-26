package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masmatix.suite.generated.coaching.Student;
import com.masmatix.suite.generated.coaching.StudentRepository;
import com.masmatix.suite.generated.coaching.StudyBatch;
import com.masmatix.suite.generated.coaching.StudyBatchRepository;
import com.masmatix.suite.generated.construction.LabourDay;
import com.masmatix.suite.generated.construction.LabourDayRepository;
import com.masmatix.suite.generated.construction.SiteMaterial;
import com.masmatix.suite.generated.construction.SiteMaterialRepository;
import com.masmatix.suite.generated.housing.Plot;
import com.masmatix.suite.generated.housing.PlotInstalment;
import com.masmatix.suite.generated.housing.PlotInstalmentRepository;
import com.masmatix.suite.generated.housing.PlotRepository;
import com.masmatix.suite.generated.lab.LabOrder;
import com.masmatix.suite.generated.lab.LabOrderRepository;
import com.masmatix.suite.generated.lab.LabTest;
import com.masmatix.suite.generated.lab.LabTestRepository;
import com.masmatix.suite.generated.lawfirm.LegalCase;
import com.masmatix.suite.generated.lawfirm.LegalCaseRepository;
import com.masmatix.suite.generated.library.Book;
import com.masmatix.suite.generated.library.BookRepository;
import com.masmatix.suite.generated.microfinance.Loan;
import com.masmatix.suite.generated.microfinance.LoanRepaymentRepository;
import com.masmatix.suite.generated.microfinance.LoanRepository;
import com.masmatix.suite.generated.payroll.Employee;
import com.masmatix.suite.generated.payroll.EmployeeRepository;
import com.masmatix.suite.generated.tailoring.TailoringOrder;
import com.masmatix.suite.generated.tailoring.TailoringOrderRepository;
import com.masmatix.suite.generated.travel.TravelBooking;
import com.masmatix.suite.generated.travel.TravelBookingRepository;
import com.masmatix.suite.generated.wholesale.AdvanceOrder;
import com.masmatix.suite.generated.wholesale.AdvanceOrderRepository;
import com.masmatix.suite.generated.wholesale.DeliveryRoute;
import com.masmatix.suite.generated.wholesale.DeliveryRouteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end API tests for the twelve service and trade systems. Each test makes its own rows. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceSystemsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired BookRepository books;
    @Autowired StudyBatchRepository studyBatches;
    @Autowired StudentRepository students;
    @Autowired LabTestRepository labTests;
    @Autowired LabOrderRepository labOrders;
    @Autowired TailoringOrderRepository tailoringOrders;
    @Autowired TravelBookingRepository travelBookings;
    @Autowired DeliveryRouteRepository routes;
    @Autowired AdvanceOrderRepository advanceOrders;
    @Autowired SiteMaterialRepository siteMaterials;
    @Autowired LabourDayRepository labourDays;
    @Autowired PlotRepository plots;
    @Autowired PlotInstalmentRepository instalments;
    @Autowired EmployeeRepository employees;
    @Autowired LoanRepository loans;
    @Autowired LoanRepaymentRepository repayments;
    @Autowired LegalCaseRepository legalCases;

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

    private ResultActions postEmpty(String url, String user) throws Exception {
        return mvc.perform(post(url).header("Authorization", token(user)));
    }

    private ResultActions getAs(String url, String user) throws Exception {
        return mvc.perform(get(url).header("Authorization", token(user)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    @Test
    void libraryIssueRespectsCopiesAndReturnIsOnce() throws Exception {
        Book b = new Book();
        b.setAccessionNumber("T-LIB-1");
        b.setTitle("Test book (sample)");
        b.setCopies(1);
        books.save(b);
        JsonNode loan = read(postJson("/api/library/desk/issue", "staff", Map.of("accessionNumber", "t-lib-1", "borrower", "Test Reader"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueOn").value(today().plusDays(14).toString())));
        postJson("/api/library/desk/issue", "staff", Map.of("accessionNumber", "T-LIB-1", "borrower", "Second Reader"))
                .andExpect(status().isUnprocessableEntity());
        postEmpty("/api/library/desk/loans/" + loan.get("id").asLong() + "/return", "staff")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fine").value(0.0));
        postEmpty("/api/library/desk/loans/" + loan.get("id").asLong() + "/return", "staff")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void coachingOverdueListAndFeePayment() throws Exception {
        StudyBatch batch = new StudyBatch();
        batch.setName("Test batch (sample)");
        batch.setSubject("Maths");
        batch.setMonthlyFee(new BigDecimal("40.00"));
        studyBatches.save(batch);
        Student s = new Student();
        s.setFullName("Test Student Late");
        s.setBatchName("Test batch (sample)");
        s.setFeePaidUntil(today().minusDays(1));
        s = students.save(s);

        JsonNode overdue = read(getAs("/api/coaching/fees/overdue", "staff").andExpect(status().isOk()));
        boolean listed = false;
        for (JsonNode row : overdue) {
            if (row.get("student").get("id").asLong() == s.getId()) {
                listed = true;
                assertThat(row.get("monthsOwed").asInt()).isEqualTo(1);
                assertThat(row.get("amountOwed").decimalValue()).isEqualByComparingTo("40.00");
            }
        }
        assertThat(listed).isTrue();

        postJson("/api/coaching/fees/students/" + s.getId() + "/pay", "staff", Map.of("months", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(80.0))
                .andExpect(jsonPath("$.paidUntil").value(today().minusDays(1).plusMonths(2).toString()));
        postJson("/api/coaching/fees/students/" + s.getId() + "/pay", "staff", Map.of("months", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void labResultIsFlaggedReportedOnceAndPrinted() throws Exception {
        LabTest t = new LabTest();
        t.setCode("T-RANGE");
        t.setName("Test analyte (sample)");
        t.setNormalRange("13.0-17.0");
        labTests.save(t);
        LabOrder o = new LabOrder();
        o.setOrderNumber("T-L-0001");
        o.setPatientName("Test Patient");
        o.setTestCode("T-RANGE");
        o.setCollectedOn(today());
        o.setStatus("COLLECTED");
        o = labOrders.save(o);

        mvc.perform(get("/api/lab/desk/orders/" + o.getId() + "/report.pdf").header("Authorization", token("staff")))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/lab/desk/orders/" + o.getId() + "/result", "staff", Map.of("result", "18.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value("HIGH"))
                .andExpect(jsonPath("$.order.status").value("REPORTED"));
        postJson("/api/lab/desk/orders/" + o.getId() + "/result", "staff", Map.of("result", "15.0"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/lab/desk/orders/" + o.getId() + "/report.pdf").header("Authorization", token("staff")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void tailoringDeliveryNeedsTheExactBalance() throws Exception {
        TailoringOrder o = new TailoringOrder();
        o.setOrderNumber("T-TL-1");
        o.setCustomerName("Test Customer");
        o.setGarment("Shalwar kameez (sample)");
        o.setPrice(new BigDecimal("45.00"));
        o.setAdvancePaid(new BigDecimal("20.00"));
        o.setStatus("TRIAL");
        o = tailoringOrders.save(o);
        String base = "/api/tailoring/desk/orders/" + o.getId();

        postJson(base + "/deliver", "staff", Map.of("payment", 25)).andExpect(status().isUnprocessableEntity());
        postEmpty(base + "/next", "staff").andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("READY"));
        postEmpty(base + "/next", "staff").andExpect(status().isUnprocessableEntity());
        postJson(base + "/deliver", "staff", Map.of("payment", 10)).andExpect(status().isUnprocessableEntity());
        postJson(base + "/deliver", "staff", Map.of("payment", 25)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("DELIVERED"))
                .andExpect(jsonPath("$.balanceDue").value(0.0));
        mvc.perform(get(base + "/slip.pdf").header("Authorization", token("staff")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void travelReminderListsTripsWithMissingDocuments() throws Exception {
        TravelBooking b = new TravelBooking();
        b.setReference("T-TA-1");
        b.setTravellerName("Test Traveller");
        b.setTripType("UMRAH");
        b.setTravelOn(today().plusDays(5));
        b.setPassportReceived(false);
        b.setVisaStatus("SUBMITTED");
        b.setAmount(new BigDecimal("1000.00"));
        b.setPaid(new BigDecimal("1000.00"));
        b = travelBookings.save(b);

        getAs("/api/travel/desk/bookings/" + b.getId() + "/checklist", "staff").andExpect(status().isOk())
                .andExpect(jsonPath("$.missing.length()").value(2))
                .andExpect(jsonPath("$.ready").value(false));
        JsonNode remind = read(getAs("/api/travel/desk/remind", "staff").andExpect(status().isOk()));
        boolean found = false;
        for (JsonNode c : remind) {
            found |= c.get("booking").get("reference").asText().equals("T-TA-1");
        }
        assertThat(found).isTrue();
    }

    @Test
    void wholesaleLoadsTheRouteAndFollowsTheOrderFlow() throws Exception {
        DeliveryRoute r = new DeliveryRoute();
        r.setName("Test route (sample)");
        r.setRouteDay(today().getDayOfWeek().name().substring(0, 3));
        r = routes.save(r);
        AdvanceOrder o = new AdvanceOrder();
        o.setOrderNumber("T-AO-1");
        o.setShopName("Test Shop");
        o.setRouteName("Test route (sample)");
        o.setDeliveryOn(today());
        o.setAmount(new BigDecimal("120.50"));
        o.setStatus("BOOKED");
        o = advanceOrders.save(o);

        postEmpty("/api/wholesale/desk/routes/" + r.getId() + "/load?date=" + today().plusDays(1), "staff")
                .andExpect(status().isUnprocessableEntity());
        postEmpty("/api/wholesale/desk/routes/" + r.getId() + "/load?date=" + today(), "staff")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.total").value(120.5))
                .andExpect(jsonPath("$.orders[0].status").value("LOADED"));
        postJson("/api/wholesale/desk/orders/" + o.getId() + "/status", "staff", Map.of("status", "CANCELLED"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/wholesale/desk/orders/" + o.getId() + "/status", "staff", Map.of("status", "DELIVERED"))
                .andExpect(status().isOk());
    }

    @Test
    void weighbridgeWorksOutNetWeightAndPrintsTheSlip() throws Exception {
        JsonNode slip = read(postJson("/api/weighbridge/desk/weigh", "staff", Map.of("vehicleNumber", "test-01",
                "material", "WHEAT", "grossKg", 24850, "tareKg", 9120))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.netKg").value(15730))
                .andExpect(jsonPath("$.slip.vehicleNumber").value("TEST-01")));
        assertThat(slip.get("slip").get("slipNumber").asText()).matches("WB-\\d{6}");
        postJson("/api/weighbridge/desk/weigh", "staff", Map.of("vehicleNumber", "TEST-02", "material", "WHEAT",
                "grossKg", 9120, "tareKg", 24850)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/weighbridge/desk/slips/" + slip.get("slip").get("id").asLong() + "/slip.pdf")
                        .header("Authorization", token("staff")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        JsonNode day = read(getAs("/api/weighbridge/desk/day?date=" + today(), "staff").andExpect(status().isOk()));
        assertThat(day.get("totalNetKg").asInt()).isGreaterThanOrEqualTo(15730);
    }

    @Test
    void constructionCostsAddUpPerSite() throws Exception {
        SiteMaterial m = new SiteMaterial();
        m.setSite("T-SITE");
        m.setMaterial("CEMENT");
        m.setQuantity(new BigDecimal("12.5"));
        m.setUnitCost(new BigDecimal("3.40"));
        m.setReceivedOn(today());
        siteMaterials.save(m);
        LabourDay l = new LabourDay();
        l.setSite("T-SITE");
        l.setWorkDate(today());
        l.setWorkers(6);
        l.setDailyRate(new BigDecimal("25.00"));
        labourDays.save(l);

        JsonNode costs = read(getAs("/api/construction/desk/costs", "staff").andExpect(status().isOk()));
        JsonNode site = null;
        for (JsonNode c : costs) {
            if (c.get("site").asText().equals("T-SITE")) {
                site = c;
            }
        }
        assertThat(site).isNotNull();
        assertThat(site.get("materials").decimalValue()).isEqualByComparingTo("42.50");
        assertThat(site.get("labour").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(site.get("total").decimalValue()).isEqualByComparingTo("192.50");
    }

    @Test
    void housingTransferWaitsForOverdueInstalmentsAndNeedsAManager() throws Exception {
        Plot p = new Plot();
        p.setPlotNumber("T-P1");
        p.setStatus("ALLOTTED");
        p.setOwnerName("First Owner (sample)");
        p = plots.save(p);
        PlotInstalment i = new PlotInstalment();
        i.setPlotNumber("T-P1");
        i.setDueOn(today().minusDays(3));
        i.setAmount(new BigDecimal("250.00"));
        i = instalments.save(i);
        String transfer = "/api/housing/desk/plots/" + p.getId() + "/transfer";

        postJson(transfer, "staff", Map.of("ownerName", "New Owner")).andExpect(status().isForbidden());
        postJson(transfer, "manager", Map.of("ownerName", "New Owner")).andExpect(status().isUnprocessableEntity());
        postEmpty("/api/housing/desk/instalments/" + i.getId() + "/pay", "staff").andExpect(status().isOk());
        postJson(transfer, "manager", Map.of("ownerName", "New Owner")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSFERRED"))
                .andExpect(jsonPath("$.ownerName").value("New Owner"));
    }

    @Test
    void payrollFinesLateCheckInAndBuildsThePayslip() throws Exception {
        Employee e = new Employee();
        e.setEmployeeCode("T-EMP");
        e.setFullName("Test Employee");
        e.setMonthlySalary(new BigDecimal("1800.00"));
        e.setShiftStart("09:00");
        employees.save(e);

        JsonNode a = read(postJson("/api/payroll/desk/check-in", "staff", Map.of("employeeCode", "T-EMP", "checkIn", "09:15"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutesLate").value(15))
                .andExpect(jsonPath("$.fineAmount").value(2.16)));
        postJson("/api/payroll/desk/check-in", "staff", Map.of("employeeCode", "T-EMP", "checkIn", "09:00"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/payroll/desk/attendance/" + a.get("id").asLong() + "/overtime", "staff", Map.of("minutes", 60))
                .andExpect(status().isOk());
        getAs("/api/payroll/desk/payslip?employeeCode=T-EMP&month=" + YearMonth.from(today()), "manager")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fines").value(2.16))
                .andExpect(jsonPath("$.overtimePay").value(12.98))
                .andExpect(jsonPath("$.net").value(1810.82));
    }

    @Test
    void loanRepaymentTakesLateFeeAndClosesWhenRepaid() throws Exception {
        Loan l = new Loan();
        l.setLoanNumber("T-LN-1");
        l.setBorrowerName("Test Borrower");
        l.setPrincipal(new BigDecimal("300.00"));
        l.setMonthlyInstalment(new BigDecimal("100.00"));
        l.setMonths(3);
        // Two instalments are already due, the third is next month.
        l.setStartsOn(today().minusMonths(2).minusDays(1));
        l.setStatus("ACTIVE");
        l = loans.save(l);
        String base = "/api/microfinance/desk/loans/" + l.getId();

        getAs(base + "/statement", "staff").andExpect(status().isOk())
                .andExpect(jsonPath("$.dueSoFar").value(200.0))
                .andExpect(jsonPath("$.arrears").value(200.0));
        postJson(base + "/repay", "staff", Map.of("amount", 50)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrears").value(150.0))
                .andExpect(jsonPath("$.remaining").value(250.0));
        assertThat(repayments.findAll().stream().filter(r -> r.getLoanNumber().equals("T-LN-1")).findFirst().orElseThrow()
                .getPenalty()).isEqualByComparingTo("4.00");
        postJson(base + "/repay", "staff", Map.of("amount", 300)).andExpect(status().isUnprocessableEntity());
        postJson(base + "/repay", "staff", Map.of("amount", 250)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.loan.status").value("CLOSED"));
        postJson(base + "/repay", "staff", Map.of("amount", 1)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void hearingsRefuseDuplicatesAndAdjournmentAddsTheNextDate() throws Exception {
        LegalCase c = new LegalCase();
        c.setCaseNumber("T-CASE-1");
        c.setTitle("Test v Sample (sample)");
        c.setCourt("Test court");
        c.setClientName("Test Client");
        c.setStatus("OPEN");
        c = legalCases.save(c);
        String add = "/api/lawfirm/desk/cases/" + c.getId() + "/hearings";

        JsonNode h = read(postJson(add, "staff", Map.of("hearingOn", today().toString(), "purpose", "Arguments"))
                .andExpect(status().isCreated()));
        postJson(add, "staff", Map.of("hearingOn", today().toString())).andExpect(status().isUnprocessableEntity());
        postJson(add, "staff", Map.of("hearingOn", today().minusDays(1).toString())).andExpect(status().isUnprocessableEntity());
        postJson("/api/lawfirm/desk/hearings/" + h.get("id").asLong() + "/outcome", "staff",
                Map.of("outcome", "Adjourned for evidence", "nextHearingOn", today().plusDays(7).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADJOURNED"));

        JsonNode cal = read(getAs("/api/lawfirm/desk/calendar?days=14", "staff").andExpect(status().isOk()));
        int mine = 0;
        for (JsonNode day : cal) {
            for (JsonNode e : day.get("hearings")) {
                if (e.get("hearing").get("caseNumber").asText().equals("T-CASE-1")) {
                    mine++;
                }
            }
        }
        assertThat(mine).isEqualTo(2);
    }
}
