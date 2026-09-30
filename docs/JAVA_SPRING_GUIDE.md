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

`LibraryApplication` starts and Tomcat listens on port 8080. It serves:
- `/api/info` (§5.1);
- CRUD for `/api/authors` and `/api/books` (§5.2), stored in PostgreSQL through Spring Data JPA (§5.4), with paged lists (§5.5);
- members and loans: `/api/members`, `/api/loans` (§5.5), safe when two requests race for the same book, member or loan (§5.6);
- an audit trail of loan requests: `/api/audit-events` (§5.6);
- Swagger UI at `/swagger-ui.html`.

Request bodies are validated, and every error is an RFC 9457 `ProblemDetail` (§5.3). On `spring-boot:run`, Spring Boot starts PostgreSQL in Docker and Flyway creates the schema before Tomcat opens the port. `LibraryApplicationIT` (`@SpringBootTest` on a throwaway PostgreSQL) boots the same context in a test, so a broken configuration fails the build.

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

**What it is.** The collections library (`java.util`), lambdas and the Streams API. Together they are Java's `List<T>` + `Dictionary` + LINQ. The examples are in [`collections/`](../java-core/src/test/java/dev/playground/core/collections/) and [`functional/`](../java-core/src/test/java/dev/playground/core/functional/) under `java-core/src/test/java/dev/playground/core/`, with their small types in the matching `src/main` packages. Run them with `./mvnw -pl java-core test -Dtest=StreamsTest` (or any other class name).

**Why it matters.** Almost every service method filters, groups or maps a collection, and Spring Data returns `List` and `Optional`. Streams replace most hand-written loops in modern code.

#### The collection family — `CollectionsTest`

Declare variables with the **interface** and choose the **class** for its behaviour: `List<String> titles = new ArrayList<>();`.

| Interface | Class | Order | Notes |
|---|---|---|---|
| `List` | `ArrayList` | insertion | The default list. O(1) `get(i)` |
| | `LinkedList` | insertion | Also a `Deque`. Rarely the right choice; use `ArrayDeque` for queues |
| `Set` | `HashSet` | none | The default set |
| | `LinkedHashSet` | insertion | |
| | `TreeSet` | sorted | Uses `compareTo` or a `Comparator` |
| `Map` | `HashMap` | none | The default map |
| | `LinkedHashMap` | insertion | |
| | `TreeMap` | sorted by key | Also a `NavigableMap`: `firstKey`, `headMap`… |

Three kinds of "read-only" list look alike but behave differently:

```java
List.of("a", "b")                       // unmodifiable, rejects nulls
Collections.unmodifiableList(source)    // a read-only *view*: changes to source show through
List.copyOf(source)                     // an unmodifiable *copy*: a snapshot (also rejects nulls)
```

All three are still of type `List`, so `add` compiles and throws `UnsupportedOperationException` at runtime. Java has no `IReadOnlyList` in the type system. `Arrays.asList(array)` is different again: fixed size, but `set` writes through to the array.

The `Map` helpers replace the get/check/put dance:

```java
stock.getOrDefault("Emma", 0);
stock.merge("Dune", 1, Integer::sum);                               // count
byAuthor.computeIfAbsent(author, _ -> new ArrayList<>()).add(title); // multimap
```

Removing from a list inside a for-each loop throws `ConcurrentModificationException`: the iterator notices the change. Use `list.removeIf(predicate)`.

#### equals, hashCode and ordering — `EqualsHashCodeTest`

**How it works.** `HashSet` and `HashMap` use `hashCode()` to pick a bucket, and inside it they compare the stored hash first and only then call `equals()`. The contract is that equal objects must have equal hash codes.

- `BrokenBookKey` overrides only `equals`. Two equal keys keep their (almost certainly different) identity hash codes, so `contains` with an equal key returns false.
- `BookKey` overrides both, using the same fields: `Objects.hash(isbn, edition)`. A record would generate both for you.
- `MutableBookKey` is correct but mutable. Changing a field after adding the object to a `HashSet` strands it: the set filed it under the old hash, so lookups with the new one miss. Keep keys immutable.

**Ordering** has two mechanisms. `Comparable` is the type's own *natural order* (`compareTo`), used by `TreeSet`, `TreeMap` and `sort()` with no arguments. A `Comparator` is any other order, built by composition:

```java
keys.sort(Comparator.comparingInt(BookKey::edition).reversed().thenComparing(BookKey::isbn));
titles.sort(Comparator.nullsLast(Comparator.naturalOrder()));
```

`TreeSet` treats `compareTo(...) == 0` as "duplicate", so keep `compareTo` consistent with `equals`.

#### Lambdas and method references — `LambdasTest`

A lambda implements a **functional interface**, an interface with exactly one abstract method. The standard ones are in `java.util.function`:

| Interface | Shape | Method | C# |
|---|---|---|---|
| `Function<T,R>` | `T -> R` | `apply` | `Func<T,TResult>` |
| `BiFunction<T,U,R>` | `(T,U) -> R` | `apply` | `Func<T1,T2,TResult>` |
| `UnaryOperator<T>` | `T -> T` | `apply` | `Func<T,T>` |
| `Predicate<T>` | `T -> boolean` | `test` | `Predicate<T>` / `Func<T,bool>` |
| `Supplier<T>` | `() -> T` | `get` | `Func<T>` |
| `Consumer<T>` | `T -> void` | `accept` | `Action<T>` |

There are also primitive versions (`IntFunction`, `ToIntFunction`, `IntPredicate`…) that avoid boxing. Functions compose (`f.andThen(g)` is g(f(x)), `f.compose(g)` is f(g(x))), and predicates combine with `and`, `or`, `negate` and `Predicate.not`.

You can declare your own functional interface (`PriceRule`). Add `@FunctionalInterface` so the compiler rejects a second abstract method.

A **method reference** is shorthand for a lambda that only calls one method:

| Kind | Example | Equivalent lambda |
|---|---|---|
| Static | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Bound instance | `prefix::concat` | `s -> prefix.concat(s)` |
| Unbound instance | `String::toUpperCase` | `s -> s.toUpperCase()` |
| Constructor | `StringBuilder::new` | `s -> new StringBuilder(s)` |

A lambda can read local variables only if they are **effectively final** (never reassigned), so `count++` inside a lambda does not compile. The `java.util.function` interfaces declare no checked exceptions, so `Files::readString` is not a `Function<Path,String>`. `CheckedFunctions.unchecked(...)` wraps it and rethrows the `IOException` as `UncheckedIOException`.

#### Streams — `StreamsTest`

A stream is a pipeline: a **source** (`list.stream()`), lazy **intermediate** operations (`filter`, `map`, `sorted`…) and one **terminal** operation (`toList`, `collect`, `count`, `findFirst`…) that runs it all.

```java
List<String> titles = books.stream()
        .filter(b -> b.year() > 1950)
        .sorted(Comparator.comparingInt(BookSample::year))
        .map(BookSample::title)
        .limit(3)
        .toList();
```

Grouping uses `Collectors`, and a *downstream* collector decides what each group holds:

```java
Map<Genre, Long> perGenre = books.stream()
        .collect(groupingBy(BookSample::genre, () -> new EnumMap<>(Genre.class), counting()));
Map<String, List<String>> titlesByAuthor = books.stream()
        .collect(groupingBy(BookSample::author, TreeMap::new, mapping(BookSample::title, Collectors.toList())));
```

Also in the test:
- `partitioningBy` splits into `true`/`false`.
- `joining(", ", "[", "]")` builds a string.
- `flatMap` flattens nested lists.
- `reduce` folds values together; `mapToInt(...).sum()` and `summaryStatistics()` do the same on primitives, without boxing.
- `IntStream.range(0, 5)` replaces a counting loop.
- `toMap` throws on duplicate keys unless you pass a merge function.

**Laziness.** Nothing runs until the terminal operation. Elements then flow through the whole pipeline **one at a time**, so `findFirst` stops as soon as it finds a match: in the test only 2 of the 8 books are visited. `peek` exists for debugging exactly this. Do not rely on it for side effects, though: since Java 9 `count()` on a list with no `filter` in between just returns the size, and `peek` never runs (`countMaySkipThePipelineWhenTheSizeIsKnown`).

**Single use.** A stream can be consumed only once. A second terminal operation throws `IllegalStateException`. Keep the collection and call `stream()` again.

#### Optional — `OptionalTest`

