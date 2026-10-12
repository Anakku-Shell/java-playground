package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import dev.playground.library.member.Role;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * How a login finds the member. Spring Security calls {@code loadUserByUsername} and then compares
 * the password against {@code getPassword()} itself. Guide: §5.7 Security.
 */
@ExtendWith(MockitoExtension.class)
class MemberUserDetailsServiceTest {

    @Mock
    private MemberRepository members;

    @InjectMocks
    private MemberUserDetailsService service;

    @Test
    void aMemberBecomesUserDetailsWithTheRoleAsAuthority() {
        Member grace = new Member("grace@library.test", "Grace Hopper", "{bcrypt}$2a$10$hash", Role.LIBRARIAN);
        ReflectionTestUtils.setField(grace, "id", 3L);
        // The login name is compared lowercase, like the stored emails.
        given(members.findByEmail("grace@library.test")).willReturn(Optional.of(grace));

        UserDetails user = service.loadUserByUsername("Grace@Library.test");

        assertThat(user)
                .isEqualTo(new MemberUserDetails(3L, "grace@library.test", "{bcrypt}$2a$10$hash", Role.LIBRARIAN));
        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_LIBRARIAN");
        assertThat(user.toString()).doesNotContain("hash");
    }

    @Test
    void anUnknownEmailIsNotFound() {
        given(members.findByEmail("nobody@library.test")).willReturn(Optional.empty());

        // Spring Security turns this into BadCredentialsException, the same as a wrong password.
        assertThatThrownBy(() -> service.loadUserByUsername("nobody@library.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void aMemberWithoutAPasswordCannotLogIn() {
        given(members.findByEmail("old@library.test"))
                .willReturn(Optional.of(new Member("old@library.test", "From before V4", null, Role.MEMBER)));

        assertThatThrownBy(() -> service.loadUserByUsername("old@library.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
