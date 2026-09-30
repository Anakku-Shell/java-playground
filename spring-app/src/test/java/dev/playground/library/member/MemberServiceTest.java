package dev.playground.library.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.member.dto.CreateMemberRequest;
import dev.playground.library.member.dto.MemberResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Member rules with the repository mocked. Guide: §5.5 Advanced JPA. */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository repository;

    @InjectMocks
    private MemberService service;

    @Test
    void createStoresTheEmailInLowercase() {
        given(repository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        MemberResponse created = service.create(new CreateMemberRequest("Ada@Library.TEST", "Ada Lovelace"));

        assertThat(created).isEqualTo(new MemberResponse(1L, "ada@library.test", "Ada Lovelace"));
    }

    @Test
    void anEmailIsTakenWhateverItsCase() {
        given(repository.existsByEmail("ada@library.test")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateMemberRequest("ADA@library.test", "Ada")))
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
}