`Optional<T>` is a **return type** that says "maybe no result" (`BookCatalog.findByTitle`, Spring Data's `findById`).

```java
catalog.findByTitle("Dune Messiah")
        .map(BookSample::author)              // runs only if present, like ?.
        .flatMap(catalog::findFirstByAuthor)  // the function returns an Optional itself
        .map(BookSample::title)
        .orElseThrow(() -> new IllegalArgumentException("not found"));
```

- `orElse(x)` evaluates `x` **always**, even when a value is present. `orElseGet(() -> x)` evaluates it only when empty. That matters when `x` is expensive or has side effects.
- `Optional.of(null)` throws. Use `Optional.ofNullable` for values that may be null.
- Do not use `Optional` for fields or method parameters, and do not wrap collections in it: return an empty list instead of `Optional<List<T>>`.

**Gotchas**
- `Stream.toList()` (Java 16) returns an unmodifiable list (which, unlike `List.of`, accepts nulls). `Collectors.toList()` does not promise either way. When you need to add to the result, collect with `toCollection(ArrayList::new)`.
- `Collectors.toMap` throws `IllegalStateException: Duplicate key` unless you pass a merge function.
- A `HashSet` or `HashMap` iterates in an unspecified order that can change between runs or JDK versions. Do not write tests that depend on it; use `LinkedHash*` or `Tree*` when order matters.
- `map.get(key)` returns `null` for a missing key. Unboxing that `null` into an `int` throws `NullPointerException`.

### 4.3 Concurrency

**What it is.** Running work on several threads at once: the raw `Thread` API, executors (thread pools), `CompletableFuture` for chaining async steps, and virtual threads. The examples are in [`concurrency/`](../java-core/src/test/java/dev/playground/core/concurrency/) under `java-core/src/test/java/dev/playground/core/`. Run them with `./mvnw -pl java-core test -Dtest=CompletableFutureTest` (or any other class name). Every test has a `@Timeout`, so a concurrency bug fails instead of hanging the build.

**Why it matters.** A Spring MVC app is multi-threaded from the first request: Tomcat serves each request on its own thread, so every singleton bean (services, repositories) is shared between threads. Shared mutable state in a bean is a race condition waiting to happen. Calling several remote services in parallel is the other everyday case.

#### Threads and race conditions — `ThreadsTest`

```java
Thread worker = new Thread(task, "worker-1");
worker.start();   // runs task on a new thread and returns at once
worker.join();    // waits for it to finish
```

`worker.run()` compiles too, but it is a plain method call on the current thread: no concurrency at all.

**The race.** `UnsafeCounter.increment()` does `count++`, which is three steps: read, add, write. Two threads can read the same value and both write value + 1, so an increment is lost. `Hammer.hammer` starts 8 threads that each increment 100 000 times; with `UnsafeCounter` the total usually ends up well below 800 000. The test `racyCounterLosesUpdates` is `@Disabled` because a test that fails *usually* is useless; the comment above it shows how to run it anyway.

Three fixes, all exact:

| Fix | Class | How it works | Use it for |
|---|---|---|---|
| A lock | `SynchronizedCounter` | `synchronized` methods: one thread at a time per object | Several fields that must change together |
| An atomic | `AtomicCounter` | `AtomicInteger.incrementAndGet()`: one atomic hardware operation, no lock | A single counter or reference |
| A concurrent collection | `ConcurrentHashMap` | `merge(key, 1, Integer::sum)` is atomic per key | Shared maps and caches |

`synchronized` also guarantees **visibility**: what one thread wrote before releasing the lock is seen by the next thread that takes it. Without a lock, an atomic, `volatile` or `join`, a thread may read a stale value. That is why `SynchronizedCounter.value()` is synchronized too.

**Interruption** is how you ask a thread to stop. `interrupt()` wakes a thread blocked in `sleep`, `wait` or `join` with an `InterruptedException`; the thread decides what to do. Code that catches it and does not stop should restore the flag with `Thread.currentThread().interrupt()` (see `CheckedFunctions` in §4.2).

#### Executors — `ExecutorsTest`

You rarely create threads yourself. An `ExecutorService` owns a pool of threads, and you submit tasks to it:

```java
try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
    Future<Integer> pages = pool.submit(() -> 412 + 188);   // a Callable returns a value
    int result = pages.get(5, TimeUnit.SECONDS);           // blocks until done
}   // close(): no new tasks, waits for the submitted ones (Java 19+)
```

- `Future.get()` **blocks**. If the task threw, `get()` throws an `ExecutionException` with the real exception as its cause.
- `get(timeout)` throws `TimeoutException`, but the task keeps running. `cancel(true)` interrupts it, which stops it only if the task blocks in an interruptible call (`sleep`, `await`…) or checks the flag.
- `invokeAll(tasks)` runs a list of `Callable`s and returns when every one is done; the futures keep the tasks' order.
- After `shutdown()` (or `close()`), `submit` throws `RejectedExecutionException`. A pool that is never shut down keeps its threads, and they are non-daemon threads, so they keep the JVM alive.

In Spring you do not build pools by hand either: the framework provides a `TaskExecutor` bean and `@Async` (chapter 5.9).

#### CompletableFuture — `CompletableFutureTest`, `AsyncCatalog`

A `Future` can only be waited on. A `CompletableFuture` can be **chained**: you describe what happens when the value arrives, and no thread sits blocked in between. `AsyncCatalog` wraps the §4.2 `BookCatalog` so that each lookup returns a `CompletableFuture`.

```java
CompletableFuture<String> summary = catalog.findAsync("Emma")
        .thenCombine(catalog.stockAsync("Emma"), (book, stock) -> book.title() + ": " + stock)
        .exceptionally(error -> "unknown");
String text = summary.join();   // block once, at the end
```

| Method | Does | Streams / Optional equivalent |
|---|---|---|
| `supplyAsync(supplier, executor)` | Starts a task that returns a value | |
| `thenApply(f)` | Transforms the value | `map` |
| `thenCompose(f)` | Next step returns a future itself | `flatMap` |
| `thenCombine(other, f)` | Joins two independent futures | |
| `allOf(f1, f2, …)` | Completes when all are done (`Void`: read the values from the originals) | |
| `exceptionally(f)` | Replaces an error with a value | |
| `handle((value, error) -> …)` | Sees either the value or the error | |
| `orTimeout` / `completeOnTimeout` | Fails with `TimeoutException` / completes with a default after a delay | |
| `join()` / `get()` | Block for the result | |

- Without an executor argument, `supplyAsync` runs on `ForkJoinPool.commonPool()` (or on a new thread per task when that pool has fewer than 2 threads, as on a 1–2 CPU machine): shared by the whole JVM and sized for CPU work. Pass your own executor for anything that blocks.
- Errors skip the `thenApply` steps and travel down the chain until an `exceptionally` or `handle`. An exception thrown inside a stage arrives wrapped in a `CompletionException`, so look at `getCause()`. It arrives unwrapped only when the future was failed directly (`completeExceptionally`, `orTimeout`, `failedFuture`).
- `join()` throws the unchecked `CompletionException`; `get()` throws the checked `ExecutionException`. Both have the real error as the cause.
- `cancel(true)` on a `CompletableFuture` does **not** interrupt the task (`cancelMarksTheFutureButDoesNotStopTheWork`): the future is completed with a `CancellationException` and the work runs on. To really stop it, the task has to check a flag of its own.

#### Virtual threads — `VirtualThreadsTest`

A platform thread is an OS thread: expensive to create, about a megabyte of stack reserved: thousands of them, not millions. A **virtual thread** (Java 21) is managed by the JVM. When it blocks (sleep, socket read, JDBC call), the JVM unmounts it and its *carrier* (a platform thread) runs another virtual thread in the meantime. You can have millions.

```java
Thread.ofVirtual().start(task);
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(task);   // a new virtual thread per task, no pool size to tune
}
```

`tenThousandSleepingTasksFinishQuickly` runs 10 000 tasks that sleep 10 ms each; they all overlap and finish in well under a second. A fixed pool of 10 platform threads can only have 10 sleeps in flight: `aFixedPlatformPoolQueuesTheWaiting` shows 100 such tasks taking at least 100 ms, so the 10 000 would need 10 s.

- Virtual threads help with **waiting** (blocking I/O). They do not speed up CPU-bound work: by default there are only as many carriers as cores.
- Do not pool them: they are cheap, create one per task.
- Write plain blocking code (`restClient.get()...`, a JDBC query) and run it on a virtual thread. You get the scalability of async code without the callbacks.
- Spring Boot runs Tomcat's request handling on virtual threads when you set `spring.threads.virtual.enabled: true`.
- A virtual thread that cannot be unmounted *pins* its carrier while it blocks: inside a class initialiser, or with native (JNI/FFM) code on its stack. Since Java 24 (JEP 491), `synchronized` no longer pins. File I/O does not unmount either; the scheduler adds a temporary carrier to compensate.

**Gotchas**
- `thread.run()` instead of `thread.start()` runs the code on the current thread.
- Swallowing `InterruptedException` hides the stop request. Restore the flag or rethrow.
- `Future.get()` without a timeout can wait forever. Tests should always use a bound.
- `HashMap`, `ArrayList` and the other ordinary collections are not thread-safe. Use `ConcurrentHashMap`, or do not share them.
- A fixed thread pool that is never shut down keeps the JVM running. Use try-with-resources.

---

## 5. Spring

### 5.1 Spring Boot fundamentals

**What it is.** The machinery every later chapter relies on: the IoC container and its beans, dependency injection, scopes and lifecycle, auto-configuration, configuration files and profiles, and logging. The code is in `spring-app`: [`config/`](../spring-app/src/main/java/dev/playground/library/config/) (`LibraryProperties`, `LifecycleLogger`, `DevModeBanner`) and [`info/`](../spring-app/src/main/java/dev/playground/library/info/) (`InfoController`, the greeters). The tests are in the matching test packages plus [`fundamentals/`](../spring-app/src/test/java/dev/playground/library/fundamentals/). Requests to try: [`http/04-fundamentals.http`](../spring-app/http/04-fundamentals.http).

**Why it matters.** Once you know how the container finds, builds and wires beans, most Spring "magic" becomes predictable, and so do most startup errors.

#### The container and beans — `DependencyInjectionTest`

A **bean** is an object the Spring container (the `ApplicationContext`) creates, wires and manages. You mark a class for component scanning with a *stereotype* annotation:

| Annotation | Meaning |
|---|---|
| `@Component` | A generic bean |
| `@Service` | Business logic. Same as `@Component`; the name documents the role |
| `@Repository` | Data access. Also translates persistence exceptions into Spring's `DataAccessException` (when a `PersistenceExceptionTranslationPostProcessor` is registered; Boot adds one once JPA or JDBC is on the classpath) |
| `@Controller` / `@RestController` | Web layer; `@RestController` writes return values as the response body (JSON) |
| `@Configuration` + `@Bean` methods | Beans you build yourself, typically for classes you do not own |

**Constructor injection.** A bean lists its dependencies as constructor parameters, and the container passes them in. With a single constructor, no `@Autowired` is needed:

```java
public InfoController(LibraryProperties properties, Environment environment,
        Greeter greeter, @Qualifier("casual") Greeter casualGreeter,
        @Value("${spring.application.name}") String applicationName) { ... }
```

This keeps fields `final`, shows every dependency in the signature, never leaves a half-built object, and lets a unit test create the class with `new`. Field injection (`@Autowired` on a field) hides dependencies and needs reflection to test. It is fine only in test classes, which JUnit creates.

**Several candidates.** `Greeter` has two implementations. The container resolves a parameter like this:
- **one** matching bean → injected;
- **several**, one of them `@Primary` → the primary one (`FormalGreeter`);
- **several** and a `@Qualifier("casual")` on the parameter → the bean with that qualifier (`CasualGreeter`);
- **several** and neither → startup fails with `NoUniqueBeanDefinitionException`;
- **none** → startup fails with `NoSuchBeanDefinitionException` ("No qualifying bean of type…").

`DependencyInjectionTest` checks each case with `ApplicationContextRunner`, which starts a tiny context with only the classes you name. `assertThat(context).hasFailed()` lets you test the failures too.

#### Scopes and lifecycle — `BeanScopesTest`, `LifecycleLoggerTest`

`BeanScopesTest` uses the plain Spring Framework container (`new AnnotationConfigApplicationContext(...)`): what Spring Boot builds for you, without Boot.

| Scope | Instances | Typical use |
|---|---|---|
| `singleton` (default) | One per container, shared by all | Services, repositories, controllers: almost everything |
| `prototype` | A new one on every lookup or injection | Stateful helpers |
| `request` / `session` | One per HTTP request / session | Rare in REST APIs |

Singletons are shared by every request thread (§4.3), so keep them **stateless**: no mutable fields holding per-request data.

Two surprises with prototypes:
- A prototype injected into a singleton is created **once**, when the singleton is built, so in practice it behaves like a singleton. Inject an `ObjectProvider<T>` and call `getObject()` when you need a fresh instance.
- The container does not track prototypes after handing them out, so their `@PreDestroy` is never called.

The lifecycle of a singleton, as logged by `LifecycleLogger`:

```
constructor → dependencies injected → @PostConstruct → … all beans ready …
→ Tomcat started → ApplicationRunner beans (DevModeBanner) → ApplicationReadyEvent
→ … requests … → context closing → @PreDestroy
```

`@PostConstruct` and `@PreDestroy` come from `jakarta.annotation`. `@EventListener` on a method subscribes to an application event; `ApplicationReadyEvent` means the app is fully started. To run code once at startup, implement `ApplicationRunner` (or `CommandLineRunner`).

#### Auto-configuration and starters

`@EnableAutoConfiguration` (inside `@SpringBootApplication`) loads the auto-configuration classes listed by the jars on the classpath. Each one is guarded by conditions:

```java
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)        // only if Spring MVC is on the classpath
public class WebMvcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                          // only if you did not define your own
    public SomeMvcBean someMvcBean() { ... }
}
```

So **adding a starter to the POM switches features on**, and **defining your own bean switches the default off**. To see what was applied and why, start the app with `--debug` (or `debug: true` in `application.yml`). The log then prints the *CONDITIONS EVALUATION REPORT*: "Positive matches" and "Negative matches", each with the reason. Starters (§2) are just dependency bundles; the auto-configuration inside them is what does the work.

#### Configuration — `LibraryPropertiesTest`

Settings come from many **property sources**, and a higher one overrides a lower one. The most useful, highest first:

1. Test properties (`@SpringBootTest(properties = ...)`, `@TestPropertySource`)
2. Command-line arguments: `--library.loans.max-active=5`
3. Java system properties: `-Dlibrary.loans.max-active=5`
4. OS environment variables: `LIBRARY_LOANS_MAXACTIVE=5`
5. Profile files: `application-dev.yml`
6. `application.yml`
7. Defaults in code (`@DefaultValue`)

The environment variable name comes from **relaxed binding**: uppercase, `.` becomes `_`, dashes are dropped. So `LIBRARY_LOANS_MAX_ACTIVE` does **not** work: its extra `_` reads as another level (`max.active`). In YAML, `max-active` (the recommended form) and `maxActive` both bind to the `maxActive` component.

**`@ConfigurationProperties`** binds a whole prefix into a typed object:

```java
@ConfigurationProperties("library")
public record LibraryProperties(String name, @DefaultValue Loans loans) {
    public record Loans(@DefaultValue("3") int maxActive, @DefaultValue("14") int durationDays) {}
}
```

`@ConfigurationPropertiesScan` on `LibraryApplication` registers it as a bean. A record makes the settings immutable. The empty `@DefaultValue` on `loans` creates the nested record with its own defaults when the whole `library.loans` group is missing; without it, `loans` would be `null`. **`@Value("${key}")`** injects a single value (`InfoController` uses it for `spring.application.name`). It is fine for one-off values; for groups of settings prefer a properties record, which gives one place, types, defaults and, from §5.3, validation.

`LibraryPropertiesTest` uses `@SpringBootTest(classes = Config.class)`: a context with only the binding, not the whole app. `application.yml` is still read.

#### Profiles

A profile is a named set of configuration and beans, like an environment name.
- `application-dev.yml` is loaded on top of `application.yml` when `dev` is active; its keys win.
- `@Profile("dev")` on a bean creates it only in that profile (`DevModeBanner`).
- Activate with `SPRING_PROFILES_ACTIVE=dev` (the VS Code launch configuration does this), `--spring.profiles.active=dev`, or `./mvnw -pl spring-app spring-boot:run -Dspring-boot.run.profiles=dev`. In tests: `@ActiveProfiles("dev")`.
- Tests read OS environment variables too. With `SPRING_PROFILES_ACTIVE=dev` exported in your shell, every test runs with `dev` and `InfoControllerTest` fails. Set it per run, not globally.
- With no active profile, Spring uses the `default` profile, and `Environment.getActiveProfiles()` returns an empty array (see `GET /api/info`).

#### Logging

Spring Boot logs through **SLF4J** (the API you code against) and **Logback** (the implementation, chosen by `spring-boot-starter-logging`).

```java
private static final Logger log = LoggerFactory.getLogger(LifecycleLogger.class);
log.info("Library ready on port {}", port);   // {} placeholders: the message is built only if INFO is on
```

Levels are set per package: `logging.level.dev.playground: DEBUG` in `application-dev.yml`, `logging.level.root: WARN` to quiet everything else. Do not concatenate strings in log calls; the placeholders skip the work when the level is off.

#### The info endpoint and slice tests — `InfoControllerTest`

`GET /api/info` returns the bound configuration, the `@Value` field and the active profiles. `GET /api/info/greetings?name=Ada` shows the `@Primary` and the qualified greeter side by side.

`@WebMvcTest(InfoController.class)` is a **slice test**. It starts the MVC layer only: the controller, Jackson and the MVC infrastructure, with no Tomcat, no other `@Component`s and no `@ConfigurationPropertiesScan`. So the test brings the greeters with `@Import` and the properties with `@EnableConfigurationProperties`. `MockMvcTester` sends requests and asserts on the JSON:

```java
assertThat(mvc.get().uri("/api/info"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo("""
                { "application": "library", "name": "Playground Library", ... }
                """);
```

Slices keep tests fast and focused. `@SpringBootTest` with no `classes` starts the whole application; the playground keeps that for `LibraryApplicationIT` and the other integration tests, which need Docker for their database (§5.4, §5.8).

**Gotchas**
- A class outside `dev.playground.library` is not scanned, so it is not a bean, and injecting it fails at startup.
- `new InfoController(...)` in your own code gives an object the container does not manage: it is not injected anywhere, and its `@PostConstruct` and `@PreDestroy` never run. Let the container create beans and inject them.
- A bean with two constructors needs `@Autowired` on the one Spring should use. Worse, if one of them takes no arguments, Spring silently uses that one and the dependencies stay `null`.
- `@Value("${missing.key}")` fails at startup; `@Value("${missing.key:fallback}")` supplies a default.
- A property with a typo (`library.loans.max-activ`) is silently ignored: nothing binds it.

### 5.2 REST API

**What it is.** CRUD endpoints for authors and books with Spring MVC, JSON through Jackson, and an OpenAPI document with Swagger UI. This chapter first kept the data in memory; §5.4 swapped the storage for PostgreSQL without touching the controllers. The code is in [`author/`](../spring-app/src/main/java/dev/playground/library/author/) and [`book/`](../spring-app/src/main/java/dev/playground/library/book/), plus `config/OpenApiConfig`. Requests: [`http/05-rest.http`](../spring-app/http/05-rest.http).

**Why it matters.** This layering (controller → service → repository, with DTOs at the edge) is the shape of almost every Spring API you will work on.

#### One feature, one package

```
book/
  BookController   HTTP only: paths, status codes, headers
  BookService      use cases; takes and returns DTOs
  BookRepository   storage (a ConcurrentHashMap at first, a Spring Data JPA interface since §5.4)
  Book             the stored object (a plain class at first, a JPA entity since §5.4)
  BookMapper       static Book ↔ DTO methods, written by hand
  dto/             BookResponse, CreateBookRequest, UpdateBookRequest, AuthorSummary (records)
```

**Why DTOs.** The API returns `BookResponse`, never `Book`. The stored class can change (new columns, lazy JPA relations, internal fields) without changing the JSON contract. Separate request and response records also stop a client from setting fields it should not, such as `id` or `availableCopies`. The service does the mapping, so `Book` never leaves it.

#### Controllers — `AuthorControllerTest`, `BookControllerTest`

`@RestController` is `@Controller` + `@ResponseBody`: every method's return value is written to the response body (as JSON), instead of naming a view template to render.

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/{id}")                                   // GET /api/books/1
    public BookResponse get(@PathVariable Long id) { ... }

    @PostMapping                                           // POST /api/books
    public ResponseEntity<BookResponse> create(@RequestBody CreateBookRequest request) {
        BookResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);   // 201 + Location header
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)                 // 204, empty body
    public void delete(@PathVariable Long id) { ... }
}
```

| Annotation | Binds |
|---|---|
| `@PathVariable Long id` | A segment of the path: `/api/books/{id}` |
| `@RequestParam String name` | A query parameter: `?name=Ada` (`defaultValue`, `required = false` for optional ones) |
| `@RequestBody CreateBookRequest request` | The JSON body, read by Jackson |
| `@RequestHeader`, `@CookieValue` | A header or a cookie |

Return a plain object for `200 OK`. Use `ResponseEntity<T>` when you need to set the status or headers yourself, and `@ResponseStatus` for a fixed status.

| Operation | Status |
|---|---|
| `GET` list / one | `200`, or `404` for a missing id |
| `POST` | `201 Created` + `Location` + the new resource |
| `PUT` (full replacement) | `200` + the updated resource |
| `DELETE` | `204 No Content` |

**A missing id** makes the service throw `NotFoundException("Book", 7)`, which `GlobalExceptionHandler` turns into a 404 `ProblemDetail` (§5.3). The first version of this chapter threw `ResponseStatusException(HttpStatus.NOT_FOUND, "Book 7 not found")` from the service. You will meet that in other codebases: it works, but it ties the service to HTTP, and without a handler the body is Spring Boot's generic error JSON (`timestamp`, `status`, `error`, `path`), without the message.

Spring answers some errors **before your method runs**; the controller tests pin them (each also checks that the service was never called). Since §5.3 each of these errors is a `ProblemDetail` too.

| Request | Status |
|---|---|
| Body that is not valid JSON | `400` (`HttpMessageNotReadableException`) |
| A missing or `null` value for a primitive field (`int totalCopies`) | `400` (Jackson 3, see below) |
| `/api/books/abc` for a `Long` id | `400` (type mismatch) |
| `Content-Type: text/plain` on a JSON endpoint | `415 Unsupported Media Type` |
| A method the path does not map (`PATCH`) | `405 Method Not Allowed` |

One arrives **after** the method: `Accept: application/xml` → `406 Not Acceptable`. The mappings declare no `produces`, so the request matches, the method runs, and only writing the result fails. Add `produces = MediaType.APPLICATION_JSON_VALUE` to a mapping to reject it up front.

The 415 and 406 cases are **content negotiation**. Spring picks an `HttpMessageConverter` from the request's `Content-Type` (to read) and `Accept` (to write) headers. The API speaks JSON, plus YAML by accident: springdoc brings in Jackson 2's YAML module, and Spring registers a YAML converter whenever that module is on the classpath (`Accept: application/yaml` works, through a separate Jackson 2 mapper that Spring Boot does not configure).

#### JSON with Jackson

Records map to JSON field by field, in declaration order. `null` components are written as `null`. **Unknown properties** in a request are dropped silently: that is Jackson 3's default (with Jackson 2, Spring Boot switched the failure off for you). A **missing** field becomes `null` for an object type (`Integer birthYear`). For a primitive (`int totalCopies`) Jackson 3 **rejects** a missing or `null` value with a 400 (`FAIL_ON_NULL_FOR_PRIMITIVES` is on by default; Jackson 2 silently used `0`). That 400 happens while the body is read, before validation, so its `ProblemDetail` only says `"Failed to read request"` and names no field; use `Integer` + `@NotNull` for a per-field message (§5.3).

Spring Boot 4 uses **Jackson 3**, whose packages moved from `com.fasterxml.jackson` to `tools.jackson` (the annotations such as `@JsonProperty` stay in `com.fasterxml.jackson.annotation`). Spring Boot 3 used Jackson 2, so older examples import the old packages. Watch the imports: springdoc still brings Jackson 2 onto the classpath, so an IDE may offer `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) where you want `tools.jackson.databind` (Jackson 3, the one Spring Boot configures).

