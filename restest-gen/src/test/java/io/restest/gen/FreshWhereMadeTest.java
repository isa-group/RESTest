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
import io.restest.core.model.RequestBodyModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.GenerationSettings;
import io.restest.core.settings.Settings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Filling the body of a request that makes something with values of its own, rather than with
 * copies of what the API already holds.
 *
 * <p>Inside a body, invention asks the better-informed sources first, and the memory of what the
 * API returned is one of them. For a registration that means the user name or the e-mail address
 * of somebody the API already knows, and the API refuses it as a duplicate. So, in the body of a
 * {@code POST} and as often as the settings say, what the API returned is passed over and the
 * property is invented - except an identifier, which names the existing thing the new one belongs
 * to. These tests are about which properties that touches, which requests, and that a run with it
 * switched off draws exactly the numbers it drew before.
 */
class FreshWhereMadeTest {

    private static final OperationId ADD_USER = OperationId.of("addUser");
    private static final OperationId REPLACE_USER = OperationId.of("replaceUser");
    private static final OperationId LIST_USERS = OperationId.of("listUsers");

    /** An address somebody the API already holds is registered under. */
    private static final JsonValue ANA = JsonValue.of("ana@example.org");

    /** The owner she belongs to, which exists. */
    private static final JsonValue HER_OWNER = JsonValue.of(7);

    /**
     * A user as a registration sends one: an address, the owner it belongs to, and the teams it
     * joins. Every property is required, so every body carries every one of them.
     */
    private static final ObjectSchema USER = ObjectSchema.of(inOrder(
            "email", StringSchema.of(),
            "ownerId", NumberSchema.of(NumberKind.INTEGER),
            "teamIds", ArraySchema.of(NumberSchema.of(NumberKind.INTEGER))),
            Set.of("email", "ownerId", "teamIds"));

    /** Something holding nothing but an address, wherever in a request it is. */
    private static final ObjectSchema ONLY_AN_ADDRESS =
            ObjectSchema.of(inOrder("email", StringSchema.of()), Set.of("email"));

    /** Somewhere to register users, somewhere to replace one, and somewhere to list them. */
    private static final ApiModel USERS = ApiModel.of("users", "1", List.of(
            Operation.of(HttpMethod.POST, "/users").withId(ADD_USER)
                    .withRequestBody(RequestBodyModel.json(USER, true)),
            Operation.of(HttpMethod.PUT, "/users").withId(REPLACE_USER)
                    .withRequestBody(RequestBodyModel.json(USER, true)),
            Operation.of(HttpMethod.GET, "/users").withId(LIST_USERS)));

    @Test
    @DisplayName("drawn to be made afresh, a POST's body invents a property that is not an "
            + "identifier, rather than copying the value the API returned for it")
    void a_creation_invents_what_the_api_returned() {
        RandomValueProvider afresh = inventing(fresh(true, 1.0), rememberingAna(), 3L);

        for (int draw = 0; draw < 20; draw++) {
            assertThat(body(afresh, ADD_USER).members().get("email"))
                    .describedAs("the address of somebody the API already holds gets the "
                            + "registration refused as a duplicate")
                    .isNotEqualTo(ANA);
        }
    }

    @Test
    @DisplayName("but an identifier is still given what the API returned, because it names the "
            + "thing the new one belongs to")
    void an_identifier_still_names_what_exists() {
        RandomValueProvider afresh = inventing(fresh(true, 1.0), rememberingAna(), 5L);

        for (int draw = 0; draw < 20; draw++) {
            assertThat(body(afresh, ADD_USER).members().get("ownerId")).isEqualTo(HER_OWNER);
        }
    }

    @Test
    @DisplayName("and so is a list of identifiers, element by element")
    void several_identifiers_still_name_what_exists() {
        ValueProvider teamsThatExist = request -> request.name().equals("teamIds")
                && request.schema() instanceof NumberSchema
                        ? Optional.of(returned(JsonValue.of(12))) : Optional.empty();
        RandomValueProvider afresh = inventing(fresh(true, 1.0), teamsThatExist, 7L);

        JsonValue teams = body(afresh, ADD_USER).members().get("teamIds");

        assertThat(((JsonValue.JsonArray) teams).elements()).isNotEmpty()
                .allSatisfy(team -> assertThat(team).isEqualTo(JsonValue.of(12)));
    }

