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

import static io.restest.core.auth.CredentialPlanTest.CORE;
import static io.restest.core.auth.CredentialPlanTest.KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.Mutation;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.TestCaseId;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Every appearance of a key, in every part of an exchange, hidden before anything sees it. */
class SecretsTest {

    private static final String MASK = "REDACTED-AUTH.api_key";

    private final Secrets secrets = new Secrets(List.of(new Secret(KEY, MASK)));

    @Nested
    @DisplayName("a key written in any way")
    class Spellings {

        @Test
        @DisplayName("as it is, as an address or a form writes it, and as JSON or a web page escapes it")
        void every_way_a_key_is_written_is_hidden() {
            String awkward = "a\"b\\c<d&e'f g/h+i";
            Secrets hiding = new Secrets(List.of(new Secret(awkward, "REDACTED-AUTH")));

            for (String written : List.of(awkward,
                    Encoding.percent(awkward),
                    Encoding.percentInLowerCase(awkward),
                    Encoding.formStyle(awkward),
                    Encoding.json(awkward, false),
                    Encoding.json(awkward, true),
                    Encoding.jsonEscapingAllPunctuation(awkward, true),
                    Encoding.jsonEscapingAllPunctuation(awkward, false),
                    Encoding.html(awkward, false),
                    Encoding.html(awkward, true))) {
                assertThat(hiding.hidden("before " + written + " after"))
                        .describedAs(written)
                        .isEqualTo("before REDACTED-AUTH after");
            }
        }

        @Test
        @DisplayName("a key in Base64's alphabet is found however an address writes its + / =")
        void a_base64_key_is_found_in_an_address() {
            assertThat(secrets.hidden("https://x/y?k=" + Encoding.percent(KEY) + "&z=1"))
                    .isEqualTo("https://x/y?k=" + MASK + "&z=1");
            assertThat(secrets.hidden("k=" + Encoding.formStyle(KEY)))
                    .isEqualTo("k=" + MASK);
        }

