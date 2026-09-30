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

`LibraryApplication` starts and Tomcat listens on port 8080. The only endpoints are `GET /api/info` and `GET /api/info/greetings` from §5.1; every other URL answers **404**. `LibraryApplicationTest` (`@SpringBootTest`) boots the same context in a test, so a broken configuration fails the build.

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

Slices keep tests fast and focused. `@SpringBootTest` with no `classes` starts the whole application; the playground keeps that for `LibraryApplicationTest` and the integration tests (§5.8).

**Gotchas**
- A class outside `dev.playground.library` is not scanned, so it is not a bean, and injecting it fails at startup.
- `new InfoController(...)` in your own code gives an object the container does not manage: it is not injected anywhere, and its `@PostConstruct` and `@PreDestroy` never run. Let the container create beans and inject them.
- A bean with two constructors needs `@Autowired` on the one Spring should use. Worse, if one of them takes no arguments, Spring silently uses that one and the dependencies stay `null`.
- `@Value("${missing.key}")` fails at startup; `@Value("${missing.key:fallback}")` supplies a default.
- A property with a typo (`library.loans.max-activ`) is silently ignored: nothing binds it.

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