#### Two kinds of test

- **Service unit tests** (`AuthorServiceTest`, `BookServiceTest`) build the service without Spring. In this chapter they passed the real in-memory repository; since §5.4 they pass a Mockito mock of the repository interface (`@Mock` + `@InjectMocks`). No Spring context at all, so they run in milliseconds.
- **Controller slice tests** (`AuthorControllerTest`, `BookControllerTest`) use `@WebMvcTest(XController.class)` with `@MockitoBean XService`: the real MVC stack (routing, JSON, status codes) and a Mockito mock behind it. `@MockitoBean` (Spring Framework 6.2+) replaces Spring Boot's `@MockBean`, which Boot 4 removed.

```java
given(service.findById(1L)).willReturn(DUNE);             // stub the mock

assertThat(mvc.get().uri("/api/books/1"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo("""
                {"id": 1, "isbn": "9780441013593", "title": "Dune", ... }
                """);
verify(service).delete(1L);                                // check the call was made
```

In MockMvc the host is `localhost` with no port, so `Location` reads `http://localhost/api/books/1`.

#### OpenAPI and Swagger UI

`springdoc-openapi-starter-webmvc-ui` reads the controllers and DTO records (on the first request to `/v3/api-docs`, not at startup) and publishes:
- `/v3/api-docs`: the OpenAPI 3 document (JSON), for client generators and tools;
- `/swagger-ui.html`: redirects to Swagger UI, a page that lists every endpoint and lets you send requests.

springdoc is not managed by the Spring Boot BOM, so its version (`springdoc.version`, the 3.x line for Boot 4) is pinned in the root POM. `OpenApiConfig` only adds the title (from `library.name`) and the version; everything else is generated. The operation ids come from the method names, so two controllers with a `get` method produce `get` and `get_1`. That is harmless in Swagger UI, but client generators turn it into awkward names; `@Operation(operationId = "getBook")` fixes it when that matters.

#### `.http` files

`spring-app/http/*.http` hold ready-made requests for the VS Code **REST Client** extension: open the file and click *Send Request* above a request. `@host = http://localhost:8080` defines a variable used as `{{host}}`. Each request's comment states the expected status. IntelliJ's HTTP client reads the same format.

**Gotchas**
- `@PathVariable` / `@RequestParam` rely on parameter names compiled into the class (`-parameters`, set by `spring-boot-starter-parent`). Without it you must write `@PathVariable("id")`.
- Forgetting `@RequestBody` does not fail to compile. Spring then treats the parameter as a `@ModelAttribute` bound from query or form parameters, and the JSON body is ignored.
- A misspelled JSON field is dropped silently (unknown properties are ignored). The record then gets `null` for a wrapper type, or the request fails with a 400 for a primitive.
- (Up to §5.4) The in-memory storage was emptied on every restart, and it was not fully thread-safe: only the map was concurrent, the service changed stored objects in place, and delete was a check-then-act (§4.3). A database handles concurrent writers with transactions (§5.6).

### 5.3 Validation & errors

**What it is.** This section has two halves:
- **Bean Validation** rejects bad input before it reaches a service.
- **One `@RestControllerAdvice`** turns every exception into an RFC 9457 `ProblemDetail`.

Where the code is:
- [`common/`](../spring-app/src/main/java/dev/playground/library/common/): the exceptions, `GlobalExceptionHandler` and `validation/PastOrPresentYear`.
- `book/Isbn` and [`book/validation/`](../spring-app/src/main/java/dev/playground/library/book/validation/).
- The constraints on the request records.

Requests: [`http/06-validation.http`](../spring-app/http/06-validation.http).

**Why it matters.** Clients get one error format for everything, with every broken field listed at once. Services throw domain exceptions and never deal with HTTP.

#### Bean Validation — `IsbnValidatorTest`, `PastOrPresentYearTest`

*Jakarta Validation* is the specification: the annotations and the `jakarta.validation` API. *Hibernate Validator* is its implementation. It comes from the same project as Hibernate ORM but has nothing to do with databases. `spring-boot-starter-validation` brings both. The constraints sit on the record components:

```java
public record CreateBookRequest(
        @NotBlank @ValidIsbn String isbn,
        @NotBlank @Size(max = 300) String title,
        @PastOrPresentYear Integer publishedYear,
        @PositiveOrZero int totalCopies,
        Set<Long> authorIds) {}
```

| Constraint | Rejects |
|---|---|
| `@NotNull` | `null` |
| `@NotEmpty` | `null`, `""`, an empty collection |
| `@NotBlank` | `null`, `""`, `"  "` (strings only) |
| `@Size(min, max)` | a string, collection or array of the wrong length |
| `@Min` / `@Max`, `@Positive`, `@PositiveOrZero` | numbers out of range (the bounds are compile-time constants) |
| `@Past`, `@Future`, `@PastOrPresent` | dates and times (`LocalDate`, `Instant`…), not a plain `Integer` year |
| `@Email`, `@Pattern(regexp)` | strings that do not match |

Every constraint except the "not null / empty / blank" family **accepts `null`**, so constraints compose. `@PastOrPresentYear Integer publishedYear` means "optional, but sensible if present". Add `@NotNull` to make it required.

Annotations alone do nothing. Validation runs only when something triggers it:
- `@Valid` on a controller parameter;
- `@Validated` on a Spring bean or a `@ConfigurationProperties` class;
- a direct call to `Validator.validate(object)`, which is what `IsbnValidatorTest` does, with no Spring at all.

#### Custom constraints

A constraint is an annotation plus a `ConstraintValidator`. The annotation must declare `message`, `groups` and `payload` (the spec says so).

```java
@Constraint(validatedBy = IsbnValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface ValidIsbn {
    String message() default "must be a valid ISBN-10 or ISBN-13";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || Isbn.isValid(value);   // leave null/blank to @NotBlank
    }
}
```

The checksum logic lives in `book/Isbn`, not in the validator, because `BookMapper` also uses it: every ISBN is stored as 13 digits (`Isbn.toIsbn13`). That is how `0-441-01359-7` and `9780441013593` are recognised as the same book by the duplicate check (a `409`).

`@PastOrPresentYear` exists because `@Max` needs a constant, and "no later than this year" changes every January. With a `java.time.Year` field the standard `@PastOrPresent` would do; the domain model uses a plain `Integer`, which maps straight to an `int` column. When Spring creates a validator, it can have beans injected (a `Clock`, a repository).

#### `@Valid`, `@Validated` and method validation

| You write | What happens | Failure |
|---|---|---|
| `@Valid @RequestBody CreateBookRequest request` | Spring MVC validates the body before calling the method | `MethodArgumentNotValidException` → 400 |
| `@PathVariable @Positive Long id` (a constraint directly on a parameter) | Spring MVC's **built-in method validation** (Spring 6.1+) | `HandlerMethodValidationException` → 400 |
| `@Valid` on a field, record component or type argument (`@Valid AddressRequest address`, `List<@Valid Item> items`) | Validation cascades into the nested object; without `@Valid` its constraints are ignored (`CascadingValidationTest`) | reported with the outer object (`address.city`, `items[0].name`) |
| `@Validated` on a `@ConfigurationProperties` class | Validated once, at startup. Boot validates every nested object it binds, `@Valid` or not; `@Valid` on `loans` also covers the case where no `library.loans.*` key is set | the application does not start (`LibraryPropertiesTest.invalidSettingsStopTheStartup`) |
| `@Validated` on a `@Service` class + constraints on method parameters | A proxy validates every call | `ConstraintViolationException` (or `MethodValidationException` with `spring.validation.method.adapt-constraint-violations=true`); not handled here, so a 500 |

`@Valid` is the Jakarta annotation ("validate this object"). `@Validated` is Spring's own: it switches on method validation for a bean and accepts **validation groups** (`@Validated(OnCreate.class)`), which select a subset of the constraints. This project uses separate `Create…`/`Update…` records instead of groups, which is simpler to read.

**The gotcha.** Once a controller method has a constraint directly on a parameter (the `@Positive` id), method validation takes over. The `@Valid` body is then validated as part of that step, so a bad body on `PUT /api/books/{id}` raises `HandlerMethodValidationException`, **not** `MethodArgumentNotValidException`. `GlobalExceptionHandler` handles both into the same `errors` shape (`AuthorControllerTest.updateWithAnInvalidBodyReportsTheFieldErrors`). Older code does this with `@Validated` on the controller class and handles `ConstraintViolationException`; without a handler for it, that code answers 500.

#### Errors as `ProblemDetail` — `GlobalExceptionHandlerTest`

RFC 9457 (which replaced RFC 7807) defines a JSON error format, served as `application/problem+json`:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/books",
  "errors": {
    "isbn": ["must be a valid ISBN-10 or ISBN-13"],
    "title": ["must not be blank"],
    "totalCopies": ["must be greater than or equal to 0"]
  }
}
```

| Member | Meaning |
|---|---|
| `type` | A URI naming the problem type. Its default, `about:blank`, means "just the status"; Spring leaves it out of the JSON, which the RFC allows |
| `title` | Short summary of the type; Spring fills in the status reason phrase |
| `status` | The HTTP status, repeated in the body |
| `detail` | This occurrence, for a human |
| `instance` | The request path, filled in by Spring MVC |
| anything else | Extension members: here `errors`, set with `problem.setProperty("errors", map)` |

`ProblemDetail` is Spring's class for that body. `GlobalExceptionHandler` is a `@RestControllerAdvice`: its `@ExceptionHandler` methods apply to every controller.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)                       // everything else is a bug
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
    // + overrides of handleMethodArgumentNotValid / handleHandlerMethodValidationException that add "errors"
}
```

