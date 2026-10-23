package com.masmatix.suite.gym;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.crud.CrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standard member records (list, edit, CSV). Fees and entry are in GymController. */
@RestController
@RequestMapping("/api/gym/members")
public class GymMemberController extends CrudController<GymMember> {
    public GymMemberController(GymMemberRepository repository, AuditService audit) {
        super(repository, audit, "member");
    }
}
