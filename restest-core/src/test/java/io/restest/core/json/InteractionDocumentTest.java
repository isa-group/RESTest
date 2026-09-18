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
package io.restest.core.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.InteractionOutcome;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.OperationId;
import io.restest.core.model.ParameterLocation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that an attempt written down and read back is the same attempt.
 *
 * <p>Two quite separate things depend on this being true. A run stored on disk is read back by
 * whatever asks it questions afterwards, and the evidence quoted beside a fault in a report is
 * written the same way. Both would be silently wrong if anything were lost on the way through, which
 * is why almost every test here goes out and comes back rather than checking the text.
 */
class InteractionDocumentTest {

    @Test
    @DisplayName("an ordinary attempt survives being written down and read back")
    void an_answered_attempt_survives_the_round_trip() {
        Interaction original = answered();

        assertThat(InteractionDocument.toInteraction(InteractionDocument.of(original)))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("a reply that was nonsense survives too, with whatever was salvaged")
    void a_malformed_reply_survives_the_round_trip() {
        Interaction original = new Interaction(InteractionId.generate(), testCase(), request(),
                new InteractionOutcome.MalformedResponse("the reply stopped after 9 bytes",
                        Optional.of(new StatusLine(200, Optional.of("OK"), Optional.of("HTTP/1.1"))),
                        List.of(Header.of("Content-Length", "4096")),
                        Optional.of(Payload.partial("{\"id\": 7".getBytes(StandardCharsets.UTF_8),
                                "application/json", 4096))),
                Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42));

        assertThat(InteractionDocument.toInteraction(InteractionDocument.of(original)))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("a request nothing ever answered survives too, with the reason")
    void a_transport_failure_survives_the_round_trip() {
        Interaction original = Interaction.transportFailure(testCase(), request(),
                "the connection was refused", Instant.EPOCH, Duration.ofMillis(7));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original));