| Exception | Status | Where it comes from |
|---|---|---|
| `NotFoundException("Book", 7)` | 404, `"Book 7 not found"` | services |
| `ConflictException(message)` | 409 | services (duplicate ISBN; more rules in §5.5) |
| `ExternalServiceException(service, cause)` | 502, cause only in the log | the Open Library client (§5.9) |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException` | 400 + `errors` | `@Valid`, parameter constraints |
| Spring MVC's own: unreadable JSON, type mismatch, unknown path, 405, 406, 415 | as before (§5.2), now as ProblemDetails | handled by the base class `ResponseEntityExceptionHandler` |
| any other `Exception` | 500, generic `detail`, stack trace logged at ERROR | bugs |

- **The services know nothing about HTTP.** They throw plain unchecked exceptions from `common/`; the handler decides the status. §5.2's `ResponseStatusException` worked, but it tied the service to Spring MVC.
- **The closest exception type wins.** When several handlers match, Spring picks the one declared for the nearest superclass. The catch-all `Exception` handler therefore never hides the more specific ones, including the base class's. An `@ExceptionHandler` method inside a controller wins over the advice, for that controller only.
- **`spring.mvc.problemdetails.enabled: true`** registers Spring Boot's own `ResponseEntityExceptionHandler`, which makes Spring MVC's exceptions ProblemDetails. It backs off as soon as the application defines its own handler (`@ConditionalOnMissingBean`), so this project does not set it.
- **Unhandled errors** (none should be left) and errors raised outside Spring MVC, such as in a servlet filter, go to Spring Boot's `/error` endpoint. That endpoint writes the older `timestamp`/`status`/`error`/`path` JSON.

#### Messages and locale

Hibernate Validator ships its built-in messages in many languages. On its own it picks the JVM's locale. Inside a request, Spring passes it the request's locale instead: the `LocaleResolver` reads `Accept-Language`, and when the header is missing it uses the JVM's locale. The first run on a Spanish Windows machine returned `"no debe estar vacío"` next to our English-only custom messages. `application.yml` pins the API to English:

```yaml
spring:
  web:
    locale: en
    locale-resolver: fixed
```

Validation that runs **outside a request** still uses the JVM locale; `@ConfigurationProperties` validation at startup is an example. That is why `LibraryPropertiesTest` checks the key and the constraint name, not the message text. MockMvc requests default to English, so the tests alone did not catch any of this.

#### Three ways to test it

| Test | Setup | Use it for |
|---|---|---|
| `IsbnValidatorTest`, `PastOrPresentYearTest`, `CascadingValidationTest` | `Validation.buildDefaultValidatorFactory().getValidator()`, no Spring | A constraint's rules and Bean Validation itself, fast |
| `GlobalExceptionHandlerTest` | `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler)`: MVC without a Spring context | The handler, driven by a test-only controller that throws each exception |
| `AuthorControllerTest`, `BookControllerTest` | `@WebMvcTest`: it picks up every `@ControllerAdvice` automatically | The real JSON a client gets for each endpoint |

The test-only controller in `GlobalExceptionHandlerTest` needs `@RestController`: since Spring 6, even standalone MockMvc maps only `@Controller` classes. It is a **non-static** inner class so that component scanning skips it; scanning only picks up top-level and static nested classes. Otherwise it would show up in every test that boots the whole application.

#### OpenAPI

springdoc reads the constraints into the schema. `@NotBlank` fields are listed under `required`, `@Size(max = 300)` becomes `maxLength`, and `@Positive` on the id becomes `exclusiveMinimum: 0`. Error responses are not documented automatically: the handlers have no `@ResponseStatus`. Add `@ApiResponse` on the operations when a client needs them listed.

**Gotchas**
- **Forgetting `@Valid` silently skips validation.** The constraints on the record are still there; nothing checks them.
- **Cascading needs `@Valid`.** In a request body, a nested object's constraints (or a list element's) are ignored unless the field or type argument that holds it has `@Valid` (`CascadingValidationTest`). `@ConfigurationProperties` binding is the exception: Boot validates nested objects anyway.
- **A missing primitive fails before validation.** Jackson rejects a missing `int totalCopies` while reading the body. The 400 then says only `"Failed to read request"`, with no `errors` map. For a per-field message, use `Integer` with `@NotNull`.
- **Two exceptions for one kind of mistake.** A bad body arrives as `MethodArgumentNotValidException` or as `HandlerMethodValidationException`, depending on whether any parameter of that method has a constraint. Handle both.
- **The catch-all handler catches too much.** From §5.7, Spring Security's `AccessDeniedException` must reach the security filters to become a 403. A generic `@ExceptionHandler(Exception.class)` would turn it into a 500 unless it is handled or rethrown first. The same happens to an exception class annotated with `@ResponseStatus`: that annotation is read by a resolver that runs after the advice, so the catch-all gets there first.
- **Never echo an unexpected exception's message.** It can contain SQL, file paths or data. The 500 body is generic on purpose; the log has the rest.

### 5.4 Persistence with JPA

**What it is.** Authors and books now live in PostgreSQL. The pieces:
- **Flyway** creates and evolves the schema from hand-written SQL files.
- **Hibernate** (JPA) maps the tables to the `Author` and `Book` classes.
- **Spring Data JPA** generates the repositories from interfaces.
- **Docker Compose support** starts the database on `spring-boot:run`, and **Testcontainers** starts a throwaway one for each test context.

The controllers did not change. The services now use repository interfaces and `@Transactional`.

Where the code is:
- The entities and repositories in [`author/`](../spring-app/src/main/java/dev/playground/library/author/) and [`book/`](../spring-app/src/main/java/dev/playground/library/book/).
- [`db/migration/`](../spring-app/src/main/resources/db/migration/) and [`db/demo/`](../spring-app/src/main/resources/db/demo/).
- [`compose.yaml`](../spring-app/compose.yaml), and the `spring.jpa` keys in `application.yml` / `application-dev.yml`.
- Tests: `BookRepositoryIT`, `BookControllerIT`, `AuthorControllerIT`, `LibraryApplicationIT`, `DemoDataIT`, plus the Mockito service tests.

Requests: [`http/07-jpa.http`](../spring-app/http/07-jpa.http).

**Why it matters.** Almost every Spring job is Spring Data JPA on a relational database, with Flyway or Liquibase owning the schema. Most JPA surprises come from not knowing when Hibernate actually talks to the database. This section is about exactly that.

#### Three layers: JPA, Hibernate, Spring Data JPA

| Layer | What it is | What you write |
|---|---|---|
| **JPA** (Jakarta Persistence) | A specification: annotations (`@Entity`, `@Id`…), the `EntityManager` API, JPQL | The annotations on the entities |
| **Hibernate ORM** | The implementation Spring Boot uses. It turns entity changes into SQL | Nothing directly; you see it in the SQL log |
| **Spring Data JPA** | Generates repository classes from interfaces, on top of the `EntityManager` | `interface BookRepository extends JpaRepository<Book, Long>` |

`spring-boot-starter-data-jpa` brings all three, plus the HikariCP connection pool. The `postgresql` JDBC driver is a `runtime` dependency, because the code never imports it.

#### How it starts

```
./mvnw -pl spring-app spring-boot:run -Dspring-boot.run.profiles=dev
  │
  ├─ Docker Compose support finds spring-app/compose.yaml → docker compose up
  │     └─ postgres:17 starts; on first start the image creates the "library" database
  │        from POSTGRES_DB (nothing to create by hand)
  ├─ Boot builds the DataSource from the running compose service (URL, user, password):
  │     no spring.datasource.* keys anywhere
  ├─ Flyway: reads flyway_schema_history, applies what is pending
  │     V1__create_authors_and_books.sql   (versioned, once)
  │     R__demo_data.sql                   (repeatable, dev profile only)
  ├─ Hibernate: ddl-auto=validate → every entity must match its table, or startup fails
  └─ Tomcat on :8080
```

On shutdown Boot runs `docker compose stop`, but only if it started the services itself; a container that was already running is left alone. The data lives in a named volume, so it survives restarts. `docker compose -f spring-app/compose.yaml down -v` deletes it. A port already in use (another PostgreSQL on 5432) makes the container fail to start: stop the other one or change the host port in `compose.yaml`.

Compose support is a development convenience. `spring-boot-docker-compose` is an optional dependency, so the packaged jar leaves it out. `java -jar` (§3) then needs the connection settings from outside: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/library`, plus `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

Tests use a different route to the same result:

```
BookControllerIT starts
  ├─ @Import(TestcontainersConfiguration) → a PostgreSQLContainer bean → a fresh postgres:17 container
  ├─ @ServiceConnection → the DataSource points at it
  ├─ Flyway migrates the empty database (no demo data: db/demo is only on the dev path)
  ├─ tests run
  └─ the JVM ends → Testcontainers removes the container
```

Spring caches test contexts (§5.8), so classes with the same setup (`BookControllerIT` and `AuthorControllerIT`) share one context and one container. Compose support is off in tests by default (`spring.docker.compose.skip.in-tests`).

Boot 3 tutorials use other names for these pieces:

| Boot 4.1 / Testcontainers 2 (this project) | Boot 3 / Testcontainers 1 |
|---|---|
| `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, from `spring-boot-starter-data-jpa-test` | `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest`, from `spring-boot-starter-test` |
| `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager` | `org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager` |
| `org.testcontainers.postgresql.PostgreSQLContainer` (not generic) | `org.testcontainers.containers.PostgreSQLContainer<?>` |
| artifacts `testcontainers-postgresql`, `testcontainers-junit-jupiter` | `postgresql`, `junit-jupiter` (group `org.testcontainers`) |
| nothing extra: `@DataJpaTest` keeps a `@ServiceConnection` database | often `@AutoConfigureTestDatabase(replace = NONE)`: before Boot 3.4, `@DataJpaTest` swapped any DataSource for an embedded one |

#### Schema strategies

| Strategy | Source of truth | Tooling |
|---|---|---|
| **Code first** | The classes; the tool generates migrations from them | EF Core Migrations. Hibernate's `ddl-auto: create`/`update` is the closest thing, but it has no versioned migrations: prototypes only |
| **Database first** | An existing database; the classes are generated from it | EF `Scaffold-DbContext`. Hibernate Tools or IDE plugins reverse-engineer entities |
| **Migration first** (this project) | Hand-written SQL migrations; the entities are written to match | **Flyway** (SQL files), or **Liquibase** (XML/YAML/SQL changelogs; it can also diff entities against a database) |

`ddl-auto: validate` connects the two sides. Hibernate does not create anything; it checks at startup that every mapped table and column exists with a compatible type, and fails if not. A misspelled column name is caught at boot, not on the first request. It does not check lengths, constraints or indexes, and it ignores extra columns (the unmapped `created_at`/`updated_at`, filled by the database defaults until §5.5 maps them).

#### Flyway

```
db/migration/V1__create_authors_and_books.sql   V<version>__<description>.sql
db/demo/R__demo_data.sql                         R__<description>.sql
```

- **Versioned** (`V1`, `V2`…) migrations run once, in order. Flyway records each one, with a checksum, in the `flyway_schema_history` table (`LibraryApplicationIT` reads it).
- **Never edit an applied migration.** The checksum changes and Flyway refuses to start ("migration checksum mismatch"). Write `V2__...` instead. In a playground you can reset with `down -v`; on a shared database you cannot.
- **Repeatable** (`R__`) migrations run after the versioned ones, and again whenever their checksum changes. So they must be idempotent. The demo script uses `ON CONFLICT (isbn) DO NOTHING` for books, and `WHERE NOT EXISTS` for authors (no natural key to conflict on). `DemoDataIT` runs it twice.
- `spring.flyway.locations` chooses the folders. `application-dev.yml` adds `classpath:db/demo`, so tests and other profiles never get sample data.
- Flyway also **validates** the history against the files it can see. After a dev run, the history lists `R__demo_data.sql`, which a run without `dev` cannot see. Flyway's default is then to refuse to start ("Detected applied migration not resolved locally"). `application.yml` sets `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"` so both profiles can share the database (`DemoDataIT.aRunWithoutTheDevProfileStillStartsOnThisDatabase`). Setting the property replaces the default `*:future`, so it is listed again.
- Since Flyway 10, database support is a separate module: `flyway-database-postgresql`, next to `spring-boot-starter-flyway`.

#### Entities — `Author`, `Book`, `BookTest`

```java
@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // bigserial: the database assigns it
    private Long id;

    @Column(nullable = false, unique = true, length = 13)  // documentation only, with validate
    private String isbn;

    private Integer publishedYear;                         // → published_year

    protected Book() {}                                    // for Hibernate (reflection)
    public Book(String isbn, String title, Integer publishedYear, int totalCopies) { ... }
    // getters, setters for the mutable fields, no setId
}
```

- **Column names** come from Spring Boot's naming strategy: `publishedYear` → `published_year`. Use `@Column(name = ...)` only when the names differ.
- **A no-argument constructor** (at least `protected`), and the class and its methods **not `final`**. Hibernate instantiates entities by reflection and creates lazy-loading proxies as subclasses (§5.5).
- **`IDENTITY`** matches `bigserial`: the id is known only after the `INSERT`. So `persist()` runs the INSERT at once instead of waiting for the flush (`BookRepositoryIT.persistMakesTheBookManagedAndAssignsTheId`). The alternative, `SEQUENCE`, lets Hibernate fetch ids ahead and batch inserts.
- **`equals`/`hashCode` follow the row, not the fields.** Two objects with the same id are the same book, even with different titles in memory. A new entity (id `null`) equals only itself. `hashCode` is a constant, because the id goes from `null` to a value on save. An id-based hash would move the entity to another bucket, and a `HashSet` holding it would lose it (`BookTest.staysInAHashSetWhenTheIdIsAssigned`). Use `instanceof` and `getId()`, not `getClass()` and the field, so that a Hibernate proxy still compares equal.
- Why not a record? Records are final, immutable and have no no-arg constructor: the opposite of what Hibernate needs. Records stay at the edges, as the DTOs.

#### The persistence context, and when SQL actually runs

The **persistence context** is Hibernate's first-level cache and change tracker, one per transaction. It is the `EntityManager`, which `DbContext` resembles. An entity is in one of four states:

| State | Meaning | How it gets there |
|---|---|---|
| **Transient** | A plain object Hibernate knows nothing about | `new Book(...)` |
| **Managed** | Tracked: Hibernate keeps a snapshot and compares it at flush | `persist`/`save` of a new entity, or loaded by `find`/a query inside a transaction |
| **Detached** | Was managed, the context is gone (the transaction ended) | The end of the transaction; `em.clear()` / `em.detach()` |
| **Removed** | Scheduled for `DELETE` at the next flush | `em.remove` / `repository.delete` |

`save()` on an entity that already has an id is a **merge**: Hibernate copies its state onto a managed instance and **returns that one**; the object you passed stays detached. Always use what `save()` returns (`book = repository.save(book)`).

**Dirty checking.** At **flush** time Hibernate compares every managed entity with its snapshot and sends an `UPDATE` for each one that changed. A flush happens at commit, on `em.flush()` / `saveAndFlush`, and before a query that reads a table with pending changes (**auto flush**). So inside a transaction, changing a managed entity *is* the save:

