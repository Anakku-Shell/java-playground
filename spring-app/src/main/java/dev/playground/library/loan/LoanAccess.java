package dev.playground.library.loan;

import dev.playground.library.security.CurrentMember;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Ownership checks for method security. {@code @PreAuthorize} expressions call a bean by name with
 * {@code @loanAccess.isOwner(...)}: a role alone cannot say whose loan it is, so the rule needs
 * data. Guide: §5.7 Security.
 */
@Component("loanAccess")
public class LoanAccess {

    private final LoanRepository loans;

    public LoanAccess(LoanRepository loans) {
        this.loans = loans;
    }

    /**
     * False for a loan that does not exist too, so a member gets a 403 for any loan that is not
     * theirs, existing or not: the answer does not reveal which loan ids exist.
     */
    public boolean isOwner(Long loanId, Authentication authentication) {
        return loans.existsByIdAndMemberId(
                loanId, CurrentMember.from(authentication).id());
    }
}
