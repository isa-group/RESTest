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

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every series that can be asked of every creation in the corpus, walked from its first step to its
 * last against an API that says yes to everything - which is where a series goes furthest, and so
 * where a document the tool does not entirely understand has the most chances to break it.
 */
class SequencesAcrossTheCorpusTest {

    private static final long SEED = 20261005L;

    @Test
    @DisplayName("every series of every creation in the corpus is built from start to end without "
            + "throwing, sends every step it plans, and only ever sends operations the run may "
            + "touch")
    void every_series_builds() {
        int walked = 0;
        int steps = 0;
        Set<String> stoppedShort = new TreeSet<>();
        for (Path document : TheCorpus.all()) {
            ApiModel model = TheCorpus.parse(document);
            for (Sequences.Shape shape : Sequences.Shape.values()) {
                RandomTestCaseGenerator generator = new RandomTestCaseGenerator(model, SEED,
                        List.of(), onlySeries(), Settings.defaults().withSequences(only(shape)));
                Optional<Sequences> series = generator.sequences();
                if (series.isEmpty()) {
                    continue;
                }
                Set<OperationId> testable = generator.testableOperations().stream()
                        .map(Operation::id).collect(Collectors.toSet());
                Creations creations = Creations.among(generator.testableOperations());
                for (Operation operation : generator.testableOperations()) {
                    if (!series.get().startsOn(operation)) {
                        continue;
                    }
                    Optional<TestCase> first = generator.generate(operation);
                    if (first.isEmpty()) {
                        continue;
                    }
                    String described = document.getParent().getFileName() + " " + shape.named()
                            + " from " + operation.id();
                    int planned = series.get().stepsPlanned(first.get());
                    List<TestCase> sent = walk(series.get(), first.get(),
                            aReplyNaming(model, creations.of(operation).orElseThrow(),
                                    first.get()));
                    assertThat(sent).describedAs(described)
                            .allSatisfy(request -> {
                                assertThat(testable).contains(request.operation());
                                assertThat(request.sequence().orElseThrow().shape())
                                        .isEqualTo(shape.named());
                            });
                    int last = sent.get(sent.size() - 1).sequence().orElseThrow().step();
                    if (last != planned) {
                        stoppedShort.add(described + ": step " + last + " of " + planned);
                    }
                    walked++;
                    steps += sent.size();
                }
            }
        }
        assertThat(walked)
                .describedAs("the corpus has creations of every kind to start series from")
                .isGreaterThan(200);
        assertThat(steps).isGreaterThan(walked * 2);
        assertThat(stoppedShort)
                .describedAs("a series against an API that accepts everything sends every step it "
                        + "planned - BigOven's reviews included, whose read and deletion declare "
                        + "the same gap as a number and as a word")
                .isEmpty();
    }

    /** Every step a series sends when every answer is a success, the creation's naming the thing. */
    private static List<TestCase> walk(Sequences series, TestCase first, String createdReply) {
        List<TestCase> sent = new ArrayList<>();
        TestCase step = first;
        while (true) {
            sent.add(step);
            Interaction answer = answered(step, step == first ? 201 : 200,
                    step == first ? createdReply : "{}");
            Optional<Sequences.Next> next = series.heard(step, Optional.of(answer));
            Optional<TestCase> built = Optional.empty();
            while (next.isPresent() && built.isEmpty()) {
                built = series.build(next.get());
                if (built.isEmpty()) {
                    next = series.notBuilt(next.get());
                }
            }
            if (built.isEmpty()) {
                return sent;
            }
            step = built.get();
        }
    }

    /**
     * What a creation's reply might say: the thing, with a value fitting every gap its own
     * addresses have, under the gap's name and as {@code id} - and none that the creation was sent
     * in its own address, which a series would take for the thing it was made under.
     */
    private static String aReplyNaming(ApiModel model, Creations.Creation creation,
            TestCase first) {
        Set<JsonValue> sent = first.parameterValues().stream()
                .filter(value -> value.location() == ParameterLocation.PATH)
                .map(ParameterValue::value)
                .collect(Collectors.toSet());
        Map<String, JsonValue> thing = new LinkedHashMap<>();
        for (Creations.Address own : creation.own()) {
            for (Operation operation : own.operations().values()) {
                Optional<Parameter> gap = operation.parameter(own.gap(), ParameterLocation.PATH);
                if (gap.isPresent()) {
                    JsonValue fitting = fitting(model, gap.get().schema(), sent);
                    thing.putIfAbsent(own.gap(), fitting);
                    thing.putIfAbsent("id", fitting);
                }
            }
        }
        return JsonText.write(JsonValue.object(thing));
    }

    private static JsonValue fitting(ApiModel model, CanonicalSchema declared,
            Set<JsonValue> notThese) {
        CanonicalSchema schema = Shapes.resolved(model, declared, 6);
        List<JsonValue> choices = new ArrayList<>(schema.metadata().enumeration());
        if (choices.isEmpty() && schema instanceof NumberSchema) {
            for (int number = 1; number <= 3; number++) {
                choices.add(JsonValue.of(BigDecimal.valueOf(number)));
            }
        }
        if (choices.isEmpty() && schema instanceof StringSchema text
                && text.format().filter("uuid"::equals).isPresent()) {
            choices.add(JsonValue.of("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
            choices.add(JsonValue.of("0b0c2c1e-8f0e-4bde-9a4e-3f1f6c2d7e5a"));
        }
        if (choices.isEmpty()) {
            choices.addAll(List.of(JsonValue.of("a1"), JsonValue.of("a2"), JsonValue.of("a3")));
        }
        return choices.stream().filter(choice -> !notThese.contains(choice)).findFirst()
                .orElse(choices.get(0));
    }

    private static Interaction answered(TestCase request, int status, String body) {
        return Interaction.answered(request, HttpRequestRecord.of(
                        io.restest.core.model.HttpMethod.GET, "http://localhost/"),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.text(body, "application/json"))),
                Instant.EPOCH, Duration.ofMillis(1));
    }

    private static SequenceSettings only(Sequences.Shape shape) {
        return new SequenceSettings(
                shape == Sequences.Shape.READ_AFTER_DELETE,
                shape == Sequences.Shape.DELETE_TWICE,
                shape == Sequences.Shape.WRITE_UNDER_DELETED,
                shape == Sequences.Shape.PUT_TWICE,
                shape == Sequences.Shape.SAFE_GET,
                shape == Sequences.Shape.CREATE_TWICE);
    }

    private static Campaign onlySeries() {
        return new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100, List.of(
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.ENUM)),
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE)),
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))),
                false, true)), WhichOperations.everything());
    }
}
