/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SequenceStepTest {

    @Test
    @DisplayName("the first step of a series is the creation, and follows nothing")
    void the_first_step_follows_nothing() {
        SequenceStep first = SequenceStep.first("readAfterDelete", "create the thing to delete");

        assertThat(first.step()).isEqualTo(1);
        assertThat(first.follows()).isEmpty();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SequenceStep("readAfterDelete", 1,
                        List.of(InteractionId.generate()), "create the thing to delete"))
                .withMessageContaining("follows nothing");
    }

    @Test
    @DisplayName("a later step names the exchanges it was built from, in the order they happened")
    void a_later_step_names_what_it_follows() {
        InteractionId creation = InteractionId.generate();
        InteractionId deletion = InteractionId.generate();

        SequenceStep read = new SequenceStep("readAfterDelete", 4, List.of(creation, deletion),
                "read the thing the API said it deleted; it should be gone");

        assertThat(read.follows()).containsExactly(creation, deletion);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SequenceStep("readAfterDelete", 2, List.of(),
                        "read the thing just created"))
                .withMessageContaining("names the exchange");
    }

    @Test
    @DisplayName("what a step follows cannot be changed afterwards")
    void what_a_step_follows_is_copied() {
        List<InteractionId> follows = new ArrayList<>(List.of(InteractionId.generate()));
        SequenceStep again = new SequenceStep("createTwice", 2, follows, "the same creation again");

        follows.add(InteractionId.generate());

        assertThat(again.follows()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> again.follows().clear());
    }

    @Test
    @DisplayName("a step names its kind of series and says what it does, and counts from one")
    void a_step_is_described_completely() {
        InteractionId creation = InteractionId.generate();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SequenceStep.first(" ", "create the thing"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SequenceStep.first("createTwice", ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SequenceStep("createTwice", 0, List.of(creation),
                        "the same creation again"))
                .withMessageContaining("counted from one");
    }
}
