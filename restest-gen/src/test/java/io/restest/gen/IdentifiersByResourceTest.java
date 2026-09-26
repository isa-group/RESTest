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
import io.restest.core.execution.ValueOrigin;
import io.restest.core.gen.GeneratedValue;
import io.restest.core.gen.ValueRequest;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.AnySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.MemorySettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Filling a gap in a web address with the identifier of a thing the API returned at an address of
 * the same kind.
 *
 * <p>The API calls a pet type's identifier {@code id}; the address that reads one calls it
 * {@code {petTypeId}}. Matching by name alone never connects the two, so the run went on inventing
 * numbers for that gap and being told there is no such pet type. These tests are about the
 * connection being made, being made to the right kind of thing, and not being made where it would
 * send something the gap cannot take.
 */
class IdentifiersByResourceTest {

    private static final NumberSchema AN_INTEGER = NumberSchema.of(NumberKind.INTEGER);

    @Nested
    @DisplayName("the kind of thing an address is about")
    class KindOfThing {

        @Test
        @DisplayName("is the last fixed part of the address, with the gaps after it left off")
        void is_the_last_fixed_part() {
            assertThat(ObservedValues.kindOfThingAt("/pets")).contains("pet");
            assertThat(ObservedValues.kindOfThingAt("/pets/{petId}")).contains("pet");
            assertThat(ObservedValues.kindOfThingAt("/owners/{ownerId}/pets/{petId}"))
                    .contains("pet");
            assertThat(ObservedValues.kindOfThingAt("/v1/hospitais/"))
                    .describedAs("a trailing slash is not a part of its own, and a plural in "
                            + "another language is kept as it is, the same way every time")
                    .contains("hospitais");
            assertThat(ObservedValues.kindOfThingAt("/{anything}")).isEmpty();
        }

        @Test
        @DisplayName("is one word however it is spelt, plural or singular")
        void is_one_word_however_it_is_spelt() {
            assertThat(ObservedValues.kindOfThingNamed("pet-types")).contains("pettype");
            assertThat(ObservedValues.kindOfThingNamed("PetTypes")).contains("pettype");
            assertThat(ObservedValues.kindOfThingNamed("pettype")).contains("pettype");
            for (List<String> both : List.of(List.of("movies", "movie"),
                    List.of("specialties", "specialty"), List.of("boxes", "box"),
                    List.of("addresses", "address"), List.of("statuses", "status"),
                    List.of("classes", "class"), List.of("pets", "pet"))) {
                assertThat(ObservedValues.kindOfThingNamed(both.get(0)))
                        .describedAs("%s and %s are one kind of thing", both.get(0), both.get(1))
                        .isEqualTo(ObservedValues.kindOfThingNamed(both.get(1)));
            }
        }

        @Test
        @DisplayName("of a gap, is what the fixed part before it names, or else what its name names")
        void of_a_gap() {
            assertThat(ObservedValues.kindOfThingBefore("/pettypes/{petTypeId}", "petTypeId"))
                    .contains("pettype");
            assertThat(ObservedValues.kindOfThingBefore("/{petId}", "petId")).isEmpty();
            assertThat(ObservedValues.kindOfThingInTheName("petId")).contains("pet");
            assertThat(ObservedValues.kindOfThingInTheName("movieId"))
                    .isEqualTo(ObservedValues.kindOfThingAt("/movies"));
            assertThat(ObservedValues.kindOfThingInTheName("hospital_id")).contains("hospital");
            assertThat(ObservedValues.kindOfThingInTheName("vetID")).contains("vet");
            assertThat(ObservedValues.kindOfThingInTheName("name")).isEmpty();
            assertThat(ObservedValues.kindOfThingInTheName("id"))
                    .describedAs("a bare id names no kind of thing")
                    .isEmpty();
        }

