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
package io.restest.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.BodyContent;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.SecurityRequirement;
import io.restest.core.model.SecurityScheme;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.ChoiceSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaMetadata;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which key handed over goes with which operation, and where: every rule, on documents shaped like
 * the ones in the corpus that ask for keys.
 */
class CredentialPlanTest {

    /** A key in Base64's alphabet, whose middle no way of writing it ever changes. */
    static final String KEY = "Zk9-leakprobe-4f+q/Rw==";

    /** What every way of writing the key still contains. */
    static final String CORE = "leakprobe";

    private static final SecurityScheme.ApiKey IN_THE_HEADER =
            new SecurityScheme.ApiKey(ParameterLocation.HEADER, "api_key");

    @Nested
    @DisplayName("reading what was handed over")
    class Reading {

        @Test
        @DisplayName("a key on its own answers the document's one scheme, even one ending in =")
        void a_key_on_its_own_answers_the_only_scheme() {
            CredentialPlan.Found found = gather(petstore(), AuthGiven.typed(KEY, 1));

            assertThat(found.refusals()).isEmpty();
            assertThat(credentials(found, "getInventory"))
                    .containsExactly(new Credential(new Place(Place.Where.HEADER, "api_key"),
                            new Secret(KEY, "REDACTED-AUTH")));
        }

        @Test
        @DisplayName("a key for a scheme names it before an =, and the text in its place names it too")
        void a_key_for_a_scheme_names_it() {
            CredentialPlan.Found found = gather(twoSchemes(), AuthGiven.typed("other=" + KEY, 1));

            assertThat(found.refusals()).isEmpty();
            assertThat(credentials(found, "listPets"))
                    .containsExactly(new Credential(new Place(Place.Where.QUERY, "token"),
                            new Secret(KEY, "REDACTED-AUTH.other")));
        }

        @Test
        @DisplayName("a scheme named with a space, or in other capitals, is still the scheme meant")
        void names_are_read_the_way_people_write_them() {
            ApiModel dhl = model(Map.of("API Key",
                            new SecurityScheme.ApiKey(ParameterLocation.HEADER, "DHL-API-Key")),
                    Optional.of(requiring("API Key")), get("track", "/track"));

            CredentialPlan.Found spaced = gather(dhl, AuthGiven.typed("API Key=" + KEY, 1));
            CredentialPlan.Found capitals = gather(dhl, AuthGiven.typed("api key=" + KEY, 1));

            assertThat(credentials(spaced, "track")).extracting(Credential::secret)
                    .containsExactly(new Secret(KEY, "REDACTED-AUTH.API_Key"));
            assertThat(credentials(capitals, "track")).extracting(Credential::secret)
                    .containsExactly(new Secret(KEY, "REDACTED-AUTH.API_Key"));
        }

