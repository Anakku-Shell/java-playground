package dev.playground.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Every error the API can produce, as a {@code ProblemDetail}. A test-only controller throws each
 * exception; <strong>standalone</strong> MockMvc wires it to the real handler without starting a
 * Spring context. Guide: §5.3 Validation & errors.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build());

    @Test
    void notFoundIs404ProblemDetail() {
        // No "type" member: Spring leaves out the default "about:blank", and RFC 9457 says a missing
        // type means exactly that. "instance" is the request path, filled in by Spring MVC.
        assertThat(mvc.get().uri("/things/7"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Thing 7 not found",
                          "instance": "/things/7"
                        }
                        """);
    }

    @Test
    void conflictIs409() {
        assertThat(mvc.post().uri("/things/conflict"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Thing already exists");
    }

    @Test
    void dataIntegrityViolationIs409WithoutTheSql(CapturedOutput output) {
        // The safety net for races the service checks cannot see: two requests insert the same ISBN
        // at once, both pass existsByIsbn, and the unique constraint rejects the second INSERT.
        assertThat(mvc.post().uri("/things/race"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Conflict",
                          "status": 409,
                          "detail": "The request conflicts with existing data.",
                          "instance": "/things/race"
                        }
                        """);
        // The body says nothing about tables or constraints; the log names them.
        assertThat(output).contains("WARN").contains("books_isbn_key");
    }

    @Test
    void optimisticLockFailureIs409AskingForARetry(CapturedOutput output) {
        // Another transaction changed the row between our read and our write (§5.6). Nothing is
        // wrong with the request itself: sent again, it runs against the new state.
        assertThat(mvc.post().uri("/things/concurrent"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Conflict",
                          "status": 409,
                          "detail": "The data was changed by another request at the same time. Retry the request.",
                          "instance": "/things/concurrent"
                        }
                        """);
        // Expected under load, not a bug: no ERROR log.
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void unknownSortPropertyIs400() {
        // A client mistake (?sort=popularity), not a server bug: without a handler, the catch-all
        // would answer 500.
        assertThat(mvc.get().uri("/things/sorted"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Cannot sort by 'popularity': no such property.",
                          "instance": "/things/sorted"
                        }
                        """);
    }

    @Test
    void externalServiceFailureIs502WithoutTheCause() {
        assertThat(mvc.get().uri("/things/external"))
                .hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Bad Gateway",
                          "status": 502,
                          "detail": "Open Library is unavailable",
                          "instance": "/things/external"
                        }
                        """);
    }

    @Test
    void unexpectedExceptionIs500WithAGenericBodyAndAnErrorLog(CapturedOutput output) {
        assertThat(mvc.get().uri("/things/boom"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Internal Server Error",
                          "status": 500,
                          "detail": "An unexpected error occurred.",
                          "instance": "/things/boom"
                        }
                        """);
        // The client learns nothing about the internals; the log keeps the full stack trace.
        assertThat(output)
                .contains("ERROR")
                .contains("java.lang.IllegalStateException: secret internal detail")
                .contains("at dev.playground.library.common.GlobalExceptionHandlerTest$ThrowingController");
    }

    @Test
    void invalidBodyIs400WithErrorsPerField() {
        // Two broken fields; "code" breaks two constraints.
        MvcTestResult result = mvc.post()
                .uri("/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "code": "toolong"}
                        """)
                .exchange();

        assertThat(result.getMvcResult().getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class);
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of(
                        "name", List.of("must not be blank"),
                        "code", List.of("must match \"[A-Z]*\"", "size must be between 0 and 3")));
        // Map equality ignores order, so check the raw text: keys and messages are sorted, and the same
        // request always gets byte-for-byte the same JSON.
        assertThat(result)
                .bodyText()
                .contains("\"errors\":{\"code\":[\"must match \\\"[A-Z]*\\\"\",\"size must be between 0 and 3\"],"
                        + "\"name\":[\"must not be blank\"]}");
    }

    @Test
    void constraintOnAParameterIs400WithTheParameterName() {
        // @Positive on the @PathVariable itself: Spring's built-in method validation, a different
        // exception (HandlerMethodValidationException), the same "errors" shape.
        assertThat(mvc.get().uri("/things/0"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("id", List.of("must be greater than 0")));
    }

    @Test
    void bodyErrorsKeepTheirShapeWhenMethodValidationRuns() {
        // PUT has @Positive on the id, so method validation takes over and also validates the
        // @Valid body: MethodArgumentNotValidException is not thrown at all.
        MvcTestResult result = mvc.put()
                .uri("/things/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "", "code": "AB"}
                        """)
                .exchange();

        assertThat(result.getMvcResult().getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.errors").isEqualTo(Map.of("name", List.of("must not be blank")));
            // Spring's default detail differs per exception; the handler uses one text for both.
            assertThat(json).extractingPath("$.detail").isEqualTo("Invalid request content.");
        });
    }

    @Test
    void malformedJsonIsAProblemDetailToo() {
        // Handled by ResponseEntityExceptionHandler itself: the base class covers Spring MVC's own
        // exceptions (400, 404, 405, 406, 415...).
        assertThat(mvc.post()
                        .uri("/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Failed to read request");
    }

    record ThingRequest(
            @NotBlank String name,
            @Size(max = 3) @Pattern(regexp = "[A-Z]*") String code) {}

    /**
     * Standalone MockMvc still needs {@code @RestController} (since Spring 6 a class is a handler only
     * with {@code @Controller}). It is a <em>non-static</em> inner class on purpose: component
     * scanning only picks up top-level and static nested classes, so this controller never leaks
     * into the tests that boot the whole application.
     */
    @RestController
    class ThrowingController {

        @GetMapping("/things/{id}")
        void get(@PathVariable @Positive Long id) {
            throw new NotFoundException("Thing", id);
        }

        @PostMapping("/things/conflict")
        void conflict() {
            throw new ConflictException("Thing already exists");
        }

        @PostMapping("/things/race")
        void race() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [ERROR: duplicate key value violates unique constraint"
                            + " \"books_isbn_key\"]");
        }

        @PostMapping("/things/concurrent")
        void concurrent() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 7L);
        }

        @GetMapping("/things/sorted")
        void sorted() {
            // What Spring Data throws when ?sort= names something the entity does not have.
            throw new PropertyReferenceException("popularity", TypeInformation.of(Object.class), List.of());
        }

        @GetMapping("/things/external")
        void external() {
            throw new ExternalServiceException("Open Library", new IOException("connect timed out"));
        }

        @GetMapping("/things/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/things")
        void create(@Valid @RequestBody ThingRequest request) {}

        @PutMapping("/things/{id}")
        void update(@PathVariable @Positive Long id, @Valid @RequestBody ThingRequest request) {}
    }
}