        @Test
        @DisplayName("an identifier is recognised however it is spelt, _id included")
        void an_identifier_however_it_is_spelt() {
            assertThat(List.of("id", "ID", "_id", "petId", "pet_id", "petID", "pet-id", "PET_ID"))
                    .allMatch(ObservedValues::looksLikeAnIdentifier);
            assertThat(List.of("name", "valid", "uuid", "kind", "Id_"))
                    .noneMatch(ObservedValues::looksLikeAnIdentifier);
            assertThat(ObservedValues.isTheIdentifierOf("_id", "pet")).isTrue();
            assertThat(ObservedValues.isTheIdentifierOf("movieId",
                    ObservedValues.kindOfThingNamed("movies").orElseThrow())).isTrue();
            assertThat(ObservedValues.isTheIdentifierOf("ownerId", "pet")).isFalse();
        }
    }

    @Nested
    @DisplayName("what is remembered")
    class WhatIsRemembered {

        @Test
        @DisplayName("every thing a list returned, under the kind of thing its address is about")
        void the_things_of_a_list() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": 1, \"name\": \"cat\"},"
                    + " {\"id\": 2, \"name\": \"dog\"}]");

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("pettype")).hasSize(2);
        }

        @Test
        @DisplayName("the things inside a wrapper, when the wrapper has no identifier of its own")
        void the_things_inside_a_wrapper() {
            Api api = new Api();
            api.replies("listClusters", 200, "{\"kind\": \"KafkaClusterList\","
                    + " \"data\": [{\"kind\": \"KafkaCluster\", \"cluster_id\": \"abc\"}]}");

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("cluster"))
                    .extracting(ObservedValues.Observation::value)
                    .contains(JsonValue.object(Map.of(
                            "kind", JsonValue.of("KafkaCluster"),
                            "cluster_id", JsonValue.of("abc"))));
        }

        @Test
        @DisplayName("not what is inside a thing that has an identifier, which is some other kind")
        void not_what_is_inside_a_thing() {
            Api api = new Api();
            api.replies("listPets", 200,
                    "[{\"_id\": \"65f0\", \"name\": \"Rex\", \"owner\": {\"id\": 7}}]");

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("pet"))
                    .describedAs("the pet's owner is an owner, not a pet - even when the pet "
                            + "spells its own identifier _id")
                    .extracting(ObservedValues.Observation::value)
                    .containsExactly(JsonValue.object(Map.of(
                            "_id", JsonValue.of("65f0"), "name", JsonValue.of("Rex"))));
        }

        @Test
        @DisplayName("a thing whose identifier came back empty is still a thing, not a wrapper")
        void a_thing_with_an_empty_identifier_is_not_a_wrapper() {
            Api api = new Api();
            api.replies("listPets", 200, "[{\"id\": null, \"owner\": {\"id\": 7}}]");

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("pet"))
                    .describedAs("the owner inside it is not filed as a pet")
                    .isEmpty();
        }

        @Test
        @DisplayName("nothing from a deletion, which returns the thing that has stopped existing")
        void nothing_from_a_deletion() {
            Api api = new Api();
            api.replies("deletePetType", 200, "{\"id\": 1, \"name\": \"cat\"}");

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("pettype")).isEmpty();
        }

        @Test
        @DisplayName("nothing at all when the setting is off")
        void nothing_when_switched_off() {
            Api api = new Api(settings(false, true));
            api.replies("listPetTypes", 200, "[{\"id\": 1, \"name\": \"cat\"}]");

            assertThat(api.seen.underTheKindOfThingTheyAre().size()).isZero();
            assertThat(api.offers("getPetType", "petTypeId", AN_INTEGER))
                    .describedAs("off, a gap is filled by its own name alone")
                    .isEmpty();
        }

        @Test
        @DisplayName("only the most recent few of any one kind")
        void only_the_most_recent_few() {
            Api api = new Api();
            int most = MemorySettings.defaults().mostValuesUnderOneName();
            for (int identifier = 1; identifier <= most + 5; identifier++) {
                api.replies("listPetTypes", 200, "[{\"id\": " + identifier + "}]");
            }

            assertThat(api.seen.underTheKindOfThingTheyAre().thingsOfKind("pettype"))
                    .hasSize(most);
        }

        @Test
        @DisplayName("only so many kinds of thing, as only so many names")
        void only_so_many_kinds() {
            MemorySettings on = MemorySettings.defaults();
            Api api = new Api(new MemorySettings(on.mostValuesUnderOneName(), 1,
                    on.longestValueKept(), on.longestReplyRead(), on.asDeepAsAReplyIsRead(),
                    true, true));
            api.replies("listPetTypes", 200, "[{\"id\": 1}]");
            api.replies("listFlights", 200, "[{\"id\": 2}]");

            assertThat(api.seen.underTheKindOfThingTheyAre().size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("what a gap is filled with")
    class WhatAGapIsFilledWith {

        @Test
        @DisplayName("the id of a thing its address is about, though the gap is called something else")
        void the_id_of_the_thing_its_address_is_about() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": 3, \"name\": \"lizard\"}]");

            GeneratedValue offered = api.offers("getPetType", "petTypeId", AN_INTEGER)
                    .orElseThrow();

            assertThat(offered.value()).isEqualTo(JsonValue.of(3));
            assertThat(((ValueOrigin.Derived) offered.origin()).description())
                    .describedAs("where the value came from, in the document's own words")
                    .isEqualTo("the 'id' of one of the pettypes an earlier reply returned");
        }

        @Test
        @DisplayName("an identifier of the right kind of thing, not whatever else carried the name")
        void the_right_kind_of_thing() {
            Api api = new Api();
            api.replies("listAirports", 200, "[{\"id\": 900}]");
            api.replies("listFlights", 200, "[{\"id\": 42}]");

            assertThat(fiftyDraws(api, "getFlight", "id", AN_INTEGER))
                    .describedAs("an airport's id is a real identifier of the wrong thing")
                    .containsExactly(JsonValue.of(42));
        }

        @Test
        @DisplayName("switched to ask the name first, a value under the gap's name wins, as before")
        void the_name_first_when_switched() {
            Api api = new Api(settings(true, false));
            api.replies("listAirports", 200, "[{\"id\": 900}]");
            api.replies("listFlights", 200, "[{\"id\": 42}]");

            assertThat(fiftyDraws(api, "getFlight", "id", AN_INTEGER))
                    .describedAs("every id the API returned answers to the name id")
                    .contains(JsonValue.of(900));
            Api nothingByName = new Api(settings(true, false));
            nothingByName.replies("listPetTypes", 200, "[{\"id\": 3}]");
            assertThat(nothingByName.offers("getPetType", "petTypeId", AN_INTEGER)
                    .map(GeneratedValue::value))
                    .describedAs("and the kind still answers where the name finds nothing")
                    .contains(JsonValue.of(3));
        }

        @Test
        @DisplayName("a property named exactly like the gap before one merely called id")
        void the_gap_s_own_name_first() {
            Api api = new Api();
            api.replies("listClusters", 200,
                    "{\"data\": [{\"id\": \"crn:1\", \"cluster_id\": \"abc\"}]}");

            assertThat(fiftyDraws(api, "getCluster", "cluster_id", StringSchema.of()))
                    .containsExactly(JsonValue.of("abc"));
        }

        @Test
        @DisplayName("a value under the gap's own name, when its kind of thing has said nothing yet")
        void the_name_when_the_kind_has_said_nothing() {
            Api api = new Api();
            api.replies("listVisits", 200, "[{\"id\": 5, \"petTypeId\": 8}]");

            assertThat(api.offers("getPetType", "petTypeId", AN_INTEGER)
                    .map(GeneratedValue::value)).contains(JsonValue.of(8));
        }

        @Test
        @DisplayName("the gap's name anywhere before what only looks like an identifier of its kind")
        void the_name_before_what_only_looks_like_one() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"typeId\": 6, \"name\": \"hamster\"}]");
            api.replies("listVisits", 200, "[{\"id\": 5, \"petTypeId\": 8}]");

            assertThat(fiftyDraws(api, "getPetType", "petTypeId", AN_INTEGER))
                    .containsExactly(JsonValue.of(8));
        }

        @Test
        @DisplayName("something that only looks like an identifier, when nothing better is known")
        void something_that_only_looks_like_one() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"typeId\": 6, \"name\": \"hamster\"}]");

            assertThat(api.offers("getPetType", "petTypeId", AN_INTEGER)
                    .map(GeneratedValue::value))
                    .describedAs("the best there is, rather than nothing to try")
                    .contains(JsonValue.of(6));
        }

        @Test
        @DisplayName("the kind the fixed part before the gap names, before the kind in the gap's name")
        void the_fixed_part_before_the_name() {
            Api api = new Api();
            api.replies("listTransfers", 200, "[{\"id\": 1}]");
            api.replies("listProducts", 200, "[{\"id\": 2}]");

            assertThat(fiftyDraws(api, "getTransfer", "productId", AN_INTEGER))
                    .containsExactly(JsonValue.of(1));
        }

        @Test
        @DisplayName("the kind of thing the gap's name names, where no fixed part comes before it")
        void the_kind_in_the_gap_s_name() {
            Api api = new Api();
            api.replies("listPets", 200, "[{\"id\": 11}]");

            assertThat(api.offers("getPetAtTheTop", "petId", AN_INTEGER)
                    .map(GeneratedValue::value)).contains(JsonValue.of(11));
        }

        @Test
        @DisplayName("a gap not named like an identifier takes only a property with its own name")
        void a_gap_not_named_like_an_identifier() {
            Api api = new Api();
            api.replies("listUsers", 200, "[{\"id\": \"u-7\", \"node_id\": \"n-1\"}]");
            assertThat(api.offers("getUser", "username", StringSchema.of()))
                    .describedAs("a user's id is not a username, and would push aside the "
                            + "document's own sample for one")
                    .isEmpty();

            api.replies("listUsers", 200, "[{\"id\": \"u-8\", \"username\": \"alice\"}]");
            assertThat(fiftyDraws(api, "getUser", "username", StringSchema.of()))
                    .containsExactly(JsonValue.of("alice"));
        }
    }

    @Nested
    @DisplayName("what a gap is not filled with")
    class WhatAGapIsNotFilledWith {

        @Test
        @DisplayName("a word where the gap wants a number")
        void not_the_wrong_kind() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": \"three\"}]");

            assertThat(api.offers("getPetType", "petTypeId", AN_INTEGER)).isEmpty();
        }

        @Test
        @DisplayName("a word that does not read as a uuid where the gap declares one")
        void not_outside_a_declared_uuid() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": \"not-a-uuid\"},"
                    + " {\"id\": \"0b6c7d0a-2f3e-4a51-9c8e-3d1f2a4b5c6d\"}]");

            assertThat(fiftyDraws(api, "getPetType", "petTypeId", StringSchema.ofFormat("uuid")))
                    .containsExactly(JsonValue.of("0b6c7d0a-2f3e-4a51-9c8e-3d1f2a4b5c6d"));
        }

        @Test
        @DisplayName("a number too large for the int32 the gap declares")
        void not_outside_a_declared_int32() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": 9999999999}]");

            assertThat(api.offers("getPetType", "petTypeId", new NumberSchema(
                    SchemaMetadata.none(), NumberKind.INTEGER, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of("int32")))).isEmpty();
        }

        @Test
        @DisplayName("a word that would leave the gap empty")
        void not_a_word_that_cannot_be_sent() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": \"\"}]");

            assertThat(api.offers("getPetType", "petTypeId", AnySchema.of())).isEmpty();
        }

        @Test
        @DisplayName("an identifier for a parameter that is not a gap in the address")
        void not_for_anything_but_a_gap() {
            Api api = new Api();
            api.replies("listPetTypes", 200, "[{\"id\": 3}]");

            assertThat(api.provider.offer(ValueRequest.of(OperationId.of("getPetType"),
                    "petTypeId", ParameterLocation.QUERY, AN_INTEGER)))
                    .describedAs("a query parameter is matched by its name, as before")
                    .isEmpty();
        }
    }

    /** Everything a gap is offered in fifty asks, so that one lucky draw proves nothing. */
    private static Set<JsonValue> fiftyDraws(Api api, String operation, String gap,
            CanonicalSchema schema) {
        Set<JsonValue> sent = new HashSet<>();
        for (int draw = 0; draw < 50; draw++) {
            api.offers(operation, gap, schema).map(GeneratedValue::value).ifPresent(sent::add);
        }
        return sent;
    }

    private static MemorySettings settings(boolean byResource, boolean byResourceFirst) {
        MemorySettings on = MemorySettings.defaults();
        return new MemorySettings(on.mostValuesUnderOneName(), on.mostNames(),
                on.longestValueKept(), on.longestReplyRead(), on.asDeepAsAReplyIsRead(),
                byResource, byResourceFirst);
    }

    /** A small API of lists and reads of one thing, and a memory listening to it. */
    private static final class Api {

        private final ApiModel model;
        private final ObservedValues seen;
        private final ObservedValueProvider provider;

        Api() {
            this(MemorySettings.defaults());
        }

        Api(MemorySettings settings) {
            List<Operation> operations = new ArrayList<>();
            operations.add(list("listPetTypes", "/pettypes"));
            operations.add(readOne("getPetType", HttpMethod.GET, "/pettypes/{petTypeId}",
                    "petTypeId"));
            operations.add(readOne("deletePetType", HttpMethod.DELETE, "/pettypes/{petTypeId}",
                    "petTypeId"));
            operations.add(list("listAirports", "/airports"));
            operations.add(list("listFlights", "/flights"));
            operations.add(readOne("getFlight", HttpMethod.GET, "/flights/{id}", "id"));
            operations.add(list("listClusters", "/v3/clusters"));
            operations.add(readOne("getCluster", HttpMethod.GET, "/v3/clusters/{cluster_id}",
                    "cluster_id"));
            operations.add(list("listVisits", "/visits"));
            operations.add(list("listPets", "/pets"));
            operations.add(readOne("getPetAtTheTop", HttpMethod.GET, "/{petId}", "petId"));
            operations.add(list("listTransfers", "/transfers"));
            operations.add(list("listProducts", "/products"));
            operations.add(readOne("getTransfer", HttpMethod.GET, "/transfers/{productId}",
                    "productId"));
            operations.add(list("listUsers", "/users"));
            operations.add(readOne("getUser", HttpMethod.GET, "/users/{username}", "username"));
            this.model = ApiModel.of("things", "1", operations);
            this.seen = new ObservedValues(model, settings);
            this.provider = new ObservedValueProvider(model, seen, new SplittableRandom(92L),
                    null);
        }

        void replies(String operation, int status, String body) {
            seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Interaction.answered(
                    TestCase.of(OperationId.of(operation), List.of()),
                    HttpRequestRecord.of(HttpMethod.GET, "https://api.example/"),
                    new HttpResponseRecord(StatusLine.of(status),
                            List.of(Header.of("Content-Type", "application/json")),
                            Optional.of(Payload.of(body.getBytes(StandardCharsets.UTF_8),
                                    "application/json"))),
                    Instant.EPOCH, Duration.ofMillis(3))));
        }

        Optional<GeneratedValue> offers(String operation, String gap, CanonicalSchema schema) {
            return provider.offer(ValueRequest.of(OperationId.of(operation), gap,
                    ParameterLocation.PATH, schema));
        }

        private static Operation list(String id, String path) {
            return Operation.of(HttpMethod.GET, path).withId(OperationId.of(id))
                    .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        }

        private static Operation readOne(String id, HttpMethod method, String path, String gap) {
            return Operation.of(method, path,
                            List.of(Parameter.of(gap, ParameterLocation.PATH, true, AN_INTEGER)))
                    .withId(OperationId.of(id))
                    .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));
        }
    }
}
