package dev.playground.library.security;

import dev.playground.library.member.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * What Spring Security needs to know about a member to check a password: the login name, the
 * stored hash and the authorities. Built by {@link MemberUserDetailsService}; the entity itself
 * stays in the service layer. The account-state methods ({@code isEnabled}...) keep their
 * {@code true} defaults: this library does not lock accounts. Guide: §5.7 Security.
 *
 * @param id the member's id, which becomes the token's subject
 * @param email the login name
 * @param passwordHash the PasswordEncoder hash ({@code {bcrypt}...})
 * @param role becomes the authority {@code ROLE_<role>}
 */
public record MemberUserDetails(Long id, String email, String passwordHash, Role role) implements UserDetails {

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("X") checks for the authority "ROLE_X": the prefix is how Spring tells roles from
        // finer-grained authorities (such as an OAuth2 token's "SCOPE_read").
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Leaves the hash out, so logging this object never prints it. */
    @Override
    public String toString() {
        return "MemberUserDetails[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
