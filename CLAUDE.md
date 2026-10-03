# CLAUDE.md

A learning playground for Java 25 + Spring Boot 4.1 (Maven). The owner comes from .NET/EF Core.
The explanations live in `docs/JAVA_SPRING_GUIDE.md`. **Keep it in sync with the code**: any change to a
concept's code updates its guide section.

## Layout

- `pom.xml`: the parent POM (Spring Boot parent, Java version, Spotless, Failsafe). Modules: `java-core`, `spring-app`.
- `java-core/`: plain Java, **no Spring**. Examples are tests: `src/test/java/dev/playground/core/<topic>/<Concept>Test.java`; supporting types go in `src/main/java/dev/playground/core/<topic>/`.
- `spring-app/`: the library API, package `dev.playground.library`, organised **by feature** (`book/`, `author/`, `loan/`, `member/`, `audit/`, `security/`, `notification/`), plus `openlibrary/` (the HTTP client for openlibrary.org), `config/` (cross-cutting beans: caching, scheduling, async…) and `common/` (errors, shared DTOs).
  - A feature holds `XController` (HTTP only), `XService` (rules and transactions), `XRepository`, the entity `X`, `XMapper` (manual static mapping) and `dto/` (records).
  - `src/main/resources/db/migration/`: Flyway migrations (`V<n>__<desc>.sql`); never edit an applied one.
  - `http/NN-topic.http`: sample requests per chapter (VS Code REST Client).

## Conventions

- Everything in English. Comments explain the *why*. A class that introduces a concept points to its guide section.
- Constructor injection only; DTOs are records; entities never leave the service layer.
- Errors are `ProblemDetail`. The schema changes only through Flyway (`ddl-auto: validate`).
- Logging with SLF4J (`LoggerFactory.getLogger`), never `System.out` in `spring-app`.
- No Lombok or MapStruct in main code. They are test-scope dependencies, used only by the labelled legacy examples (`legacy/` test packages in both modules, §6); their annotation processors are configured for `default-testCompile` only.
- Tests: `*Test` = unit/slice with no Docker (Surefire, `./mvnw test`); `*IT` = anything that needs Docker: a full context or a `@DataJpaTest` slice on Testcontainers PostgreSQL, via `@Import(TestcontainersConfiguration.class)` (Failsafe, `./mvnw verify`). AssertJ assertions. Build test data with `testing/TestDataFactory` (entities, `withId`, `aBookRequest()`); full-context tests call `TestTables.truncateAll` first, never `@DirtiesContext`. A test class whose setup differs (another bean override, import, profile or property) adds a Spring context and a container (§5.8).
- Spring Boot 4 splits starters per technology (`spring-boot-starter-webmvc`, `...-webmvc-test`, `...-flyway`…). Check start.spring.io for the exact artifact before adding one.

## Commands

```bash
./mvnw verify                          # compile + unit tests + ITs + Spotless check
./mvnw spotless:apply                  # format the code (run before committing)
./mvnw -pl spring-app spring-boot:run -Dspring-boot.run.profiles=dev  # run the API on :8080 with demo data and users
./mvnw -pl spring-app package -DskipTests      # the executable jar; java -jar needs the database started by hand (§6)
./mvnw -pl java-core test -Dtest=RecordsTest   # one test class
./mvnw -pl spring-app verify -Dit.test=ApiSmokeIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false  # one IT
# verify also writes the JaCoCo coverage report: spring-app/target/site/jacoco/index.html
```

- Docker Desktop must be running for `spring-boot:run` (it starts `spring-app/compose.yaml`) and for the `*IT` tests. The dev profile adds the demo data and users (`db/demo`, password `demo-password`), SQL logging and a dev-only JWT secret; without it the app needs `LIBRARY_SECURITY_JWT_SECRET`.
- Tests never call Open Library: `OpenLibraryStub` (a local HTTP server) stands in for it. Only the import requests in `http/12-beyond-crud.http` and manual runs need internet. Calls to other services go through an HTTP interface with timeouts, never inside a `@Transactional` method (§5.9).
- Endpoints need a bearer token from `POST /api/auth/login` (§5.7). Tests: `@WithMockUser` or `TestUsers` (`jwt()`) when security is not the subject.
- If `java` is not on PATH, set `JAVA_HOME` to the Temurin 25 install folder.

## Git

- All the work happens on `feature/java-spring-playground`; the owner merges it into `develop` (PR) and `develop` into `main` (PR).
- One commit per chapter, Conventional Commits style (`feat(jpa): ...`).
