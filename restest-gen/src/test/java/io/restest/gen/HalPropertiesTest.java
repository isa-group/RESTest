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
import io.restest.core.event.RunListener;
import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonText;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.SchemaReference;
import io.restest.core.schema.StringSchema;
import io.restest.core.settings.Settings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Sending a body without the two properties HAL keeps for what a server writes.
 *
 * <p>market describes {@code _links} as a list of links, and reads it as HAL's object keyed by the
 * kind of link: a request carrying one as described is answered with an error before anything else
 * in it is looked at. These tests are about the two names being gone from every body, however it
 * was made and however deep they were, and about {@code links} - an ordinary property - staying.
 */
class HalPropertiesTest {

    private static final long SEED = 20261003L;

    /** A link, as market describes one. */
    private static final ObjectSchema LINK = ObjectSchema.of(Map.of(
            "rel", StringSchema.of(), "href", StringSchema.of()), Set.of("rel", "href"));

    /**
     * A market as a document like market's describes one: links to other things, things carried
     * inside it, a seller with links of its own, and stalls that each have theirs. Every property
     * is required, so that every invented market carries every one of them.
     */
    private static final ObjectSchema MARKET = ObjectSchema.of(Map.of(
            "name", StringSchema.of(),
            "links", ArraySchema.of(StringSchema.of()),
            "_links", ArraySchema.of(LINK),
            "_embedded", ObjectSchema.of(Map.of("owner", StringSchema.of()), Set.of("owner")),
            "seller", ObjectSchema.of(Map.of(
                    "name", StringSchema.of(),
                    "_links", ArraySchema.of(LINK)), Set.of("name", "_links")),
            "stalls", ArraySchema.of(ObjectSchema.of(Map.of(
                    "number", NumberSchema.of(NumberKind.INTEGER),
                    "_links", ArraySchema.of(LINK)), Set.of("number", "_links")))),
            Set.of("name", "links", "_links", "_embedded", "seller", "stalls"));

    private static final Operation CREATE_MARKET = Operation.of(HttpMethod.POST, "/markets")
            .withId(OperationId.of("createMarket"))
            .withRequestBody(RequestBodyModel.json(SchemaReference.to("Market"), true));

    private static final Operation GET_MARKET = Operation.of(HttpMethod.GET, "/markets/{id}",
                    List.of(Parameter.of("id", ParameterLocation.PATH, true,
                            NumberSchema.of(NumberKind.INTEGER))))
            .withId(OperationId.of("getMarket"))
            .withResponses(List.of(ResponseModel.json("200", SchemaReference.to("Market"))));

    private static final ApiModel API = ApiModel.of("market", "1", List.of(CREATE_MARKET,
            GET_MARKET)).withSchemas(Map.of("Market", MARKET));

    /** What market's server writes back: a market with HAL's own properties in it. */
    private static final String A_MARKET_THE_API_RETURNED = """
            {"name": "Triana", "links": ["https://example.org"],
             "_links": [{"rel": "self", "href": "/markets/1"}],
             "_embedded": {"owner": "Ana"},
             "seller": {"name": "Luis", "_links": [{"rel": "self", "href": "/sellers/2"}]},
             "stalls": [{"number": 4, "_links": [{"rel": "self", "href": "/stalls/4"}]}]}""";

    @Nested
    @DisplayName("taking them out of a value")
    class TakingThemOut {

        @Test
        @DisplayName("takes both out at the top, inside objects and inside the elements of lists")
        void at_every_depth() {
            JsonValue market = JsonText.read(A_MARKET_THE_API_RETURNED);

            JsonValue sent = HalProperties.withoutTheServersOwn(market);

            assertThat(namesIn(sent)).doesNotContain("_links", "_embedded")
                    .contains("name", "links", "seller", "stalls", "number");
            assertThat(sent).isEqualTo(JsonText.read("""
                    {"name": "Triana", "links": ["https://example.org"],
                     "seller": {"name": "Luis"}, "stalls": [{"number": 4}]}"""));
        }

        @Test
        @DisplayName("hands back the very value it was given when neither is there")
        void the_same_value_when_there_is_nothing_to_take_out() {
            JsonValue clean = JsonText.read("""
                    {"links": [{"rel": "self"}], "items": [1, {"a": "b"}], "links_": "x"}""");

            assertThat(HalProperties.withoutTheServersOwn(clean)).isSameAs(clean);
            assertThat(HalProperties.withoutTheServersOwn(JsonValue.of("_links")))
                    .describedAs("a word that happens to read _links is a word")
                    .isEqualTo(JsonValue.of("_links"));
        }
    }

    @Nested
    @DisplayName("a body the tool sends")
    class ABodyTheToolSends {

        @Test
        @DisplayName("invented, carries neither, at any depth, and keeps links")
        void invented() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                    planOf(Campaign.Builtin.RANDOM), Settings.defaults());

