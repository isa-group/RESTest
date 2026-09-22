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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.restest.core.event.RunEvent;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueProvider;
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
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Sending back what the API itself has already sent.
 *
 * <p>Two things are being checked here and they pull in opposite directions. One is that the memory
 * is actually used - an identifier the API handed back a moment ago should be the one that goes out
 * next, because it is the one that exists. The other is that it is not used carelessly: a resource
 * an API returns is not a resource it accepts, so what gets sent back has the parts the API only
 * ever sends taken out of it, and one of its values replaced, so that what arrives is something new
 * rather than a copy of something that already exists.
 */
class ObservedValueProviderTest {

    private static final OperationId GET_PET = OperationId.of("getPet");
    private static final OperationId ADD_PET = OperationId.of("addPet");

    /** What the API sends back: an identifier it allots, and the two fields a caller supplies. */
    private static final ObjectSchema PET_AS_RETURNED = ObjectSchema.of(new LinkedHashMap<>(Map.of(
            "id", onlyEverReturned(NumberSchema.of(NumberKind.INTEGER)),
            "name", StringSchema.of(),
            "tag", StringSchema.of())));

    @Nested
    @DisplayName("one value at a time")
    class OneValue {

        @Test
        @DisplayName("an identifier the API handed back is what goes out next")
        void an_identifier_that_exists_is_sent() {
            ObservedValueProvider source = sourceKnowing("{\"id\": 7, \"name\": \"Fluffy\"}", null);

            Optional<GeneratedValue> offered = source.offer(ValueRequest.of(GET_PET, "id",
                    ParameterLocation.PATH, NumberSchema.of(NumberKind.INTEGER)));

            assertThat(offered).isPresent();
            assertThat(offered.get().value()).isEqualTo(JsonValue.of(7));
        }

        @Test
        @DisplayName("a value says which exchange it was read out of, not merely that it was seen")
        void a_value_names_the_exchange_it_came_from() {
            ObservedValueProvider source = sourceKnowing("{\"name\": \"Fluffy\"}", null);

            GeneratedValue offered = source.offer(ValueRequest.of(GET_PET, "name",
                    ParameterLocation.QUERY, StringSchema.of())).orElseThrow();

            assertThat(offered.origin())
                    .describedAs("when a request fails the first question is where the value came "
                            + "from, and 'observed' is not an answer anybody can follow up")
                    .isInstanceOf(ValueOrigin.Derived.class);
            assertThat(((ValueOrigin.Derived) offered.origin()).description())
                    .contains("name");
        }

        @Test
        @DisplayName("a word seen under one name is not offered where a number is wanted")
        void the_kind_has_to_match() {
            ObservedValueProvider source = sourceKnowing("{\"id\": \"a-uuid\"}", null);

            assertThat(source.offer(ValueRequest.of(ADD_PET, "id", ParameterLocation.QUERY,
                    NumberSchema.of(NumberKind.INTEGER))))
                    .describedAs("one name means different things in different parts of an API")
                    .isEmpty();
        }

