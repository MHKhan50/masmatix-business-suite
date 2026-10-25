package com.masmatix.suite.imei;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.crud.CrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standard handset records (list, edit, CSV). Lookup and claims are in ImeiController. */
@RestController
@RequestMapping("/api/imei/handsets")
public class HandsetController extends CrudController<Handset> {
    public HandsetController(HandsetRepository repository, AuditService audit) {
        super(repository, audit, "handset");
    }
}
