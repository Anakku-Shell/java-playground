package dev.playground.library;

import static dev.playground.library.testing.TestDataFactory.aBookRequest;
import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.loan.dto.CreateLoanRequest;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import dev.playground.library.member.Role;
import dev.playground.library.member.dto.RegisterRequest;
import dev.playground.library.security.dto.LoginRequest;
import dev.playground.library.security.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The whole application over real HTTP: {@code RANDOM_PORT} starts the embedded Tomcat on a free
 * port, and {@code RestTestClient} (new in Spring Framework 7; older code uses
 * {@code TestRestTemplate}) sends real requests to it. Nothing is mocked: a librarian and a new
 * member log in for real tokens, and a loan goes from borrow to return. (Only the librarian is
 * seeded straight into the database: the API cannot create one.) One journey, not one test per
 * rule: the rules have their own, faster tests; this one proves the pieces fit. Guide: §5.8 Testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ApiSmokeIT {

    private static final String PASSWORD = "smoke-test-password";

    @Autowired
    private RestTestClient client;

    // The port the server picked; @Value("${local.server.port}") underneath.
    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository members;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void aLibrarianWithAPassword() {
        TestTables.truncateAll(jdbc);
        // Librarians cannot register through the API, so this one goes straight to the database.
        members.save(
                new Member("librarian@library.test", "Libby Rarian", passwordEncoder.encode(PASSWORD), Role.LIBRARIAN));
    }

    private String login(String email) {
        TokenResponse token = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        return "Bearer " + token.accessToken();
    }

    private <T> T post(String uri, String bearer, Object body, Class<T> responseType) {
        return client.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(responseType)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void aBookIsCataloguedBorrowedAndReturned() {
        String librarian = login("librarian@library.test");
        AuthorResponse herbert =
                post("/api/authors", librarian, new CreateAuthorRequest("Frank Herbert", 1920), AuthorResponse.class);
        BookResponse dune = post(
                "/api/books", librarian, aBookRequest().authorIds(herbert.id()).create(), BookResponse.class);

        client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("ada@library.test", "Ada Lovelace", PASSWORD))
                .exchange()
                .expectStatus()
                .isCreated();
        String ada = login("ada@library.test");

        LoanResponse loan = post("/api/loans", ada, new CreateLoanRequest(dune.id(), null), LoanResponse.class);

        client.get()
                .uri("/api/members/me/loans")
                .header(HttpHeaders.AUTHORIZATION, ada)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].bookTitle")
                .isEqualTo("Dune");
        client.get()
                .uri("/api/books/{id}", dune.id())
                .header(HttpHeaders.AUTHORIZATION, ada)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(BookResponse.class)
                .value(book -> assertThat(book.availableCopies()).isEqualTo(2));

        client.post()
                .uri("/api/loans/{id}/return", loan.id())
                .header(HttpHeaders.AUTHORIZATION, ada)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoanResponse.class)
                .value(returned -> assertThat(returned.returnedAt()).isNotNull());
    }

    @Test
    void theLocationHeaderPointsAtTheRealServer() {
        // MockMvc has no server, so its Location reads http://localhost/api/authors/1. Here it carries
        // the random port, and a client can follow it as is.
        String librarian = login("librarian@library.test");
        String location = client.post()
                .uri("/api/authors")
                .header(HttpHeaders.AUTHORIZATION, librarian)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAuthorRequest("Mary Beard", 1955))
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        assertThat(location).isEqualTo("http://localhost:" + port + "/api/authors/1");
        client.get()
                .uri(location)
                .header(HttpHeaders.AUTHORIZATION, librarian)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthorResponse.class)
                .isEqualTo(new AuthorResponse(1L, "Mary Beard", 1955));
    }
}