```java
@Transactional
public BookResponse update(Long id, UpdateBookRequest request) {
    Book book = getOrThrow(id);                       // managed until the method returns
    if (repository.existsByIsbnAndIdNot(isbn13, id))  // check BEFORE changing the entity
        throw duplicateIsbn(isbn13);
    BookMapper.apply(request, book);                  // just setters
    return BookMapper.toResponse(book);               // no save(): UPDATE on commit
}
```

Two traps are pinned by tests (each one checked by breaking the code and watching the test fail):
- **Without `@Transactional` nothing is saved.** Each repository call then runs in its own short transaction, so `book` is already detached when the setters run. The PUT answers 200 with the new values, and the table never changes (`BookControllerIT.createReadUpdateDeleteRoundTrip` catches it).
- **Auto flush turns the order into a bug.** Apply the changes first, and the `exists` query makes Hibernate flush the `UPDATE` before running the check. The duplicate ISBN then hits the unique constraint: the client gets the generic 409 instead of the service's message (`BookControllerIT.updateOntoAnotherBooksIsbnReturns409FromTheServiceCheck`).

`@Transactional` goes on the service layer. Since §5.6 every service is read-only at class level (`@Transactional(readOnly = true)`) and its write methods add `@Transactional`. §5.6 explains how it works (a proxy), `readOnly`, propagation and rollback rules.

#### Repositories and queries — `BookRepositoryIT`

```java
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);                                   // derived
    boolean existsByIsbnAndIdNot(String isbn, Long id);
    List<Book> findByTitleContainingIgnoreCase(String title, Sort sort);

    @Query("select b from Book b where b.publishedYear between :from and :to order by b.publishedYear, b.title")
    List<Book> findPublishedBetween(int from, int to);                    // JPQL

    @Query(value = "select * from books where to_tsvector('english', title) @@ plainto_tsquery('english', :words)"
            + " order by id", nativeQuery = true)
    List<Book> searchTitles(String words);                                // native SQL
}
```

- **`JpaRepository<Book, Long>`** gives `findAll`, `findAll(Sort)`, `findById`, `save`, `saveAndFlush`, `existsById`, `deleteById`, `count`… Spring Data generates the class at startup. Its methods are transactional on their own (read-only for reads).
- **Derived queries** are parsed from the method name: `findBy`/`existsBy`/`countBy`/`deleteBy`, then properties joined by `And`/`Or`, with keywords (`ContainingIgnoreCase`, `Between`, `Not`, `OrderByTitleAsc`…). A misspelled property fails at startup. Past two or three conditions the names get unreadable: switch to `@Query`.
- **JPQL** queries entities and fields (`Book`, `publishedYear`), not tables and columns, and Hibernate checks it at startup. `:from` binds to the parameter named `from`: this works because the code is compiled with `-parameters`; otherwise add `@Param("from")`.
- **Native SQL** (`nativeQuery = true`) is for what JPQL cannot say. Here that is PostgreSQL full-text search, where `"dunes"` finds *Dune* through stemming. It ties the query to PostgreSQL and is only checked when it runs, which is one more reason these tests use a real PostgreSQL and not H2.
- A `Sort` parameter adds `order by`. `GET /api/books?title=` uses the derived query, and paging arrives in §5.5.

`@DataJpaTest` is the JPA slice: entities, repositories, Flyway and a `TestEntityManager`, with no web layer and no services. Each test runs in a transaction that is **rolled back** at the end, so the tests do not see each other's rows. The full-stack `*ControllerIT` tests go through MockMvc and the service's own transactions. They commit for real, so they empty the tables in `@BeforeEach`.

#### Errors

The service checks the rules first (`existsByIsbn` → `ConflictException` → 409 with a precise message). Two requests can still pass the same check at the same moment. The database's unique constraint then rejects the second `INSERT`, and Spring translates the driver's exception into `DataIntegrityViolationException`. `GlobalExceptionHandler` maps that to a 409 with a generic detail, and the log keeps the constraint name (`GlobalExceptionHandlerTest.dataIntegrityViolationIs409WithoutTheSql`).

That translation turns every vendor's `SQLException`s into Spring's one `DataAccessException` hierarchy. It happens in two places, with nothing to annotate:
- **Spring Data's repository proxies** translate what a repository call throws. A hand-written DAO gets the same by carrying `@Repository`.
- **`JpaTransactionManager`** translates what fails at commit. A concurrent update, for example, only reaches the database at the flush on commit.

