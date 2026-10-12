package dev.playground.library.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * The member making the request, read from the {@code Authentication} that the resource server
 * built from the token: its name is the token's subject (the member id), its authorities the roles.
 * Guide: §5.7 Security.
 *
 * @param id the member id
 * @param librarian whether the member has the LIBRARIAN role
 */
public record CurrentMember(Long id, boolean librarian) {

    public static CurrentMember from(Authentication authentication) {
        boolean librarian = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_LIBRARIAN".equals(authority.getAuthority()));
        return new CurrentMember(Long.valueOf(authentication.getName()), librarian);
    }

    /**
     * Who a borrow is for. No member id in the request means the caller. A member may name only
     * themselves; a librarian may borrow on behalf of anyone. A different id from a member is
     * refused (403) rather than silently replaced: the client asked for something it may not do.
     */
    public Long borrowerFor(Long requestedMemberId) {
        if (requestedMemberId == null || requestedMemberId.equals(id)) {
            return id;
        }
        if (librarian) {
            return requestedMemberId;
        }
        throw new AccessDeniedException("Only a librarian can borrow for another member");
    }
}
