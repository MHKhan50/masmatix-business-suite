package com.masmatix.suite.bakery;

import com.masmatix.suite.generated.bakery.ProductionBatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bakery")
public class BakeryController {

    public record QtyRequest(@NotNull Long itemId, @NotNull @Min(1) @Max(1000) Integer quantity) {}

    private final BakeryService service;

    public BakeryController(BakeryService service) {
        this.service = service;
    }

    @PostMapping("/bake")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionBatch bake(@Valid @RequestBody QtyRequest req) {
        return service.bake(req.itemId(), req.quantity());
    }

    @PostMapping("/sell")
    public BakeryService.SaleResult sell(@Valid @RequestBody QtyRequest req) {
        return service.sell(req.itemId(), req.quantity());
    }

    @GetMapping("/expiry")
    public List<BakeryService.ExpiryRow> expiry() {
        return service.expiryList();
    }

    @PostMapping("/batches/{id}/write-off")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductionBatch writeOff(@PathVariable Long id) {
        return service.writeOff(id);
    }
}
