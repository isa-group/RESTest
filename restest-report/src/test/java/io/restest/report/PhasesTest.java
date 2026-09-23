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
package io.restest.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.TestCase;
import io.restest.core.model.OperationId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhasesTest {

    private static final Instant START = Instant.parse("2026-09-23T10:00:00Z");

    private final Phases phases = new Phases();

    @Test
    @DisplayName("a stretch of the run counts its requests, their replies, and the operations "
            + "that answered with a success")
    void a_stretch_counts_what_it_sent_and_what_came_back() {
        TestCase listPets = testCase("GET /pets");
        TestCase addPet = testCase("POST /pets");
        TestCase getPet = testCase("GET /pets/{petId}");

        phases.on(new RunEvent.PhaseStarted(START, "opening lap"));
        planned(listPets, addPet, getPet);
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(listPets, 200)));
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(addPet, 201)));
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(getPet, 404)));
        phases.on(new RunEvent.PhaseFinished(START.plusMillis(900), "opening lap", false));

        Phases.Phase lap = phases.all().get(0);
        assertThat(lap.name()).isEqualTo("opening lap");
        assertThat(lap.requests()).isEqualTo(3);
        assertThat(lap.operations()).isEqualTo(3);
        assertThat(lap.operationsAnsweredWithASuccess()).isEqualTo(2);
        assertThat(lap.repliesByClass()).containsExactly(Map.entry("2xx", 2), Map.entry("4xx", 1));
        assertThat(lap.elapsed()).isEqualTo(Duration.ofMillis(900));
        assertThat(lap.cutShort()).isFalse();
    }

    @Test
    @DisplayName("an answer that arrives after its stretch has ended still counts in that stretch")
    void a_late_answer_counts_where_it_was_sent() {
        TestCase slow = testCase("POST /pets");
        TestCase afterwards = testCase("GET /pets");

        phases.on(new RunEvent.PhaseStarted(START, "opening lap"));
        planned(slow);
        phases.on(new RunEvent.PhaseFinished(START.plusSeconds(2), "opening lap", false));
        planned(afterwards);
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(afterwards, 200)));
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(slow, 201)));

        Phases.Phase lap = phases.all().get(0);
        assertThat(lap.requests())
                .describedAs("the request sent after the stretch ended is not one of its own")
                .isEqualTo(1);
        assertThat(lap.operationsAnsweredWithASuccess())
                .describedAs("the slow answer arrived late and still belongs to the stretch")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a stretch still going when the run ends was cut short by the budget")
    void a_stretch_still_open_at_the_end_was_cut_short() {
        phases.on(new RunEvent.PhaseStarted(START, "opening lap"));
        planned(testCase("GET /pets"));
        phases.on(new RunEvent.RunFinished(START.plusSeconds(5), Duration.ofSeconds(5),
                Runs.engine()));

        Phases.Phase lap = phases.all().get(0);
        assertThat(lap.cutShort()).isTrue();
        assertThat(lap.elapsed()).isEqualTo(Duration.ofSeconds(5));
        assertThat(lap.operationsAnsweredWithASuccess()).isZero();
    }

    @Test
    @DisplayName("a request that got no reply at all is counted as that, not as a failure of "
            + "some kind")
    void no_reply_is_counted_apart() {
        TestCase refused = testCase("GET /pets");

        phases.on(new RunEvent.PhaseStarted(START, "opening lap"));
        planned(refused);
        phases.on(new RunEvent.InteractionCompleted(START, Runs.neverAnswering(refused)));
        phases.on(new RunEvent.PhaseFinished(START, "opening lap", false));

        Phases.Phase lap = phases.all().get(0);
        assertThat(lap.noReply()).isEqualTo(1);
        assertThat(lap.repliesByClass()).isEmpty();
    }

    @Test
    @DisplayName("requests sent outside any stretch are not counted in one, and a run with no "
            + "stretch has none to report")
    void requests_outside_any_stretch_belong_to_none() {
        TestCase outside = testCase("GET /pets");

        planned(outside);
        phases.on(new RunEvent.InteractionCompleted(START, Runs.answering(outside, 200)));
        phases.on(new RunEvent.FaultFound(START, Runs.fellOver()));
        phases.on(new RunEvent.RunStarted(START, "Pets", Runs.BASE));

        assertThat(phases.all()).isEmpty();
    }

    @Test
    @DisplayName("a stretch that begins while another is going ends the first, which did not "
            + "get to finish")
    void a_new_stretch_ends_the_one_before() {
        phases.on(new RunEvent.PhaseStarted(START, "first"));
        phases.on(new RunEvent.PhaseStarted(START.plusSeconds(1), "second"));
        phases.on(new RunEvent.PhaseFinished(START.plusSeconds(2), "first", false));
        phases.on(new RunEvent.PhaseFinished(START.plusSeconds(3), "second", false));

        List<Phases.Phase> all = phases.all();
        assertThat(all).extracting(Phases.Phase::name).containsExactly("first", "second");
        assertThat(all.get(0).cutShort()).isTrue();
        assertThat(all.get(0).elapsed()).isEqualTo(Duration.ofSeconds(1));
        assertThat(all.get(1).cutShort())
                .describedAs("the late end of the first is not taken for the end of the second")
                .isFalse();
        assertThat(all.get(1).elapsed()).isEqualTo(Duration.ofSeconds(2));
    }

    private void planned(TestCase... testCases) {
        for (TestCase testCase : testCases) {
            phases.on(new RunEvent.TestCasePlanned(START, testCase));
        }
    }

    private static TestCase testCase(String operation) {
        return TestCase.of(OperationId.of(operation), List.of());
    }
}