    @Test
    @DisplayName("the same body sent to replace a user, rather than to make one, keeps what the "
            + "API returned")
    void a_replacement_keeps_what_the_api_returned() {
        RandomValueProvider afresh = inventing(fresh(true, 1.0), rememberingAna(), 9L);

        for (int draw = 0; draw < 20; draw++) {
            assertThat(body(afresh, REPLACE_USER).members().get("email")).isEqualTo(ANA);
        }
    }

    @Test
    @DisplayName("a value no reply carried - a list somebody handed over, a sample, a default - "
            + "is sent as it always was, since it is not a copy of something the API holds")
    void every_other_source_is_untouched() {
        JsonValue fromAList = JsonValue.of("someone@example.net");
        ValueProvider aList = request -> request.name().equals("email")
                ? Optional.of(GeneratedValue.generatedBy(fromAList, "given"))
                : Optional.empty();
        RandomValueProvider afresh = inventing(fresh(true, 1.0), aList, 11L);

        for (int draw = 0; draw < 20; draw++) {
            assertThat(body(afresh, ADD_USER).members().get("email")).isEqualTo(fromAList);
        }
    }

    @Test
    @DisplayName("a parameter of a POST is not its body, even one called body, and keeps what the "
            + "API returned")
    void a_parameter_is_not_a_body() {
        RandomValueProvider afresh = inventing(fresh(true, 1.0), rememberingAna(), 13L);

        for (int draw = 0; draw < 20; draw++) {
            JsonValue asked = afresh.offer(ValueRequest.of(ADD_USER, ValueRequest.THE_BODY,
                    ParameterLocation.QUERY, ONLY_AN_ADDRESS)).orElseThrow().value();
            assertThat(((JsonValue.JsonObject) asked).members().get("email")).isEqualTo(ANA);
        }
    }

    @Test
    @DisplayName("nor is one part of a body asked about on its own, as when one value of a thing "
            + "the API returned is being replaced: the body it belongs to was decided elsewhere")
    void a_part_of_a_body_is_not_a_body() {
        RandomValueProvider afresh = inventing(fresh(true, 1.0), rememberingAna(), 15L);

        for (int draw = 0; draw < 20; draw++) {
            JsonValue asked = afresh.offer(new ValueRequest(ADD_USER, "contact", "body.contact",
                    ParameterLocation.BODY, ONLY_AN_ADDRESS, List.of(), Optional.empty()))
                    .orElseThrow().value();
            assertThat(((JsonValue.JsonObject) asked).members().get("email")).isEqualTo(ANA);
        }
    }

    @Test
    @DisplayName("how often is decided once for each body, as often as the settings say")
    void the_chance_is_honoured() {
        RandomValueProvider sometimes = inventing(fresh(true, 0.3), rememberingAna(), 17L);

        long afresh = IntStream.range(0, 400)
                .mapToObj(ignored -> body(sometimes, ADD_USER).members().get("email"))
                .filter(email -> !email.equals(ANA))
                .count();

        assertThat(afresh).isBetween(80L, 160L);
    }

    @Test
    @DisplayName("switched off, invention draws exactly what it drew before, number for number")
    void off_is_as_before() {
        // Nothing is drawn for the body of a PUT, so it stands for a run from before this existed.
        // A body that makes something with the setting off has to be built from the very same
        // numbers, and the same body with the setting on and never filled afresh has not: the one
        // draw that decides it is enough to tell them apart.
        RandomValueProvider off = inventing(fresh(false, 1.0), knowingAna(), 19L);
        RandomValueProvider asBefore = inventing(fresh(true, 1.0), knowingAna(), 19L);
        RandomValueProvider onButNever = inventing(fresh(true, 0.0), knowingAna(), 19L);

        boolean drawnDifferently = false;
        for (int draw = 0; draw < 30; draw++) {
            JsonValue.JsonObject made = body(off, ADD_USER);
            assertThat(made).isEqualTo(body(asBefore, REPLACE_USER));
            assertThat(made.members().get("email")).isEqualTo(ANA);
            drawnDifferently |= !made.equals(body(onButNever, ADD_USER));
        }
        assertThat(drawnDifferently)
                .describedAs("one draw more ought to show in what is invented; if it did not, "
                        + "this test could not tell a run that draws one from one that does not")
                .isTrue();
    }

