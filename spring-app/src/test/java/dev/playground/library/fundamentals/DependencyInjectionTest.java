package dev.playground.library.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.config.DevModeBanner;
import dev.playground.library.info.CasualGreeter;
import dev.playground.library.info.FormalGreeter;
import dev.playground.library.info.Greeter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * How the container picks a bean for a constructor parameter. Guide: §5.1 Spring Boot
 * fundamentals.
 *
 * <p>{@link ApplicationContextRunner} starts a tiny context with only the classes you name, runs
 * the assertions and closes it. AssertJ can then check the context itself: which beans it has, or
 * why it failed to start.
 */
class DependencyInjectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    /** Asks for "a Greeter" and for "the casual Greeter". */
    record GreetingDesk(
            Greeter greeter, @Qualifier("casual") Greeter casual) {}

    /** A second implementation without @Primary, to cause ambiguity. */
    static class ShoutingGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "HEY " + name + "!";
        }
    }

    record NeedsAGreeter(Greeter greeter) {}

    @Test
    void primaryWinsWhenSeveralBeansMatch() {
        runner.withUserConfiguration(FormalGreeter.class, CasualGreeter.class, GreetingDesk.class)
                .run(context -> {
                    assertThat(context).getBeans(Greeter.class).hasSize(2);
                    // Two candidates for "a Greeter": @Primary on FormalGreeter settles it.
                    assertThat(context.getBean(GreetingDesk.class).greeter()).isInstanceOf(FormalGreeter.class);
                });
    }

    @Test
    void qualifierPicksTheQualifiedBean() {
        runner.withUserConfiguration(FormalGreeter.class, CasualGreeter.class, GreetingDesk.class)
                .run(context ->
                        assertThat(context.getBean(GreetingDesk.class).casual()).isInstanceOf(CasualGreeter.class));
    }

    @Test
    void twoCandidatesWithoutPrimaryFailTheStartup() {
        runner.withUserConfiguration(CasualGreeter.class, ShoutingGreeter.class, NeedsAGreeter.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void aMissingDependencyFailsTheStartup() {
        // Wiring errors surface when the context starts, not on the first request.
        runner.withUserConfiguration(NeedsAGreeter.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void profileBeanExistsOnlyInThatProfile() {
        runner.withUserConfiguration(DevModeBanner.class)
                .run(context -> assertThat(context).doesNotHaveBean(DevModeBanner.class));

        runner.withUserConfiguration(DevModeBanner.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(DevModeBanner.class));
    }
}