        assertThat(read).isEqualTo(original);
        assertThat(read.outcome()).isInstanceOf(InteractionOutcome.TransportFailure.class);
    }

    @Test
    @DisplayName("where each value came from survives, whichever of the three it was")
    void every_kind_of_provenance_survives() {
        InteractionId earlier = InteractionId.generate();
        TestCase testCase = TestCase.of(OperationId.of("POST /pets"), List.of(
                ParameterValue.of("status", ParameterLocation.QUERY, JsonValue.of("sold"),
                        ValueOrigin.DECLARED),
                ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(10),
                        new ValueOrigin.Generated("random")),
                ParameterValue.of("ownerId", ParameterLocation.PATH, JsonValue.of(3),
                        new ValueOrigin.Derived(earlier, "the id of the owner just created"))));
        Interaction original = Interaction.answered(testCase, request(),
                HttpResponseRecord.of(201), Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original));

        assertThat(read.testCase().parameterValues())
                .extracting(ParameterValue::origin)
                .containsExactly(ValueOrigin.DECLARED, new ValueOrigin.Generated("random"),
                        new ValueOrigin.Derived(earlier, "the id of the owner just created"));
    }

    @Test
    @DisplayName("which of the document's statements a value came from survives")
    void which_statement_a_value_came_from_survives() {
        TestCase testCase = TestCase.of(OperationId.of("GET /owners/{ownerId}"), List.of(
                ParameterValue.of("ownerId", ParameterLocation.PATH, JsonValue.of(1),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.EXAMPLE)),
                ParameterValue.of("status", ParameterLocation.QUERY, JsonValue.of("sold"),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.ENUMERATION)),
                ParameterValue.of("limit", ParameterLocation.QUERY, JsonValue.of(10),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.DEFAULT))));
        Interaction original = Interaction.answered(testCase, request(),
                HttpResponseRecord.of(200), Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original));

        assertThat(read.testCase().parameterValues()).extracting(ParameterValue::origin)
                .containsExactly(
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.EXAMPLE),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.ENUMERATION),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.DEFAULT));
    }

    @Test
    @DisplayName("a run recorded before RESTest kept those apart still reads")
    void a_declared_value_without_a_statement_still_reads() {
        TestCase testCase = TestCase.of(OperationId.of("GET /pets"), List.of(
                ParameterValue.of("status", ParameterLocation.QUERY, JsonValue.of("sold"),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.ENUMERATION))));
        JsonValue.JsonObject written = (JsonValue.JsonObject) InteractionDocument.of(
                Interaction.answered(testCase, request(), HttpResponseRecord.of(200),
                        Instant.EPOCH, Duration.ofMillis(5)));

        assertThat(JsonText.write(written))
                .describedAs("which statement a value came from is written down")
                .contains("\"stated\":\"enumeration\"");

        Interaction older = InteractionDocument.toInteraction(
                JsonText.read(JsonText.write(written)
                        .replace(",\"stated\":\"enumeration\"", "")));

        assertThat(older.testCase().parameterValues()).extracting(ParameterValue::origin)
                .describedAs("an older run says the document stated the value, and no more")
                .containsExactly(ValueOrigin.DECLARED);
    }

    @Test
    @DisplayName("a value stated in a way nobody recognises is refused, not guessed at")
    void an_unrecognised_statement_is_refused() {
        TestCase testCase = TestCase.of(OperationId.of("GET /pets"), List.of(
                ParameterValue.of("status", ParameterLocation.QUERY, JsonValue.of("sold"),
                        ValueOrigin.declared(ValueOrigin.Declared.Statement.EXAMPLE))));
        String written = JsonText.write(InteractionDocument.of(
                Interaction.answered(testCase, request(), HttpResponseRecord.of(200),
                        Instant.EPOCH, Duration.ofMillis(5))))
                .replace("\"stated\":\"example\"", "\"stated\":\"a horoscope\"");

        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> InteractionDocument.toInteraction(JsonText.read(written)))
                .withMessageContaining("a horoscope");
    }

    @Test
    @DisplayName("a request body and where it came from survive")
    void a_request_body_survives() {
        TestCase testCase = TestCase.of(OperationId.of("POST /pets"), List.of(),
                new BodyValue("application/json",
                        JsonValue.object(Map.of("name", JsonValue.of("Rex"))),
                        new ValueOrigin.Generated("random")));
        Interaction original = Interaction.answered(testCase, request(),
                HttpResponseRecord.of(201), Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original));

        assertThat(read.testCase().body()).contains(testCase.body().orElseThrow());
    }

    @Test
    @DisplayName("a body that reads as text is written as that text, so a person can read it")
    void a_text_body_is_written_as_text() {
        JsonValue.JsonObject written = (JsonValue.JsonObject) InteractionDocument.of(answered());
        JsonValue.JsonObject body = member(member(written, "outcome"), "body");

        assertThat(body.member("text")).contains(JsonValue.of("{\"id\": 7}"));
        assertThat(body.member("base64")).isEmpty();
    }

    @Test
    @DisplayName("a body that is not text is written as letters and digits, so nothing is lost")
    void a_binary_body_is_written_as_base64() {
        byte[] notText = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
        Interaction original = Interaction.answered(testCase(), request(),
                new HttpResponseRecord(StatusLine.of(200), List.of(),
                        Optional.of(Payload.of(notText, "image/png"))),
                Instant.EPOCH, Duration.ofMillis(5));

        JsonValue.JsonObject written = (JsonValue.JsonObject) InteractionDocument.of(original);
        JsonValue.JsonObject body = member(member(written, "outcome"), "body");

        assertThat(body.member("base64")).contains(JsonValue.of("//4AAQ=="));
        assertThat(body.member("text")).isEmpty();
        assertThat(InteractionDocument.toInteraction(written)).isEqualTo(original);
    }

    @Test
    @DisplayName("a reply too large to keep whole says how large it really was")
    void a_truncated_body_remembers_its_real_length() {
        Interaction original = Interaction.answered(testCase(), request(),
                new HttpResponseRecord(StatusLine.of(200), List.of(),
                        Optional.of(Payload.partial("abc".getBytes(StandardCharsets.UTF_8),
                                "text/plain", 9000))),
                Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original));

        assertThat(read.response().orElseThrow().body().orElseThrow().truncated()).isTrue();
        assertThat(read.response().orElseThrow().body().orElseThrow().deliveredLength())
                .isEqualTo(9000);
    }

    @Test
    @DisplayName("asked for only the start of a body, it keeps that and says how long the whole was")
    void a_body_can_be_written_down_in_part() {
        String reply = "x".repeat(5000);
        Interaction original = Interaction.answered(testCase(), request(),
                new HttpResponseRecord(StatusLine.of(200), List.of(),
                        Optional.of(Payload.text(reply, "text/plain"))),
                Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original, 100));

        Payload body = read.response().orElseThrow().body().orElseThrow();
        assertThat(body.size())
                .describedAs("somewhere that has to stay small enough to open keeps only the start")
                .isEqualTo(100);
        assertThat(body.deliveredLength())
                .describedAs("and says how much really arrived, so our trimming is never mistaken "
                        + "for the API having sent less than it did")
                .isEqualTo(5000);
        assertThat(body.truncated()).isTrue();
    }

    @Test
    @DisplayName("a body kept in part is cut between characters, not through one")
    void a_trimmed_body_is_still_readable_text() {
        // Three bytes per character, so a limit of ten falls in the middle of the fourth one.
        String reply = "€".repeat(100);
        Interaction original = Interaction.answered(testCase(), request(),
                new HttpResponseRecord(StatusLine.of(200), List.of(),
                        Optional.of(Payload.text(reply, "text/plain"))),
                Instant.EPOCH, Duration.ofMillis(5));

        Interaction read = InteractionDocument.toInteraction(InteractionDocument.of(original, 10));

        Payload body = read.response().orElseThrow().body().orElseThrow();
        assertThat(new String(body.content(), StandardCharsets.UTF_8))
                .describedAs("cut through a character, what is kept stops being text at all and is "
                        + "written out as letters and digits nobody can read")
                .isEqualTo("€€€");
    }

    @Test
    @DisplayName("a body shorter than the limit is kept whole, and does not claim to be cut short")
    void a_small_body_is_untouched_by_a_limit() {
        // The same attempt throughout: each call to answered() mints a fresh identifier.
        Interaction original = answered();

        Interaction read = InteractionDocument.toInteraction(
                InteractionDocument.of(original, 10_000));

        assertThat(read.response().orElseThrow().body().orElseThrow().truncated()).isFalse();
        assertThat(read).isEqualTo(original);
    }

    @Test
    @DisplayName("times are written the way the rest of the world writes them")
    void times_are_written_in_the_usual_notation() {
        JsonValue.JsonObject written = (JsonValue.JsonObject) InteractionDocument.of(answered());

        assertThat(written.member("sentAt")).contains(JsonValue.of("2026-09-13T10:00:00Z"));
        assertThat(written.member("elapsed")).contains(JsonValue.of("PT0.042S"));
    }

    @Test
    @DisplayName("text that is not an attempt at all is refused, saying so")
    void text_that_is_not_an_interaction_is_refused() {
        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() ->
                        InteractionDocument.toInteraction(JsonValue.of("not a document")));
    }

    @Test
    @DisplayName("an attempt missing something it needs is refused, naming what is missing")
    void a_document_missing_a_part_is_refused() {
        JsonValue.JsonObject complete = (JsonValue.JsonObject) InteractionDocument.of(answered());
        Map<String, JsonValue> without = new LinkedHashMap<>(complete.members());
        without.remove("request");

        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> InteractionDocument.toInteraction(JsonValue.object(without)))
                .withMessageContaining("request");
    }

    @Test
    @DisplayName("an outcome of a kind nobody recognises is refused")
    void an_unknown_outcome_is_refused() {
        JsonValue.JsonObject complete = (JsonValue.JsonObject) InteractionDocument.of(answered());
        Map<String, JsonValue> broken = new LinkedHashMap<>(complete.members());
        broken.put("outcome", JsonValue.object(Map.of("kind", JsonValue.of("something else"))));

        assertThatExceptionOfType(JsonException.class)
                .isThrownBy(() -> InteractionDocument.toInteraction(JsonValue.object(broken)));
    }

    private static Interaction answered() {
        return Interaction.answered(testCase(), request(),
                new HttpResponseRecord(StatusLine.of(200),
                        List.of(Header.of("Content-Type", "application/json")),
                        Optional.of(Payload.text("{\"id\": 7}", "application/json"))),
                Instant.parse("2026-09-13T10:00:00Z"), Duration.ofMillis(42));
    }

    private static TestCase testCase() {
        return TestCase.of(OperationId.of("GET /pets/{petId}"), List.of(
                ParameterValue.of("petId", ParameterLocation.PATH, JsonValue.of(7),
                        new ValueOrigin.Generated("random"))));
    }

    private static HttpRequestRecord request() {
        return new HttpRequestRecord(HttpMethod.GET, "https://api.example/pets/7",
                List.of(Header.of("Accept", "application/json")), Optional.empty());
    }

    private static JsonValue.JsonObject member(JsonValue.JsonObject parent, String name) {
        return (JsonValue.JsonObject) parent.member(name).orElseThrow();
    }
}