The same exception also covers `NOT NULL`, `CHECK` and "value too long" violations. Those should not happen here, because validation (§5.3) rejects such input first. If a bug lets one through, the client gets a 409 and the log shows the constraint. A stricter handler would answer 409 only for unique violations (SQLState `23505`, the driver's code for them) and 500 for the rest.

#### Configuration

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate      # Flyway owns the schema
    open-in-view: false
```

- **`open-in-view: false`.** Spring Boot's default is `true`: the persistence context stays open until the HTTP response is written. Lazy relations (§5.5) then load silently from the controller or the JSON serializer, firing extra queries outside any transaction. Boot even logs a warning about it at startup. Off, data access stays in the services, and a missing fetch fails loudly with `LazyInitializationException` (§5.5).
- **Seeing the SQL** (dev profile): `logging.level.org.hibernate.SQL: DEBUG` logs every statement, `org.hibernate.orm.jdbc.bind: TRACE` logs the parameter values, and `hibernate.format_sql: true` indents them. Prefer these over `spring.jpa.show-sql`, which prints to stdout and bypasses the logger.

**Gotchas**
- **`save()` on a managed entity is redundant**, and forgetting `@Transactional` makes a missing `save()` lose the change silently (above).
- **`ddl-auto: update` in real projects.** It never drops or renames anything, it has no history, and two developers get two different schemas. Keep it off; write a migration.
- **Editing `V1__...sql` after it ran** fails the next startup with a checksum mismatch. Add a new version.
- **H2 is not PostgreSQL.** Native queries, `ON CONFLICT`, `timestamptz` and the constraint names differ. Tests that pass on an in-memory database can fail in production, which is why Testcontainers exists.
- **Lombok's `@Data` on an entity** generates field-based `equals`/`hashCode`/`toString`. That breaks sets, and with relations (§5.5) it can recurse forever or trigger lazy loads. Write them by hand as above.
- **`IDENTITY` disables JDBC insert batching** in Hibernate. That does not matter here; it matters for bulk imports.
- **Docker must be running** for `spring-boot:run` and for every `*IT` test. `./mvnw test` (unit and slice tests) still runs without it.

### 5.5 Advanced JPA

**What it is.** The library gets its relations and its rules:
- Books and authors become **many-to-many** through a join table.
- **Members** and **loans** are new. A loan points to one book and one member.
- Lists are **paged**, and filters are composed at runtime with **Specifications**.
- Timestamps are filled by **auditing**.

Most of the section is about the question §5.4 started: *how many SQL statements does this code send, and when?*

Where the code is:
- [`V2__book_authors_members_loans.sql`](../spring-app/src/main/resources/db/migration/V2__book_authors_members_loans.sql).
- `Book.authors` / `Author.books`, `BookRepository`, `BookSpecifications`, `BookTitleOnly`.
- The new [`member/`](../spring-app/src/main/java/dev/playground/library/member/) and [`loan/`](../spring-app/src/main/java/dev/playground/library/loan/) features.
- `common/AuditedEntity`, `common/PageResponse`, `common/Paging`, `config/ClockConfig`, `config/JpaAuditingConfig`.
- Tests: `BookQueriesIT` and `LoanRepositoryIT` (they count statements), `AuditingIT`, `LoanServiceTest`, and `LoanControllerIT`, `MemberControllerIT` and the other `*ControllerIT`s.

Requests: [`http/08-relations.http`](../spring-app/http/08-relations.http).

**Why it matters.** Relations are where JPA code gets slow (N+1) or breaks at runtime (`LazyInitializationException`). Knowing which side of a relation writes, what loads lazily, and how to fetch a list in a fixed number of queries is most of day-to-day JPA.

#### The schema

```
authors ──< book_authors >── books ──< loans >── members
             (book_id, author_id)        book_id, member_id, loaned_at,
                                         due_date, returned_at (NULL = active)
```

- `book_authors` has no entity: it is the join table of `Book.authors`. Deleting a book deletes its links (`ON DELETE CASCADE`). Deleting an author who still has books is refused by the foreign key, and before that by the service (409).
- `loans` has no `ON DELETE`: a book or member with loans cannot be deleted. `BookService.delete` checks it first, for a clear 409.
- Two indexes serve the queries that run most: `book_authors(author_id)` for "books of an author", and a **partial index** `loans(book_id) WHERE returned_at IS NULL` for the availability count.

#### Mapping relations — owning and inverse side

```java
// Book: the owning side. Hibernate writes book_authors from this set.
@ManyToMany
@JoinTable(name = "book_authors",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id"))
@BatchSize(size = 100)
private Set<Author> authors = new HashSet<>();

// Author: the inverse side. mappedBy names the owning field; nothing here is ever written.
@ManyToMany(mappedBy = "authors")
private Set<Book> books = new HashSet<>();

// Loan: many loans per book. The foreign key column is on this table, so this side owns it.
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "book_id")
private Book book;
```

- **One relation, one owner.** A bidirectional relation is one set of rows mapped twice. Hibernate writes only from the owning side, the one without `mappedBy`. Add a book to `author.getBooks()` and nothing reaches the database. That is why `Author.getBooks()` returns an unmodifiable view, and links change through `Book.replaceAuthors`. `BookQueriesIT.linksAreWrittenThroughTheOwningSide` shows the write, and `theInverseSideReadsTheSameJoinTable` shows the inverse side reading the same rows.
- **`@ManyToOne`** owns the foreign key column. A `@OneToMany(mappedBy = "book")` on `Book` would be its inverse. It is left out on purpose: nothing needs "all loans of a book" as a collection, and counting them is a query.
- **`Set`, not `List`**, for `@ManyToMany`. A `List` without an order column is a "bag": removing one author makes Hibernate delete every link row of the book and insert the rest again. A `Set` gets one DELETE or INSERT per change. `replaceAuthors` uses `retainAll` + `addAll` rather than `clear` + `addAll`, so the links that stay are never touched.
- **Cascade and `orphanRemoval`: none here.** `cascade = CascadeType.PERSIST/MERGE/REMOVE` repeats an operation on the related entities. `orphanRemoval = true` deletes a child that leaves its parent's collection. Both fit children that belong to one parent, such as order lines. They do not fit shared entities: `REMOVE` on `Book.authors` would delete an author who wrote other books too. Authors, books and members have their own lifecycles, so each is saved on its own.

#### LAZY and EAGER

| Annotation | Default fetch |
|---|---|
| `@ManyToOne`, `@OneToOne` | **EAGER** |
| `@OneToMany`, `@ManyToMany` | LAZY |

EAGER means "always load it, whether this query needs it or not". The to-one default dates from JPA 1.0 and is widely regretted: every loan loaded would load its book and member too, often with one extra query each. **Mark every `@ManyToOne` `LAZY`** and fetch explicitly where a use case needs the data.

- **Lazy means a placeholder.** A lazy to-one is a **proxy**, a generated subclass of `Book` holding only the id. A lazy collection is a `PersistentSet`. The first real use runs the query, and that needs an open persistence context.
- **`getId()` on a proxy is free.** The proxy was built from `loans.member_id`, so `loan.getMember().getId()` sends nothing (`LoanRepositoryIT.theMemberIdOfALazyProxyNeedsNoQuery`). Any other method loads the row, and so does `hashCode()`. The constant `hashCode` of §5.4 is therefore not free on a proxy: putting one in a `HashSet` runs a SELECT.
- **`LazyInitializationException`.** Touch a lazy relation after the transaction has ended (a detached entity), and Hibernate cannot load it: *"Cannot lazily initialize collection of role … (no session)"* in Hibernate 7 (older versions say "failed to lazily initialize a collection") (`BookQueriesIT.aLazyCollectionCannotLoadOnceTheBookIsDetached`). With `open-in-view: false` (§5.4) the persistence context closes when the service method returns. So the mapping to DTOs happens **inside** the service, in a transaction: `@Transactional(readOnly = true)` on `BookService.findAll/findById` and `LoanService.findAll/findById`. With open-in-view on, the same code "works", and the controller or Jackson fires the queries without you noticing.

#### N+1, and three fixes

List 20 books and map each one's authors, and a lazy collection costs one query per book: 1 query for the list, plus N for the authors. That is the **N+1 problem**. It is invisible in a unit test and slow in production. The tests turn Hibernate's `Statistics` on (`hibernate.generate_statistics`, in the test only) and count the statements:

| Code | Statements (4 books) | Test |
|---|---|---|
| `findAll()`, then touch each book's authors, no fix | 5 = 1 + 4 | the RED run of `touchingEveryBooksAuthorsIsOneBatchNotOneQueryPerBook` |
| Same, with `@BatchSize(size = 100)` on `Book.authors` | 2 | `touchingEveryBooksAuthorsIsOneBatchNotOneQueryPerBook` |
| `BookService.findAll` (a page of books, as `GET /api/books` returns it), no fix | 6 = 1 + 4 + 1 | the RED run of `listingAPageOfBooksTakesThreeStatements` |
| Same, with `@BatchSize` | **3**, for any page size | `listingAPageOfBooksTakesThreeStatements` |
| `findById`, then touch the authors | 2 | `anEntityGraphFetchesTheAuthorsInTheSameStatement` |
| `findWithAuthorsById` (`@EntityGraph`) | 1 | same test |
| Loans with their books: inherited `findAll()` | 4 = 1 + 3 distinct books | `LoanRepositoryIT.withoutAnEntityGraphEachLoansBookIsAnotherQuery` |
| Loans with `@EntityGraph(attributePaths = "book")` | 1 | `theEntityGraphFetchesTheBooksInTheSameQuery` |

The three fixes:
1. **A fetch join in JPQL**: `select b from Book b left join fetch b.authors where ...`. One query; you write it by hand.
2. **`@EntityGraph(attributePaths = "authors")`** on a repository method does the same for a derived or inherited query. It is used on `findWithAuthorsById` and on the loans list (`LoanRepository` overrides `findAll(Specification, Sort)` only to add it).
3. **Batch fetching**: `@BatchSize` on the relation, or `hibernate.default_batch_fetch_size` for all of them. The code stays naive; when the first collection is touched, Hibernate loads the collections of up to *size* books from the persistence context in one statement (`... where book_id = any (?)` on PostgreSQL).

The page of books uses batch fetching. Its three statements, from the SQL log: the page (`... order by title, id offset ? rows fetch first ? rows only`), the active loans grouped by book, then the authors of all the books on the page.

**Collection fetch + paging.** Older advice says never to fetch-join a collection on a paged query. Older Hibernate versions could not put `LIMIT` on rows that a join had multiplied: they loaded everything and paged in memory, with only a warning (`HHH000104` in Hibernate 5, `HHH90003004` in 6). Hibernate 7.4, the version here, did it in SQL for both an `@EntityGraph` and a JPQL `join fetch` on a paged query (checked while writing this phase): the log shows the page as a subquery on `books`, with the authors joined outside it. So `@EntityGraph` on the paged query would work too (2 statements instead of 3). Batch fetching was kept because it covers every path that touches `authors` (create, update, the single book) without a per-query annotation, and does not depend on how a Hibernate version pages. On an older Hibernate, check the SQL before trusting a fetch join on a page. `hibernate.query.fail_on_pagination_over_collection_fetch=true` turns the in-memory fallback into an error.

`availableCopies` is the other half of N+1. A count query per book would be N+1 again, so the page gets one grouped query:

```java
@Query("""
        select new dev.playground.library.loan.ActiveLoanCount(l.book.id, count(l))
        from Loan l
        where l.book.id in :bookIds and l.returnedAt is null
        group by l.book.id""")
List<ActiveLoanCount> countActiveByBookIds(Collection<Long> bookIds);
```

#### Projections

Loading entities for read-only output is often more than needed. A projection selects only the columns you ask for, and the result is not tracked.

| Kind | How | Here |
|---|---|---|
| **Interface** | A repository method returns an interface with getters; Spring Data backs them with the row | `BookTitleOnly` (`getId`, `getTitle`) from `findByAuthorsIdOrderByTitle`, for `GET /api/authors/{id}/books`. The SQL selects `id, title` only |
| **DTO / record** | A JPQL constructor expression `select new pkg.Record(...)` | `ActiveLoanCount`, one row per group |
| **Entity + mapper** | Load entities, map them to DTOs in the service | Everything else. Needed when you change the entity, handy when you need most of its fields |

#### Paging and sorting

```java
@GetMapping
public PageResponse<BookResponse> list(
        @RequestParam(required = false) String title,
        @RequestParam(required = false) Long authorId,
        @SortDefault("title") Pageable pageable) { ... }
```

- Spring builds the `Pageable` from `?page=` (**zero-based**), `?size=` (default 20) and `?sort=title,desc` (repeatable). `spring.data.web.pageable.max-page-size: 100` **clamps** `?size=500` to 100 rather than rejecting it (`BookControllerTest.aPageSizeAboveTheMaximumIsClamped`).
- `JpaSpecificationExecutor.findAll(spec, pageable)` returns a `Page`: the content plus the total. The total costs a `select count(*)`. Spring Data skips it when the first page is shorter than the page size, because that page is the whole result. `Slice` is the variant with no count, for "load more" lists.
- The API returns our own `PageResponse(content, page, size, totalElements, totalPages)`, not the `Page` object. Spring Data warns that `PageImpl`'s JSON is not a stable format. Its own alternative is `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.
- **A stable order across pages.** Sort by title alone, and two books with the same title can come back in either order. Page 1 and page 2 are separate queries, so a book could appear twice or never. `Paging.sanitize` appends `id` to every sort.
- **Only listed properties can be sorted by.** `?sort=popularity` does not exist: Spring Data would throw `PropertyReferenceException` and the catch-all would answer 500. `?sort=authors.name` is worse: it exists, so Spring Data joins `book_authors`, a book with two authors becomes two rows, and `LIMIT`/`OFFSET` counts rows, not books. The review of this phase found the same book on pages 1 and 3 and a total that changed from page to page, all with a 200. `Paging.sanitize` checks each sort against the endpoint's allow-list (`BookService.SORTABLE`) and answers 400 (`BookControllerIT.sortingThroughARelationIs400`). The `PropertyReferenceException` handler stays as a safety net.

#### Specifications — filters built at runtime

`?title=` and `?authorId=` are both optional, so there are four combinations. Four derived methods would be ugly. A **Specification** is one condition written with the JPA Criteria API; `Specification.allOf(...)` combines them, and a filter that was not given is `Specification.unrestricted()`:

```java
public static Specification<Book> hasAuthor(Long authorId) {
    if (authorId == null) return Specification.unrestricted();
    return (book, query, cb) -> cb.equal(book.join("authors").get("id"), authorId);
}

Specification<Book> filters = Specification.allOf(titleContains(title), hasAuthor(authorId));
Page<Book> page = books.findAll(filters, Paging.sanitize(pageable, SORTABLE));
```

- The repository opts in with `extends JpaSpecificationExecutor<Book>`. It is the closest thing to composing `IQueryable` in LINQ.
- Property names are strings (`"title"`), checked only when the query runs. The Hibernate annotation processor can generate a typed metamodel (`Book_.title`), which this project does not use.
- **Escape the `LIKE` wildcards.** A derived `Containing` query escapes `%` and `_` for you. A hand-written `like` does not, and `?title=%` would match every book. `BookSpecifications.titleContains` uses Spring Data's `EscapeCharacter` (`BookQueriesIT.likeWildcardsInTheTitleFilterAreLiteral`).
- **Fold the case on one side only.** The first version upper-cased the pattern in Java and the column in PostgreSQL. They disagree on some letters: Java turns `ß` into `SS`, PostgreSQL keeps `ß`, so `?title=straße` found nothing. Both sides now go through the database's `upper()` (`theTitleFilterFoldsCaseLikeTheDatabase`).

#### Auditing and the clock

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(nullable = false) private Instant updatedAt;
}
```

- `Author` and `Book` extend it. A `@MappedSuperclass` has no table of its own: its fields become columns of each subclass's table. `Member` has only `created_at`, so it registers the listener itself.
- `@EnableJpaAuditing` turns the listener on. It sits on its own class, `JpaAuditingConfig`, and not on `LibraryApplication`: `@WebMvcTest` reads the main class's annotations, and auditing needs JPA, which that slice does not have. The time comes from our `Clock` bean (`dateTimeProviderRef`), so tests decide what "now" is. `AuditingIT` replaces the clock with `@MockitoBean`.
- **Everything that needs "now" takes the `Clock`.** `LoanService` computes `dueDate = LocalDate.now(clock).plusDays(14)`, and `LoanServiceTest` passes `Clock.fixed(...)` to assert an exact date. The same idea as injecting `TimeProvider` in .NET 8.

#### The loan rules — `LoanService`, `LoanServiceTest`, `LoanControllerIT`

| Rule | Answer |
|---|---|
| Unknown book, member or loan | 404 |
| No copy left (`active loans >= totalCopies`) | 409 `Book 4 has no available copies` |
| The member already holds `library.loans.max-active` (3) | 409 |
| Returning a returned loan | 409, and `returnedAt` keeps its first value (**`returningTwiceIsRejected`**) |
| `PUT` a book with `totalCopies` below its active loans | 409 |
| Delete a book with loans, or an author with books | 409 |
| Unknown id in a book's `authorIds` | 404 `Author 999 not found` |

- `availableCopies` is not stored. It is `totalCopies` minus the active loans, counted on every read, so it can never drift.
- Returning is `POST /api/loans/{id}/return`: an action on the loan, not a `PUT` of the whole resource.
- **Two borrows of the last copy at the same moment both succeeded** with the code of this section: both pass the count before either inserts. §5.6 closes that race with optimistic locking.
- **Two returns of the same loan at the same moment both answered 200**, and the second overwrote `returnedAt`, for the same reason. §5.6 closes that one too.

**Gotchas**
- **`@PageableDefault(sort = "title")` also sets the page size**, to its own default of 10, not the global 20. `@SortDefault("title")` sets only the sort. A RED test caught this.
- **`@DataJpaTest` does not load your `@Configuration` classes.** Without `@Import(JpaAuditingConfig.class)`, `created_at` is inserted as `null` and the NOT NULL constraint fails. `BookRepositoryIT` hit exactly that.
- **Timestamps lose precision in the database.** Java's clock ticks below a microsecond on this machine, and `timestamptz` keeps microseconds. `POST /api/loans` answered `…896334700Z` while a later `GET` read `…896335Z`, the value rounded by PostgreSQL. `LoanService` truncates its "now" to microseconds.
- **A change to a collection alone moves `updatedAt` only on a versioned entity.** A `PUT` that changes only `authorIds` rewrites `book_authors`. `Book` has a `@Version` (§5.6), and Hibernate counts a change to a collection the entity owns as a change of the entity: it bumps the version with an `UPDATE` of the `books` row, so `@PreUpdate`, and with it `@LastModifiedDate`, runs (`AuditingIT`). On an entity without a version, no `UPDATE` of the row is sent and `updatedAt` stays.
- **`mappedBy` side edits are silently ignored**, and so are edits to a detached entity's collection. There is no error; nothing is written.
- **`toString`, `equals` or JSON serialisation walking a relation** loads it, or recurses forever through a bidirectional pair. Keep those methods on plain fields, and never serialise entities.
- **Spring Data 4 moved some types.** `PropertyReferenceException` and `TypeInformation` now live in `org.springframework.data.core` (they were in `org.springframework.data.mapping`/`util`). `Specification.unrestricted()` is the "no condition" specification; older code passed `null` around instead.

### 5.6 Transactions

**What it is.** A transaction groups statements into one unit: they all commit, or none of them does. §5.4 put `@Transactional` on the write methods without explaining it. This section covers:
- how the annotation works (a proxy), and where it stops working;
- propagation (what happens when a transactional method calls another one), rollback rules and read-only transactions;
- isolation levels;
- the races §5.5 left open (the last copy, the double return, and one more it did not mention), closed with **optimistic locking**.

Where the code is:
- [`V3__versions_and_audit_events.sql`](../spring-app/src/main/resources/db/migration/V3__versions_and_audit_events.sql): `version` columns on `books`, `members` and `loans`, and the `audit_events` table.
- `Book.version`, `Member.version` and `Loan.version` (`@Version`), `BookRepository.findWithVersionIncrementById`, `MemberRepository.findWithVersionIncrementById`, `LoanService`.
- The [`audit/`](../spring-app/src/main/java/dev/playground/library/audit/) feature: `AuditService.record` runs in its own transaction.
- `GlobalExceptionHandler.handleOptimisticLock` (409).
- `@Transactional(readOnly = true)` on the class of every service.
- Tests: [`TransactionBehaviourIT`](../spring-app/src/test/java/dev/playground/library/transactions/TransactionBehaviourIT.java) (the rules, one test each), **`LoanConcurrencyIT`** (the races), `LoanControllerIT.aRejectedBorrowIsStillAudited`.

Requests: [`http/09-transactions.http`](../spring-app/http/09-transactions.http).

**Why it matters.** `@Transactional` looks like a keyword and behaves like a library: it works only where Spring can intercept the call, and several of its defaults surprise people (checked exceptions commit, a caught exception can still roll everything back). And a check followed by a write is not safe on its own when two requests run at once, whatever the code looks like.

#### How `@Transactional` works — a proxy

Spring does not change your class. At startup it wraps every bean that has `@Transactional` methods in a **proxy**, a generated subclass, and injects the proxy everywhere:

```
LoanController ──► LoanService proxy ──────────────────────► LoanService (your object)
                   before: take a connection, BEGIN,           borrow(...) runs here
                           open a persistence context,
                           bind both to the current thread
                   after:  returned         → flush, COMMIT
                           RuntimeException → ROLLBACK
                           then release the connection
```

- The transaction belongs to the **thread**. The repositories, `JdbcTemplate` and the `EntityManager` all find it there. Work handed to another thread (an executor, `@Async`, a parallel stream) runs outside it.
- **Only calls that go through the proxy count.** A call from one method to another of the same object is a plain Java call on `this`: its annotation is ignored (**self-invocation**). The proxy cannot override `private` or `final` methods either: their annotations are ignored without an error (and a `final` method runs on the proxy object itself, whose injected fields are `null`). Fixes: move the method to another bean (what `AuditService` is), or call it through the injected proxy.
- It goes on the **service layer**: a use case (check the rules, then write) is the unit of work. Each repository method already has its own short transaction (Spring Data's `SimpleJpaRepository` is annotated), which is why a service without `@Transactional` still reads, but loses its changes (§5.4).
- `TransactionTemplate` is the same thing without the annotation, for part of a method or one transaction per batch:

  ```java
  transactionTemplate.executeWithoutResult(status -> {
      writer.joining("Joined");     // commits if the lambda returns, rolls back if it throws
  });
  ```

#### Propagation — `TransactionBehaviourIT`

What happens when a transactional method calls another one:

| Propagation | Inside a transaction | Without one |
|---|---|---|
| `REQUIRED` (default) | joins it: one commit, one rollback for both | starts one |
| `REQUIRES_NEW` | suspends it, runs in a new one on a **second connection**, commits on return | starts one |
| `SUPPORTS` | joins it | runs without |
| `MANDATORY` | joins it | throws |
| `NOT_SUPPORTED` | suspends it, runs without | runs without |
| `NEVER` | throws | runs without |
| `NESTED` | a savepoint inside it, with a plain JDBC transaction manager; **throws with JPA** | starts one |

`NESTED` needs savepoints, and a savepoint would roll back the database but not the persistence context: entities would keep changes the database dropped. So `JpaTransactionManager` refuses it with `NestedTransactionNotSupportedException` (`nestedIsNotSupportedByTheJpaTransactionManager`). It works with `DataSourceTransactionManager` (plain JDBC, no JPA).

`isolation`, `readOnly` and `timeout` apply only where a transaction starts. A method that joins its caller's transaction runs with the caller's settings, whatever its own annotation says.

The audit trail uses `REQUIRES_NEW`. `LoanService` records every borrow and return request first, and a rejected request must still leave its event:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(String action, String detail) {
    events.save(new AuditEvent(action, detail, now()));
}   // commits here, before LoanService goes on to its checks
```

A borrow of a book with no copies left answers 409, and its transaction rolls back: no loan. The audit event committed before that and stays (`LoanControllerIT.aRejectedBorrowIsStillAudited`; with `REQUIRED` it is rolled back too and the test fails). `GET /api/audit-events` shows it.

Two consequences, both deliberate here and both written down in `AuditService`:
- **It fails closed.** If the audit insert fails, its exception reaches `borrow`, which fails and rolls back too: nothing happens unaudited.
- **Every borrow and return holds two connections for a moment**, its own and the audit's. That is the pool trap in the gotchas below. Fine at this scale; under load, write the event after the commit instead (§5.9) and accept that a crash in between loses it.

