package com.masmatix.suite.wholesale;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.wholesale.AdvanceOrder;
import com.masmatix.suite.generated.wholesale.AdvanceOrderRepository;
import com.masmatix.suite.generated.wholesale.DeliveryRoute;
import com.masmatix.suite.generated.wholesale.DeliveryRouteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Loading list per route and day, and the order flow from booked to delivered. */
@RestController
@RequestMapping("/api/wholesale/desk")
public class WholesaleController {

    public record MoveRequest(@NotBlank @Pattern(regexp = "LOADED|DELIVERED|CANCELLED") String status) {}

    public record RouteLoadingList(DeliveryRoute route, LocalDate date, boolean routeRunsThatDay, List<AdvanceOrder> orders,
                              int orderCount, BigDecimal total) {}

    private final DeliveryRouteRepository routes;
    private final AdvanceOrderRepository orders;
    private final AuditService audit;

    public WholesaleController(DeliveryRouteRepository routes, AdvanceOrderRepository orders, AuditService audit) {
        this.routes = routes;
        this.orders = orders;
        this.audit = audit;
    }

    private RouteLoadingList build(Long routeId, LocalDate date) {
        DeliveryRoute r = routes.findById(routeId).orElseThrow(() -> new NotFoundException("route " + routeId + " not found"));
        List<AdvanceOrder> list = orders.findAll().stream()
                .filter(o -> r.getName().equalsIgnoreCase(o.getRouteName()) && date.equals(o.getDeliveryOn())
                        && ("BOOKED".equals(o.getStatus()) || "LOADED".equals(o.getStatus())))
                .sorted(Comparator.comparing(AdvanceOrder::getShopName)).toList();
        BigDecimal total = list.stream().map(o -> o.getAmount() == null ? BigDecimal.ZERO : o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new RouteLoadingList(r, date, WholesaleRules.runsOn(r.getRouteDay(), date), list, list.size(), total);
    }

    @GetMapping("/routes/{id}/loading-list")
    @Transactional(readOnly = true)
    public RouteLoadingList loadingList(@PathVariable Long id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return build(id, date);
    }

    /** Marks every booked order on the list as loaded on the van. */
    @PostMapping("/routes/{id}/load")
    @Transactional
    public RouteLoadingList load(@PathVariable Long id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        RouteLoadingList list = build(id, date);
        if (!list.routeRunsThatDay()) {
            throw new BusinessRuleException(list.route().getName() + " runs on " + list.route().getRouteDay() + ", not " + date.getDayOfWeek());
        }
        int n = 0;
        for (AdvanceOrder o : list.orders()) {
            if (WholesaleRules.canMove(o.getStatus(), "LOADED")) {
                o.setStatus("LOADED");
                n++;
            }
        }
        audit.record("LOAD", "route", id, n + " order(s) loaded for " + date);
        return build(id, date);
    }

    @PostMapping("/orders/{id}/status")
    @Transactional
    public AdvanceOrder move(@PathVariable Long id, @Valid @RequestBody MoveRequest req) {
        AdvanceOrder o = orders.findById(id).orElseThrow(() -> new NotFoundException("order " + id + " not found"));
        if (!WholesaleRules.canMove(o.getStatus(), req.status())) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " cannot go from " + o.getStatus() + " to " + req.status());
        }
        o.setStatus(req.status());
        audit.record(req.status(), "advance order", o.getId(), o.getOrderNumber());
        return o;
    }
}
