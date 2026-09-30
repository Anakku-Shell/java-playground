package dev.playground.library.member;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Members from HTTP to PostgreSQL. Guide: §5.5 Advanced JPA. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheTables() {
        TestTables.truncateAll(jdbc);
    }

    private MvcTestResult create(String email, String fullName) {
        return mvc.post()
                .uri("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "fullName": "%s"}
                        """.formatted(email, fullName))
                .exchange();
    }

    @Test
    void theLocationOfANewMemberCanBeRead() {
        MvcTestResult created = create("Ada@Library.test", "Ada Lovelace");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String location = created.getResponse().getHeader("Location");
        assertThat(mvc.get().uri(location)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"email": "ada@library.test", "fullName": "Ada Lovelace"}
                """);
    }

    @Test
    void anEmailIsUniqueWhateverItsCase() {
        create("ada@library.test", "Ada Lovelace");

        assertThat(create("ADA@library.TEST", "Someone else"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A member with email ada@library.test already exists");
    }

    @Test
    void anInvalidEmailIs400() {
        assertThat(create("not-an-email", "Ada")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
