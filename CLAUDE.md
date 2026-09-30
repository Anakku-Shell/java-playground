# CLAUDE.md

A learning playground for Java 25 + Spring Boot 4.1 (Maven). The owner comes from .NET/EF Core.
The explanations live in `docs/JAVA_SPRING_GUIDE.md`. **Keep it in sync with the code**: any change to a
concept's code updates its guide section.

## Layout

- `pom.xml`: the parent POM (Spring Boot parent, Java version, Spotless, Failsafe). Modules: `java-core`, `spring-app`.
- `java-core/`: plain Java, **no Spring**. Examples are tests: `src/test/java/dev/playground/core/<topic>/<Concept>Test.java`; supporting types go in `src/main/java/dev/playground/core/<topic>/`.
- `spring-app/`: the library API, package `dev.playground.library`, organised **by feature** (`book/`, `author/`, `loan/`, `member/`, `security/`), plus `config/` (cross-cutting beans) and `common/` (errors, shared DTOs).
  - A feature holds `XController` (HTTP only), `XService` (rules and transactions), `XRepository`, the entity `X`, `XMapper` (manual static mapping) and `dto/` (records).
  - `src/main/resources/db/migration/`: Flyway migrations (`V<n>__<desc>.sql`); never edit an applied one.
  - `http/NN-topic.http`: sample requests per chapter (VS Code REST Client).

## Conventions

- Everything in English. Comments explain the *why*. A class that introduces a concept points to its guide section.
- Constructor injection only; DTOs are records; entities never leave the service layer.
- Errors are `ProblemDetail`. The schema changes only through Flyway (`ddl-auto: validate`).
- Logging with SLF4J (`LoggerFactory.getLogger`), never `System.out` in `spring-app`.
- No Lombok or MapStruct in main code (only in the labelled legacy examples).
- Tests: `*Test` = unit/slice with no Docker (Surefire, `./mvnw test`); `*IT` = anything that needs Docker: a full context or a `@DataJpaTest` slice on Testcontainers PostgreSQL, via `@Import(TestcontainersConfiguration.class)` (Failsafe, `./mvnw verify`). AssertJ assertions.
- Spring Boot 4 splits starters per technology (`spring-boot-starter-webmvc`, `...-webmvc-test`, `...-flyway`…). Check start.spring.io for the exact artifact before adding one.

## Commands

```bash
./mvnw verify                          # compile + unit tests + ITs + Spotless check
./mvnw spotless:apply                  # format the code (run before committing)
./mvnw -pl spring-app spring-boot:run  # run the API on :8080
./mvnw -pl java-core test -Dtest=RecordsTest   # one test class
```

- Docker Desktop must be running for `spring-boot:run` (it starts `spring-app/compose.yaml`) and for the `*IT` tests. `-Dspring-boot.run.profiles=dev` adds the demo data (`db/demo`) and SQL logging.
- If `java` is not on PATH, set `JAVA_HOME` to the Temurin 25 install folder.

## Git

- All the work happens on `feature/java-spring-playground`; the owner merges it into `develop` (PR) and `develop` into `main` (PR).
- One commit per chapter, Conventional Commits style (`feat(jpa): ...`).
