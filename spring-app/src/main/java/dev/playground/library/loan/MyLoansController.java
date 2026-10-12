package dev.playground.library.loan;

import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.security.CurrentMember;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own loans, {@code ?active=} optional. Under {@code /api/members/me}, but in the loan
 * feature: loans are this package's business, and the member package stays free of it. A member
 * sees only their own loans here; {@code GET /api/loans} (everyone's) is for librarians. Guide:
 * §5.7 Security.
 */
@RestController
public class MyLoansController {

    private final LoanService service;

    public MyLoansController(LoanService service) {
        this.service = service;
    }

    @GetMapping("/api/members/me/loans")
    public List<LoanResponse> myLoans(Authentication authentication, @RequestParam(required = false) Boolean active) {
        return service.findAll(CurrentMember.from(authentication).id(), active);
    }
}