    @Test
    @DisplayName("on unless somebody says otherwise, three bodies in ten")
    void on_by_default() {
        GenerationSettings defaults = GenerationSettings.defaults();

        assertThat(defaults.freshWhereMade()).isTrue();
        assertThat(defaults.freshWhereMadeChance()).isEqualTo(0.3);
        assertThat(Settings.from(Map.of("generation.freshWhereMade", "false"))
                .generation().freshWhereMade()).isFalse();
    }

    /** The body invention builds for this operation, every property of {@link #USER} in it. */
    private static JsonValue.JsonObject body(RandomValueProvider inventing,
            OperationId operation) {
        return (JsonValue.JsonObject) inventing.offer(new ValueRequest(operation,
                ValueRequest.THE_BODY, ValueRequest.THE_BODY, ParameterLocation.BODY, USER,
                List.of(), Optional.empty())).orElseThrow().value();
    }

    private static RandomValueProvider inventing(GenerationSettings settings, ValueProvider inside,
            long seed) {
        return new RandomValueProvider(USERS, new SplittableRandom(seed), inside, settings);
    }

    /**
     * The memory of a run that has listed the users once and been shown Ana: her address, and her
     * owner's identifier.
     */
    private static ValueProvider rememberingAna() {
        ObservedValues seen = new ObservedValues(USERS);
        seen.on(new RunEvent.InteractionCompleted(Instant.EPOCH, Interaction.answered(
                TestCase.of(LIST_USERS, List.of()),
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/users"),
                new HttpResponseRecord(StatusLine.of(200),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.of(
                                "[{\"email\": \"ana@example.org\", \"ownerId\": 7}]"
                                        .getBytes(StandardCharsets.UTF_8), "application/json"))),
                Instant.EPOCH, Duration.ofMillis(3))));
        return new ObservedValueProvider(USERS, seen, new SplittableRandom(1L), null);
    }

    /**
     * The same two values, answered without drawing anything, so that every number a test sees
     * drawn is one invention drew.
     */
    private static ValueProvider knowingAna() {
        return request -> switch (request.name()) {
            case "email" -> Optional.of(returned(ANA));
            case "ownerId" -> Optional.of(returned(HER_OWNER));
            default -> Optional.empty();
        };
    }

    private static GeneratedValue returned(JsonValue value) {
        return new GeneratedValue(value, new ValueOrigin.Derived(InteractionId.of("listed"),
                "a value an earlier reply returned"));
    }

    private static GenerationSettings fresh(boolean on, double chance) {
        GenerationSettings d = GenerationSettings.defaults();
        return new GenerationSettings(d.optionalNestingDepth(), d.hardNestingDepth(),
                d.usualLongestString(), d.longestString(), d.lowestNumber(), d.roomAboveIt(),
                d.decimalPlaces(), d.usualMostItems(), d.mostItems(),
                d.optionalPropertyChance(), d.optionalBodyChance(),
                d.optionalParameterContinueChance(), d.optionalParametersBySize(),
                d.nullInOneIn(), d.uniqueAttempts(), d.sendableAttempts(),
                d.writableBodyAttempts(), d.impliedFormats(), d.impliedFormatChance(),
                d.omitHalProperties(), on, chance);
    }

    /** Properties in the order written, so that they are invented in that order on every run. */
    private static Map<String, CanonicalSchema> inOrder(Object... namesAndShapes) {
        Map<String, CanonicalSchema> properties = new LinkedHashMap<>();
        for (int at = 0; at < namesAndShapes.length; at += 2) {
            properties.put((String) namesAndShapes[at], (CanonicalSchema) namesAndShapes[at + 1]);
        }
        return properties;
    }
}