The tests pin each case, and each one failed when the code was broken on purpose:

| Test | Shows |
|---|---|
| `requiredJoinsTheCallersTransaction` | the outer rollback undoes the inner write |
| `requiresNewCommitsOnItsOwn` | the inner write survives the outer rollback |
| `catchingAnInnerFailureDoesNotSaveTheCallersTransaction` | see *Rollback-only* below |
| `aCheckedExceptionCommitsByDefault` / `rollbackForMakesACheckedExceptionRollBack` | the rollback rules |
| `selfInvocationSkipsTheProxy` | `REQUIRES_NEW` ignored on a call through `this` (the transaction keeps the outer method's name) |
| `aReadOnlyTransactionDoesNotWrite` | read-only: no flush, and PostgreSQL refuses a write |
| `nestedIsNotSupportedByTheJpaTransactionManager` | `NESTED` throws with JPA |

**Rollback-only.** When an exception leaves a method that *joined* a transaction, the proxy cannot roll back yet (the caller owns the transaction), so it marks the transaction **rollback-only**. Catching the exception in the caller does not clear the mark. The caller returns normally, the commit finds the mark, rolls back, and throws `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`. If a failure really is optional, run that call in `REQUIRES_NEW`, or check before calling instead of catching.

#### Rollback rules

- **Unchecked exceptions (`RuntimeException`, `Error`) roll back. Checked exceptions commit.** A method that throws `IOException` after a write commits the write. The rule comes from EJB, where a checked exception was a business outcome, not a failure.
- `@Transactional(rollbackFor = Exception.class)` rolls back on checked ones too; `noRollbackFor` does the opposite.
- The domain exceptions here (`NotFoundException`, `ConflictException`) are unchecked, so a broken rule always rolls back.

#### Read-only transactions

Every service carries `@Transactional(readOnly = true)` on the class, and its write methods override it with `@Transactional`. The method-level annotation wins. Spring Data's own repositories follow the same pattern. A read-only transaction:
- sets Hibernate's flush mode to manual: no dirty checking, no flush. An entity changed by mistake is not written (`aReadOnlyTransactionDoesNotWrite`), and Hibernate keeps no snapshots to compare, so it uses less memory;
- marks the JDBC connection read-only. PostgreSQL then runs a `READ ONLY` transaction and refuses any write: `cannot execute INSERT in a read-only transaction`.

Why a transaction for reads at all: any transaction, read-only or not, keeps the persistence context open for the whole method, and lazy loading needs that now that open-in-view is off (§5.5). It also lets a router send read-only transactions to a replica (not done here).

#### Isolation levels

What one transaction can see of another one's uncommitted or later work. `@Transactional(isolation = ...)` sets it per method; by default the database's own default applies, **READ COMMITTED** on PostgreSQL.

| Level | Dirty read | Non-repeatable read | Phantom | Notes on PostgreSQL |
|---|---|---|---|---|
| READ UNCOMMITTED | possible | possible | possible | behaves as READ COMMITTED |
| **READ COMMITTED** | no | possible | possible | the default: each statement sees what was committed when it started |
| REPEATABLE READ | no | no | no (in PostgreSQL) | one snapshot per transaction; updating a row someone else changed fails (`could not serialize access`) |
| SERIALIZABLE | no | no | no | as if the transactions ran one after the other; conflicts fail with SQLState `40001`, and the client retries |

The last-copy race is a *check, then insert a different row* pattern. REPEATABLE READ does not stop it: both transactions insert their own loan, and nothing conflicts. SERIALIZABLE does, at the price of retrying failed transactions everywhere. Optimistic locking fixes this one place instead.

#### Optimistic locking — the races

Two borrows of the last copy, one copy, READ COMMITTED:

```
T1 (Ada)                         T2 (Alan)
count active loans → 0
                                 count active loans → 0   (T1's loan is not committed)
insert loan
                                 insert loan
COMMIT                           COMMIT                   → two loans for one copy
```

**Returning** is the simpler case, because both transactions update the same row. `@Version` on `Loan` makes Hibernate check the version it read:

```java
@Version
private long version;
```
```sql
update loans set ..., returned_at=?, version=1 where id=? and version=0
```

The second UPDATE matches no row. Hibernate throws `StaleObjectStateException`, Spring translates it into `ObjectOptimisticLockingFailureException`, and everything the second transaction did rolls back. `returnedAt` keeps the first value.

**Borrowing** changes no existing row: each transaction inserts its own loan, so there is no row to compare versions on. The book becomes that row. `LoanService` reads it with a lock mode that bumps its version at commit, whether or not a book column changed:

```java
@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
Optional<Book> findWithVersionIncrementById(Long id);
```
```
T1: ... insert loan ... COMMIT: update books set version=1 where id=4 and version=0   → 1 row
T2: ... insert loan ... COMMIT: update books set version=1 where id=4 and version=0
    (waits for T1's row lock, then PostgreSQL re-checks the WHERE on the new row)     → 0 rows
    → the version check fails, and T2 rolls back, its loan with it
```

- `GlobalExceptionHandler` turns `OptimisticLockingFailureException` into **409** `The data was changed by another request at the same time. Retry the request.` A retry runs against the new state: the loser of the borrow race then gets `Book 4 has no available copies`, the loser of the return race `Loan 5 was already returned`. It logs at INFO: under load this is normal, not a bug.
- `Book.version` also moves with every edit (`update books set ..., version=? where id=? and version=?`), so an edit and a borrow racing each other collide as well: the one that commits second gets the 409 (`anEditAndABorrowOfTheSameBookCollide`). A borrow cannot slip past a `totalCopies` change it never saw.

**Each rule needs its own row to collide on.** The review of this chapter found a third race: a member with 2 loans (the maximum is 3) borrows two *different* books at once. Both pass the member count, and the two book versions are different rows, so both commit: 4 loans. The member row is the one both borrows share, so `borrow` reads the member with `OPTIMISTIC_FORCE_INCREMENT` too (`members.version`, `aMemberCannotPassTheMaximumWithTwoBorrowsAtOnce`). The general question for any check-then-write: *which row would both transactions have to update if they conflict?* If there is none, make one.
- Hibernate owns the version. A plain SQL `UPDATE` that does not bump it goes unnoticed. SQL Server's `rowversion`, in contrast, is maintained by the database.
- By default Hibernate's UPDATE sets every column, not only the changed ones (see the `update loans` above). `@DynamicUpdate` changes that; it rarely matters.

**The alternatives**

| Approach | How | Trade-off |
|---|---|---|
| Optimistic (used here) | `@Version`, `OPTIMISTIC_FORCE_INCREMENT` | Nobody waits; the loser gets a 409 and retries. Best when collisions are rare |
| Pessimistic | `@Lock(LockModeType.PESSIMISTIC_WRITE)` → `select ... for update` | The second borrow waits for the first to commit, then sees its loan: no failed request. Every borrow of that book queues; keep such transactions short, and lock rows in a fixed order to avoid deadlocks |
| Conditional UPDATE | `update loans set returned_at = ? where id = ? and returned_at is null` (`@Modifying @Query`), then check the row count | One statement for the return. Bypasses the entity, so no dirty checking or listeners |
| SERIALIZABLE | the isolation level above | Covers every race, and every transaction needs a retry |
| Automatic retry | Spring Framework 7's `@Retryable` (with `@EnableResilientMethods`) | Hides the 409. The retry must wrap the transactional call: retrying inside the failed transaction is useless, it is already rolled back |

**Lost updates across requests.** The version here protects the few milliseconds of one transaction. "I opened the edit form, someone else saved, then I saved" spans two requests: the second `PUT` reads the new version and overwrites the other change. The HTTP answer is to send the version to the client (an `ETag` header), require it back (`If-Match`), and answer `412 Precondition Failed` when it no longer matches. Not implemented here.

#### Forcing the race in a test — `LoanConcurrencyIT`

Two threads started at the same moment rarely overlap: a request takes milliseconds, and the second one usually starts after the first has committed. It sees the loan and fails the check, so the test passes even without the fix. The test forces the overlap instead:

```java
statement.execute("LOCK TABLE loans IN EXCLUSIVE MODE");   // in a transaction of the test's own
// start both requests; EXCLUSIVE lets their SELECTs through and blocks their INSERT/UPDATE
await().until(() -> a.isDone() || b.isDone() || sessionsWaitingOn("loans") == 2);   // pg_locks
// a request that already finished never reached its write: fail at once, there was no race
lockHolder.commit();                                        // release: both race to commit
```

The edit-versus-borrow test locks `books` instead: the edit's `UPDATE` and the borrow's foreign key check on its new loan both wait there.

Without the versions, both borrows answered 201 (in each of the two borrow races) and both returns 200. With them: one success and one 409 every time, and the loser wrote nothing.

**Gotchas**
- **Self-invocation, `private` and `final` methods.** The annotation is ignored, silently. Put the method on another bean.
- **A caught exception can still roll everything back** (rollback-only, above).
- **Checked exceptions commit.**
- **`REQUIRES_NEW` holds two connections at once.** With a pool of 10, ten requests that each hold one connection and wait for a second one block each other until the pool times out (30 s) and fails them all. This app does it on every borrow and return (the audit). Keep the inner transaction short, and never nest it in a loop.
- **Keep transactions short.** A transaction holds a connection and its row locks until it ends. Do not call a slow HTTP service inside one (§5.9).
- **`@DataJpaTest` wraps each test in a transaction and rolls it back at the end.** Handy, but nothing inside ever commits: commit behaviour, `REQUIRES_NEW` and races need a test without that transaction, like `TransactionBehaviourIT` and `LoanConcurrencyIT` (`@SpringBootTest`, tables emptied before each test).
- **Two `@Transactional` annotations exist.** Spring's (`org.springframework.transaction.annotation`) and Jakarta's (`jakarta.transaction.Transactional`). Spring honours both. Jakarta's has the same propagation names except `NESTED` (`@Transactional(TxType.REQUIRES_NEW)`) and `rollbackOn` instead of `rollbackFor`, but no `readOnly`, `isolation` or `timeout`. Use Spring's.
- **A version bump is an UPDATE of the row**, so `@LastModifiedDate` moves with it (§5.5).

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
- Persistence: [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/) (query method keywords), [Hibernate ORM documentation](https://hibernate.org/orm/documentation/), [Flyway documentation](https://documentation.red-gate.com/flyway), [Testcontainers for Java](https://java.testcontainers.org/)

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
| `List<T>` / `Dictionary<K,V>` / `HashSet<T>` | `ArrayList` / `HashMap` / `HashSet` (declared as `List` / `Map` / `Set`) | Java declares variables by interface by convention |
| `SortedDictionary` / `SortedSet` | `TreeMap` / `TreeSet` | |
| `IReadOnlyList<T>` / `ReadOnlyCollection<T>` (a read-only view) | `Collections.unmodifiableList(list)` | Changes to the underlying list show through. Still typed as `List`: `add` compiles and throws at runtime |
| `ImmutableList<T>` (a snapshot) | `List.of(...)`, `List.copyOf(list)` | Rejects nulls. Same runtime-only protection |
| `Equals` + `GetHashCode` (`IEquatable<T>`) | `equals` + `hashCode` | Same contract; records generate both |
| `IComparable<T>` / `IComparer<T>` | `Comparable<T>` / `Comparator<T>` | `Comparator.comparing(...).thenComparing(...)` ↔ `OrderBy(...).ThenBy(...)` |
| `Func<...>` / `Action<T>` / `Predicate<T>` | `Function`, `BiFunction`, `Supplier` / `Consumer` / `Predicate` | Java uses ordinary (generic) interfaces instead of delegate types |
| Delegates, method groups | Functional interfaces, method references (`String::length`) | |
| LINQ to Objects | Streams API | Both lazy. No query syntax, and a stream is single-use (see below) |
| Nullable reference types (`string?`), `?.`, `??` | `Optional<T>` with `map`, `orElse` | Only for return values; fields and parameters are plain (nullable) references |
| `Thread` | `Thread` | Same idea; `Thread.ofPlatform()` / `Thread.ofVirtual()` builders |
| `ThreadPool`, `Task.Run` | `ExecutorService` (`Executors.newFixedThreadPool`…) | Java makes you pick and close the pool |
| `Task<T>` | `CompletableFuture<T>` | `ContinueWith` ↔ `thenApply`/`thenCompose`, `Task.WhenAll` ↔ `allOf` (which returns `Void`, not the results), `.Result`/`.Wait()` ↔ `join()`/`get()` |
| `async` / `await` | No keywords: chain `CompletableFuture`s, or write blocking code on virtual threads | |
| Async I/O freeing the thread while it waits | Virtual threads | Same effect (cheap waiting), different route: the code stays blocking and the JVM unmounts the virtual thread |
| `lock (obj) { … }` | `synchronized (obj) { … }` / `synchronized` methods | |
| `Interlocked.Increment` | `AtomicInteger.incrementAndGet()` | `Interlocked` works on a plain field; Java needs an `AtomicInteger` object (or a `VarHandle`) |
| `ConcurrentDictionary.AddOrUpdate` | `ConcurrentHashMap.merge` / `compute` | Opposite guarantee: .NET may run the update delegate more than once, outside the lock. Java runs it once, atomically, holding a lock, so keep it short and do not touch the map inside it |
| `CancellationToken` | `Thread.interrupt()` / `Future.cancel(true)` | Interruption is cooperative too. `cancel(true)` interrupts only executor tasks; on a `CompletableFuture` it marks the future cancelled and the work keeps running |
| `AggregateException` from `.Result` | `ExecutionException` (`get`) / `CompletionException` (`join`) | The real exception is the cause |
| `IServiceCollection` + `services.AddScoped<IFoo, Foo>()` | Component scanning (`@Component`/`@Service`…) or `@Bean` methods | Spring discovers beans by scanning packages instead of explicit registration |
| Lifetimes: Singleton / Scoped / Transient | Scopes: `singleton` / `request` / `prototype` | Spring's default is singleton, where .NET makes you pick. Not exact matches: a Spring prototype's `@PreDestroy` never runs, while .NET disposes transients with their scope |
| Constructor injection | Constructor injection | Same idea; one constructor needs no annotation |
| Several registrations of `IFoo`, the last one wins | Several beans of a type fail unless one is `@Primary` or the parameter has a `@Qualifier` | .NET 8 keyed services ↔ `@Qualifier`. `IEnumerable<IFoo>` ↔ a `List<Foo>` parameter, which receives every matching bean |
| `appsettings.json` / `appsettings.{Environment}.json` | `application.yml` / `application-{profile}.yml` | |
| `ASPNETCORE_ENVIRONMENT=Development` | `SPRING_PROFILES_ACTIVE=dev` | Spring can activate several profiles at once |
| `IOptions<T>` + `services.Configure<T>(section)` | `@ConfigurationProperties` record | Injected directly as the record, with no `.Value` wrapper |
| `IConfiguration["Key"]` | `@Value("${key}")`, `Environment.getProperty("key")` | |
| Env var `Library__Loans__MaxActive` | Env var `LIBRARY_LOANS_MAXACTIVE` | Relaxed binding |
| `ILogger<T>` | SLF4J `Logger` via `LoggerFactory.getLogger(T.class)` | Logback is the default provider |
| `IHostedService`, `IHostApplicationLifetime.ApplicationStarted` | `ApplicationRunner`, `@EventListener(ApplicationReadyEvent.class)` | A hosted service starts before the server listens; an `ApplicationRunner` runs after Tomcat has started |
| `IDisposable` on a service | `@PreDestroy` | |
| `WebApplicationFactory<T>` | `@SpringBootTest` (whole app) or slices such as `@WebMvcTest` | Slices have no direct .NET equivalent |
| `[ApiController]` + `ControllerBase` | `@RestController` | |
| `[Route("api/books")]`, `[HttpGet("{id}")]` | `@RequestMapping("/api/books")`, `@GetMapping("/{id}")` | |
| `[FromRoute]` / `[FromQuery]` / `[FromBody]` | `@PathVariable` / `@RequestParam` / `@RequestBody` | Spring does not infer the body: `@RequestBody` is required |
| Model binding | Argument resolvers + `HttpMessageConverter`s | |
| `IActionResult` / `ActionResult<T>` | `ResponseEntity<T>` | Or return the object for a 200 |
| `CreatedAtAction(...)` | `ResponseEntity.created(location).body(...)` | Build the URI with `ServletUriComponentsBuilder` |
| `NoContent()` / `NotFound()` | `@ResponseStatus(NO_CONTENT)` / an exception mapped to 404 | |
| `System.Text.Json` | Jackson (3 in Boot 4) | Both ignore unknown JSON properties by default |
| Swashbuckle / `Microsoft.AspNetCore.OpenApi` | springdoc-openapi | `/v3/api-docs` + `/swagger-ui.html` |
| Moq `Setup(...).Returns(...)` / `Verify(...)` | Mockito `given(...).willReturn(...)` / `verify(...)` | `@MockitoBean` puts the mock in the Spring context |
| `.http` files in Visual Studio / Rider | `.http` files with VS Code REST Client or IntelliJ | Nearly the same syntax |
| DataAnnotations: `[Required]`, `[StringLength]`, `[Range]`, `[RegularExpression]` | Bean Validation: `@NotBlank`/`@NotNull`, `@Size`, `@Min`/`@Max`, `@Pattern` | Same idea: attributes on the model, checked by a framework |
| A custom `ValidationAttribute` / `IValidatableObject` | A custom constraint + `ConstraintValidator` / a class-level constraint | |
| FluentValidation | No standard equivalent | Custom constraints, or a validator you call from the service |
| `[ApiController]`'s automatic 400 on an invalid `ModelState` | `@Valid` on each parameter | Spring validates only where you ask |
| `ValidationProblemDetails` (`errors` dictionary) | `ProblemDetail` + an `errors` property you add | Spring's default validation ProblemDetail has no per-field map |
| `ProblemDetails`, `Results.Problem(...)` | `ProblemDetail.forStatusAndDetail(...)` | RFC 9457 in both |
| `UseExceptionHandler` / `IExceptionHandler` / exception filters | `@RestControllerAdvice` + `@ExceptionHandler` | |
| `services.AddOptions<T>().ValidateDataAnnotations().ValidateOnStart()` | `@Validated` on a `@ConfigurationProperties` class | The bean is validated when it is created, which is at startup; there is no separate `ValidateOnStart` step |
| EF Core | JPA (the spec) + Hibernate (the implementation) + Spring Data JPA (repositories) | Three layers where .NET has one library |
| `DbContext` | `EntityManager` / the persistence context | One per transaction; you rarely touch it directly, the repositories do |
| `DbSet<Book>` + your own repository class | `interface BookRepository extends JpaRepository<Book, Long>` | Spring Data writes the implementation |
| LINQ to Entities (`Where`, `OrderBy`…) | Derived queries (`findByTitleContainingIgnoreCase`), JPQL `@Query`, Specifications | No compiler-checked query language; derived names and JPQL are checked at startup |
| `FromSqlRaw` / `FromSql` | `@Query(nativeQuery = true)` | |
| Change tracking + `SaveChanges()` | Dirty checking + flush on commit | No explicit save call: the end of the `@Transactional` method writes the changes |
| `AsNoTracking()` | Projections (§5.5), read-only transactions (§5.6) | A projection is never tracked; `readOnly` skips dirty checking |
| `Include()` / `ThenInclude()` | `@EntityGraph(attributePaths = ...)` / JPQL `join fetch` | |
| `AsSplitQuery()` | Batch fetching (`@BatchSize`, `default_batch_fetch_size`) | Not the same mechanism, same goal: no cartesian join, no N+1 |
| Lazy-loading proxies (`UseLazyLoadingProxies`, opt-in) | LAZY relations and Hibernate proxies (default for collections) | Without proxies EF leaves an unloaded navigation null; Hibernate loads it on first use, or throws once the transaction is over |
| `HasMany(...).WithMany(...)` (skip navigations) | `@ManyToMany` + `@JoinTable` on the owning side, `mappedBy` on the other | EF fixes up both navigations; JPA writes only the owning side |
| `Select(b => new BookDto(...))` | Interface projection / `select new ...Record(...)` | |
| Composing `IQueryable` (`if (x) q = q.Where(...)`) | `Specification.allOf(...)` + `JpaSpecificationExecutor` | |
| `Skip` / `Take` + `CountAsync` | `Pageable` → `Page` (content + total) | Page numbers are zero-based |
| Setting `CreatedAt` in `SaveChanges` / an interceptor | `@CreatedDate` / `@LastModifiedDate` + `AuditingEntityListener` | |
| `TimeProvider` (.NET 8) | `java.time.Clock` | Inject it; tests pass `Clock.fixed(...)` |
| EF Migrations (code first, generated from the model) | Flyway (hand-written SQL files) | `__EFMigrationsHistory` ↔ `flyway_schema_history` |
| `Database.EnsureCreated()` | `spring.jpa.hibernate.ddl-auto: create` | Prototypes only in both. Not the same: `create` drops and recreates the schema on every start, losing the data, while `EnsureCreated` leaves an existing database alone |
| `Scaffold-DbContext` (database first) | Hibernate Tools / IDE reverse engineering | |
| Connection string in `appsettings.json` | `spring.datasource.*`, or none: Docker Compose support / `@ServiceConnection` | |
| .NET Aspire / Testcontainers for .NET | `spring-boot-docker-compose` / Testcontainers for Java | The Java Testcontainers is the original |
| `DbUpdateException` (unique index violation) | `DataIntegrityViolationException` | Spring translates every vendor's SQL errors into one `DataAccessException` hierarchy |
| `SaveChanges()` (its own transaction), `BeginTransaction()`, `TransactionScope` | `@Transactional` on the service method, or `TransactionTemplate` | Declarative, through a proxy (§5.6) |
| `TransactionScopeOption.RequiresNew` | `@Transactional(propagation = Propagation.REQUIRES_NEW)` | Also a second connection |
| `BeginTransaction(IsolationLevel.Serializable)` | `@Transactional(isolation = Isolation.SERIALIZABLE)` | |
| `[ConcurrencyCheck]`, `[Timestamp]` / `IsRowVersion()` | `@Version` | SQL Server bumps a `rowversion` itself; Hibernate bumps `@Version` in its own UPDATE |
| `DbUpdateConcurrencyException` | `ObjectOptimisticLockingFailureException` | Spring's translation of Hibernate's `StaleObjectStateException` |

**LINQ ↔ Streams**

| LINQ | Streams |
|---|---|
| `Where` | `filter` |
| `Select` | `map` |
| `SelectMany` | `flatMap` |
| `OrderBy` / `ThenBy` | `sorted(Comparator.comparing(...).thenComparing(...))` |
| `Take` / `Skip` | `limit` / `skip` |
| `Distinct` | `distinct` |
| `First()` / `FirstOrDefault()` | `findFirst().orElseThrow()` / `findFirst().orElse(null)` (or keep the `Optional`) |
| `Any` / `All` | `anyMatch` / `allMatch` |
| `Count` / `Sum` / `Aggregate` | `count` / `mapToInt(...).sum()` / `reduce` |
| `GroupBy` | `collect(groupingBy(...))` |
| `ToList` / `ToDictionary` | `toList()` / `collect(toMap(...))` |
| `string.Join` | `collect(joining(", "))` |

### Things that trip you up

- **`==` on objects.** `==` on `String`, `Integer` and any other object compares references. Small tests often pass by accident (the string pool, the `Integer` cache from -128 to 127). Use `equals`.
- **Checked exceptions.** A method that calls `Files.readString` does not compile until you catch `IOException` or add `throws IOException`. The `java.util.function` interfaces (and so stream lambdas) cannot throw them, so they get wrapped in unchecked exceptions.
- **Type erasure.** `List<String>` and `List<Integer>` are the same class at runtime, so reflection-based code (JSON libraries, Spring) sometimes needs a hint such as a `ParameterizedTypeReference`.
- **Default visibility.** Forgetting `public` makes a member package-private, not private.
- **No properties.** Java has fields plus methods. Records give you `name()` accessors. Classes use `getName()`/`setName()` by convention (JavaBeans).
- **Locale-sensitive formatting.** `String.format` and `toUpperCase()` use the machine's locale unless you pass `Locale.ROOT`.
- **Streams are single-use.** An `IEnumerable` can be enumerated again. A second terminal operation on a stream throws `IllegalStateException`, so store the collection, not the stream.
- **Read-only collections are a runtime property.** `List.of(...)` and `stream.toList()` return a `List` that throws on `add`. The compiler does not help.
- **No `await`.** A method that returns `CompletableFuture` does not suspend anything. Calling `join()` blocks the current thread. Either keep chaining or accept the blocking (cheap on a virtual thread).
- **The default async pool.** `CompletableFuture.supplyAsync(task)` without an executor uses the JVM-wide `ForkJoinPool.commonPool()` (on machines with more than 2 CPUs). Blocking calls there starve everything else that uses it.
- **`start()`, not `run()`.** `thread.run()` compiles and runs the task on the current thread.
- **Wrapped exceptions.** `Future.get()` throws `ExecutionException` and `CompletableFuture.join()` throws `CompletionException`. A `catch (NoSuchElementException e)` around them never fires; unwrap `getCause()`.
- **Cancelling a `CompletableFuture` does not stop the work.** Unlike a `Task` with a `CancellationToken`, `cancel(true)` only completes the future with a `CancellationException`; the running code carries on.
- **Beans are singletons by default.** A field in a `@Service` is shared by every request thread. In .NET you pick a lifetime on every registration; in Spring a missing choice means "one instance for everyone".
- **Two beans of one type fail the startup.** .NET quietly injects the last registration. Spring stops with `NoUniqueBeanDefinitionException` until you add `@Primary` or `@Qualifier`.
- **Unknown configuration keys are silently ignored.** A typo in `application.yml` does not fail anything; the default applies.
- **`@RequestBody` is not inferred.** `[ApiController]` binds a complex parameter from the body by itself. In Spring, leave out `@RequestBody` and the JSON is silently ignored.
- **An unhandled exception is a 500 with a generic body.** There is no developer exception page. Map your exceptions to statuses yourself; this project does it in `GlobalExceptionHandler` (§5.3).
- **Validation is opt-in per parameter.** `[ApiController]` validates every bound model and answers 400 by itself. In Spring, a `@RequestBody` without `@Valid` is never validated, whatever constraints its record carries.
- **Validation messages follow the locale.** Spring hands Hibernate Validator the request's `Accept-Language`, or else the machine's locale, and the built-in messages are translated to it. On a Spanish Windows machine you get `"no debe estar vacío"` unless the locale is pinned (§5.3).
- **A missing `int` in the JSON is a 400.** System.Text.Json leaves a missing value-type property at its default (`0`). Jackson 3 rejects a missing or `null` primitive. Use `Integer` for optional numbers.
- **There is no `SaveChanges()`.** Changes to a managed entity are written when the transaction commits. Without `@Transactional` on the service method there is no transaction to commit, and the change is lost without an error (§5.4).
- **Hibernate may write before you expect.** Before a query, it flushes pending changes to the tables that query reads (auto flush). EF only writes on `SaveChanges()`. Run your checks before you change the entity (§5.4).
- **Entity `equals`/`hashCode`.** EF tracks entities by reference and rarely cares. JPA code relies on them wherever entities sit in sets and collections (the relations of §5.5), and field-based ones (Lombok's `@Data`, a record) break. Compare by id, and return a constant hash code (§5.4).
- **`@ManyToOne` loads eagerly by default.** EF loads nothing you did not `Include`. JPA loads every to-one relation unless you write `fetch = FetchType.LAZY` (§5.5).
- **Only one side of a relation is written.** EF fixes up both navigation properties. In JPA, adding to the `mappedBy` side does nothing, without an error (§5.5).
- **A lazy relation outside the transaction throws.** `LazyInitializationException` means the data was needed after the service returned. Fetch it in the query or map inside the transaction; do not turn open-in-view back on (§5.5).
- **Checked exceptions do not roll back.** A `@Transactional` method that throws `IOException` commits what it wrote. Only unchecked exceptions roll back, unless you add `rollbackFor` (§5.6).
- **`@Transactional` works only through the proxy.** A call to a method of the same class, or to a `private` method, runs without its annotation, and nothing tells you (§5.6).
- **Catching an exception does not save the transaction.** If it came out of a method that joined your transaction, the commit fails with `UnexpectedRollbackException` (§5.6).
- **Pages start at 0.** `?page=1` is the second page.
- **The schema is not generated from the classes.** Flyway runs your SQL; Hibernate only validates. Adding a field to an entity means writing a migration too, or the app does not start.
