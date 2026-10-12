package dev.playground.library.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Bean scopes and lifecycle callbacks, with the plain Spring Framework container: no Spring Boot,
 * no auto-configuration, no web server. {@link AnnotationConfigApplicationContext} is the IoC
 * container that Spring Boot builds for you. Guide: §5.1 Spring Boot fundamentals.
 */
class BeanScopesTest {

    /** Records lifecycle events so the tests can see what the container called and when. */
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private static final AtomicInteger DRAFT_IDS = new AtomicInteger();

    /** Singleton scope is the default: one instance per container, shared by everyone. */
    @Component
    static class Catalog {
        @PostConstruct
        void init() {
            EVENTS.add("catalog created");
        }

        @PreDestroy
        void close() {
            EVENTS.add("catalog destroyed");
        }
    }

    /** Prototype scope: the container creates a new instance on every lookup or injection. */
    @Component
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    static class LoanDraft {
        final int id = DRAFT_IDS.incrementAndGet();

        @PostConstruct
        void init() {
            EVENTS.add("draft " + id + " created");
        }

        @PreDestroy
        void close() {
            EVENTS.add("draft " + id + " destroyed"); // never happens, see the last test
        }
    }

    /** A singleton that holds a prototype: the classic surprise. */
    @Component
    static class LoanDesk {
        final LoanDraft injectedDraft;
        final ObjectProvider<LoanDraft> drafts;

        // One constructor, so Spring uses it without @Autowired (constructor injection).
        LoanDesk(LoanDraft injectedDraft, ObjectProvider<LoanDraft> drafts) {
            this.injectedDraft = injectedDraft;
            this.drafts = drafts;
        }
    }

    /** A second singleton that also takes a draft. */
    @Component
    static class ReturnDesk {
        final LoanDraft injectedDraft;

        ReturnDesk(LoanDraft injectedDraft) {
            this.injectedDraft = injectedDraft;
        }
    }

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
    }

    private static AnnotationConfigApplicationContext newContext() {
        return new AnnotationConfigApplicationContext(Catalog.class, LoanDraft.class, LoanDesk.class, ReturnDesk.class);
    }

    @Test
    void singletonIsOneSharedInstance() {
        try (var context = newContext()) {
            assertThat(context.getBean(Catalog.class)).isSameAs(context.getBean(Catalog.class));
        }
    }

    @Test
    void prototypeIsANewInstanceOnEveryLookup() {
        try (var context = newContext()) {
            assertThat(context.getBean(LoanDraft.class)).isNotSameAs(context.getBean(LoanDraft.class));
        }
    }

    @Test
    void prototypeInjectedIntoASingletonIsCreatedOnlyOnce() {
        try (var context = newContext()) {
            LoanDesk desk = context.getBean(LoanDesk.class);
            ReturnDesk returns = context.getBean(ReturnDesk.class);
            for (int use = 0; use < 3; use++) {
                context.getBean(LoanDesk.class); // ask for the desk again
            }

            // Each singleton received its own draft once, when it was built. Using the desk
            // again never creates another one: in practice its draft behaves like a singleton.
            assertThat(desk.injectedDraft).isNotSameAs(returns.injectedDraft);
            assertThat(EVENTS).filteredOn(event -> event.startsWith("draft")).hasSize(2);
        }
    }

    @Test
    void objectProviderGivesAFreshPrototypeEachTime() {
        try (var context = newContext()) {
            LoanDesk desk = context.getBean(LoanDesk.class);

            // Inject a provider instead, and ask it for an instance when you need one.
            assertThat(desk.drafts.getObject()).isNotSameAs(desk.drafts.getObject());
        }
    }

    @Test
    void preDestroyRunsForSingletonsButNotForPrototypes() {
        var context = newContext();
        LoanDraft draft = context.getBean(LoanDraft.class);

        context.close();

        // Singletons are created eagerly at startup; @PostConstruct runs after injection.
        assertThat(EVENTS.getFirst()).isEqualTo("catalog created");
        assertThat(EVENTS).contains("draft " + draft.id + " created", "catalog destroyed");
        // The container hands a prototype over and forgets it, so it never calls its @PreDestroy.
        assertThat(EVENTS).noneMatch(event -> event.endsWith("destroyed") && event.startsWith("draft"));
    }
}
