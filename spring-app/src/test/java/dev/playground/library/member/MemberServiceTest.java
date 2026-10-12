package dev.playground.library.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.member.dto.MemberResponse;
import dev.playground.library.member.dto.RegisterRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Member rules with the repository mocked and the real password encoder: hashing is the point of
 * registration, so a mocked encoder would test nothing. Guide: §5.5 Advanced JPA, §5.7 Security.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository repository;

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private MemberService service;

    @BeforeEach
    void setUp() {
        service = new MemberService(repository, encoder);
    }

    @Test
    void registerStoresALowercaseEmailAndAHashNotThePassword() {
        given(repository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        MemberResponse created =
                service.register(new RegisterRequest("Ada@Library.TEST", "Ada Lovelace", "analytical-engine"));

        assertThat(created).isEqualTo(new MemberResponse(1L, "ada@library.test", "Ada Lovelace", Role.MEMBER));
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        then(repository).should().save(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        // The algorithm's id, then the BCrypt hash: $2a$ (version), 10 (cost), 22 chars of salt, the hash.
        assertThat(hash).startsWith("{bcrypt}$2a$10$").doesNotContain("analytical-engine");
        assertThat(encoder.matches("analytical-engine", hash)).isTrue();
    }

    @Test
    void theSamePasswordHashesDifferentlyEachTime() {
        // A random salt per hash: two members with the same password have different hashes, so a
        // table of precomputed hashes is useless.
        assertThat(encoder.encode("analytical-engine")).isNotEqualTo(encoder.encode("analytical-engine"));
    }

    @Test
    void anEmailIsTakenWhateverItsCase() {
        given(repository.existsByEmail("ada@library.test")).willReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ADA@library.test", "Ada", "analytical-engine")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A member with email ada@library.test already exists");
        then(repository).should(never()).save(any());
    }

    @Test
    void missingIdIsNotFound() {
        given(repository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member 9 not found");
    }

    @Test
    void theRequestNeverPrintsThePassword() {
        // A record's generated toString lists every component; RegisterRequest overrides it, so a
        // log line or an error message with the request in it does not leak the password.
        assertThat(new RegisterRequest("ada@library.test", "Ada", "analytical-engine").toString())
                .doesNotContain("analytical-engine");
    }
}
