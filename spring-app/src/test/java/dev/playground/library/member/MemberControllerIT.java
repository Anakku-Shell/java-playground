package dev.playground.library.member;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.TestTables;
import dev.playground.library.TestUsers;
import dev.playground.library.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Members from HTTP to PostgreSQL. Registration is in {@code AuthControllerIT}. Guide: §5.5
 * Advanced JPA, §5.7 Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member ada;

    @BeforeEach
    void oneMember() {
        TestTables.truncateAll(jdbc);
        ada = members.save(new Member("ada@library.test", "Ada Lovelace", "{noop}unused", Role.MEMBER));
    }

    @Test
    void meIsWhoeverTheTokenBelongsTo() {
        // The response never carries the password hash.
        assertThat(mvc.get().uri("/api/members/me").with(TestUsers.member(ada)))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"id": %d, "email": "ada@library.test", "fullName": "Ada Lovelace", "role": "MEMBER"}
                        """.formatted(ada.getId()));
    }

    @Test
    void anyMemberIsForLibrariansOnly() {
        assertThat(mvc.get().uri("/api/members/{id}", ada.getId()).with(TestUsers.librarian()))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/members/{id}", ada.getId()).with(TestUsers.member(ada)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