        @Test
        @DisplayName("a value the document does not allow here is not offered, wherever it was seen")
        void a_value_outside_a_closed_list_is_not_offered() {
            ObservedValueProvider source = sourceKnowing("{\"status\": \"sold\"}", null);
            StringSchema onlyTwo = new StringSchema(
                    SchemaMetadata.none().withEnumeration(
                            List.of(JsonValue.of("pending"), JsonValue.of("available"))),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

            assertThat(source.offer(ValueRequest.of(ADD_PET, "status", ParameterLocation.QUERY,
                    onlyTwo)))
                    .describedAs("a closed list is the whole set of values the API takes, and a "
                            + "value is not made acceptable by having been seen elsewhere")
                    .isEmpty();
        }

        @Test
        @DisplayName("a value that could not be written into a path is not offered for one")
        void a_value_that_cannot_be_sent_is_not_offered() {
            ObservedValueProvider source = sourceKnowing("{\"name\": \"\"}", null);

            assertThat(source.offer(ValueRequest.of(GET_PET, "name", ParameterLocation.PATH,
                    StringSchema.of())))
                    .describedAs("an empty word leaves a gap in the path addressing something else")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("a thing asked for by its own name, not by the name of its shape")
    class UnderItsOwnName {

        /** What an operation accepts for an owner: a town, and nothing the API allots itself. */
        private ObjectSchema anOwnerAsAccepted() {
            Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
            properties.put("id", onlyEverReturned(NumberSchema.of(NumberKind.INTEGER)));
            properties.put("town", StringSchema.of());
            return ObjectSchema.of(properties);
        }

        @Test
        @DisplayName("it is cut down the same way, because there is one set of rules and no exception")
        void it_is_cut_down_the_same_way() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"owner\": {\"id\": 7, \"town\": \"Sevilla\", \"secret\": \"x\"}}",
                    nothing());

            JsonValue owner = source.offer(ValueRequest.of(ADD_PET, "owner",
                    ParameterLocation.BODY, anOwnerAsAccepted())).orElseThrow().value();

            assertThat(((JsonValue.JsonObject) owner).members())
                    .describedAs("an owner the API returns is not an owner it accepts, and that is "
                            + "as true when it is asked for by its own name as when the document "
                            + "names its shape")
                    .doesNotContainKey("id")
                    .doesNotContainKey("secret")
                    .containsEntry("town", JsonValue.of("Sevilla"));
        }

        @Test
        @DisplayName("and what is too large to send inside it is left out of it")
        void what_is_too_large_inside_it_is_left_out() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"owner\": {\"town\": \"" + "x".repeat(400_000) + "\"}}", nothing());

            assertThat(source.offer(ValueRequest.of(ADD_PET, "owner", ParameterLocation.BODY,
                    anOwnerAsAccepted())))
                    .describedAs("the town is all this owner had and nobody could send it, so "
                            + "there is no owner left to offer")
                    .isEmpty();
        }

