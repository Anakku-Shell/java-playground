package dev.playground.library.member;

import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.member.dto.MemberResponse;
import dev.playground.library.member.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member use cases. Transactions as in BookService: read-only by default, read-write on
 * {@code register}. Guide: §5.5 Advanced JPA, §5.7 Security.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository repository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public MemberResponse findById(Long id) {
        return MemberMapper.toResponse(repository.findById(id).orElseThrow(() -> new NotFoundException("Member", id)));
    }

    /** A new MEMBER with a hashed password. The password itself is never stored. */
    @Transactional
    public MemberResponse register(RegisterRequest request) {
        String email = MemberMapper.normalizeEmail(request.email());
        // Like the ISBN check: a race can pass it, and the unique constraint answers with a 409 too.
        if (repository.existsByEmail(email)) {
            throw new ConflictException("A member with email " + email + " already exists");
        }
        Member member = MemberMapper.toNewMember(request, passwordEncoder.encode(request.password()));
        return MemberMapper.toResponse(repository.save(member));
    }
}
