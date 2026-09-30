package dev.playground.library.member;

import org.springframework.data.jpa.repository.JpaRepository;

/** Members; emails are stored lowercase, so pass a lowercase one. Guide: §5.5 Advanced JPA. */
public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);
}
