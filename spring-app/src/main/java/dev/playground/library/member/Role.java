package dev.playground.library.member;

/**
 * What a member may do. Stored by name in {@code members.role}, and sent in the JWT's {@code roles}
 * claim, which Spring Security turns into the authority {@code ROLE_<name>}: what
 * {@code hasRole('LIBRARIAN')} checks. Guide: §5.7 Security.
 */
public enum Role {
    /** Reads the catalogue, borrows and returns their own loans. */
    MEMBER,
    /** Also manages authors and books, and sees and handles every member's loans. */
    LIBRARIAN
}
