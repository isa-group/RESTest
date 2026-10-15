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
import io.restest.core.settings.CheckSettings;
import io.restest.core.settings.Settings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OraclesTest {

    /** The rules RESTest ships with, by name, in the order of the catalogue's numbers. */
    private static final List<String> SHIPPED = List.of("server-error", "other-server-error",
            "response-schema", "invalid-data-accepted");

    @Test
    @DisplayName("the rules RESTest ships with are the four this release reports on, in the order "
            + "of the catalogue's numbers")
    void the_standard_rules_are_the_four_this_release_has() {
        assertThat(Oracles.standard(CheckSettings.defaults())).extracting(Oracle::name)
                .containsExactlyElementsOf(SHIPPED);
    }

    @ParameterizedTest
    @CsvSource({
            "checks.internalServerError, server-error",
            "checks.otherServerError, other-server-error",
            "checks.replyOffSchema, response-schema",
            "checks.invalidDataAccepted, invalid-data-accepted"})
    @DisplayName("a check switched off is left out, and the others stay where they were")
    void a_check_switched_off_is_left_out(String check, String rule) {
        assertThat(Oracles.standard(Settings.from(Map.of(check, "false")).checks()))
                .extracting(Oracle::name)
                .containsExactlyElementsOf(SHIPPED.stream()
                        .filter(name -> !name.equals(rule))
                        .toList());
    }

    @Test
    @DisplayName("every check off is a run that judges nothing")
    void every_check_off_judges_nothing() {
        assertThat(Oracles.standard(new CheckSettings(false, false, false, false)))
                .describedAs("every check off is a run that judges nothing, which an experiment "
                        + "measuring what judging costs may want")
                .isEmpty();
    }

    @Test
    @DisplayName("the tool finds its own rules by looking, not by a list somebody keeps up to date")
    void the_rules_are_found_by_looking() {
        assertThat(Oracles.discovered()).extracting(Oracle::name)
                .containsExactly("invalid-data-accepted", "other-server-error", "response-schema",
                        "server-error");
    }

    @Test
    @DisplayName("a run's listener judges with the checks its settings leave on")
    void the_listener_judges_with_the_checks_left_on() {
        try (EventStream events = new EventStream()) {
            assertThat(OracleListener.standard(Specifications.pets(),
                    new CheckSettings(false, true, true, true), events).oracles())
                    .describedAs("with the check for a 500 off, the other checks are still made")
                    .extracting(Oracle::name)
                    .containsExactly("other-server-error", "response-schema",
                            "invalid-data-accepted");
        }
    }

    @Test
    @DisplayName("every rule says what it is called and what it checks")
    void every_rule_describes_itself() {
        assertThat(Oracles.standard(CheckSettings.defaults())).allSatisfy(oracle -> {
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
            events.subscribe(OracleListener.standard(api, CheckSettings.defaults(), events));

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
            events.subscribe(OracleListener.standard(api, CheckSettings.defaults(), events));

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
            OracleListener listener = new OracleListener(api,
                    Oracles.standard(CheckSettings.defaults()), events,
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            events.subscribe(listener);

            events.publish(new RunEvent.RunStarted(Instant.EPOCH, "Pets", Attempts.BASE));

            assertThat(listener.oracles()).hasSize(4);
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

    @Test
    @DisplayName("one broken rule is counted as one rule, however many replies it fails on")
    void a_broken_rule_is_counted_once_however_often_it_fails() {
        ApiModel api = Specifications.pets();
        OracleListener listener;

        try (EventStream events = new EventStream()) {
            listener = new OracleListener(api, List.of(new AlwaysThrows()), events,
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            events.subscribe(listener);

            for (int reply = 0; reply < 3; reply++) {
                events.publish(new RunEvent.InteractionCompleted(Instant.EPOCH,
                        Attempts.answered(OperationId.of("GET /pets"), "/pets", 200,
                                "application/json", "[]")));
            }
        }

        // Both numbers, because they say different things and only one of them is the size of the
        // problem. Against a fast API a rule that fails on everything fails thousands of times in a
        // minute; reporting that as thousands of broken listeners would describe a catastrophe
        // where there is one rule to fix.
        assertThat(listener.failures()).isEqualTo(3);
        assertThat(listener.rulesThatFailed()).isEqualTo(1);
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
