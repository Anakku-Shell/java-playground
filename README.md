# java-playground

Sandbox repository to (re)learn modern **Java 25** and **Spring Boot 4**, built chapter by chapter.

- **`java-core`**: plain Java, no framework. Language features written as JUnit tests you can read, run and tweak.
- **`spring-app`**: a library REST API (authors, books, members, loans) that grows one topic at a time: REST, validation, JPA, transactions, security, testing and more.

Everything is explained in **[docs/JAVA_SPRING_GUIDE.md](docs/JAVA_SPRING_GUIDE.md)**.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 (Eclipse Temurin) | `winget install EclipseAdoptium.Temurin.25.JDK` (make sure `JAVA_HOME` is set, see the guide §1) |
| Docker Desktop | any recent | Needed from the persistence chapter on (PostgreSQL, Testcontainers) |
| VS Code | + *Extension Pack for Java* and *Spring Boot Extension Pack* | Recommended extensions are listed in `.vscode/extensions.json` |

Maven does **not** need to be installed: the Maven Wrapper (`mvnw`) downloads the right version.

## Quick start

```bash
./mvnw verify                          # build both modules and run all the checks
./mvnw -pl spring-app spring-boot:run -Dspring-boot.run.profiles=dev   # start the API on http://localhost:8080 with demo data
```

In Windows PowerShell, use `.\mvnw` and quote `-D` arguments that contain a dot: `.\mvnw -pl spring-app spring-boot:run '-Dspring-boot.run.profiles=dev'`.

The API needs a JWT signing secret. The dev profile brings a dev-only one; without that profile, set `LIBRARY_SECURITY_JWT_SECRET` (32+ characters) or the app refuses to start.

**Demo users** (dev profile only, password `demo-password` for all): `librarian@library.test` (LIBRARIAN), and `ada@library.test`, `alan@library.test`, `grace@library.test` (MEMBER). Log in with `POST /api/auth/login` and send the returned token as `Authorization: Bearer <token>`; `spring-app/http/10-security.http` shows how. These credentials exist only for local learning.

With Docker Desktop running, `spring-boot:run` starts PostgreSQL by itself (`spring-app/compose.yaml`), and `verify` runs the integration tests on throwaway containers. `docker compose -f spring-app/compose.yaml down -v` deletes the local data. `verify` also writes a coverage report to `spring-app/target/site/jacoco/index.html`.

On PowerShell, use `.\mvnw` instead of `./mvnw`.

## License

[GPL-3.0](LICENSE)