        @Test
        @DisplayName("of two scheme names a text begins with, the longer is the one it names")
        void the_longest_name_wins() {
            ApiModel model = model(Map.of(
                            "key", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-Short"),
                            "key=inner", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-Long")),
                    Optional.of(new SecurityRequirement(List.of(Set.of("key"), Set.of("key=inner")))),
                    get("listPets", "/pets"));

            CredentialPlan.Found found = gather(model, AuthGiven.typed("key=inner=" + KEY, 1));

            assertThat(credentials(found, "listPets")).extracting(Credential::place)
                    .containsExactly(new Place(Place.Where.HEADER, "X-Long"));
        }

        @Test
        @DisplayName("a key given with its place goes there, whatever capitals the place is written in")
        void a_key_with_its_place_goes_there() {
            ApiModel nothingDeclared = model(Map.of(), Optional.empty(), get("listPets", "/pets"));

            assertThat(credentials(gather(nothingDeclared,
                    AuthGiven.typed("HEADER:X-API-Key=" + KEY, 1)), "listPets"))
                    .containsExactly(new Credential(new Place(Place.Where.HEADER, "X-API-Key"),
                            new Secret(KEY, "REDACTED-AUTH.header.X-API-Key")));
            assertThat(credentials(gather(nothingDeclared,
                    AuthGiven.typed("query:api key=" + KEY, 1)), "listPets"))
                    .containsExactly(new Credential(new Place(Place.Where.QUERY, "api key"),
                            new Secret(KEY, "REDACTED-AUTH.query.api_key")));
            assertThat(credentials(gather(nothingDeclared,
                    AuthGiven.typed("cookie:sid=" + KEY, 1)), "listPets"))
                    .containsExactly(new Credential(new Place(Place.Where.COOKIE, "sid"),
                            new Secret(KEY, "REDACTED-AUTH.cookie.sid")));
        }

        @Test
        @DisplayName("one typed for the place a key in the environment goes wins over it, silently")
        void the_command_line_wins_over_the_environment() {
            CredentialPlan.Found found = gather(petstore(),
                    AuthGiven.fromTheEnvironment("from-the-environment"),
                    AuthGiven.typed("api_key=" + KEY, 1));

            assertThat(found.refusals()).isEmpty();
            assertThat(found.warnings()).isEmpty();
            assertThat(credentials(found, "getInventory")).extracting(Credential::secret)
                    .extracting(Secret::value).containsExactly(KEY);
        }

        @Test
        @DisplayName("a line break at the end of the variable, as a file read into it leaves, is not part of the key")
        void a_line_break_ending_the_variable_is_left_off() {
            for (String end : List.of("\n", "\r\n", "\n\n")) {
                CredentialPlan.Found found = gather(petstore(),
                        AuthGiven.fromTheEnvironment(KEY + end));

                assertThat(found.warnings()).isEmpty();
                assertThat(credentials(found, "getInventory")).extracting(Credential::secret)
                        .extracting(Secret::value).containsExactly(KEY);
            }
            assertThat(AuthGiven.fromTheEnvironment("\r\n").isEmpty()).isTrue();
            assertThat(gather(petstore(), AuthGiven.fromTheEnvironment("Zk9-leakprobe-4f\nmore"))
                    .warnings()).singleElement().asString().contains("a line break");
        }

        @Test
        @DisplayName("two keys whose names come out the same in the text put in their place are told apart")
        void no_two_keys_share_the_text_in_their_place() {
            ApiModel model = model(orderedSchemes(
                            "API Key", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-One"),
                            "API_Key", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-Two")),
                    Optional.of(new SecurityRequirement(List.of(Set.of("API Key", "API_Key")))),
                    get("track", "/track"));

            CredentialPlan.Found found = gather(model, AuthGiven.typed("API Key=" + KEY, 1),
                    AuthGiven.typed("API_Key=Qm7-leakprobe-8x+z/Pt==", 2));

            assertThat(credentials(found, "track")).extracting(Credential::secret)
                    .extracting(Secret::mask)
                    .containsExactlyInAnyOrder("REDACTED-AUTH.API_Key", "REDACTED-AUTH.API_Key.2");
            assertThat(found.plan().placed()).extracting(CredentialPlan.Placed::mask)
                    .containsExactly("REDACTED-AUTH.API_Key", "REDACTED-AUTH.API_Key.2");
        }

        @Test
        @DisplayName("a key in the environment that cannot be used is left out with a warning, not refused")
        void a_key_in_the_environment_only_warns() {
            ApiModel nothingDeclared = model(Map.of(), Optional.empty(), get("listPets", "/pets"));

            CredentialPlan.Found found = gather(nothingDeclared,
                    AuthGiven.fromTheEnvironment(KEY));

            assertThat(found.refusals()).isEmpty();
            assertThat(found.warnings()).singleElement().asString()
                    .startsWith("the key in RESTEST_AUTH is a key on its own")
                    .endsWith("the run goes on without it")
                    .doesNotContain(CORE);
            assertThat(found.plan().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("refusing what cannot be used")
    class Refusing {

        @Test
        @DisplayName("every refusal says why without repeating the key")
        void no_refusal_repeats_the_key() {
            ApiModel pet = petstore();
            ApiModel two = twoSchemes();
            ApiModel none = model(Map.of(), Optional.empty(), get("listPets", "/pets"));
            ApiModel withABearer = model(Map.of(
                            "bearer", new SecurityScheme.Http("bearer", Optional.empty()),
                            "oauth", new SecurityScheme.Other("oauth2"),
                            "broken", new SecurityScheme.Unreadable("it names no header"),
                            "badName", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X Key"),
                            "owned", new SecurityScheme.ApiKey(ParameterLocation.HEADER,
                                    "Content-Type")),
                    Optional.empty(), get("listPets", "/pets"));
            Map<String, CredentialPlan.Found> refused = new LinkedHashMap<>();
            refused.put("empty", gather(pet, AuthGiven.typed("", 1)));
            refused.put("on its own, no scheme", gather(none, AuthGiven.typed(KEY, 1)));
            refused.put("on its own, two schemes", gather(two, AuthGiven.typed(KEY, 1)));
            refused.put("a bearer", gather(withABearer, AuthGiven.typed("bearer=" + KEY, 1)));
            refused.put("oauth", gather(withABearer, AuthGiven.typed("oauth=" + KEY, 1)));
            refused.put("broken", gather(withABearer, AuthGiven.typed("broken=" + KEY, 1)));
            refused.put("no name for its header",
                    gather(withABearer, AuthGiven.typed("badName=" + KEY, 1)));
            refused.put("a header the client writes",
                    gather(withABearer, AuthGiven.typed("owned=" + KEY, 1)));
            refused.put("scheme and nothing after",
                    gather(pet, AuthGiven.typed("api_key=", 1)));
            refused.put("a place and no =",
                    gather(none, AuthGiven.typed("header:Zk9-leakprobe-4f", 1)));
            refused.put("a header, and a key without the name it goes under",
                    gather(none, AuthGiven.typed("header:" + KEY, 1)));
            refused.put("the query, and a key without the name it goes under",
                    gather(none, AuthGiven.typed("query:Zk9leakprobe4fqRw==", 1)));
            refused.put("a cookie, and a key without the name it goes under",
                    gather(none, AuthGiven.typed("cookie:Zk9leakprobe4fqRw==", 1)));
            refused.put("shorter than four characters", gather(pet, AuthGiven.typed("abc", 1)));
            refused.put("a scheme's key shorter than four characters",
                    gather(pet, AuthGiven.typed("api_key==", 1)));
            refused.put("a name before an = that is no scheme's",
                    gather(pet, AuthGiven.typed("apikey=" + KEY, 1)));
            refused.put("a name before an =, and no key declared",
                    gather(none, AuthGiven.typed("leakprobe=" + KEY, 1)));
            refused.put("a name before an = that is none of two schemes'",
                    gather(two, AuthGiven.typed("leakprobe=" + KEY, 1)));
            refused.put("a place and no name", gather(none, AuthGiven.typed("query:=" + KEY, 1)));
            refused.put("a place and no key", gather(none, AuthGiven.typed("cookie:sid=", 1)));
            refused.put("a line break", gather(pet, AuthGiven.typed(KEY + "\r", 1)));
            refused.put("not ASCII", gather(pet, AuthGiven.typed(KEY + "é", 1)));
            refused.put("a space at the end", gather(pet, AuthGiven.typed(KEY + " ", 1)));
            refused.put("a space at the start", gather(pet, AuthGiven.typed(" " + KEY, 1)));
            refused.put("a cookie with a semicolon",
                    gather(none, AuthGiven.typed("cookie:sid=" + KEY + ";x", 1)));
            refused.put("a header name that is no name",
                    gather(none, AuthGiven.typed("header:X Key=" + KEY, 1)));
            refused.put("the cookie header",
                    gather(none, AuthGiven.typed("header:Cookie=" + KEY, 1)));
            refused.put("following redirections", CredentialPlan.gather(
                    List.of(AuthGiven.typed(KEY, 1)), pet, true));
            refused.put("twice for one place", gather(pet, AuthGiven.typed(KEY, 1),
                    AuthGiven.typed("api_key=" + KEY + "x", 2)));
            refused.put("part of its own replacement", gather(pet, AuthGiven.typed("AUTH", 1)));

            refused.forEach((what, found) -> {
                assertThat(found.refusals()).describedAs(what).isNotEmpty();
                assertThat(found.refusals()).describedAs(what)
                        .allSatisfy(refusal -> assertThat(refusal).doesNotContain(CORE));
                assertThat(found.plan().isEmpty()).describedAs(what).isTrue();
            });
            assertThat(refused.get("on its own, two schemes").refusals()).singleElement()
                    .asString().contains("key, other").contains("--auth key=<key>");
            assertThat(refused.get("a bearer").refusals()).singleElement().asString()
                    .contains("HTTP bearer scheme");
            assertThat(refused.get("the cookie header").refusals()).singleElement().asString()
                    .contains("--auth cookie:<name>=<key>");
            assertThat(refused.get("following redirections").refusals()).singleElement()
                    .asString().contains("engine.followRedirects");
            assertThat(refused.get("the query, and a key without the name it goes under")
                    .refusals()).singleElement().asString().contains("nothing but '='")
                    .contains("query:<name>=<key>");
            assertThat(refused.get("shorter than four characters").refusals()).singleElement()
                    .asString().contains("shorter than 4 characters");
            assertThat(refused.get("a name before an = that is no scheme's").refusals())
                    .singleElement().asString().contains("no scheme of that name")
                    .contains("--auth api_key=<key>");
            assertThat(refused.get("a name before an = that is none of two schemes'").refusals())
                    .singleElement().asString().contains("no scheme of that name")
                    .contains("key, other");
            assertThat(refused.get("twice for one place").refusals()).singleElement().asString()
                    .isEqualTo("the key given with the second --auth goes in the header api_key, "
                            + "and so does the key given with --auth: one key for each place");
        }

        @Test
        @DisplayName("two keys typed for one header, in two capitalisations, are one too many")
        void one_header_in_two_capitalisations_is_one_place() {
            ApiModel model = model(Map.of("api_key", IN_THE_HEADER),
                    Optional.of(requiring("api_key")), get("listPets", "/pets"));

            CredentialPlan.Found found = gather(model, AuthGiven.typed(KEY, 1),
                    AuthGiven.typed("header:x-other=" + KEY + "2", 2),
                    AuthGiven.typed("header:X-OTHER=" + KEY + "3", 3));

            assertThat(found.refusals()).singleElement().asString()
                    .isEqualTo("the key given with the third --auth goes in the header X-OTHER, "
                            + "and so does the key given with the second --auth: one key for "
                            + "each place");
        }
    }

    @Nested
    @DisplayName("placing each key")
    class PlacingEachKey {

        @Test
        @DisplayName("the pet shop: the key goes where it is asked for, and fills the header deletePet declares")
        void the_pet_shop() {
            CredentialPlan.Found found = gather(petstore(), AuthGiven.typed(KEY, 1));
            CredentialPlan plan = found.plan();

            assertThat(operationsWithAKey(plan, petstore()))
                    .containsExactlyInAnyOrder("getPetById", "getInventory", "deletePet");
            assertThat(plan.placed()).singleElement().satisfies(placed -> {
                assertThat(placed.named()).isEqualTo("the key given with --auth");
                assertThat(placed.mask()).isEqualTo("REDACTED-AUTH");
                assertThat(placed.places())
                        .containsExactly(new Place(Place.Where.HEADER, "api_key"));
                assertThat(placed.operations()).extracting(OperationId::value)
                        .containsExactlyInAnyOrder("getPetById", "getInventory", "deletePet");
                assertThat(placed.whyWithNone()).isEmpty();
            });
            assertThat(plan.missing()).isEmpty();
            assertThat(plan.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("of the ways an operation offers, the first made of keys given is taken")
        void the_first_way_made_of_keys_given_is_taken() {
            ApiModel model = model(Map.of("api_key", IN_THE_HEADER,
                            "oauth", new SecurityScheme.Other("oauth2")),
                    Optional.empty(),
                    get("anybodyOrTheKey", "/a").withSecurity(new SecurityRequirement(
                            List.of(Set.of(), Set.of("api_key")))),
                    get("oauthThenTheKey", "/b").withSecurity(new SecurityRequirement(
                            List.of(Set.of("oauth"), Set.of("api_key")))),
                    get("theKeyAndOauth", "/c").withSecurity(new SecurityRequirement(
                            List.of(Set.of("api_key", "oauth")))),
                    get("undeclared", "/d").withSecurity(requiring("missing")));

            CredentialPlan plan = gather(model, AuthGiven.typed(KEY, 1)).plan();

            assertThat(operationsWithAKey(plan, model))
                    .containsExactlyInAnyOrder("anybodyOrTheKey", "oauthThenTheKey");
        }

        @Test
        @DisplayName("the document's own requirement applies unless an operation says otherwise")
        void the_document_wide_requirement_is_inherited() {
            ApiModel model = model(Map.of("api_key", IN_THE_HEADER),
                    Optional.of(requiring("api_key")),
                    get("inherits", "/a"),
                    get("asksForNothing", "/b").withSecurity(SecurityRequirement.none()));

            CredentialPlan plan = gather(model, AuthGiven.typed(KEY, 1)).plan();

            assertThat(operationsWithAKey(plan, model)).containsExactly("inherits");
        }

        @Test
        @DisplayName("a key the document declares and never asks for goes with every operation that does not say it asks for nothing")
        void a_key_asked_for_nowhere_goes_everywhere() {
            ApiModel bigOven = model(Map.of("api_key",
                            new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-BigOven-API-Key")),
                    Optional.empty(),
                    get("recipes", "/recipes"),
                    get("open", "/open").withSecurity(SecurityRequirement.none()));

            CredentialPlan plan = gather(bigOven, AuthGiven.typed(KEY, 1)).plan();

            assertThat(operationsWithAKey(plan, bigOven)).containsExactly("recipes");
        }

        @Test
        @DisplayName("LanguageTool: a key given with its place goes everywhere, and fills the query and form inputs of its name")
        void language_tool() {
            ApiModel model = languageTool();

            CredentialPlan plan = gather(model, AuthGiven.typed("query:apiKey=" + KEY, 1)).plan();

            Place query = new Place(Place.Where.QUERY, "apiKey");
            Place field = new Place(Place.Where.FORM_FIELD, "apiKey");
            assertThat(plan.forOperation(OperationId.of("check"))).extracting(Credential::place)
                    .containsExactly(query, field);
            assertThat(plan.forOperation(OperationId.of("words"))).extracting(Credential::place)
                    .containsExactly(query);
            assertThat(plan.forOperation(OperationId.of("addWord"))).extracting(Credential::place)
                    .containsExactly(query, field);
            assertThat(plan.placed()).singleElement().extracting(CredentialPlan.Placed::places)
                    .isEqualTo(List.of(query, field));
        }

        @Test
        @DisplayName("an input is filled only in the part of the request the key goes in")
        void an_input_is_filled_only_in_its_own_part() {
            ApiModel model = model(Map.of(), Optional.empty(),
                    get("search", "/search/{key}",
                            Parameter.of("key", ParameterLocation.PATH, true, StringSchema.of()),
                            Parameter.of("key", ParameterLocation.HEADER, false, StringSchema.of()),
                            Parameter.of("key", ParameterLocation.COOKIE, false, StringSchema.of()),
                            Parameter.of("Key", ParameterLocation.QUERY, false, StringSchema.of())));

            CredentialPlan plan = gather(model, AuthGiven.typed("query:key=" + KEY, 1)).plan();

            assertThat(plan.forOperation(OperationId.of("search"))).extracting(Credential::place)
                    .containsExactly(new Place(Place.Where.QUERY, "key"));
            assertThat(plan.modelToFillIn(model))
                    .describedAs("nothing of its name is in the query, so nothing is left out")
                    .isSameAs(model);

            CredentialPlan inTheHeader = gather(model,
                    AuthGiven.typed("header:KEY=" + KEY, 1)).plan();
            assertThat(inTheHeader.forOperation(OperationId.of("search")))
                    .extracting(Credential::place)
                    .containsExactly(new Place(Place.Where.HEADER, "KEY"));
            assertThat(inTheHeader.modelToFillIn(model).operation(OperationId.of("search"))
                    .orElseThrow().parameters())
                    .extracting(Parameter::name, Parameter::location)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("key", ParameterLocation.PATH),
                            org.assertj.core.groups.Tuple.tuple("key", ParameterLocation.COOKIE),
                            org.assertj.core.groups.Tuple.tuple("Key", ParameterLocation.QUERY));

            CredentialPlan inACookie = gather(model, AuthGiven.typed("cookie:key=" + KEY, 1))
                    .plan();
            assertThat(inACookie.forOperation(OperationId.of("search")))
                    .extracting(Credential::place)
                    .containsExactly(new Place(Place.Where.COOKIE, "key"));
        }

        @Test
        @DisplayName("a key given with the place a scheme's key goes counts as that scheme's key")
        void a_place_that_is_a_schemes_place_is_that_scheme() {
            CredentialPlan plan = gather(petstore(),
                    AuthGiven.typed("header:API_KEY=" + KEY, 1)).plan();

            assertThat(plan.missing()).isEmpty();
            assertThat(plan.forOperation(OperationId.of("getInventory")))
                    .extracting(Credential::place)
                    .containsExactly(new Place(Place.Where.HEADER, "API_KEY"));
        }

        @Test
        @DisplayName("a key that goes with no operation says why")
        void a_key_that_goes_nowhere_says_why() {
            ApiModel model = model(Map.of("api_key", IN_THE_HEADER,
                            "oauth", new SecurityScheme.Other("oauth2")),
                    Optional.of(new SecurityRequirement(List.of(Set.of("api_key", "oauth")))),
                    get("listPets", "/pets"));

            CredentialPlan plan = gather(model, AuthGiven.typed(KEY, 1)).plan();

            assertThat(plan.isEmpty()).describedAs("a key was handed over").isFalse();
            assertThat(plan.placed()).singleElement().satisfies(placed -> {
                assertThat(placed.operations()).isEmpty();
                assertThat(placed.whyWithNone()).hasValueSatisfying(why ->
                        assertThat(why).startsWith("every operation that asks for api_key"));
            });
        }
    }

    @Nested
    @DisplayName("what is missing")
    class WhatIsMissing {

        @Test
        @DisplayName("the operations asking for a key nobody gave are named, and only those")
        void the_operations_asking_are_named() {
            CredentialPlan plan = gather(petstore()).plan();

            assertThat(plan.isEmpty()).isTrue();
            assertThat(plan.missing()).singleElement().satisfies(missing -> {
                assertThat(missing.scheme()).isEqualTo("api_key");
                assertThat(missing.place()).isEqualTo(new Place(Place.Where.HEADER, "api_key"));
                assertThat(missing.operations()).extracting(OperationId::value)
                        .containsExactlyInAnyOrder("getPetById", "getInventory");
                assertThat(missing.askedForNowhere()).isFalse();
            });
        }

        @Test
        @DisplayName("an operation anybody may call, or one asking for more than keys, is not counted")
        void only_keys_that_would_help_are_missing() {
            ApiModel model = model(Map.of("api_key", IN_THE_HEADER,
                            "oauth", new SecurityScheme.Other("oauth2")),
                    Optional.empty(),
                    get("anybody", "/a").withSecurity(new SecurityRequirement(
                            List.of(Set.of(), Set.of("api_key")))),
                    get("withOauth", "/b").withSecurity(new SecurityRequirement(
                            List.of(Set.of("api_key", "oauth")))),
                    get("theKey", "/c").withSecurity(requiring("api_key")));

            assertThat(gather(model).plan().missing()).singleElement()
                    .extracting(CredentialPlan.Missing::operations)
                    .isEqualTo(Set.of(OperationId.of("theKey")));
        }

        @Test
        @DisplayName("a key declared and asked for nowhere is missing from every operation, until one such key is given")
        void a_key_asked_for_nowhere_is_missing_everywhere() {
            ApiModel two = model(orderedSchemes(
                            "first", new SecurityScheme.ApiKey(ParameterLocation.QUERY, "api_key"),
                            "second", new SecurityScheme.ApiKey(ParameterLocation.HEADER, "X-Key")),
                    Optional.empty(),
                    get("recipes", "/recipes"),
                    get("open", "/open").withSecurity(SecurityRequirement.none()));

            List<CredentialPlan.Missing> missing = gather(two).plan().missing();
            assertThat(missing).extracting(CredentialPlan.Missing::scheme)
                    .containsExactly("first", "second");
            assertThat(missing).allSatisfy(each -> {
                assertThat(each.askedForNowhere()).isTrue();
                assertThat(each.operations()).containsExactly(OperationId.of("recipes"));
            });
            assertThat(gather(two, AuthGiven.typed("first=" + KEY, 1)).plan().missing())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the model the values are invented from")
    class TheModelToFillIn {

        @Test
        @DisplayName("with no key, it is the very same model")
        void with_no_key_it_is_the_same_model() {
            ApiModel model = languageTool();

            assertThat(gather(model).plan().modelToFillIn(model)).isSameAs(model);
            assertThat(CredentialPlan.none().modelToFillIn(model)).isSameAs(model);
            assertThat(CredentialPlan.none().isEmpty()).isTrue();
            assertThat(CredentialPlan.none().placed()).isEmpty();
            assertThat(CredentialPlan.none().missing()).isEmpty();
        }

        @Test
        @DisplayName("an input the key fills is left out, and every other operation stays as it was")
        void a_filled_input_is_left_out() {
            ApiModel model = petstore();
            ApiModel trimmed = gather(model, AuthGiven.typed(KEY, 1)).plan().modelToFillIn(model);

            assertThat(trimmed.operation(OperationId.of("deletePet")).orElseThrow().parameters())
                    .extracting(Parameter::name).containsExactly("petId");
            assertThat(trimmed.operation(OperationId.of("getInventory")))
                    .containsSame(model.operation(OperationId.of("getInventory")).orElseThrow());
            assertThat(trimmed.securitySchemes()).isEqualTo(model.securitySchemes());
        }

        @Test
        @DisplayName("a form's field is left out of its shape, its samples and its limits")
        void a_forms_field_is_left_out_everywhere() {
            ApiModel model = languageTool();
            ApiModel trimmed = gather(model, AuthGiven.typed("query:apiKey=" + KEY, 1)).plan()
                    .modelToFillIn(model);

            BodyContent form = trimmed.operation(OperationId.of("addWord")).orElseThrow()
                    .requestBody().orElseThrow().content().get(ModelToFillIn.FORM);
            ObjectSchema shape = (ObjectSchema) form.schema();
            assertThat(shape.properties()).containsOnlyKeys("word");
            assertThat(shape.required()).containsExactly("word");
            assertThat(shape.minProperties()).contains(0);
            assertThat(shape.maxProperties()).contains(1);
            assertThat(shape.metadata().examples())
                    .containsExactly(JsonValue.object(Map.of("word", JsonValue.of("a"))));
            assertThat(shape.metadata().defaultValue())
                    .contains(JsonValue.object(Map.of("word", JsonValue.of("b"))));
            assertThat(shape.metadata().enumeration()).containsExactly(JsonValue.of("unchanged"));
            assertThat(form.examples())
                    .containsExactly(JsonValue.object(Map.of("word", JsonValue.of("c"))));
            assertThat(trimmed.operation(OperationId.of("words")).orElseThrow().parameters())
                    .extracting(Parameter::name).containsExactly("username");
        }

        @Test
        @DisplayName("a form whose shape is named, or offers a choice, loses the field in every shape it may take")
        void named_and_chosen_shapes_lose_the_field() {
            ObjectSchema named = ObjectSchema.of(fields("apiKey", "word"), Set.of("apiKey"));
            ObjectSchema other = ObjectSchema.of(fields("apiKey", "text"), Set.of());
            ApiModel model = model(Map.of(), Optional.empty(),
                    post("byName", "/a", SchemaReference.to("Form")),
                    post("byChoice", "/b", ChoiceSchema.of(List.of(SchemaReference.to("Form"),
                            other))),
                    post("loop", "/c", SchemaReference.to("Loop")))
                    .withSchemas(Map.of("Form", named, "Loop", SchemaReference.to("Loop")));

            ApiModel trimmed = gather(model, AuthGiven.typed("query:apiKey=" + KEY, 1)).plan()
                    .modelToFillIn(model);

            assertThat(formOf(trimmed, "byName")).isInstanceOfSatisfying(ObjectSchema.class,
                    shape -> assertThat(shape.properties()).containsOnlyKeys("word"));
            assertThat(formOf(trimmed, "byChoice")).isInstanceOfSatisfying(ChoiceSchema.class,
                    choice -> assertThat(choice.alternatives()).allSatisfy(alternative ->
                            assertThat(((ObjectSchema) alternative).properties())
                                    .doesNotContainKey("apiKey")));
            assertThat(formOf(trimmed, "loop")).isEqualTo(SchemaReference.to("Loop"));
            assertThat(trimmed.schema("Form")).contains(named);
        }
    }

    @Nested
    @DisplayName("the model the values are invented from, for a form whose shapes lead back to one another")
    class ShapesThatLeadBack {

        @Test
        @DisplayName("each named shape is copied once, not once for every way round")
        void each_named_shape_is_copied_once() {
            // Every shape a choice among the others and the form itself: followed afresh each time,
            // every way round would multiply the work by four, over and over. Three shapes rather
            // than four already take seconds that way.
            ObjectSchema theFields = ObjectSchema.of(fields("apiKey", "word"), Set.of("apiKey"));
            Map<String, CanonicalSchema> shapes = new LinkedHashMap<>();
            shapes.put("Form", ChoiceSchema.of(List.of(SchemaReference.to("A"),
                    SchemaReference.to("B"), SchemaReference.to("C"), SchemaReference.to("D"),
                    theFields)));
            for (String name : List.of("A", "B", "C", "D")) {
                List<CanonicalSchema> others = new ArrayList<>();
                for (String other : List.of("A", "B", "C", "D", "Form")) {
                    if (!other.equals(name)) {
                        others.add(SchemaReference.to(other));
                    }
                }
                shapes.put(name, ChoiceSchema.of(others));
            }
            ApiModel model = model(Map.of(), Optional.empty(),
                    post("addWord", "/words", SchemaReference.to("Form"))).withSchemas(shapes);

            ApiModel trimmed = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    gather(model, AuthGiven.typed("query:apiKey=" + KEY, 1)).plan()
                            .modelToFillIn(model));

            assertThat(formOf(trimmed, "addWord")).isInstanceOfSatisfying(ChoiceSchema.class,
                    choice -> assertThat(choice.alternatives().get(4))
                            .isInstanceOfSatisfying(ObjectSchema.class, shape ->
                                    assertThat(shape.properties()).containsOnlyKeys("word")));
        }
    }

    @Test
    @DisplayName("nothing handed over, and nothing asked for, is no plan at all")
    void nothing_is_no_plan() {
        CredentialPlan.Found found = gather(model(Map.of(), Optional.empty(),
                get("listPets", "/pets")));

        assertThat(found.refusals()).isEmpty();
        assertThat(found.warnings()).isEmpty();
        assertThat(found.plan().isEmpty()).isTrue();
        assertThat(found.plan().forOperation(OperationId.of("listPets"))).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> AuthGiven.typed(KEY, 0));
    }

    // --- Documents shaped like the corpus's ------------------------------------------------------

    /** The pet shop: its key asked for by two operations, and declared as a header by a third. */
    static ApiModel petstore() {
        Parameter petId = Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of());
        return model(new LinkedHashMap<>(Map.of("api_key", IN_THE_HEADER)),
                Optional.empty(),
                get("getPetById", "/pet/{petId}", petId).withSecurity(requiring("api_key")),
                get("getInventory", "/store/inventory").withSecurity(requiring("api_key")),
                post("addPet", "/pet", StringSchema.of()).withSecurity(requiring("petstore_auth")),
                Operation.of(HttpMethod.DELETE, "/pet/{petId}", List.of(petId,
                                Parameter.of("api_key", ParameterLocation.HEADER, false,
                                        StringSchema.of())))
                        .withId(OperationId.of("deletePet"))
                        .withSecurity(requiring("petstore_auth")),
                get("listUsers", "/users"),
                get("health", "/health").withSecurity(SecurityRequirement.none()))
                .withSecurity(orderedSchemes("api_key", IN_THE_HEADER,
                        "petstore_auth", new SecurityScheme.Other("oauth2")), Optional.empty());
    }

    /** Two keys the document declares, one in a header and one in the query. */
    static ApiModel twoSchemes() {
        return model(orderedSchemes("key", IN_THE_HEADER,
                        "other", new SecurityScheme.ApiKey(ParameterLocation.QUERY, "token")),
                Optional.of(new SecurityRequirement(List.of(Set.of("other")))),
                get("listPets", "/pets"));
    }

    /** LanguageTool: no scheme, and its key asked for as a field of a form and a query parameter. */
    static ApiModel languageTool() {
        SchemaMetadata samples = SchemaMetadata.none()
                .withExamples(List.of(JsonValue.object(orderedMembers("word", "a", "apiKey", "x"))))
                .withDefault(JsonValue.object(orderedMembers("word", "b", "apiKey", "y")))
                .withEnumeration(List.of(JsonValue.of("unchanged")));
        ObjectSchema words = new ObjectSchema(samples, fields("word", "apiKey"),
                Set.of("word", "apiKey"), Optional.empty(), Optional.of(1), Optional.of(2));
        ObjectSchema check = ObjectSchema.of(fields("language", "apiKey"), Set.of("language"));
        Operation addWord = Operation.of(HttpMethod.POST, "/words/add")
                .withId(OperationId.of("addWord"))
                .withRequestBody(new RequestBodyModel(true, Map.of(ModelToFillIn.FORM,
                        new BodyContent(words, List.of(JsonValue.object(
                                orderedMembers("word", "c", "apiKey", "z"))))), Optional.empty()));
        Operation checkText = Operation.of(HttpMethod.POST, "/check")
                .withId(OperationId.of("check"))
                .withRequestBody(new RequestBodyModel(true,
                        Map.of(ModelToFillIn.FORM, BodyContent.of(check)), Optional.empty()));
        Operation wordsList = get("words", "/words",
                Parameter.of("username", ParameterLocation.QUERY, true, StringSchema.of()),
                Parameter.of("apiKey", ParameterLocation.QUERY, true, StringSchema.of()));
        return model(Map.of(), Optional.empty(), checkText, wordsList, addWord);
    }

    // --- Helpers ---------------------------------------------------------------------------------

    static CredentialPlan.Found gather(ApiModel model, AuthGiven... given) {
        return CredentialPlan.gather(List.of(given), model, false);
    }

    static List<Credential> credentials(CredentialPlan.Found found, String operation) {
        assertThat(found.refusals()).isEmpty();
        return found.plan().forOperation(OperationId.of(operation));
    }

    static List<String> operationsWithAKey(CredentialPlan plan, ApiModel model) {
        List<String> with = new ArrayList<>();
        for (Operation operation : model.operations()) {
            if (!plan.forOperation(operation.id()).isEmpty()) {
                with.add(operation.id().value());
            }
        }
        return with;
    }

    static ApiModel model(Map<String, SecurityScheme> schemes,
            Optional<SecurityRequirement> security, Operation... operations) {
        return ApiModel.of("t", "1", List.of(operations)).withSecurity(schemes, security);
    }

    static Operation get(String id, String path, Parameter... parameters) {
        return Operation.of(HttpMethod.GET, path, List.of(parameters)).withId(OperationId.of(id));
    }

    static Operation post(String id, String path, CanonicalSchema form) {
        return Operation.of(HttpMethod.POST, path).withId(OperationId.of(id))
                .withRequestBody(new RequestBodyModel(true,
                        Map.of(ModelToFillIn.FORM, BodyContent.of(form)), Optional.empty()));
    }

    static SecurityRequirement requiring(String scheme) {
        return new SecurityRequirement(List.of(Set.of(scheme)));
    }

    private static Map<String, SecurityScheme> orderedSchemes(String first, SecurityScheme one,
            String second, SecurityScheme other) {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put(first, one);
        schemes.put(second, other);
        return schemes;
    }

    private static Map<String, CanonicalSchema> fields(String... names) {
        Map<String, CanonicalSchema> fields = new LinkedHashMap<>();
        for (String name : names) {
            fields.put(name, StringSchema.of());
        }
        return fields;
    }

    private static Map<String, JsonValue> orderedMembers(String first, String one, String second,
            String other) {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put(first, JsonValue.of(one));
        members.put(second, JsonValue.of(other));
        return members;
    }

    private static CanonicalSchema formOf(ApiModel model, String operation) {
        return model.operation(OperationId.of(operation)).orElseThrow().requestBody().orElseThrow()
                .content().get(ModelToFillIn.FORM).schema();
    }
}