        @Test
        @DisplayName("a thing whose shape says nothing about it is still measured")
        void a_thing_of_no_stated_shape_is_still_measured() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"anything\": {\"price\": 1e-10000}}", nothing());

            assertThat(source.offer(ValueRequest.of(ADD_PET, "anything", ParameterLocation.BODY,
                    io.restest.core.schema.AnySchema.of())))
                    .describedAs("a shape that says nothing refuses nothing for its shape, and "
                            + "still cannot make a number JSON has no way of writing")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("a whole thing the API produced")
    class AWholeThing {

        @Test
        @DisplayName("what the API only ever sends back is not sent to it")
        void what_is_only_ever_returned_is_taken_out() {
            ObservedValueProvider source =
                    sourceKnowing("{\"id\": 7, \"name\": \"Fluffy\", \"tag\": \"small\"}", null);

            JsonValue body = source.offer(askedForAPet()).orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("readOnly is the document's own way of saying do not send me this")
                    .doesNotContainKey("id")
                    .containsKeys("name", "tag");
        }

        @Test
        @DisplayName("a property this operation does not ask for is taken out")
        void something_the_operation_never_asked_for_is_taken_out() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\", \"createdAt\": \"2026-01-01\"}",
                    null);

            JsonValue body = source.offer(askedForAPet()).orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("an API that refuses what it did not ask for is ordinary")
                    .doesNotContainKey("createdAt");
        }

        @Test
        @DisplayName("nothing is offered when what the operation insists on did not survive")
        void an_incomplete_thing_is_not_offered() {
            ObservedValueProvider source = sourceKnowing("{\"tag\": \"small\"}", null);
            ObjectSchema insistsOnAName = new ObjectSchema(SchemaMetadata.none(),
                    Map.of("name", StringSchema.of(), "tag", StringSchema.of()),
                    Set.of("name"), Optional.empty(), Optional.empty(), Optional.empty());

            assertThat(source.offer(askedFor(insistsOnAName)))
                    .describedAs("a body knowingly missing something required is not worth "
                            + "sending when something else could build a complete one")
                    .isEmpty();
        }

        @Test
        @DisplayName("exactly one value inside it is replaced with a fresh one")
        void exactly_one_value_is_changed() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"id\": 7, \"name\": \"Fluffy\", \"tag\": \"small\"}", freshWords());

            JsonValue body = source.offer(askedForAPet()).orElseThrow().value();

            Map<String, JsonValue> members = ((JsonValue.JsonObject) body).members();
            assertThat(members.values().stream().filter(JsonValue.of("a fresh word")::equals))
                    .describedAs("sending an unchanged copy asks the API to create a duplicate; "
                            + "changing one thing asks it to accept something new that is "
                            + "otherwise exactly as real as what it sent")
                    .hasSize(1);
            assertThat(members).hasSize(2);
        }

        @Test
        @DisplayName("the exchange it came from, and what was changed in it, are both recorded")
        void what_was_changed_is_recorded() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\"}", freshWords());

            GeneratedValue offered = source.offer(askedForAPet()).orElseThrow();

            assertThat(offered.origin()).isInstanceOf(ValueOrigin.Derived.class);
            assertThat(((ValueOrigin.Derived) offered.origin()).description())
                    .contains("Pet")
                    .contains("changed");
        }

        @Test
        @DisplayName("a replacement equal to what is already there is not a change, and is not "
                + "recorded as one")
        void an_equal_replacement_is_not_a_change() {
            // What supplies the replacement is the rest of the strategy, and the rest of the
            // strategy contains this very source - so the likeliest single answer for a value
            // inside a remembered thing is the value already in it. Taking it would send an exact
            // copy of something that exists and record that something had been changed.
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\"}", whatIsAlreadyThere());

            GeneratedValue offered = source.offer(askedForAPet()).orElseThrow();

            assertThat(((JsonValue.JsonObject) offered.value()).members())
                    .containsEntry("name", JsonValue.of("Fluffy"))
                    .containsEntry("tag", JsonValue.of("small"));
            assertThat(((ValueOrigin.Derived) offered.origin()).description())
                    .describedAs("what is stored about a run has to be true of the run: this is "
                            + "the evidence a later increment's work gets read against")
                    .contains("unchanged")
                    .describedAs("and it does not name a value as having been changed, which is "
                            + "what the wording for a real change looks like")
                    .doesNotContain("with '");
        }

        @Test
        @DisplayName("a value is asked more than once for a different answer before it is given up on")
        void a_second_answer_is_asked_for() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\"}", theSameThenSomethingElse());

            JsonValue body = source.offer(askedForAPet()).orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members().values())
                    .describedAs("what answers is usually choosing among several, so one answer "
                            + "equal to what is there does not mean there is nothing else")
                    .contains(JsonValue.of("something else"));
        }

        @Test
        @DisplayName("it is offered exactly as it came back when nothing can supply a fresh value")
        void an_unchanged_thing_is_still_worth_sending() {
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\"}", nothing());

            JsonValue body = source.offer(askedForAPet()).orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .containsEntry("name", JsonValue.of("Fluffy"))
                    .containsEntry("tag", JsonValue.of("small"));
        }

        @Test
        @DisplayName("a thing whose every part this operation refuses is not offered as that thing")
        void an_emptied_thing_is_not_offered() {
            ObjectSchema nothingInCommon = ObjectSchema.of(new LinkedHashMap<>(Map.of(
                    "somethingElse", StringSchema.of())));
            ObservedValueProvider source = sourceKnowing(
                    "{\"name\": \"Fluffy\", \"tag\": \"small\"}", nothing(),
                    "Pet", nothingInCommon);

            assertThat(source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, nothingInCommon, List.of(), Optional.of("Pet"))))
                    .describedAs("an empty object wearing that thing's name is not that thing, "
                            + "and something else can build a body that is")
                    .isEmpty();
        }

        @Test
        @DisplayName("a number inside a returned thing that cannot be written down is left out of it")
        void a_number_nothing_could_write_is_left_out() {
            Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
            properties.put("price", NumberSchema.of(NumberKind.NUMBER));
            properties.put("name", StringSchema.of());
            ObjectSchema pet = ObjectSchema.of(properties);
            ObservedValueProvider source = sourceKnowing(
                    "{\"price\": 1e-10000, \"name\": \"Fluffy\"}", nothing(), "Pet", pet);

            JsonValue body = source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, pet, List.of(), Optional.of("Pet")))
                    .orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("eight characters on the wire and beyond anything JSON writes out "
                            + "in full; a body carrying one cannot be built at all")
                    .doesNotContainKey("price")
                    .containsEntry("name", JsonValue.of("Fluffy"));
            assertThatCode(() -> io.restest.core.json.JsonText.write(body))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a word inside a returned thing that is too long to send is left out of it")
        void an_enormous_word_inside_a_thing_is_left_out() {
            Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
            properties.put("photo", StringSchema.of());
            properties.put("name", StringSchema.of());
            ObjectSchema pet = ObjectSchema.of(properties);
            ObservedValueProvider source = sourceKnowing(
                    "{\"photo\": \"" + "x".repeat(400_000) + "\", \"name\": \"Fluffy\"}",
                    nothing(), "Pet", pet);

            JsonValue body = source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, pet, List.of(), Optional.of("Pet")))
                    .orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("a thing is kept under the name of its shape in one piece, so the "
                            + "values inside it are measured here or nowhere")
                    .doesNotContainKey("photo")
                    .containsEntry("name", JsonValue.of("Fluffy"));
        }

        @Test
        @DisplayName("a body that says only 'an object' keeps what came back in it")
        void a_bare_object_keeps_what_it_was_sent() {
            ObjectSchema anythingAtAll = ObjectSchema.of(Map.of());
            ObservedValueProvider source = sourceKnowing(
                    "{\"whatever\": \"came back\"}", nothing(), "Free", anythingAtAll);

            JsonValue body = source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, anythingAtAll, List.of(), Optional.of("Free")))
                    .orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("a document writes a free-form body two ways and this is the "
                            + "commoner of them: nothing beyond the word object")
                    .containsEntry("whatever", JsonValue.of("came back"));
        }

        @Test
        @DisplayName("a body declared as a free-form map keeps what came back in it")
        void a_free_form_map_keeps_what_it_was_sent() {
            ObjectSchema anyProperties = new ObjectSchema(SchemaMetadata.none(), Map.of(),
                    Set.of(), Optional.of(StringSchema.of()), Optional.empty(), Optional.empty());
            ObservedValueProvider source = sourceKnowing(
                    "{\"anything\": \"at all\"}", nothing(), "Headers", anyProperties);

            JsonValue body = source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, anyProperties, List.of(), Optional.of("Headers")))
                    .orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members())
                    .describedAs("a shape that names no properties and welcomes any is not a "
                            + "shape that accepts nothing")
                    .containsEntry("anything", JsonValue.of("at all"));
        }

        @Test
        @DisplayName("what is inside a thing is cut down the same way the thing itself is")
        void nested_things_are_cut_down_too() {
            ObjectSchema owner = ObjectSchema.of(new LinkedHashMap<>(Map.of(
                    "ownerId", onlyEverReturned(NumberSchema.of(NumberKind.INTEGER)),
                    "town", StringSchema.of())));
            ObjectSchema visit = new ObjectSchema(SchemaMetadata.none(),
                    new LinkedHashMap<>(Map.of("owner", owner,
                            "notes", ArraySchema.of(StringSchema.of()))),
                    Set.of(), Optional.empty(), Optional.empty(), Optional.empty());
            ObservedValueProvider source = sourceKnowing(
                    "{\"owner\": {\"ownerId\": 3, \"town\": \"Sevilla\", \"extra\": 1},"
                            + " \"notes\": [\"first\", \"second\"]}",
                    nothing(), "Visit", visit);

            JsonValue body = source.offer(new ValueRequest(ADD_PET, "body", "body",
                    ParameterLocation.BODY, visit, List.of(), Optional.of("Visit")))
                    .orElseThrow().value();

            JsonValue.JsonObject sent = (JsonValue.JsonObject) body;
            assertThat(((JsonValue.JsonObject) sent.members().get("owner")).members())
                    .doesNotContainKey("ownerId")
                    .doesNotContainKey("extra")
                    .containsEntry("town", JsonValue.of("Sevilla"));
            assertThat(((JsonValue.JsonArray) sent.members().get("notes")).elements())
                    .hasSize(2);
        }
    }

    /** A source that has seen this one reply, with the given supplier of the one changed value. */
    private static ObservedValueProvider sourceKnowing(String reply, ValueProvider fresh) {
        return sourceKnowing(reply, fresh, "Pet", PET_AS_RETURNED);
    }

    private static ObservedValueProvider sourceKnowing(String reply, ValueProvider fresh,
            String shapeName, ObjectSchema shape) {
        Operation returnsOne = Operation.of(HttpMethod.GET, "/pets")
                .withId(GET_PET)
                .withResponses(List.of(
                        ResponseModel.json("200", SchemaReference.to(shapeName))));
        ApiModel model = ApiModel.of("pets", "1", List.of(returnsOne))
                .withSchemas(Map.of(shapeName, shape));
        ObservedValues seen = new ObservedValues(model);
        seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Interaction.answered(
                TestCase.of(GET_PET, List.of()),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/pets"),
                new HttpResponseRecord(StatusLine.of(200),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.of(reply.getBytes(StandardCharsets.UTF_8),
                                "application/json"))),
                Instant.EPOCH, Duration.ofMillis(3))));
        return new ObservedValueProvider(model, seen, new SplittableRandom(20260922L), fresh);
    }

    private static ValueRequest askedForAPet() {
        return askedFor(PET_AS_RETURNED);
    }

    private static ValueRequest askedFor(ObjectSchema shape) {
        return new ValueRequest(ADD_PET, "body", "body", ParameterLocation.BODY, shape, List.of(),
                Optional.of("Pet"));
    }

    /** A shape the document says the API sends and never receives. */
    private static CanonicalSchema onlyEverReturned(NumberSchema schema) {
        return new NumberSchema(
                schema.metadata().withAccess(SchemaMetadata.Access.READ_ONLY), schema.kind(),
                schema.minimum(), schema.maximum(), schema.exclusiveMinimum(),
                schema.exclusiveMaximum(), schema.multipleOf(), schema.format());
    }

    /** Something to put in the one changed value: a word, for anything that wants one. */
    private static ValueProvider freshWords() {
        return request -> request.schema() instanceof StringSchema
                ? Optional.of(GeneratedValue.generatedBy(JsonValue.of("a fresh word"), "test"))
                : Optional.empty();
    }

    /** Nothing at all, which is what the sources look like while they are still being built. */
    private static ValueProvider nothing() {
        return request -> Optional.empty();
    }

    /**
     * Whatever the remembered thing already holds under that name, which is what the rest of a
     * strategy containing this source most often answers.
     */
    private static ValueProvider whatIsAlreadyThere() {
        return request -> Optional.of(GeneratedValue.generatedBy(
                switch (request.name()) {
                    case "name" -> JsonValue.of("Fluffy");
                    case "tag" -> JsonValue.of("small");
                    default -> JsonValue.of("something else");
                }, "test"));
    }

    /** The value already there the first time it is asked, and a different one afterwards. */
    private static ValueProvider theSameThenSomethingElse() {
        ValueProvider sameAsBefore = whatIsAlreadyThere();
        java.util.concurrent.atomic.AtomicInteger asked =
                new java.util.concurrent.atomic.AtomicInteger();
        return request -> asked.getAndIncrement() == 0
                ? sameAsBefore.offer(request)
                : Optional.of(GeneratedValue.generatedBy(JsonValue.of("something else"), "test"));
    }
}
