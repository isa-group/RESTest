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

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.MemorySettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the tool keeps of what the API has already sent back.
 *
 * <p>The whole point of this memory is that a value which came out of the API is real: an
 * identifier that exists, a reference that resolves, a name in whatever spelling the API actually
 * uses. So these tests are mostly about two things - that what came back is found again under the
 * name anybody would look for it under, and that what came back in a reply nobody should learn from
 * is not kept at all.
 */
class ObservedValuesTest {

    private static final OperationId GET_PET = OperationId.of("getPet");

    /** A pet, as a document would declare one it gives a name to. */
    private static final ObjectSchema PET = ObjectSchema.of(Map.of(
            "id", NumberSchema.of(NumberKind.INTEGER),
            "name", StringSchema.of()));

    @Nested
    @DisplayName("what is kept")
    class WhatIsKept {

        @Test
        @DisplayName("a value the API sent is found again under its own name")
        void a_value_is_found_under_its_name() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"id\": 7, \"name\": \"Fluffy\"}"));

            assertThat(valuesUnder(seen, "id")).containsExactly(JsonValue.of(7));
            assertThat(valuesUnder(seen, "name")).containsExactly(JsonValue.of("Fluffy"));
        }

        @Test
        @DisplayName("a value deep inside a reply is found under its own name, not its address")
        void a_value_deep_inside_is_found_under_its_name() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json",
                    "{\"owner\": {\"address\": {\"town\": \"Sevilla\"}}}"));

            assertThat(valuesUnder(seen, "town")).containsExactly(JsonValue.of("Sevilla"));
        }

        @Test
        @DisplayName("a piece of a reply is kept whole as well as taken apart")
        void a_piece_is_kept_whole_as_well() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"owner\": {\"town\": \"Sevilla\"}}"));

            assertThat(valuesUnder(seen, "owner"))
                    .describedAs("an operation wanting an owner can have this one")
                    .containsExactly(JsonValue.object(
                            Map.of("town", JsonValue.of("Sevilla"))));
            assertThat(valuesUnder(seen, "town"))
                    .describedAs("and one wanting a town can have the town out of it")
                    .containsExactly(JsonValue.of("Sevilla"));
        }

        @Test
        @DisplayName("the things in a list go under the name of the list they were in")
        void elements_go_under_the_name_of_their_list() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"tags\": [\"small\", \"loud\"]}"));

            assertThat(valuesUnder(seen, "tags"))
                    .describedAs("the list itself, and each thing in it, because both get asked for")
                    .containsExactly(
                            JsonValue.array(List.of(JsonValue.of("small"), JsonValue.of("loud"))),
                            JsonValue.of("small"),
                            JsonValue.of("loud"));
        }

        @Test
        @DisplayName("a reply the document names is kept whole under that name")
        void a_named_reply_is_kept_under_its_name() {
            ObservedValues seen = new ObservedValues(anApiReturning(SchemaReference.to("Pet")));

            seen.on(reply(200, "application/json", "{\"id\": 7, \"name\": \"Fluffy\"}"));

            assertThat(shapesUnder(seen, "Pet")).containsExactly(JsonValue.object(Map.of(
                    "id", JsonValue.of(7), "name", JsonValue.of("Fluffy"))));
        }

        @Test
        @DisplayName("a reply that is a list of named things keeps each of them under that name")
        void each_thing_in_a_list_is_kept_under_the_name_of_its_shape() {
            ObservedValues seen = new ObservedValues(
                    anApiReturning(ArraySchema.of(SchemaReference.to("Pet"))));

            seen.on(reply(200, "application/json",
                    "[{\"id\": 1, \"name\": \"A\"}, {\"id\": 2, \"name\": \"B\"}]"));

            assertThat(shapesUnder(seen, "Pet"))
                    .describedAs("a list of pets is a list of Pets, not a Pet")
                    .hasSize(2);
        }

        @Test
        @DisplayName("a reply is read as JSON when the document declares it under any wildcard")
        void a_wildcard_content_type_still_names_the_shape() {
            Operation operation = Operation.of(HttpMethod.GET, "/pets")
                    .withId(GET_PET)
                    .withResponses(List.of(new ResponseModel("200",
                            Map.of("*/*", SchemaReference.to("Pet")), Map.of(),
                            Optional.empty())));
            ObservedValues seen = new ObservedValues(
                    ApiModel.of("pets", "1", List.of(operation))
                            .withSchemas(Map.of("Pet", PET)));

            seen.on(reply(200, "application/json", "{\"id\": 7, \"name\": \"Fluffy\"}"));

            assertThat(shapesUnder(seen, "Pet"))
                    .describedAs("one of the five APIs this tool is measured on declares every "
                            + "reply it sends as */*, and they are ordinary JSON")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("what is not kept")
    class WhatIsNotKept {

        @Test
        @DisplayName("nothing is learnt from a reply the API was not happy with")
        void a_refusal_teaches_nothing() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(404, "application/json", "{\"id\": 7}"));
            seen.on(reply(500, "application/json", "{\"id\": 8}"));

            assertThat(valuesUnder(seen, "id"))
                    .describedAs("an identifier in the body of a 404 is one that does not exist")
                    .isEmpty();
        }

        @Test
        @DisplayName("nothing is learnt from a reply that is not JSON")
        void something_that_is_not_json_teaches_nothing() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "text/html", "<html><id>7</id></html>"));

            assertThat(valuesUnder(seen, "id")).isEmpty();
        }

        @Test
        @DisplayName("nothing is learnt from a reply that announced JSON and sent something else")
        void something_that_only_claims_to_be_json_teaches_nothing() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "not json at all"));

            assertThat(valuesUnder(seen, "id")).isEmpty();
        }

        @Test
        @DisplayName("nothing is learnt from a reply that broke off halfway")
        void half_a_reply_teaches_nothing() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            String half = "{\"id\": 7, \"na";
            Interaction cut = Interaction.answered(TestCase.of(GET_PET, List.of()),
                    HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets/7"),
                    new HttpResponseRecord(StatusLine.of(200),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.partial(half.getBytes(StandardCharsets.UTF_8),
                                    "application/json", 400))),
                    Instant.EPOCH, Duration.ofMillis(3));

            seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, cut));

            assertThat(valuesUnder(seen, "id")).isEmpty();
        }

        @Test
        @DisplayName("a property the API sent nothing for is not a value for it")
        void nothing_is_not_a_value() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"name\": null}"));

            assertThat(valuesUnder(seen, "name")).isEmpty();
        }

        @Test
        @DisplayName("a reply nested more deeply than any resource is does not end the run")
        void a_reply_built_to_go_down_for_ever_is_survived() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            // Eighteen kilobytes, which costs an API nothing to send. Deep enough that reading it
            // one piece inside another would run out of room on the thread that carries the run's
            // announcements to everything listening - and that thread dying takes every report
            // with it while leaving nothing blamed. It is refused before it is read at all, so the
            // depth here can be far beyond anything a machine's stack depends on.
            String deep = "[".repeat(9_000) + "{\"name\": \"Fluffy\"}" + "]".repeat(9_000);

            seen.on(reply(200, "application/json", deep));

            assertThat(valuesUnder(seen, "name"))
                    .describedAs("nothing is learnt from it, which is the whole of the cost")
                    .isEmpty();
        }

        @Test
        @DisplayName("a word too long for anybody to send is not kept")
        void an_enormous_word_is_not_kept() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json",
                    "{\"name\": \"" + "x".repeat(20_000) + "\", \"tag\": \"cat\"}"));

            assertThat(valuesUnder(seen, "name"))
                    .describedAs("invention will not build a word this long either, and a value "
                            + "nothing could send is worse than no value")
                    .isEmpty();
            assertThat(valuesUnder(seen, "tag"))
                    .describedAs("and the rest of the reply is still learnt from")
                    .containsExactly(JsonValue.of("cat"));
        }

        @Test
        @DisplayName("a number that is short to write and enormous to send is not kept")
        void an_enormous_number_is_not_kept() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"id\": 1e9999999, \"tag\": \"cat\"}"));

            assertThat(valuesUnder(seen, "id"))
                    .describedAs("ten characters in a reply and ten million in a web address")
                    .isEmpty();
            assertThat(valuesUnder(seen, "tag")).containsExactly(JsonValue.of("cat"));
        }

        @Test
        @DisplayName("a reply too big to be a resource is left alone")
        void a_listing_is_left_alone() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            StringBuilder huge = new StringBuilder("{\"name\": \"");
            huge.append("x".repeat(600 * 1024)).append("\"}");

            seen.on(reply(200, "application/json", huge.toString()));

            assertThat(valuesUnder(seen, "name"))
                    .describedAs("reading it costs the thread every listener is served from")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("how much is kept")
    class HowMuchIsKept {

        @Test
        @DisplayName("only the most recent few values under any one name")
        void only_the_most_recent_few() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            for (int identifier = 1; identifier <= MemorySettings.defaults().mostValuesUnderOneName() + 5;
                    identifier++) {
                seen.on(reply(200, "application/json", "{\"id\": " + identifier + "}"));
            }

            assertThat(valuesUnder(seen, "id"))
                    .describedAs("what this is for is a value that is true now, and the oldest "
                            + "identifier is the likeliest to have been deleted since")
                    .hasSize(MemorySettings.defaults().mostValuesUnderOneName())
                    .contains(JsonValue.of(MemorySettings.defaults().mostValuesUnderOneName() + 5))
                    .doesNotContain(JsonValue.of(1));
        }

        @Test
        @DisplayName("a value seen twice is kept once, and counts as newly seen")
        void the_same_value_twice_is_one_value() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"id\": 7}"));
            seen.on(reply(200, "application/json", "{\"id\": 8}"));
            seen.on(reply(200, "application/json", "{\"id\": 7}"));

            assertThat(valuesUnder(seen, "id"))
                    .describedAs("an API that keeps sending the same identifier back should not "
                            + "crowd out every other value with copies of it")
                    .containsExactly(JsonValue.of(8), JsonValue.of(7));
        }

        @Test
        @DisplayName("how much is kept is a setting, so an experiment can turn the memory down to "
                + "nothing without touching the plan")
        void how_much_is_kept_can_be_changed() {
            ObservedValues small = new ObservedValues(anApiReturning(PET),
                    new MemorySettings(2, MemorySettings.defaults().mostNames(),
                            MemorySettings.defaults().longestValueKept(),
                            MemorySettings.defaults().longestReplyRead(),
                            MemorySettings.defaults().asDeepAsAReplyIsRead()));

            for (int identifier = 1; identifier <= 5; identifier++) {
                small.on(reply(200, "application/json", "{\"id\": " + identifier + "}"));
            }

            assertThat(valuesUnder(small, "id"))
                    .describedAs("two, because two is what was asked for")
                    .hasSize(2)
                    .contains(JsonValue.of(5))
                    .doesNotContain(JsonValue.of(1));
        }

        @Test
        @DisplayName("a memory asked to keep nothing keeps nothing, which is how an experiment "
                + "switches it off")
        void a_memory_of_nothing() {
            ObservedValues none = new ObservedValues(anApiReturning(PET),
                    new MemorySettings(0, 0, 0, 0, 0));

            none.on(reply(200, "application/json", "{\"id\": 1}"));

            assertThat(valuesUnder(none, "id")).isEmpty();
        }

        @Test
        @DisplayName("only so many different names, whatever an API invents")
        void only_so_many_names() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            StringBuilder madeUp = new StringBuilder("{");
            for (int at = 0; at < MemorySettings.defaults().mostNames() + 50; at++) {
                madeUp.append(at == 0 ? "" : ",").append("\"name").append(at).append("\": 1");
            }
            madeUp.append("}");

            seen.on(reply(200, "application/json", madeUp.toString()));

            assertThat(seen.underTheirOwnNames().size())
                    .describedAs("an API writing a map of identifiers as an object would grow "
                            + "this for as long as the run lasted")
                    .isEqualTo(MemorySettings.defaults().mostNames());
        }
    }

    @Test
    @DisplayName("a reply can arrive while a request is being built from what is already there")
    void it_can_be_read_while_it_is_being_written() throws InterruptedException {
        ObservedValues seen = new ObservedValues(anApiReturning(PET));
        CountDownLatch going = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> broke = new AtomicReference<>();

        Runnable writing = () -> {
            try {
                going.await();
                for (int at = 0; at < 2_000; at++) {
                    seen.on(reply(200, "application/json",
                            "{\"id\": " + at + ", \"name\": \"pet" + at + "\"}"));
                }
            } catch (Throwable failed) {
                broke.compareAndSet(null, failed);
            } finally {
                done.countDown();
            }
        };
        Runnable reading = () -> {
            try {
                going.await();
                for (int at = 0; at < 2_000; at++) {
                    // Every list handed out has to be one complete list, whatever is arriving.
                    valuesUnder(seen, "id").forEach(value -> assertThat(value).isNotNull());
                }
            } catch (Throwable failed) {
                broke.compareAndSet(null, failed);
            } finally {
                done.countDown();
            }
        };
        Thread.ofVirtual().start(writing);
        Thread.ofVirtual().start(reading);
        going.countDown();

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(broke.get()).isNull();
    }

    @Test
    @DisplayName("the word a plan asks for this by is the name it answers to")
    void the_plan_and_the_source_agree_on_the_word() {
        assertThat(Campaign.Builtin.OBSERVED.inAPlan())
                .describedAs("a report prints the name beside the value, and a plan is written "
                        + "with the word; two spellings of one thing would confuse both")
                .isEqualTo(ObservedValues.NAME);
    }

    private static List<JsonValue> valuesUnder(ObservedValues seen, String name) {
        return seen.underTheirOwnNames().valuesFor(ValueRequest.of(GET_PET, name,
                ParameterLocation.BODY, StringSchema.of()));
    }

    private static List<JsonValue> shapesUnder(ObservedValues seen, String shape) {
        return seen.underTheNameOfTheirShape().valuesFor(new ValueRequest(GET_PET, "body", "body",
                ParameterLocation.BODY, PET, List.of(), Optional.of(shape)));
    }

    /** An API with one operation, answering 200 with the given shape. */
    private static ApiModel anApiReturning(CanonicalSchema schema) {
        Operation operation = Operation.of(HttpMethod.GET, "/pets")
                .withId(GET_PET)
                .withResponses(List.of(ResponseModel.json("200", schema)));
        return ApiModel.of("pets", "1", List.of(operation)).withSchemas(Map.of("Pet", PET));
    }

    private static RunEvent.InteractionCompleted reply(int status, String contentType,
            String body) {
        Interaction interaction = Interaction.answered(TestCase.of(GET_PET, List.of()),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets/7"),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", contentType)),
                        Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8),
                                contentType))),
                Instant.EPOCH, Duration.ofMillis(3));
        return new RunEvent.InteractionCompleted(Instant.EPOCH, interaction);
    }
}
