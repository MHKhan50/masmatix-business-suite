package com.masmatix.suite.imei;

import com.masmatix.suite.generated.imei.WarrantyClaim;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imei")
public class ImeiController {

    public record ClaimRequest(@NotBlank String imei, @NotBlank @Size(max = 2000) String fault) {}

    private final ImeiService service;

    public ImeiController(ImeiService service) {
        this.service = service;
    }

    /** Check digit, handset, warranty status and claim history for one IMEI. */
    @GetMapping("/lookup/{imei}")
    public ImeiService.Lookup lookup(@PathVariable String imei) {
        return service.lookup(imei);
    }

    @PostMapping("/claims/open")
    @ResponseStatus(HttpStatus.CREATED)
    public WarrantyClaim open(@Valid @RequestBody ClaimRequest req) {
        return service.openClaim(req.imei(), req.fault());
    }

    @GetMapping("/warranty-ending")
    public List<ImeiService.Ending> ending(@RequestParam(defaultValue = "30") int days) {
        return service.warrantyEnding(days);
    }
}
