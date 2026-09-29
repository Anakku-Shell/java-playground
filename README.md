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
./mvnw -pl spring-app spring-boot:run  # start the API on http://localhost:8080
```

On PowerShell, use `.\mvnw` instead of `./mvnw`.

## License

[GPL-3.0](LICENSE)