            for (int draw = 0; draw < 30; draw++) {
                JsonValue body = generator.generate(CREATE_MARKET).orElseThrow().body()
                        .orElseThrow().value();
                assertThat(namesIn(body)).doesNotContain("_links", "_embedded")
                        .contains("name", "links", "seller", "stalls");
            }
        }

        @Test
        @DisplayName("invented with the setting off, carries both, as it did before")
        void invented_with_the_setting_off() {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                    planOf(Campaign.Builtin.RANDOM),
                    Settings.from(Map.of("generation.omitHalProperties", "false")));

            JsonValue body = generator.generate(CREATE_MARKET).orElseThrow().body()
                    .orElseThrow().value();

            assertThat(((JsonValue.JsonObject) body).members()).containsKeys("_links",
                    "_embedded");
            assertThat(((JsonValue.JsonObject) ((JsonValue.JsonObject) body).members()
                    .get("seller")).members()).containsKey("_links");
        }

        @Test
        @DisplayName("sent back from a reply, carries neither, though the reply had both")
        void sent_back_from_a_reply() {
            List<BodyValue> sentBack = sentBackFromAReply(Settings.defaults());

            assertThat(sentBack).isNotEmpty().allSatisfy(body ->
                    assertThat(namesIn(body.value())).doesNotContain("_links", "_embedded")
                            .contains("name", "links", "seller", "stalls"));
        }

        @Test
        @DisplayName("sent back from a reply with the setting off, carries both, as it did before")
        void sent_back_from_a_reply_with_the_setting_off() {
            List<BodyValue> sentBack = sentBackFromAReply(
                    Settings.from(Map.of("generation.omitHalProperties", "false")));

            assertThat(sentBack).isNotEmpty().allSatisfy(body ->
                    assertThat(namesIn(body.value())).contains("_links", "_embedded"));
        }

        @Test
        @DisplayName("changed from one the API accepted, carries neither, since the one accepted "
                + "did not")
        void changed_from_one_the_api_accepted() {
            Campaign onlyChanges = new Campaign(List.of(new Campaign.PlannedStrategy("mutation",
                    100, List.of(new Campaign.Entry.Single(
                            new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))), true)),
                    WhichOperations.everything());
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                    onlyChanges, Settings.defaults());
            TestCase accepted = generator.generate(CREATE_MARKET).orElseThrow();
            tell(generator, accepted, 201, "{}");

            int changed = 0;
            for (int draw = 0; draw < 100; draw++) {
                TestCase built = generator.generate(CREATE_MARKET).orElseThrow();
                changed += built.mutation().isPresent() ? 1 : 0;
                built.body().ifPresent(body -> {
                    assertThat(namesIn(body.value())).doesNotContain("_links", "_embedded");
                    assertThat(body.sentAs().orElse(""))
                            .describedAs("nor does a body sent as text that is not JSON")
                            .doesNotContain("_links", "_embedded");
                });
            }
            assertThat(changed).describedAs("requests made by changing the accepted one")
                    .isPositive();
        }

        /**
         * The bodies a plan asking what the API returned first sends to the creation, once the API
         * has returned a market with HAL's properties in it.
         */
        private static List<BodyValue> sentBackFromAReply(Settings settings) {
            RandomTestCaseGenerator generator = new RandomTestCaseGenerator(API, SEED, List.of(),
                    planOf(Campaign.Builtin.OBSERVED, Campaign.Builtin.RANDOM), settings);
            tell(generator, TestCase.of(GET_MARKET.id(), List.of()), 200,
                    A_MARKET_THE_API_RETURNED);

            List<BodyValue> sentBack = new ArrayList<>();
            for (int draw = 0; draw < 30; draw++) {
                BodyValue body = generator.generate(CREATE_MARKET).orElseThrow().body()
                        .orElseThrow();
                if (body.origin() instanceof ValueOrigin.Derived) {
                    sentBack.add(body);
                }
            }
            return sentBack;
        }
    }

    /** Every name of every member of every object in a value, however deep. */
    private static Set<String> namesIn(JsonValue value) {
        Set<String> names = new LinkedHashSet<>();
        switch (value) {
            case JsonValue.JsonObject thing -> thing.members().forEach((name, inside) -> {
                names.add(name);
                names.addAll(namesIn(inside));
            });
            case JsonValue.JsonArray list -> list.elements().forEach(inside ->
                    names.addAll(namesIn(inside)));
            default -> { }
        }
        return names;
    }

    /** Every source asked in turn, the first answer taken. */
    private static Campaign planOf(Campaign.Builtin... sources) {
        List<Campaign.Entry> entries = new ArrayList<>();
        for (Campaign.Builtin source : sources) {
            entries.add(new Campaign.Entry.Single(new Campaign.Source.Builtin(source)));
        }
        return new Campaign(List.of(new Campaign.PlannedStrategy("nominal", 100, entries)),
                WhichOperations.everything());
    }

    /** Tells whatever in the generator listens that the API answered this request so. */
    private static void tell(RandomTestCaseGenerator generator, TestCase sent, int status,
            String reply) {
        Interaction answered = Interaction.answered(sent,
                HttpRequestRecord.of(HttpMethod.GET, "https://api.example/markets"),
                new HttpResponseRecord(StatusLine.of(status),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.of(reply.getBytes(StandardCharsets.UTF_8),
                                "application/json"))),
                Instant.EPOCH, Duration.ofMillis(3));
        for (RunListener listener : generator.whatListensToTheRun()) {
            listener.on(new RunEvent.InteractionCompleted(Instant.EPOCH, answered));
        }
    }
}
