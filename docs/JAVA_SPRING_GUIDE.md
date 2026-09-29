# Java & Spring Guide

Companion notes for this playground. It targets **Java 25** and **Spring Boot 4.1**, and it points out what looks different in older codebases (Java 8/11/17, Spring Boot 2/3). Each chapter of the code has a matching subsection here.

## Contents

1. [Setup](#1-setup)
2. [Project anatomy](#2-project-anatomy)
3. [How a Spring Boot app runs](#3-how-a-spring-boot-app-runs)
4. [Modern Java](#4-modern-java)
5. [Spring](#5-spring)
6. [Legacy](#6-legacy)
7. [References](#7-references)
8. [Appendix: Coming from .NET](#appendix-coming-from-net)

---

## 1. Setup

### The JDK

- **JDK** (Java Development Kit) = the compiler (`javac`), the runtime (the **JVM**, `java`) and the standard library. A JRE (runtime only) is not enough to build.
- Several vendors ship builds of the same OpenJDK source code: Eclipse Temurin, Oracle, Amazon Corretto, Microsoft… They are interchangeable. This project uses **Eclipse Temurin 25**:
  ```bash
  winget install EclipseAdoptium.Temurin.25.JDK
  ```
- **LTS versions** (long-term support) are 8, 11, 17, 21 and **25**. Companies stay on LTS releases, which is why job ads say "Java 8+", "Java 17+", and so on. A new version ships every six months; one out of four is LTS.
- `JAVA_HOME` points to the JDK folder, and the JDK's `bin` folder goes on the `PATH`. The Temurin installer always adds `bin` to the `PATH`, but setting `JAVA_HOME` is an **optional installer feature** ("Set JAVA_HOME variable"). Enable it, or set the variable by hand. `mvnw` uses `JAVA_HOME` when it is set and falls back to `java` on the `PATH`. To get both from winget in one go:
  ```bash
  winget install EclipseAdoptium.Temurin.25.JDK --override "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /quiet"
  ```
- Terminals and editors opened **before** the install do not see the new variables: restart them. Check with `java -version`.
- **Pinned versions** in this repo: Spring Boot **4.1.1** (the parent in the root `pom.xml`) and Maven **3.9.16** (`.mvn/wrapper/maven-wrapper.properties`).

### Maven and the Maven Wrapper

- **Maven** is the build tool: it downloads dependencies, compiles, runs the tests and packages the result. Everything is described in `pom.xml` (see [§2](#2-project-anatomy)).
- Maven is **not installed** on the machine. The repo ships the **Maven Wrapper**:
  - `mvnw` (Bash) and `mvnw.cmd` (Windows) are small scripts;
  - `.mvn/wrapper/maven-wrapper.properties` pins the Maven version;
  - the first run downloads that version to `~/.m2/wrapper` and uses it from then on.

  It works like `npx`: everyone gets the same tool version with nothing global to install.
- Downloaded libraries are cached in `~/.m2/repository` (like the NuGet cache in `~/.nuget/packages`).
- `.mvn/jvm.config` holds JVM options for Maven itself. Here it allows `sun.misc.Unsafe` memory access, which silences a JDK 25 warning triggered by the Spotless plugin.
  - **Gotcha:** that flag only exists on JDK 23+. If `JAVA_HOME` points to an older JDK (17, 21), every `mvnw` call fails with *"Unrecognized option: --sun-misc-unsafe-memory-access"* instead of a clear "wrong Java version" error. The fix is to point `JAVA_HOME` at JDK 25.

### VS Code

Install the recommended extensions (VS Code offers them when the folder opens; the list is in `.vscode/extensions.json`):

| Extension | What it gives you |
|---|---|
| *Extension Pack for Java* (Microsoft) | Language server (errors, completion, refactors), debugger, test runner, Maven view |
| *Spring Boot Extension Pack* (Broadcom) | `application.yml` completion, Spring Boot Dashboard (run/stop apps), beans and endpoints view |
| *REST Client* | Runs the `spring-app/http/*.http` request files ("Send Request" above each request) |
| *EditorConfig* | Applies `.editorconfig` (indentation, line endings) |

Everyday use:
- **Run or debug the API:** Run and Debug view → *Spring Boot: LibraryApplication* (defined in `.vscode/launch.json`), or the Spring Boot Dashboard. Breakpoints work as in any debugger.
- **Run tests:** the Testing view (flask icon) or the ▶ gutter icons next to each test.
- After editing a `pom.xml`, the Java extension re-imports the project automatically (`java.configuration.updateBuildConfiguration: automatic`). If things look stale: *Java: Clean Java Language Server Workspace*.
- Formatting is owned by **Spotless** (`./mvnw spotless:apply`), so format-on-save is off for Java.

> **IntelliJ IDEA** is the most common Java IDE in companies. Its free tier covers Java and Maven; Spring-specific tooling is in the paid tier. It opens this project as is (*Open* → the root `pom.xml`). Nothing in the repo depends on the IDE.

### Docker Desktop

From the persistence chapter on, PostgreSQL runs in Docker. Spring Boot starts it automatically (see [§5.4](#54-persistence-with-jpa)), and the integration tests use throwaway containers (Testcontainers). Docker Desktop only needs to be **running**.

### Everyday commands

Run them from the repo root. On PowerShell, use `.\mvnw` instead of `./mvnw`.

| Command | What it does |
|---|---|
| `./mvnw verify` | Full check: compile, unit tests, integration tests, formatting check. **The "is everything OK?" command.** |
| `./mvnw test` | Compile and run the unit tests only (fast, no Docker) |
| `./mvnw spotless:apply` | Format all Java code |
| `./mvnw -pl spring-app spring-boot:run` | Run the API on http://localhost:8080 (`Ctrl+C` stops it) |
| `./mvnw -pl java-core test -Dtest=SanityTest` | Run one test class (`-Dtest=Class#method` for one method) |
| `./mvnw clean` | Delete the `target/` folders |
| `./mvnw -pl spring-app package` | Build the executable jar in `spring-app/target/` |
| `./mvnw dependency:tree -pl spring-app` | Show every dependency and where it comes from |

`-pl` (*projects list*) selects a module; `-q` makes the output quiet; `-o` works offline.

---

## 2. Project anatomy

```
java-playground/
  pom.xml                 # parent POM: shared config + list of modules
  mvnw, mvnw.cmd, .mvn/   # Maven Wrapper
  java-core/
    pom.xml
    src/main/java/...     # production code
    src/test/java/...     # tests
  spring-app/
    pom.xml
    src/main/java/...
    src/main/resources/   # application.yml, SQL migrations... (copied onto the classpath)
    src/test/java/...
    target/               # build output (git-ignored)
  docs/JAVA_SPRING_GUIDE.md
```

### The POM

`pom.xml` (*Project Object Model*) is the equivalent of a `.csproj`. Its key parts:

- **Coordinates:** `groupId` (`dev.playground`, usually a reversed domain) + `artifactId` (`spring-app`) + `version` (`0.0.1-SNAPSHOT`) identify an artifact uniquely, like a NuGet package ID + version. `-SNAPSHOT` means "work in progress, not a release".
- **`<dependencies>`:** the libraries the module uses (like `<PackageReference>`). They are downloaded from **Maven Central** (the equivalent of nuget.org), with their transitive dependencies.
- **`<build><plugins>`:** everything Maven does is done by a plugin (compiler, surefire for tests, spring-boot for `run`/packaging, spotless…).
- **`<properties>`:** variables used across the file (`${java.version}`).

### Parent POM and modules

This repo is a **multi-module** build, like a `.sln` with two projects:

- The **root `pom.xml`** has `<packaging>pom</packaging>` (it produces nothing itself) and a `<modules>` list. Running `./mvnw verify` at the root builds every module in dependency order (the *reactor*).
- Each module's `<parent>` points to the root POM, so it **inherits** its properties, dependency management and plugins.
- The root POM itself inherits from **`spring-boot-starter-parent`**, which brings:
  - **dependency management** (a *BOM*, "bill of materials"): a curated list of versions that are tested together. That is why most `<dependency>` entries have no `<version>`: the BOM decides it;
  - plugin defaults (compiler release from `java.version`, UTF-8, Surefire…).
- `java-core` inherits the Spring Boot parent only to get the JUnit and AssertJ versions. It declares **no Spring dependency**, so it stays plain Java.

### Dependency scopes

| Scope | On the compile classpath | In tests | Packaged | Example |
|---|---|---|---|---|
| `compile` (default) | ✅ | ✅ | ✅ | `spring-boot-starter-webmvc` |
| `test` | ❌ | ✅ | ❌ | JUnit, AssertJ |
| `runtime` | ❌ | ✅ | ✅ | the PostgreSQL JDBC driver |
| `provided` | ✅ | ✅ | ❌ | the servlet API in an old `.war` (the server supplies it) |

### Starters

A **starter** is a dependency that pulls in a coherent set of libraries plus their auto-configuration. `spring-boot-starter-webmvc` brings Spring MVC, Jackson (JSON) and embedded Tomcat. Since **Spring Boot 4**, starters are split more finely per technology, and most have a matching `-test` starter for their test slice (`spring-boot-starter-webmvc-test`). In Boot 2/3 you used `spring-boot-starter-web` plus one catch-all `spring-boot-starter-test`. Both names still exist in Boot 4 (`-web` as a deprecated alias), so you will see them in older code.

### The build lifecycle

Maven runs **phases** in a fixed order. Asking for one runs every phase before it:

```
validate → compile → test → package → integration-test → verify → install → deploy
              │         │        │             │              │
              │         │        │             │              └─ fail on IT failures + Spotless check
              │         │        │             └─ integration tests (Failsafe, *IT)
              │         │        └─ jar in target/
              │         └─ unit tests (Surefire, *Test)
              └─ javac: src/main/java → target/classes
```

(Simplified: there are more phases in between, such as `test-compile`, `pre-integration-test` and `post-integration-test`.)

- `./mvnw verify` therefore compiles, runs the unit tests, packages, runs the integration tests and checks formatting.
- `install` copies the artifact into `~/.m2/repository` (not needed here); `deploy` publishes it to a remote repository (not used).
- A **goal** is a single plugin action called directly, e.g. `spotless:apply`. Some goals run part of the lifecycle first: `spring-boot:run` runs everything up to `test-compile`, which is why your code is always compiled before the app starts.

### Conventions over configuration

Maven expects the standard layout (`src/main/java`, `src/main/resources`, `src/test/java`). Nothing has to be configured for it. Everything in `src/main/resources` ends up on the **classpath**, the list of folders and jars the JVM searches for classes and resource files. That is how Spring finds `application.yml`.

### Tooling in this repo

- **Spotless** (root POM) formats Java code with *Palantir Java Format* (4-space indentation, 120 columns) and forces LF line endings. `spotless:check` runs in `verify` and fails the build on unformatted code; `spotless:apply` fixes it.
- **Failsafe** (root POM) runs `*IT` classes after packaging. Unit tests (`*Test`) run earlier with Surefire, so `./mvnw test` stays fast and needs no Docker.
- **Mockito agent** (`spring-app/pom.xml`): Mockito instruments classes with a Java agent. Recent JDKs warn when an agent is attached at runtime, so the POM passes it to the test JVM up front (`-javaagent:...`), as Mockito's documentation recommends.
  - **Gotcha:** the agent path is computed in the `initialize` phase, so run tests through a phase (`./mvnw test`, `verify`, `-Dtest=...`), not by calling the `surefire:test` goal on its own.

---

## 3. How a Spring Boot app runs

### Java, Spring, Spring Boot, Tomcat: who does what

| Piece | What it is | .NET analogy |
|---|---|---|
| **Java / JVM** | The language and the runtime that executes bytecode (`.class` files) | C# / CLR |
| **Spring Framework** | A library: the IoC container (dependency injection), Spring MVC for web, transactions, data access… | ASP.NET Core's DI + MVC building blocks |
| **Spring Boot** | An opinionated layer on top of Spring: auto-configuration, starters, embedded server, externalised config | The "minimal hosting" of `WebApplication.CreateBuilder()` |
| **Tomcat** | A *servlet container*: the HTTP server that runs Java web code. Apache **Tomcat**, not the Apache HTTPD web server | Kestrel |

### What happens when you run the app

A simplified order (in reality Tomcat is created during the context refresh, and it only opens the port at the end):

```
java -jar spring-app.jar   (or ./mvnw spring-boot:run, or F5 in VS Code)
 │
 ▼
JVM starts → calls LibraryApplication.main(args)
 │
 ▼
SpringApplication.run(LibraryApplication.class, args)
 │
 ├─ 1. Prepares the Environment: application.yml, profile files, env vars, command-line args
 ├─ 2. Creates the ApplicationContext (the IoC container)
 ├─ 3. @ComponentScan: finds @Component/@Service/@RestController… under dev.playground.library
 ├─ 4. @EnableAutoConfiguration: for each library on the classpath, adds the beans it needs
 │       (Spring MVC on the classpath → DispatcherServlet, JSON converters, embedded Tomcat…)
 ├─ 5. Instantiates the beans and injects their dependencies
 ├─ 6. Starts embedded Tomcat on server.port (8080) and registers the DispatcherServlet
 │
 ▼
"Started LibraryApplication in 0.7 seconds" → ready for HTTP requests
```

An HTTP request then goes through `Tomcat → filter chain → DispatcherServlet → @RestController method`, and the return value is serialised to JSON.

`@SpringBootApplication` on `LibraryApplication` is shorthand for three annotations:
- `@SpringBootConfiguration`: a specialised `@Configuration` (this class can declare beans). It is also the marker `@SpringBootTest` searches for; a test outside the package tree fails with *"Unable to find a @SpringBootConfiguration"*;
- `@EnableAutoConfiguration`: turns step 4 on;
- `@ComponentScan`: turns step 3 on, starting from this class's package.

Classes outside `dev.playground.library` are **not** scanned. That is why every feature package lives under it.

### The old model: a `.war` in a standalone Tomcat

Before Spring Boot (and in many legacy systems still):

```
Standalone Tomcat installed on the server (its own process, its own config)
  └─ webapps/
       ├─ app-one.war   ← your app, packaged as a Web ARchive, without a server inside
       └─ app-two.war
```

- You built a `.war` and copied it into a Tomcat that the ops team installed and configured.
- Tomcat owned the process and the port, and it called into your app.

Spring Boot inverts this: **your app owns `main()` and starts an embedded Tomcat inside its own process**. You get one self-contained executable jar (the *fat jar*: your classes plus every dependency plus Tomcat), started with `java -jar`. It is the same shift as going from IIS-hosted ASP.NET to a self-hosted Kestrel app. The legacy chapter ([§6](#6-legacy)) shows how a Boot app can still be packaged as a `.war`.

### Where the playground is right now

`LibraryApplication` starts, Tomcat listens on port 8080, and any URL answers **404** because no controller exists yet. `LibraryApplicationTest` (`@SpringBootTest`) boots the same context in a test, so a broken configuration fails the build.

---

## 4. Modern Java

### 4.1 Modern language

**What it is.** The core language, plus the features added since Java 8 that change how modern code looks. Every example is a JUnit test in [`java-core/src/test/java/dev/playground/core/language/`](../java-core/src/test/java/dev/playground/core/language/), and the small types it uses are in the matching [`src/main`](../java-core/src/main/java/dev/playground/core/language/) package. Run them all with `./mvnw -pl java-core test`, or one class with `-Dtest=RecordsTest`. Break an assertion and see what happens.

| Feature | Since Java | Example |
|---|---|---|
| Generics, enums, autoboxing, for-each | 5 | `GenericsTest`, `EnumsTest` |
| Lambdas, `default`/`static` interface methods | 8 | `ClassesAndInterfacesTest` |
| `var` | 10 | `VarAndTextBlocksTest` |
| `String.isBlank/strip/repeat/lines` | 11 | `StringsAndEqualityTest` |
| Switch expressions (`case X ->`) | 14 | `EnumsTest` |
| Text blocks (`"""`) | 15 | `VarAndTextBlocksTest` |
| Records, `instanceof` patterns | 16 | `RecordsTest`, `SealedAndPatternsTest` |
| Sealed classes and interfaces | 17 | `SealedAndPatternsTest` |
| Pattern `switch`, record patterns, `getFirst()` | 21 | `SealedAndPatternsTest`, `GenericMethods` |
| Unnamed variables and patterns (`_`) | 22 | `ExceptionsTest`, `Shapes` |
| Code before `super(...)` / `this(...)` (flexible constructor bodies) | 25 | `Rect` |

A job ad saying "Java 8+" usually means the code base is somewhere between 8 and 17. Java 8 code has no `var`, and code before Java 16 has no records or pattern matching, so expect getters, setters and explicit casts there (chapter 13 covers the legacy look).

#### Classes, interfaces, abstract classes — `ClassesAndInterfacesTest`

A class has fields, constructors and methods. Constructors can chain with `this(...)` or call the parent with `super(...)`. Until Java 24 that call had to be the first statement; since Java 25 code that does not read `this` may run before it, which is handy for validating arguments first (`Rect`), and methods can be **overloaded** (same name, different parameters). A `static` field belongs to the class, so there is one copy shared by every instance.

**Access modifiers** differ from C#. The default, with no keyword, is *package-private*: visible to the same package only. There is no `internal`, because the package is the unit of encapsulation.

Since Java 8, interfaces can have `default` methods (an inherited implementation) and `static` methods (called on the interface, never inherited):

```java
public interface Figure {
    double area();
    String name();
    default String describe() { return String.format(Locale.ROOT, "%s with area %.2f", name(), area()); }
    static double totalArea(List<? extends Figure> figures) { ... }
}
```

An **abstract class** can also hold state and constructors, but a class extends only one (and implements any number of interfaces). Use an interface for the contract and an abstract class only when there is real shared state or code. `Rect` overrides `describe()` and reuses the inherited default through `super.describe()`. An **anonymous class** implements an interface inline. When the interface has a single abstract method, a lambda does the same job (chapter 02).

#### Generics — `GenericsTest`

`Box<T>` and `GenericMethods.max` show generic classes and methods. `<T extends Comparable<T>>` is a *bounded* type parameter: T must be comparable, so `compareTo` is available.

**Wildcards** exist because `List<Integer>` is **not** a `List<Number>`. You cannot pass one where the other is expected. PECS (*Producer Extends, Consumer Super*) tells you which wildcard to use:

```java
static double sum(List<? extends Number> numbers)                  // reads Numbers: List<Integer>, List<Double>...
static void fillWithIntegers(List<? super Integer> target, int n)   // writes Integers: List<Number>, List<Object>...
```

**Type erasure**: the type arguments exist only at compile time. At runtime `ArrayList<String>` and `ArrayList<Integer>` are the same class, so `new T()`, `T.class` and `obj instanceof List<String>` (with `obj` declared as `Object`) do not compile. C# generics are *reified* (kept at runtime), which is why `typeof(T)` works there. A **raw type** (`List` with no `<...>`) is the pre-Java-5 style. It only earns a compiler warning, and the resulting `ClassCastException` shows up far from the cause.

#### Records — `RecordsTest`

```java
public record Isbn(String value) {
    public Isbn {                                  // compact constructor
        Objects.requireNonNull(value, "value");
        value = value.replace("-", "");            // normalise before the field is assigned
        if (!value.matches("\\d{10}|\\d{13}")) throw new IllegalArgumentException(...);
    }
}
```

A record is an immutable data carrier. The compiler generates the constructor, the accessors, `equals`/`hashCode` (by value) and `toString` (`Isbn[value=0134685997]`). Accessors are called `value()`, not `getValue()`, which matters for libraries that expect JavaBeans getters. Records cannot extend classes and cannot declare extra instance fields, but they can implement interfaces and have methods.

Records are only **shallowly** immutable. A `List` component can still be changed through the reference (`LeakyShelf`), so copy it in the constructor with `List.copyOf` (`Shelf`). In this project, records are the DTOs of the API (chapter 05 on).

#### Enums — `EnumsTest`

A Java enum is a class: each constant is a singleton object that can have fields, a constructor and methods (`Genre.FANTASY.label()`). `values()` returns the constants in declaration order. `valueOf("SCIENCE")` is case-sensitive and throws on an unknown name. `==` is safe for enums because each constant exists once. `EnumMap`/`EnumSet` are the fast collections keyed by an enum.

A **switch expression** over an enum that lists every constant needs no `default`, so adding a constant later becomes a compile error at every switch expression that forgot it. An old-style switch *statement* compiles anyway and silently skips the new constant. Do not persist `ordinal()`: reordering the constants changes it (that is why JPA offers `EnumType.STRING`, see chapter 07).

#### Sealed types and pattern matching — `SealedAndPatternsTest`, `Shapes`

```java
public sealed interface Shape permits Circle, Square, Rectangle {}
public record Circle(double radius) implements Shape {}

static double area(Shape shape) {
    return switch (shape) {                       // no default: the compiler knows all Shapes
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Square s -> s.side() * s.side();
        case Rectangle(double width, double height) -> width * height;   // record pattern
    };
}
```

- `sealed ... permits` closes the hierarchy. The compiler can then check that a `switch` is exhaustive.
- `obj instanceof Circle c` tests and casts in one step.
- A **record pattern** `Rectangle(var w, var h)` deconstructs the record.
- A **guard** `case Circle c when c.radius() > 10` refines a case. Cases run top to bottom, so the guarded case goes first; the other way round, the compiler rejects the guarded case as *dominated* (unreachable).
- A switch over `Object` needs `default` (or an unconditional `case Object o`). It can also handle `case null` (otherwise null throws `NullPointerException`).
- `_` (Java 22) names something you will not use: `case Square _ ->`, `catch (NumberFormatException _)`, `try (var _ = ...)`.

This style (data in records, behaviour in exhaustive switches) is the Java counterpart of discriminated unions.

#### `var` and text blocks — `VarAndTextBlocksTest`

`var` infers a **local** variable's type from its initializer. The type is still static, as in C#. It is not allowed for fields, method parameters or return types, or without an initializer (lambda parameters are fine); the test's header lists what does not compile. Watch out for `var list = new ArrayList<>()`: with no type on either side it becomes `ArrayList<Object>`.

A text block `"""` is a multi-line string. The indentation common to all lines, including the closing `"""`, is removed. A `\` at the end of a line joins it with the next, and `\s` keeps a trailing space. Use `"...".formatted(args)` to fill it in. In later chapters, text blocks hold JSON in tests and JPQL in `@Query`.

#### Exceptions — `ExceptionsTest`

- **Checked exceptions** (subclasses of `Exception` that are not `RuntimeException`, such as `IOException`) must be caught or declared with `throws`, or the code does not compile. **Unchecked** ones (`RuntimeException` and subclasses) need neither. C# only has the unchecked kind.
- Modern code, and Spring in particular, uses unchecked exceptions for almost everything. The `java.util.function` interfaces behind stream lambdas declare no checked exceptions, so checked ones get wrapped: `throw new UncheckedIOException(e)`. Always pass the original exception as the cause.
- **try-with-resources** (`try (var r = ...) { }`) closes every `AutoCloseable`, last opened first, even when the body throws. It is C#'s `using`. If `close()` also throws, that exception is attached to the main one as *suppressed* (`getSuppressed()`), not lost.
- `finally` always runs, even after a `return`. Never `return` from `finally`: it silently discards any exception.
- Multi-catch: `catch (NumberFormatException | ArithmeticException _)`.

#### Strings and equality — `StringsAndEqualityTest`

`==` compares **references** for every non-primitive type. Compare values with `equals`.

- Equal string literals share one pooled object, so `"java" == "java"` is true. That is why `==` bugs pass small tests.
- `new String(...)` and runtime concatenation create new objects.
- Autoboxing caches `Integer` values from -128 to 127: `Integer a = 127, b = 127; a == b` is true, but by default it is false for 128 (the JLS only guarantees the cache for -128..127; `-XX:AutoBoxCacheMax` can widen it).
- Unboxing a `null` `Integer` into an `int` throws `NullPointerException`. A typical source is `map.get(missingKey)`.
- Strings are immutable. Use `StringBuilder` to build one in a loop.

**Gotchas**
- `String.format("%.2f", x)` follows the machine's locale, so a Spanish Windows prints `6,00`. Pass `Locale.ROOT` for machine-readable output.
- Palantir Java Format (the formatter in this build) cannot parse a bare `_` inside a record pattern yet (`Rectangle(var w, _)`), so the code uses the equivalent `var _`.
- A record's accessor is `name()`, not `getName()`. Some older libraries only understand getters.
- `List.of(...)`, `List.copyOf(...)` and records with copied lists are unmodifiable: `add` throws `UnsupportedOperationException` at runtime, not at compile time.

### 4.2 Collections & functional

_Written in Phase 02._

### 4.3 Concurrency

_Written in Phase 03._

---

## 5. Spring

### 5.1 Spring Boot fundamentals

_Written in Phase 04._

### 5.2 REST API

_Written in Phase 05._

### 5.3 Validation & errors

_Written in Phase 06._

### 5.4 Persistence with JPA

_Written in Phase 07._

### 5.5 Advanced JPA

_Written in Phase 08._

### 5.6 Transactions

_Written in Phase 09._

### 5.7 Security

_Written in Phase 10._

### 5.8 Testing

_Written in Phase 11._

### 5.9 Beyond CRUD

_Written in Phase 12._

### 5.10 Kafka

_Written in Phase 14 (optional)._

---

## 6. Legacy

_Written in Phase 13._

---

## 7. References

- Java: [dev.java](https://dev.java/learn/), [JDK 25 API docs](https://docs.oracle.com/en/java/javase/25/docs/api/)
- Maven: [Maven in 5 minutes](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html), [lifecycle reference](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- Spring Boot: [reference docs](https://docs.spring.io/spring-boot/index.html), [start.spring.io](https://start.spring.io)
- Spring Framework: [reference docs](https://docs.spring.io/spring-framework/reference/)

---

## Appendix: Coming from .NET

A map for orientation, not a claim that the pieces are identical. It grows with every chapter.

| .NET | Java / Spring | Notes |
|---|---|---|
| .NET SDK | JDK | Several vendors ship the JDK (Temurin, Oracle, Corretto…) |
| CLR | JVM | Both JIT-compile bytecode / IL |
| `dotnet` CLI | `mvnw` (Maven) | Maven drives build, test and packaging through plugins |
| `.sln` | Parent POM with `<modules>` | The parent also shares configuration, which a `.sln` does not |
| `.csproj` | Module `pom.xml` | |
| NuGet / nuget.org | Maven dependencies / Maven Central | Cache: `~/.nuget/packages` ↔ `~/.m2/repository` |
| `Directory.Packages.props` (central versions) | BOM / `<dependencyManagement>` | The Spring Boot BOM pins versions for you |
| `PackageReference` | `<dependency>` | Scopes (`test`, `runtime`…) have no direct NuGet equivalent |
| Kestrel | Embedded Tomcat | |
| `Program.cs` + `WebApplication.CreateBuilder` | `@SpringBootApplication` class + `main` | |
| IIS hosting an app | `.war` deployed to a standalone Tomcat | The legacy model |
| `record` / `record class` | `record` | Java record fields are always final (shallowly immutable); records cannot inherit; no `with` expressions |
| `sealed` class (blocks inheritance) | `final` class | Java's `sealed` means something else: see the next row |
| Closed hierarchies, F# discriminated unions | `sealed interface ... permits` + records | The compiler checks `switch` exhaustiveness |
| `switch` expression, `is` patterns | `switch` expression, `instanceof` / record patterns | Very close; guards use `when` in both |
| `enum` (named integers) | `enum` (a class with singleton instances) | Java enums can have fields, constructors and methods |
| Generics (reified: `typeof(T)` works) | Generics (erased at runtime) | No `new T()`, no `T.class`; wildcards `? extends` / `? super` instead of `out` / `in` variance |
| `var` | `var` | Same idea; Java only allows it for locals and lambda parameters |
| Raw string literals `"""` | Text blocks `"""` | Similar indentation rules, but Java processes escapes (`\n`, `\s`, `\` at line end) and keeps the final newline when the closing `"""` is on its own line |
| `using` statement | try-with-resources | `IDisposable` ↔ `AutoCloseable` |
| Exceptions (all unchecked) | Checked and unchecked exceptions | Checked ones must be caught or declared with `throws` |
| `internal` | package-private (no modifier) | Java's default visibility is the package, not private |
| `==` on `string` compares values | `==` compares references; use `equals` | See "Things that trip you up" |
| Boxed `int` (`object`, `int?`) | `Integer` (wrapper class) | `==` on two `Integer`s compares references: true inside the -128..127 cache, false outside |

### Things that trip you up

- **`==` on objects.** `==` on `String`, `Integer` and any other object compares references. Small tests often pass by accident (the string pool, the `Integer` cache from -128 to 127). Use `equals`.
- **Checked exceptions.** A method that calls `Files.readString` does not compile until you catch `IOException` or add `throws IOException`. The `java.util.function` interfaces (and so stream lambdas) cannot throw them, so they get wrapped in unchecked exceptions.
- **Type erasure.** `List<String>` and `List<Integer>` are the same class at runtime, so reflection-based code (JSON libraries, Spring) sometimes needs a hint such as a `ParameterizedTypeReference`.
- **Default visibility.** Forgetting `public` makes a member package-private, not private.
- **No properties.** Java has fields plus methods. Records give you `name()` accessors. Classes use `getName()`/`setName()` by convention (JavaBeans).
- **Locale-sensitive formatting.** `String.format` and `toUpperCase()` use the machine's locale unless you pass `Locale.ROOT`.
