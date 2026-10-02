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
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * An operation that is sent a pet, which is what makes the names a pet comes back with ones a
     * request asks for: its id in the path, and its name, tag, tags and owner's town in the body.
     * A name only a reply carries is not kept, so without this the memory would hold nothing.
     */
    private static final Operation UPDATE_PET = Operation.of(HttpMethod.PUT, "/pets/{id}",
                    List.of(Parameter.of("id", ParameterLocation.PATH, true,
                            NumberSchema.of(NumberKind.INTEGER))))
            .withId(OperationId.of("updatePet"))
            .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                    "name", StringSchema.of(),
                    "tag", StringSchema.of(),
                    "tags", ArraySchema.of(StringSchema.of()),
                    "owner", ObjectSchema.of(Map.of("town", StringSchema.of())))), true));

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
        @DisplayName("nothing is kept from the reply to a request with one thing deliberately "
                + "changed, which the API may have accepted when it should not have")
        void nothing_is_kept_from_a_changed_request() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            TestCase changed = TestCase.changed(GET_PET, List.of(), Optional.empty(),
                    io.restest.core.execution.Intent.REFUSAL_EXPECTED,
                    new io.restest.core.execution.Mutation(
                            io.restest.core.execution.InteractionId.generate(), "oversize",
                            io.restest.core.model.ParameterLocation.BODY, "body.name",
                            "sent 10000 characters for body.name"));
            Interaction answered = Interaction.answered(changed,
                    HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets/7"),
                    new HttpResponseRecord(StatusLine.of(200),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.of("{\"id\": 7, \"name\": \"xxxx\"}"
                                    .getBytes(StandardCharsets.UTF_8), "application/json"))),
                    Instant.EPOCH, Duration.ofMillis(3));

            seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered));

            assertThat(valuesUnder(seen, "name")).isEmpty();
            assertThat(valuesUnder(seen, "id")).isEmpty();
        }

        @Test
        @DisplayName("nothing is kept from the reply to a step of a series, which is about a thing "
                + "only that series may know the fate of")
        void nothing_is_kept_from_a_step_of_a_series() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));
            TestCase step = TestCase.stepOf(GET_PET, List.of(), Optional.empty(),
                    io.restest.core.execution.Intent.UNKNOWN,
                    new io.restest.core.execution.SequenceStep("readAfterDelete", 2,
                            List.of(io.restest.core.execution.InteractionId.generate()),
                            "read what was created: it should be there"));
            Interaction answered = Interaction.answered(step,
                    HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets/7"),
                    new HttpResponseRecord(StatusLine.of(200),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.of("{\"id\": 7, \"name\": \"Rex\"}"
                                    .getBytes(StandardCharsets.UTF_8), "application/json"))),
                    Instant.EPOCH, Duration.ofMillis(3));

            seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered));

            assertThat(valuesUnder(seen, "name")).isEmpty();
            assertThat(valuesUnder(seen, "id")).isEmpty();
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
                            MemorySettings.defaults().asDeepAsAReplyIsRead(), true, true, true));

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
                    new MemorySettings(0, 0, 0, 0, 0, false, false, false));

            none.on(reply(200, "application/json", "{\"id\": 1}"));

            assertThat(valuesUnder(none, "id")).isEmpty();
        }

        @Test
        @DisplayName("only so many different names, however many a document asks for")
        void only_so_many_names() {
            Map<String, CanonicalSchema> asked = new LinkedHashMap<>();
            StringBuilder madeUp = new StringBuilder("{");
            for (int at = 0; at < MemorySettings.defaults().mostNames() + 50; at++) {
                asked.put("name" + at, NumberSchema.of(NumberKind.INTEGER));
                madeUp.append(at == 0 ? "" : ",").append("\"name").append(at).append("\": 1");
            }
            madeUp.append("}");
            ObservedValues seen = new ObservedValues(anApiReturning(PET,
                    Operation.of(HttpMethod.POST, "/maps").withId(OperationId.of("addMap"))
                            .withRequestBody(RequestBodyModel.json(ObjectSchema.of(asked), true))));

            seen.on(reply(200, "application/json", madeUp.toString()));

            assertThat(seen.underTheirOwnNames().size())
                    .describedAs("a document asking for more names than this would grow it for "
                            + "as long as the run lasted")
                    .isEqualTo(MemorySettings.defaults().mostNames());
        }

        @Test
        @DisplayName("when as many names are kept as may be, the one heard of longest ago makes "
                + "room for a new one")
        void the_name_heard_of_longest_ago_makes_room() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET), new MemorySettings(
                    MemorySettings.defaults().mostValuesUnderOneName(), 3,
                    MemorySettings.defaults().longestValueKept(),
                    MemorySettings.defaults().longestReplyRead(),
                    MemorySettings.defaults().asDeepAsAReplyIsRead(), true, true, true));

            seen.on(reply(200, "application/json", "{\"id\": 1}"));
            seen.on(reply(200, "application/json", "{\"name\": \"Fluffy\"}"));
            seen.on(reply(200, "application/json", "{\"tag\": \"cat\"}"));
            seen.on(reply(200, "application/json", "{\"id\": 2}"));
            seen.on(reply(200, "application/json", "{\"owner\": {\"town\": \"Sevilla\"}}"));

            assertThat(seen.underTheirOwnNames().size()).isEqualTo(3);
            assertThat(valuesUnder(seen, "name"))
                    .describedAs("heard of longest ago, so the first to make room")
                    .isEmpty();
            assertThat(valuesUnder(seen, "tag"))
                    .describedAs("the next oldest, once the owner had made room for its town")
                    .isEmpty();
            assertThat(valuesUnder(seen, "id"))
                    .describedAs("heard first, and heard again since, which is what counts")
                    .containsExactly(JsonValue.of(1), JsonValue.of(2));
            assertThat(valuesUnder(seen, "town")).containsExactly(JsonValue.of("Sevilla"));
        }
    }

    @Nested
    @DisplayName("what the API accepted")
    class WhatTheApiAccepted {

        private static final OperationId REGISTER = OperationId.of("register");
        private static final OperationId DELETE_USER = OperationId.of("deleteUser");

        /** Registering and logging in, and deleting somebody: the names a request asks for. */
        private final ApiModel users = ApiModel.of("users", "1", List.of(
                Operation.of(HttpMethod.POST, "/users/{team}", List.of(
                                Parameter.of("team", ParameterLocation.PATH, true, StringSchema.of()),
                                Parameter.of("source", ParameterLocation.QUERY, false,
                                        StringSchema.of()),
                                Parameter.of("X-Trace", ParameterLocation.HEADER, false,
                                        StringSchema.of()),
                                Parameter.of("session", ParameterLocation.COOKIE, false,
                                        StringSchema.of())))
                        .withId(REGISTER)
                        .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                                "email", StringSchema.of(),
                                "password", StringSchema.of(),
                                "profile", ObjectSchema.of(Map.of("phone", StringSchema.of())))),
                                true)),
                Operation.of(HttpMethod.POST, "/login").withId(OperationId.of("login"))
                        .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of(
                                "email", StringSchema.of(), "password", StringSchema.of())), true)),
                Operation.of(HttpMethod.DELETE, "/users/{userId}", List.of(
                                Parameter.of("userId", ParameterLocation.PATH, true,
                                        StringSchema.of()),
                                Parameter.of("reason", ParameterLocation.QUERY, false,
                                        StringSchema.of())))
                        .withId(DELETE_USER)));

        private final TestCase registration = TestCase.of(REGISTER, List.of(
                        sent("team", ParameterLocation.PATH, "blue"),
                        sent("source", ParameterLocation.QUERY, "web"),
                        sent("X-Trace", ParameterLocation.HEADER, "abc123"),
                        sent("session", ParameterLocation.COOKIE, "s-42")),
                new io.restest.core.execution.BodyValue("application/json", JsonValue.object(Map.of(
                        "email", JsonValue.of("ana@example.com"),
                        "password", JsonValue.of("Tr0ub4dor&3"),
                        "profile", JsonValue.object(Map.of("phone", JsonValue.of("+447700900123"))))),
                        new io.restest.core.execution.ValueOrigin.Generated("random")));

        @Test
        @DisplayName("every value an accepted request carried is kept under its name, a password "
                + "no reply would ever show among them")
        void what_was_sent_is_kept() {
            ObservedValues seen = new ObservedValues(users);

            seen.on(answered(registration, 201));

            assertThat(valuesUnder(seen, "team")).containsExactly(JsonValue.of("blue"));
            assertThat(valuesUnder(seen, "source")).containsExactly(JsonValue.of("web"));
            assertThat(valuesUnder(seen, "X-Trace")).containsExactly(JsonValue.of("abc123"));
            assertThat(valuesUnder(seen, "session")).containsExactly(JsonValue.of("s-42"));
            assertThat(valuesUnder(seen, "email")).containsExactly(JsonValue.of("ana@example.com"));
            assertThat(valuesUnder(seen, "password")).containsExactly(JsonValue.of("Tr0ub4dor&3"));
            assertThat(valuesUnder(seen, "phone")).containsExactly(JsonValue.of("+447700900123"));
            assertThat(seen.underTheirOwnNames().observationsFor(ValueRequest.of(GET_PET,
                            "password", ParameterLocation.BODY, StringSchema.of())))
                    .extracting(ObservedValues.Observation::heardIn)
                    .containsExactly(ObservedValues.HeardIn.ACCEPTED_REQUEST);
        }

        @Test
        @DisplayName("nothing is kept from a request the API refused")
        void a_refused_request_teaches_nothing() {
            ObservedValues seen = new ObservedValues(users);

            seen.on(answered(registration, 400));

            assertThat(valuesUnder(seen, "password")).isEmpty();
        }

        @Test
        @DisplayName("nothing is kept from a request built to push at the API, a changed one or a "
                + "step of a series")
        void not_every_accepted_request_is_learned_from() {
            ObservedValues seen = new ObservedValues(users);
            TestCase pushing = TestCase.of(REGISTER, registration.parameterValues(),
                    registration.body(), io.restest.core.execution.Intent.PUSHING);
            TestCase changed = TestCase.changed(REGISTER, registration.parameterValues(),
                    registration.body(), io.restest.core.execution.Intent.REFUSAL_EXPECTED,
                    new io.restest.core.execution.Mutation(
                            io.restest.core.execution.InteractionId.generate(), "oversize",
                            ParameterLocation.BODY, "body.password", "a password far too long"));
            TestCase step = TestCase.stepOf(REGISTER, registration.parameterValues(),
                    registration.body(), io.restest.core.execution.Intent.UNKNOWN,
                    new io.restest.core.execution.SequenceStep("createTwice", 2,
                            List.of(io.restest.core.execution.InteractionId.generate()),
                            "create it again"));

            seen.on(answered(pushing, 201));
            seen.on(answered(changed, 201));
            seen.on(answered(step, 201));

            assertThat(valuesUnder(seen, "password")).isEmpty();
        }

        @Test
        @DisplayName("nothing at all is kept from a deletion, whose thing is gone")
        void a_deletion_teaches_nothing() {
            ObservedValues seen = new ObservedValues(users);
            TestCase deletion = TestCase.of(DELETE_USER, List.of(
                    sent("userId", ParameterLocation.PATH, "u-7"),
                    sent("reason", ParameterLocation.QUERY, "left")));

            seen.on(answered(deletion, 204));

            assertThat(valuesUnder(seen, "userId")).isEmpty();
            assertThat(valuesUnder(seen, "reason")).isEmpty();
        }

        @Test
        @DisplayName("switched off, only replies are learned from, as before")
        void switched_off() {
            MemorySettings d = MemorySettings.defaults();
            ObservedValues seen = new ObservedValues(users, new MemorySettings(
                    d.mostValuesUnderOneName(), d.mostNames(), d.longestValueKept(),
                    d.longestReplyRead(), d.asDeepAsAReplyIsRead(), true, true, false));

            seen.on(answered(registration, 201, "{\"email\": \"ana@example.com\"}"));

            assertThat(valuesUnder(seen, "password")).isEmpty();
            assertThat(valuesUnder(seen, "email")).containsExactly(JsonValue.of("ana@example.com"));
        }

        @Test
        @DisplayName("what a request sent and what a reply showed share one list under a name, "
                + "the newest last")
        void requests_and_replies_share_a_name() {
            ObservedValues seen = new ObservedValues(users);

            seen.on(answered(registration, 201, "{\"email\": \"ana.r@example.com\"}"));

            assertThat(valuesUnder(seen, "email")).containsExactly(
                    JsonValue.of("ana@example.com"), JsonValue.of("ana.r@example.com"));
            assertThat(seen.underTheirOwnNames().observationsFor(ValueRequest.of(GET_PET,
                            "email", ParameterLocation.BODY, StringSchema.of())))
                    .extracting(ObservedValues.Observation::heardIn)
                    .containsExactly(ObservedValues.HeardIn.ACCEPTED_REQUEST,
                            ObservedValues.HeardIn.REPLY);
        }

        private static io.restest.core.execution.ParameterValue sent(String name,
                ParameterLocation where, String value) {
            return io.restest.core.execution.ParameterValue.of(name, where, JsonValue.of(value),
                    new io.restest.core.execution.ValueOrigin.Generated("random"));
        }

        private static RunEvent.InteractionCompleted answered(TestCase sent, int status) {
            return answered(sent, status, null);
        }

        private static RunEvent.InteractionCompleted answered(TestCase sent, int status,
                String reply) {
            return new RunEvent.InteractionCompleted(Instant.EPOCH, Interaction.answered(sent,
                    HttpRequestRecord.of(HttpMethod.POST, "https://api.example/users/blue"),
                    new HttpResponseRecord(StatusLine.of(status),
                            List.of(Header.of("Content-Type", "application/json")),
                            reply == null ? Optional.empty() : Optional.of(Payload.of(
                                    reply.getBytes(StandardCharsets.UTF_8), "application/json"))),
                    Instant.EPOCH, Duration.ofMillis(3)));
        }
    }

    @Nested
    @DisplayName("how a full memory makes room")
    class HowAFullMemoryMakesRoom {

        private final MemorySettings oneName = new MemorySettings(
                MemorySettings.defaults().mostValuesUnderOneName(), 1,
                MemorySettings.defaults().longestValueKept(),
                MemorySettings.defaults().longestReplyRead(),
                MemorySettings.defaults().asDeepAsAReplyIsRead(), true, true, true);

        @Test
        @DisplayName("a memory allowed no names keeps none, however many values a name may hold")
        void no_names_at_all() {
            ObservedValues none = new ObservedValues(anApiReturning(PET), new MemorySettings(
                    MemorySettings.defaults().mostValuesUnderOneName(), 0,
                    MemorySettings.defaults().longestValueKept(),
                    MemorySettings.defaults().longestReplyRead(),
                    MemorySettings.defaults().asDeepAsAReplyIsRead(), true, true, true));

            none.on(reply(200, "application/json", "{\"id\": 1, \"name\": \"Fluffy\"}"));

            assertThat(none.underTheirOwnNames().size()).isZero();
        }

        @Test
        @DisplayName("whole things under the name of their shape make room the same way")
        void shapes_make_room_too() {
            ObservedValues.Remembered shapes =
                    new ObservedValues.Remembered(ValueDictionary.Keying.SCHEMA, oneName);
            InteractionId from = InteractionId.generate();

            shapes.remember("Pet", JsonValue.object(Map.of("id", JsonValue.of(1))), from);
            shapes.remember("Owner", JsonValue.object(Map.of("id", JsonValue.of(2))), from);

            assertThat(shapes.size()).isEqualTo(1);
            assertThat(shapes.valuesFor(shapeRequest("Pet"))).isEmpty();
            assertThat(shapes.valuesFor(shapeRequest("Owner"))).hasSize(1);
        }

        @Test
        @DisplayName("and so do things kept by their kind")
        void kinds_make_room_too() {
            ObservedValues.Resources kinds = new ObservedValues.Resources(oneName);
            InteractionId from = InteractionId.generate();

            kinds.remember("pet", new JsonValue.JsonObject(Map.of("id", JsonValue.of(1))), from);
            kinds.remember("owner", new JsonValue.JsonObject(Map.of("id", JsonValue.of(2))), from);

            assertThat(kinds.size()).isEqualTo(1);
            assertThat(kinds.thingsOfKind("pet")).isEmpty();
            assertThat(kinds.thingsOfKind("owner")).hasSize(1);
        }

        private static ValueRequest shapeRequest(String shape) {
            return new ValueRequest(GET_PET, "body", "body", ParameterLocation.BODY, PET,
                    List.of(), Optional.of(shape));
        }
    }

    @Nested
    @DisplayName("only what some request asks for")
    class OnlyWhatSomeRequestAsksFor {

        @Test
        @DisplayName("a name no request asks for is not kept")
        void a_name_nobody_asks_for_is_not_kept() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"org.hibernate.validator.internal.engine"
                    + ".groups\": {\"effectiveLevel\": \"INFO\"}, \"name\": \"Fluffy\"}"));

            assertThat(valuesUnder(seen, "org.hibernate.validator.internal.engine.groups"))
                    .isEmpty();
            assertThat(valuesUnder(seen, "effectiveLevel")).isEmpty();
            assertThat(valuesUnder(seen, "name")).containsExactly(JsonValue.of("Fluffy"));
            assertThat(seen.underTheirOwnNames().size())
                    .describedAs("only the one name some request asks for takes up room")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("what is inside a piece nobody asks for is still looked at")
        void inside_a_piece_nobody_asks_for() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json",
                    "{\"address\": {\"town\": \"Sevilla\"}}"));

            assertThat(valuesUnder(seen, "address")).isEmpty();
            assertThat(valuesUnder(seen, "town")).containsExactly(JsonValue.of("Sevilla"));
        }

        @Test
        @DisplayName("what is inside a piece too large to keep is still looked at")
        void inside_a_piece_too_large_to_keep() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET));

            seen.on(reply(200, "application/json", "{\"owner\": {\"town\": \"Sevilla\", "
                    + "\"photo\": \"" + "x".repeat(20_000) + "\"}}"));

            assertThat(valuesUnder(seen, "owner"))
                    .describedAs("an owner carrying a photo nobody could send is not sent")
                    .isEmpty();
            assertThat(valuesUnder(seen, "town"))
                    .describedAs("but the town beside the photo is a value like any other")
                    .containsExactly(JsonValue.of("Sevilla"));
        }

        @Test
        @DisplayName("a token a login hands back is kept, however many names came before it")
        void a_token_after_thousands_of_names() {
            Operation diagnostics = Operation.of(HttpMethod.GET, "/actuator/loggers")
                    .withId(OperationId.of("loggers"))
                    .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
            Operation refresh = Operation.of(HttpMethod.POST, "/user/refresh-token")
                    .withId(OperationId.of("refreshToken"))
                    .withRequestBody(RequestBodyModel.json(ObjectSchema.of(
                            Map.of("refreshToken", StringSchema.of())), true));
            ObservedValues seen = new ObservedValues(anApiReturning(PET, diagnostics, refresh));
            StringBuilder loggers = new StringBuilder("{\"loggers\": {");
            for (int at = 0; at < MemorySettings.defaults().mostNames() + 50; at++) {
                loggers.append(at == 0 ? "" : ",").append("\"org.example.Class").append(at)
                        .append("\": {\"effectiveLevel\": \"INFO\"}");
            }
            loggers.append("}}");

            seen.on(reply(200, "application/json", loggers.toString()));
            seen.on(reply(200, "application/json", "{\"isSuccess\": true, \"response\": "
                    + "{\"accessToken\": \"eyJhY2Nlc3M\", \"refreshToken\": \"eyJyZWZyZXNo\"}}"));

            assertThat(valuesUnder(seen, "refreshToken"))
                    .describedAs("the names of every class in the program, which no request asks "
                            + "for, used to fill the memory before the first login answered")
                    .containsExactly(JsonValue.of("eyJyZWZyZXNo"));
        }

        @Test
        @DisplayName("a value is kept under whichever names it is said to answer, and remembers the "
                + "one it came with")
        void kept_under_the_names_it_answers() {
            ObservedValues seen = new ObservedValues(anApiReturning(PET),
                    MemorySettings.defaults(), heard -> heard.equals("token")
                            ? Set.of("refreshToken", "accessToken") : Set.of());

            seen.on(reply(200, "application/json", "{\"token\": \"eyJ0b2tlbg\"}"));

            assertThat(valuesUnder(seen, "token"))
                    .describedAs("a name is only ever kept under the names a request asks for")
                    .isEmpty();
            assertThat(valuesUnder(seen, "refreshToken")).containsExactly(JsonValue.of("eyJ0b2tlbg"));
            assertThat(valuesUnder(seen, "accessToken")).containsExactly(JsonValue.of("eyJ0b2tlbg"));
            assertThat(seen.underTheirOwnNames().observationsFor(ValueRequest.of(GET_PET,
                            "refreshToken", ParameterLocation.BODY, StringSchema.of())))
                    .extracting(ObservedValues.Observation::heardAs)
                    .describedAs("so that where it came from can be told as the reply told it")
                    .containsExactly("token");
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

    /**
     * An API with an operation answering 200 with the given shape, one that is sent a pet, and
     * any others given.
     */
    private static ApiModel anApiReturning(CanonicalSchema schema, Operation... others) {
        Operation operation = Operation.of(HttpMethod.GET, "/pets")
                .withId(GET_PET)
                .withResponses(List.of(ResponseModel.json("200", schema)));
        List<Operation> operations = new ArrayList<>(List.of(operation, UPDATE_PET));
        operations.addAll(List.of(others));
        return ApiModel.of("pets", "1", operations).withSchemas(Map.of("Pet", PET));
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
