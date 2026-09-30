package dev.playground.library.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Members; emails are stored lowercase, so pass a lowercase one. Guide: §5.5 Advanced JPA. */
public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    /**
     * The member for a borrow (§5.6): bumps the member's version at commit, like
     * {@code BookRepository.findWithVersionIncrementById} does for the book. The book's version
     * guards the copies rule; this one guards the max-active rule, which two borrows of two
     * different books by the same member would otherwise both pass.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<Member> findWithVersionIncrementById(Long id);
}
