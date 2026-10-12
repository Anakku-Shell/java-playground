package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

/** Who is calling, from the Authentication the token became. Guide: §5.7 Security. */
class CurrentMemberTest {

    private static final CurrentMember ADA =
            CurrentMember.from(new TestingAuthenticationToken("7", null, "ROLE_MEMBER"));
    private static final CurrentMember LIBRARIAN =
            CurrentMember.from(new TestingAuthenticationToken("4", null, "ROLE_LIBRARIAN"));

    @Test
    void theNameIsTheMemberIdAndTheRoleComesFromTheAuthorities() {
        assertThat(ADA).isEqualTo(new CurrentMember(7L, false));
        assertThat(LIBRARIAN).isEqualTo(new CurrentMember(4L, true));
    }

    @Test
    void withoutAMemberIdABorrowIsForTheCaller() {
        assertThat(ADA.borrowerFor(null)).isEqualTo(7L);
        assertThat(ADA.borrowerFor(7L)).isEqualTo(7L);
    }

    @Test
    void onlyALibrarianBorrowsForSomeoneElse() {
        assertThat(LIBRARIAN.borrowerFor(7L)).isEqualTo(7L);
        assertThatThrownBy(() -> ADA.borrowerFor(8L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only a librarian can borrow for another member");
    }
}
