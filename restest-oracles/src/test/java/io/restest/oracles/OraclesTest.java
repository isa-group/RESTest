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
package io.restest.oracles;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.event.EventStream;
import io.restest.core.event.RunEvent;
import io.restest.core.model.ApiModel;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.Oracle;
import io.restest.core.oracle.WfcFault;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OraclesTest {

    @Test
    @DisplayName("the rules RESTest ships with are the two this release reports on")
    void the_standard_rules_are_the_two_this_release_has() {
        assertThat(Oracles.standard()).extracting(Oracle::name)
                .containsExactly("server-error", "response-schema");
    }

    @Test
    @DisplayName("the tool finds its own rules by looking, not by a list somebody keeps up to date")
    void the_rules_are_found_by_looking() {
        assertThat(Oracles.discovered()).extracting(Oracle::name)
                .containsExactly("response-schema", "server-error");
    }

    @Test
    @DisplayName("every rule says what it is called and what it checks")
    void every_rule_describes_itself() {
        assertThat(Oracles.standard()).allSatisfy(oracle -> {
            assertThat(oracle.name()).isNotBlank();
            assertThat(oracle.description()).isNotBlank();
        });
    }

    @Test
    @DisplayName("listening to a run turns each finished attempt into whatever the rules object to")
    void the_listener_announces_what_the_rules_find() {
        List<Finding> announced = new CopyOnWriteArrayList<>();
        ApiModel api = Specifications.pets();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.FaultFound found) {
                    announced.add(found.finding());
                }
            });
            events.subscribe(OracleListener.standard(api, events));

            events.publish(new RunEvent.InteractionCompleted(Instant.EPOCH,
                    Attempts.answered(OperationId.of("GET /pets/{petId}"), "/pets/7", 500,
                            "application/json", "{\"id\": \"seven\"}")));
        }

        assertThat(announced).extracting(Finding::category)
                .containsExactly(WfcFault.HTTP_STATUS_500, WfcFault.SCHEMA_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("an attempt nothing objects to produces nothing")
    void a_clean_attempt_announces_nothing() {
        List<RunEvent> announced = new CopyOnWriteArrayList<>();
        ApiModel api = Specifications.pets();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.FaultFound) {
                    announced.add(event);
                }
            });
            events.subscribe(OracleListener.standard(api, events));

            events.publish(new RunEvent.InteractionCompleted(Instant.EPOCH,
                    Attempts.answered(OperationId.of("GET /pets/{petId}"), "/pets/7", 200,
                            "application/json", "{\"id\": 7, \"name\": \"Rex\"}")));
        }

        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("only a finished attempt is judged; a plan is not something to have an opinion on")
    void other_events_are_ignored() {
        List<RunEvent> announced = new CopyOnWriteArrayList<>();
        ApiModel api = Specifications.pets();

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.FaultFound) {
                    announced.add(event);
                }
            });
            OracleListener listener = new OracleListener(api, Oracles.standard(), events,
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            events.subscribe(listener);

            events.publish(new RunEvent.RunStarted(Instant.EPOCH, "Pets", Attempts.BASE));

            assertThat(listener.oracles()).hasSize(2);
        }

        assertThat(announced)
                .describedAs("a test case that has only been planned has not been answered, so "
                        + "there is nothing yet for any rule to have an opinion about")
                .isEmpty();
    }

    @Test
    @DisplayName("a rule that throws costs its own judgement and nothing else")
    void a_throwing_rule_does_not_silence_the_rules_after_it() {
        List<RunEvent> announced = new CopyOnWriteArrayList<>();
        ApiModel api = Specifications.pets();
        OracleListener listener;

        try (EventStream events = new EventStream()) {
            events.subscribe(event -> {
                if (event instanceof RunEvent.FaultFound) {
                    announced.add(event);
                }
            });
            // The broken rule goes first, so that a rule giving up on the whole attempt would take
            // the server-error rule with it and the 500 below would go unreported.
            listener = new OracleListener(api, List.of(new AlwaysThrows(), new ServerErrorOracle()),
                    events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            events.subscribe(listener);

            events.publish(new RunEvent.InteractionCompleted(Instant.EPOCH,
                    Attempts.answered(OperationId.of("GET /pets"), "/pets", 500,
                            "application/json", "{}")));
        }

        assertThat(announced)
                .describedAs("the rule after the broken one still had to judge this attempt")
                .hasSize(1);
        assertThat(listener.failures())
                .describedAs("and the run still has to admit that one rule did not do its job, or "
                        + "a half-judged reply reads exactly like one that passed every rule")
                .isEqualTo(1);
    }

    /** A rule that does the one thing a rule is not supposed to do. */
    private static final class AlwaysThrows implements Oracle {

        @Override
        public String name() {
            return "always-throws";
        }

        @Override
        public String description() {
            return "fails on every attempt, to prove that the rules after it still run";
        }

        @Override
        public List<Finding> judge(io.restest.core.execution.Interaction interaction, ApiModel api) {
            throw new IllegalStateException("this rule is broken");
        }
    }
}
