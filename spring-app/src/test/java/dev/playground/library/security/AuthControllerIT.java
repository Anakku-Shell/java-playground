package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import dev.playground.library.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Registration and login from HTTP to PostgreSQL, with no test shortcuts: the real password encoder,
 * the real authentication manager, real tokens. Guide: §5.7 Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheTables() {
        TestTables.truncateAll(jdbc);
    }

    private MvcTestResult register(String email, String password) {
        return mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "fullName": "Ada Lovelace", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    @Test
    void theHashColumnFitsAStrongerAlgorithmLater() {
        // The {id} prefix lets a new default (Argon2) hash new passwords while old BCrypt ones still
        // match. That only works if the column fits the longer hash: Spring's Argon2 is ~125 chars.
        String argon2 =
                "{argon2@SpringSecurity_v5_8}$argon2id$v=19$m=16384,t=2,p=1$" + "s".repeat(22) + "$" + "h".repeat(43);

        members.saveAndFlush(new Member("ada@library.test", "Ada Lovelace", argon2, Role.MEMBER));

        assertThat(members.findByEmail("ada@library.test").orElseThrow().getPasswordHash())
                .isEqualTo(argon2);
    }

    @Test
    void registerCreatesAMemberWithAHashedPassword() {
        MvcTestResult registered = register("Ada@Library.test", "analytical-engine");

        assertThat(registered)
                .hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, "http://localhost/api/members/1")
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"id": 1, "email": "ada@library.test", "fullName": "Ada Lovelace", "role": "MEMBER"}
                        """);
        assertThat(jdbc.queryForObject("select password_hash from members where id = 1", String.class))
                .startsWith("{bcrypt}$2a$10$");
    }

    @Test
    void anEmailRegistersOnceWhateverItsCase() {
        register("ada@library.test", "analytical-engine");

        assertThat(register("ADA@library.TEST", "another-password"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A member with email ada@library.test already exists");
    }

    @Test
    void aPasswordNeedsEightCharactersAndAtMost72Bytes() {
        assertThat(register("ada@library.test", "short"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors.password")
                .asArray()
                .containsExactly("must be at least 8 characters");
        // 37 characters, 74 bytes: BCrypt would throw on it, so validation stops it first.
        assertThat(register("ada@library.test", "ñ".repeat(37)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors.password")
                .asArray()
                .containsExactly("must be at most 72 bytes in UTF-8");
    }

    @Test
    void loginReturnsATokenThatIdentifiesTheMember() throws Exception {
        register("ada@library.test", "analytical-engine");

        // The email is matched case-insensitively, like at registration.
        MvcTestResult loggedIn = login("Ada@Library.TEST", "analytical-engine");

        assertThat(loggedIn).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"tokenType": "Bearer", "expiresIn": 3600}
                """);
        String token = JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.accessToken");
        assertThat(mvc.get().uri("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": 1, "email": "ada@library.test", "role": "MEMBER"}
                        """);
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() throws Exception {
        register("ada@library.test", "analytical-engine");

        MvcTestResult wrongPassword = login("ada@library.test", "difference-engine");
        MvcTestResult unknownEmail = login("nobody@library.test", "analytical-engine");

        assertThat(wrongPassword)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Invalid email or password.");
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void aMemberWithoutAPasswordCannotLogIn() {
        // Like the rows that existed before V4 added passwords.
        members.save(new Member("old@library.test", "Old Member", null, Role.MEMBER));

        assertThat(login("old@library.test", "anything-at-all")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