        @Test
        @DisplayName("text with no key in it is handed back untouched, as the very same text")
        void text_without_a_key_is_the_same_text() {
            String text = "nothing to see here";

            assertThat(secrets.hidden(text)).isSameAs(text);
            assertThat(secrets.hidden("")).isEmpty();
            assertThat(Secrets.none().hidden(KEY)).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("one pass that never looks again")
    class OnePass {

        @Test
        @DisplayName("a key that begins another is hidden as the longer one where the longer one is")
        void a_key_that_begins_another() {
            Secrets two = new Secrets(List.of(new Secret("abcd1234", "REDACTED-AUTH.short"),
                    new Secret("abcd1234xyz", "REDACTED-AUTH.long")));

            assertThat(two.hidden("abcd1234xyz and abcd1234"))
                    .isEqualTo("REDACTED-AUTH.long and REDACTED-AUTH.short");
        }

        @Test
        @DisplayName("two keys that overlap are hidden together, leaving nothing of either")
        void overlapping_keys_are_hidden_together() {
            Secrets two = new Secrets(List.of(new Secret("abcdef", "REDACTED-AUTH.one"),
                    new Secret("defghi", "REDACTED-AUTH.two")));

            assertThat(two.hidden("[abcdefghi]")).isEqualTo("[REDACTED-AUTH.one]");
        }

        @Test
        @DisplayName("hiding twice changes nothing more, and what is written in a key's place is never looked into")
        void hiding_is_done_once() {
            String once = secrets.hidden(KEY + KEY + " " + KEY);

            assertThat(once).isEqualTo(MASK + MASK + " " + MASK);
            assertThat(secrets.hidden(once)).isEqualTo(once);
        }

        @Test
        @DisplayName("a megabyte of near misses is read through in one pass, and nothing but the keys changes")
        void a_megabyte_of_near_misses() {
            // Seven characters of the key at a time, one short of a piece long enough to hide.
            StringBuilder near = new StringBuilder();
            while (near.length() < 1024 * 1024) {
                near.append(KEY, 0, 7).append('#').append(KEY, 7, 14).append('#');
            }
            near.append(KEY);
            String text = near.toString();

            String hidden = secrets.hidden(text);

            assertThat(hidden).endsWith(MASK).doesNotContain(KEY);
            assertThat(hidden.length()).isEqualTo(text.length() - KEY.length() + MASK.length());
        }
    }

    @Nested
    @DisplayName("every part of an exchange")
    class Exchanges {

        @Test
        @DisplayName("the request, the reply and the test case are hidden, under the same identity")
        void every_part_is_hidden() {
            Interaction echoed = Interaction.answered(testCaseCarrying(KEY),
                    new HttpRequestRecord(HttpMethod.POST, "http://api/pets?key=" + KEY,
                            List.of(Header.of("api_key", KEY), Header.of("X-" + KEY, "named")),
                            Optional.of(Payload.text("{\"key\":\"" + KEY + "\"}",
                                    "application/json"))),
                    new HttpResponseRecord(new StatusLine(200, Optional.of("OK " + KEY),
                            Optional.of("HTTP/1.1")),
                            List.of(Header.of("Set-Cookie", "sid=" + KEY)),
                            Optional.of(Payload.text("{\"echo\":\"" + Encoding.json(KEY, true)
                                    + "\"}", "application/json"))),
                    Instant.parse("2026-09-30T10:00:00Z"), Duration.ofMillis(5));

            Interaction hidden = secrets.hidden(echoed);

            assertThat(hidden.id()).isEqualTo(echoed.id());
            assertThat(hidden.sentAt()).isEqualTo(echoed.sentAt());
            assertThat(hidden.elapsed()).isEqualTo(echoed.elapsed());
            assertThat(hidden.testCase().id()).isEqualTo(echoed.testCase().id());
            assertThat(hidden.request().url()).isEqualTo("http://api/pets?key=" + MASK);
            assertThat(hidden.request().headers()).containsExactly(Header.of("api_key", MASK),
                    Header.of("X-" + MASK, "named"));
            assertThat(new String(hidden.request().body().orElseThrow().content(),
                    StandardCharsets.UTF_8)).isEqualTo("{\"key\":\"" + MASK + "\"}");
            HttpResponseRecord response = hidden.response().orElseThrow();
            assertThat(response.statusLine().reasonPhrase()).contains("OK " + MASK);
            assertThat(response.headers()).containsExactly(Header.of("Set-Cookie", "sid=" + MASK));
            assertThat(new String(response.body().orElseThrow().content(), StandardCharsets.UTF_8))
                    .isEqualTo("{\"echo\":\"" + MASK + "\"}");
            assertThat(writtenOut(hidden)).doesNotContain(CORE);
        }

        @Test
        @DisplayName("an exchange with no key anywhere in it is passed on as the very same exchange")
        void an_exchange_without_a_key_is_the_same() {
            Interaction plain = Interaction.answered(testCaseCarrying("nothing"),
                    new HttpRequestRecord(HttpMethod.GET, "http://api/pets",
                            List.of(Header.of("Accept", "application/json")),
                            Optional.empty()),
                    new HttpResponseRecord(new StatusLine(200, Optional.of("OK"), Optional.empty()),
                            List.of(), Optional.of(Payload.text("[]", "application/json"))),
                    Instant.now(), Duration.ZERO);

            assertThat(secrets.hidden(plain)).isSameAs(plain);
            assertThat(Secrets.none().hidden(plain)).isSameAs(plain);
            assertThat(Secrets.none().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("a reply that broke off, and a request nobody answered, say why without the key")
        void reasons_are_hidden() {
            Interaction broken = Interaction.malformedResponse(testCaseCarrying("nothing"),
                    HttpRequestRecord.of(HttpMethod.GET, "http://api/pets?key=" + KEY),
                    "the reply stopped after 3 bytes: " + KEY,
                    Optional.of(new StatusLine(200, Optional.of(KEY), Optional.empty())),
                    List.of(Header.of("X-Echo", KEY)),
                    Optional.of(Payload.text("ok " + KEY.substring(0, 8), "text/plain")),
                    Instant.now(), Duration.ZERO);
            Interaction unanswered = Interaction.transportFailure(testCaseCarrying("nothing"),
                    HttpRequestRecord.of(HttpMethod.GET, "http://api/pets"),
                    "Unexpected char 0x0a in api_key value: " + KEY, Instant.now(), Duration.ZERO);

            assertThat(writtenOut(secrets.hidden(broken))).doesNotContain(CORE)
                    .contains("the reply stopped after 3 bytes: " + MASK)
                    .contains("ok " + MASK);
            assertThat(writtenOut(secrets.hidden(unanswered))).doesNotContain(CORE)
                    .contains("in api_key value: " + MASK);
        }

        @Test
        @DisplayName("every value and every description a test case carries is hidden, and its names never")
        void a_test_case_is_hidden() {
            Map<String, JsonValue> members = new LinkedHashMap<>();
            members.put(KEY, JsonValue.of("the name stays"));
            members.put("value", JsonValue.of(KEY));
            TestCase carrying = new TestCase(TestCaseId.generate(), OperationId.of("addPet"),
                    List.of(ParameterValue.of("token", ParameterLocation.QUERY, JsonValue.of(KEY),
                                    new ValueOrigin.Generated("list " + KEY)),
                            ParameterValue.of("number", ParameterLocation.QUERY,
                                    JsonValue.of(new BigDecimal("912345678")),
                                    new ValueOrigin.Derived(InteractionId.generate(),
                                            "field " + KEY)),
                            ParameterValue.of("flag", ParameterLocation.QUERY, JsonValue.of(true),
                                    ValueOrigin.DECLARED)),
                    Optional.of(new BodyValue("application/json", JsonValue.array(
                            JsonValue.object(members), JsonValue.array(JsonValue.of(KEY))),
                            ValueOrigin.DECLARED, Optional.of("{\"sent\":\"" + KEY + "\"}"))),
                    Intent.REFUSAL_EXPECTED,
                    Optional.of(new Mutation(InteractionId.generate(), "sendEmpty",
                            ParameterLocation.QUERY, "token " + KEY, "replaced " + KEY)),
                    Optional.empty());
            Secrets digits = new Secrets(List.of(new Secret(KEY, MASK),
                    new Secret("12345678", "REDACTED-AUTH.digits")));

            TestCase hidden = digits.hidden(carrying);

            assertThat(hidden.id()).isEqualTo(carrying.id());
            assertThat(hidden.parameterValues()).extracting(ParameterValue::value)
                    .containsExactly(JsonValue.of(MASK), JsonValue.of("9REDACTED-AUTH.digits"),
                            JsonValue.of(true));
            assertThat(hidden.parameterValues().get(0).origin())
                    .isEqualTo(new ValueOrigin.Generated("list " + MASK));
            assertThat(hidden.parameterValues().get(1).origin())
                    .isInstanceOfSatisfying(ValueOrigin.Derived.class, derived ->
                            assertThat(derived.description()).isEqualTo("field " + MASK));
            BodyValue body = hidden.body().orElseThrow();
            assertThat(body.sentAs()).contains("{\"sent\":\"" + MASK + "\"}");
            JsonValue.JsonObject object = (JsonValue.JsonObject)
                    ((JsonValue.JsonArray) body.value()).elements().get(0);
            assertThat(object.members()).containsKey(KEY)
                    .containsEntry("value", JsonValue.of(MASK));
            assertThat(hidden.mutation().orElseThrow().path()).isEqualTo("token " + MASK);
            assertThat(hidden.mutation().orElseThrow().description()).isEqualTo("replaced " + MASK);
            assertThat(digits.hidden(testCaseCarrying("nothing")))
                    .describedAs("a test case with no key in it is the same one")
                    .satisfies(same -> assertThat(digits.hidden(same)).isSameAs(same));
        }

        @Test
        @DisplayName("a step of a series says what it does without the key")
        void a_step_of_a_series_is_hidden() {
            TestCase step = new TestCase(TestCaseId.generate(), OperationId.of("getPet"), List.of(),
                    Optional.empty(), Intent.UNKNOWN, Optional.empty(),
                    Optional.of(new SequenceStep("readAfterDelete", 2,
                            List.of(InteractionId.generate()), "reads " + KEY)));

            assertThat(secrets.hidden(step).sequence().orElseThrow().description())
                    .isEqualTo("reads " + MASK);
        }
    }

    @Nested
    @DisplayName("bodies")
    class Bodies {

        @Test
        @DisplayName("bytes that are not text come out exactly as they went in, and the key among them is still hidden")
        void binary_bytes_are_kept() {
            byte[] noise = new byte[4096];
            new Random(20260930L).nextBytes(noise);
            Payload binary = Payload.of(noise, "application/octet-stream");

            assertThat(secrets.hidden(answeredWith(binary)).response().orElseThrow().body())
                    .containsSame(binary);

            byte[] withAKey = Arrays.copyOf(noise, noise.length + KEY.length());
            System.arraycopy(KEY.getBytes(StandardCharsets.US_ASCII), 0, withAKey, noise.length,
                    KEY.length());
            byte[] hidden = secrets.hidden(answeredWith(Payload.of(withAKey,
                    "application/octet-stream"))).response().orElseThrow().body().orElseThrow()
                    .content();
            assertThat(Arrays.copyOf(hidden, noise.length)).isEqualTo(noise);
            assertThat(new String(hidden, noise.length, hidden.length - noise.length,
                    StandardCharsets.US_ASCII)).isEqualTo(MASK);
        }

        @Test
        @DisplayName("a reply kept only in part hides the half key it ends with, and never grows past what was kept")
        void a_reply_kept_in_part() {
            String kept = "x".repeat(20) + KEY + "y".repeat(10) + KEY.substring(0, 12);
            Payload partial = Payload.partial(kept.getBytes(StandardCharsets.US_ASCII),
                    "text/plain", 10_000);

            Payload hidden = secrets.hidden(answeredWith(partial)).response().orElseThrow().body()
                    .orElseThrow();

            String written = new String(hidden.content(), StandardCharsets.US_ASCII);
            assertThat(written).doesNotContain(CORE).doesNotContain(KEY.substring(0, 12))
                    .startsWith("x".repeat(20) + MASK);
            assertThat(hidden.size()).isLessThanOrEqualTo(partial.size());
            assertThat(hidden.truncated()).isTrue();
            assertThat(hidden.wireLength()).isEqualTo(partial.wireLength());
        }

        @Test
        @DisplayName("a reply kept whole may end with a few letters of a key, which are nobody's business")
        void a_whole_reply_is_not_cut() {
            String whole = "ends with " + KEY.substring(0, 6);
            Payload payload = Payload.text(whole, "text/plain");

            assertThat(secrets.hidden(answeredWith(payload)).response().orElseThrow().body())
                    .containsSame(payload);
        }

        @Test
        @DisplayName("a key broken in two with something else in between is hidden piece by piece")
        void a_key_broken_in_two() {
            String percent = Encoding.percent(KEY);
            String broken = "| /files/1?key=" + percent.substring(0, 20) + " <<< does not match\n"
                    + "|   " + percent.substring(20) + " |";

            String hidden = secrets.hidden(broken);

            assertThat(hidden).doesNotContain(CORE)
                    .isEqualTo("| /files/1?key=" + MASK + " <<< does not match\n|   " + MASK + " |");
            assertThat(secrets.hidden("only " + KEY.substring(3, 10) + " of it"))
                    .describedAs("seven characters of it are not enough to be taken for it")
                    .isEqualTo("only " + KEY.substring(3, 10) + " of it");
        }

        @Test
        @DisplayName("a reply that stopped halfway hides the half key it stopped in")
        void a_reply_that_stopped_halfway() {
            Interaction stopped = Interaction.malformedResponse(testCaseCarrying("nothing"),
                    HttpRequestRecord.of(HttpMethod.GET, "http://api/pets"), "stopped",
                    Optional.empty(), List.of(),
                    Optional.of(Payload.text("{\"key\":\"" + KEY.substring(0, 9), "text/plain")),
                    Instant.now(), Duration.ZERO);

            assertThat(writtenOut(secrets.hidden(stopped))).contains("{\"key\":\"" + MASK)
                    .doesNotContain(KEY.substring(0, 9));
        }
    }

    @Test
    @DisplayName("an exchange kept without its details holds nothing that could be a key, and keeps its identity")
    void kept_without_its_details() {
        Interaction echoed = Interaction.answered(testCaseCarrying(KEY),
                new HttpRequestRecord(HttpMethod.GET, "http://api/pets?key=" + KEY,
                        List.of(Header.of("api_key", KEY)), Optional.of(Payload.text(KEY,
                                "text/plain"))),
                new HttpResponseRecord(new StatusLine(500, Optional.of(KEY), Optional.empty()),
                        List.of(Header.of("X-Echo", KEY)),
                        Optional.of(Payload.text(KEY, "text/plain"))),
                Instant.now(), Duration.ZERO);

        Interaction kept = Secrets.hiddenWhole(echoed);

        assertThat(kept.id()).isEqualTo(echoed.id());
        assertThat(kept.statusCode()).contains(500);
        assertThat(kept.testCase().operation()).isEqualTo(echoed.testCase().operation());
        assertThat(writtenOut(kept)).doesNotContain(CORE);
        assertThat(Secrets.hiddenWhole(Interaction.transportFailure(testCaseCarrying(KEY),
                HttpRequestRecord.of(HttpMethod.GET, "http://api/" + KEY), KEY, Instant.now(),
                Duration.ZERO)).toString()).doesNotContain(CORE);
        assertThat(secrets.hiddenWhole()).isZero();
    }

    private static TestCase testCaseCarrying(String value) {
        return TestCase.of(OperationId.of("listPets"), List.of(ParameterValue.of("q",
                ParameterLocation.QUERY, JsonValue.of(value), ValueOrigin.DECLARED)));
    }

    private static Interaction answeredWith(Payload body) {
        return Interaction.answered(testCaseCarrying("nothing"),
                HttpRequestRecord.of(HttpMethod.GET, "http://api/pets"),
                new HttpResponseRecord(StatusLine.of(200), List.of(), Optional.of(body)),
                Instant.now(), Duration.ZERO);
    }

    /**
     * Everything an exchange holds, written out as text: what the records say of themselves, and
     * every header value and body besides, which they are careful not to print.
     */
    static String writtenOut(Interaction interaction) {
        StringBuilder written = new StringBuilder(interaction.toString());
        written.append(interaction.request().url());
        interaction.request().headers().forEach(header -> written.append(header.name())
                .append(header.value()));
        interaction.request().body().ifPresent(body -> written.append(
                new String(body.content(), StandardCharsets.ISO_8859_1)));
        switch (interaction.outcome()) {
            case InteractionOutcome.Answered answered -> {
                answered.response().headers().forEach(header -> written.append(header.name())
                        .append(header.value()));
                answered.response().statusLine().reasonPhrase().ifPresent(written::append);
                answered.response().body().ifPresent(body -> written.append(
                        new String(body.content(), StandardCharsets.ISO_8859_1)));
            }
            case InteractionOutcome.MalformedResponse malformed -> {
                written.append(malformed.reason());
                malformed.headers().forEach(header -> written.append(header.value()));
                malformed.statusLine().flatMap(StatusLine::reasonPhrase).ifPresent(written::append);
                malformed.partial().ifPresent(body -> written.append(
                        new String(body.content(), StandardCharsets.ISO_8859_1)));
            }
            case InteractionOutcome.TransportFailure failure -> written.append(failure.reason());
        }
        return written.toString();
    }
}
