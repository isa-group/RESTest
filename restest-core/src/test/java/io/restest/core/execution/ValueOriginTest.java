package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValueOriginTest {

    private static final List<ValueOrigin> EVERY_KIND = List.of(
            ValueOrigin.DECLARED,
            new ValueOrigin.Generated("random"),
            new ValueOrigin.Derived(TestCaseId.of("tc-1"), "response body field 'id'"));

    @Test
    @DisplayName("every kind of origin can be told apart without a default case")
    void the_hierarchy_is_exhaustive() {
        assertThat(EVERY_KIND).map(ValueOriginTest::describe)
                .containsExactly("declared", "generated: random", "derived from tc-1");
    }

    @Test
    @DisplayName("a declared origin has no components: every instance is equal")
    void declared_is_a_single_value() {
        assertThat(new ValueOrigin.Declared()).isEqualTo(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("a generated value must name what generated it")
    void generated_requires_a_source() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ValueOrigin.Generated(" "))
                .withMessageContaining("name what generated it");
    }

    @Test
    @DisplayName("a derived value must say what was taken from the test case it depends on")
    void derived_requires_a_description() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ValueOrigin.Derived(TestCaseId.of("tc-1"), " "))
                .withMessageContaining("tc-1");
    }

    @Test
    @DisplayName("a value can name the test case it depends on before that test case is ever sent")
    void a_dependency_is_representable_before_execution() {
        TestCaseId earlierStep = TestCaseId.generate();

        // No Interaction is built here at all - the point is that a stateful generator can commit
        // to this dependency the moment it plans the earlier step, long before an engine exists to
        // send it and produce the InteractionId a response would carry.
        ValueOrigin.Derived derived = new ValueOrigin.Derived(earlierStep, "the created id");

        assertThat(derived.from()).isEqualTo(earlierStep);
    }

    /**
     * Compiling is the assertion: a switch with no default over a sealed interface stops compiling
     * the day a fourth origin is added and not handled, which is what keeps a report's "values by
     * source" breakdown honest.
     */
    private static String describe(ValueOrigin origin) {
        return switch (origin) {
            case ValueOrigin.Declared ignored -> "declared";
            case ValueOrigin.Generated generated -> "generated: " + generated.source();
            case ValueOrigin.Derived derived -> "derived from " + derived.from();
        };
    }
}
