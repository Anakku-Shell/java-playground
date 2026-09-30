package dev.playground.library.member;

import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.member.dto.CreateMemberRequest;
import dev.playground.library.member.dto.MemberResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member use cases. Registration with a password replaces {@code create} in §5.7 (security).
 * Transactions as in BookService: read-only by default, read-write on {@code create}. Guide: §5.5
 * Advanced JPA.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository repository;

    public MemberService(MemberRepository repository) {
        this.repository = repository;
    }

    public MemberResponse findById(Long id) {
        return MemberMapper.toResponse(repository.findById(id).orElseThrow(() -> new NotFoundException("Member", id)));
    }

    @Transactional
    public MemberResponse create(CreateMemberRequest request) {
        Member member = MemberMapper.toNewMember(request);
        // Like the ISBN check: a race can pass it, and the unique constraint answers with a 409 too.
        if (repository.existsByEmail(member.getEmail())) {
            throw new ConflictException("A member with email " + member.getEmail() + " already exists");
        }
        return MemberMapper.toResponse(repository.save(member));
    }
}
