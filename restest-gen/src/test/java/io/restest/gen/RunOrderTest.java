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
package io.restest.gen;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonText;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.settings.ScheduleSettings;
import io.restest.core.settings.SequenceSettings;
import io.restest.spec.SwaggerSpecificationParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The requests a run sends, in the order it sends them, pinned against one real API.
 *
 * <p>The file this compares against was written by the tool as it was before the part that decides
 * what to send next was taken out of the loop that sends it. Moving that decision is meant to change
 * nothing a run does when the new behaviour on top of it is switched off, and this is where that
 * promise is held: two full rounds of pet-clinic's operations, every value in every request, spelled
 * out. A run whose requests changed without anybody meaning them to fails here, and the difference is
 * readable line by line.
 *
 * <p>If a change to generation is intended - a new source of values, a different default - the file
 * is written again and the difference is part of the review. The run writes what it produced beside
 * the build's other output whenever the two disagree, so writing it again is a copy.
 */
class RunOrderTest {

    /** The number the pinned run starts from. Any number would do; this one is fixed. */
    private static final long SEED = 20260923L;

    /** Two full rounds of pet-clinic's 35 operations, so the second round starting again is seen too. */
    private static final int REQUESTS = 70;

    private static final String PINNED = "run-order-pet-clinic.txt";

    @Test
    @DisplayName("going round the operations produces the requests it always produced")
    void going_round_the_operations_sends_what_it_always_sent() throws IOException {
        ApiModel model = petClinic();
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, SEED);
        List<Operation> operations = generator.testableOperations();

        List<String> lines = new ArrayList<>();
        for (int request = 0; request < REQUESTS; request++) {
            Operation operation = operations.get(request % operations.size());
            lines.add(written(generator.generate(operation)));
        }

        assertThat(String.join("\n", lines) + "\n").isEqualTo(pinned(lines));
    }

    @Test
    @DisplayName("the scheduler, with the first round switched off, sends what the loop always sent")
    void the_scheduler_without_a_first_round_sends_what_was_always_sent() throws IOException {
        ApiModel model = petClinic();
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, SEED);
        ScheduleSettings noFirstRound = new ScheduleSettings(2, 1_000, Duration.ofSeconds(10),
                false, Duration.ofSeconds(2));
        Instant now = Instant.parse("2026-09-23T10:00:00Z");
        // Without pairs, for the reason given in the next test.
        Scheduler scheduler = new Scheduler(generator, noFirstRound, new SequenceSettings(false),
                now.plusSeconds(60),
                InstantSource.fixed(now), announced -> { });

        List<String> lines = new ArrayList<>();
        while (lines.size() < REQUESTS) {
            if (scheduler.next() instanceof Scheduler.Step.Send send) {
                lines.add(written(scheduler.testCaseFor(send)));
            }
        }

        assertThat(String.join("\n", lines) + "\n").isEqualTo(pinned(lines));
    }

    @Test
    @DisplayName("and with the first round on, what follows the round is still what was always sent")
    void what_follows_the_first_round_is_what_was_always_sent() throws IOException {
        ApiModel model = petClinic();
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, SEED);
        Instant now = Instant.parse("2026-09-23T10:00:00Z");
        Scheduler scheduler = new Scheduler(generator, ScheduleSettings.defaults(),
                new SequenceSettings(false), now.plusSeconds(60), InstantSource.fixed(now), announced -> { });

        // Pairs are off: the shipped plan remembers what the API returns, this test answers
        // nothing, so with them on every read of one thing would follow a creation - which is
        // what they are for, and what SchedulerTest checks.
        // The round draws on numbers of its own, so the ordinary requests after it are the ones a
        // run without it would have sent. Nothing here has any replies to remember, so the only
        // thing that could make them differ is the round moving the generator's own numbers on.
        List<String> lines = new ArrayList<>();
        int inTheRound = 0;
        while (lines.size() < REQUESTS) {
            if (scheduler.next() instanceof Scheduler.Step.Send send) {
                String line = written(scheduler.testCaseFor(send));
                if (send.inTheOpeningLap()) {
                    inTheRound++;
                } else {
                    lines.add(line);
                }
            }
        }

        assertThat(inTheRound).isEqualTo(model.operations().size());
        assertThat(String.join("\n", lines) + "\n").isEqualTo(pinned(lines));
    }

    /** One request as one line: the operation, then every value it carries and where it came from. */
    static String written(Optional<TestCase> generated) {
        if (generated.isEmpty()) {
            return "(nothing could be built)";
        }
        TestCase testCase = generated.get();
        StringBuilder line = new StringBuilder(testCase.operation().value());
        for (ParameterValue value : testCase.parameterValues()) {
            line.append(" | ").append(value.location()).append(' ').append(value.name())
                    .append('=').append(JsonText.write(value.value()))
                    .append(" from ").append(value.origin());
        }
        testCase.body().ifPresent(body -> line.append(" | body ").append(body.mediaType())
                .append('=').append(JsonText.write(body.value()))
                .append(" from ").append(body.origin()));
        return line.toString();
    }

    /**
     * What the run is expected to produce, or a failure saying where what it did produce was left.
     *
     * @param actual what this run produced, written out when it does not match
     */
    private static String pinned(List<String> actual) throws IOException {
        String produced = String.join("\n", actual) + "\n";
        try (InputStream in = RunOrderTest.class.getResourceAsStream(PINNED)) {
            String expected = in == null
                    ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            if (!produced.equals(expected)) {
                Path written = Path.of("target", PINNED);
                Files.createDirectories(written.getParent());
                Files.writeString(written, produced, StandardCharsets.UTF_8);
            }
            return expected == null ? "(no pinned file; this run's is in " + Path.of("target",
                    PINNED).toAbsolutePath() + ")" : expected;
        }
    }

    private static ApiModel petClinic() {
        Path document = repositoryRoot()
                .resolve("restest-spec/src/test/resources/specifications/restleague-2027")
                .resolve("pet-clinic")
                .resolve("openapi.yaml");
        assertThat(document).exists();
        return new SwaggerSpecificationParser().parse(document.toString());
    }

    /** The checkout, found by walking up from wherever the tests are being run. */
    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("restest-spec"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not find the checkout above "
                + Path.of("").toAbsolutePath() + ", and the corpus lives in it");
    }
}
