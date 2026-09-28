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

import static io.restest.gen.TheCorpus.operation;
import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.InteractionId;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.Payload;
import io.restest.core.execution.SequenceStep;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.ParameterLocation;
import io.restest.core.settings.SequenceSettings;
import io.restest.core.settings.Settings;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SequencesTest {

    private static final ApiModel PET_CLINIC = TheCorpus.priority("pet-clinic");
    private static final long SEED = 20261003L;
    private static final Operation ADD_OWNER = operation(PET_CLINIC, HttpMethod.POST, "/owners");

    @Test
    @DisplayName("createTwice sends the same creation again, once the first was accepted")
    void the_same_creation_twice() {
        List<Sent> sent = run(Sequences.Shape.CREATE_TWICE, ADD_OWNER, created("{\"id\":895}"));

        assertThat(sent).hasSize(2);
        TestCase first = sent.get(0).request();
        TestCase again = sent.get(1).request();
        assertThat(again.operation()).isEqualTo(ADD_OWNER.id());
        assertThat(again.parameterValues()).isEqualTo(first.parameterValues());
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(again.intent()).isEqualTo(Intent.UNKNOWN);
        assertThat(again.sequence().orElseThrow())
                .returns("createTwice", SequenceStep::shape)
                .returns(2, SequenceStep::step)
                .returns(List.of(sent.get(0).answer().id()), SequenceStep::follows);
    }

    @Test
    @DisplayName("a creation the API refused makes nothing to ask about, and costs nothing more")
    void a_refused_creation_ends_the_series() {
        List<Sent> sent = run(Sequences.Shape.CREATE_TWICE, ADD_OWNER, request -> reply(request,
                400, "{\"message\":\"no\"}"));

        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("readAfterDelete reads, deletes, reads again and reads what hung from it")
    void read_after_delete() {
        List<Sent> sent = run(Sequences.Shape.READ_AFTER_DELETE, ADD_OWNER, request ->
                switch (method(request)) {
                    case POST -> reply(request, 201, "{\"firstName\":\"George\",\"id\":895}");
                    case DELETE -> reply(request, 204, "");
                    default -> reply(request, deleted(request) ? 404 : 200, "{\"id\":895}");
                });

        assertThat(sent).extracting(each -> method(each.request()) + " " + path(each.request()))
                .containsExactly(
                        "POST /owners",
                        "GET /owners/{ownerId}",
                        "DELETE /owners/{ownerId}",
                        "GET /owners/{ownerId}",
                        "GET /owners/{ownerId}/pets/{petId}");
        for (Sent later : sent.subList(1, sent.size())) {
            ParameterValue owner = later.request().parameterValue("ownerId",
                    ParameterLocation.PATH).orElseThrow();
            assertThat(owner.value()).isEqualTo(JsonValue.of(new BigDecimal("895")));
            assertThat(owner.origin())
                    .describedAs("every identifier the series uses is the one it created")
                    .isEqualTo(new ValueOrigin.Derived(sent.get(0).answer().id(),
                            "the 'id' of what POST /owners created, earlier in this series"));
        }
        assertThat(sent).extracting(each -> each.request().intent())
                .containsExactly(Intent.UNKNOWN, Intent.UNKNOWN, Intent.UNKNOWN,
                        Intent.REFUSAL_EXPECTED, Intent.REFUSAL_EXPECTED);
        InteractionId creation = sent.get(0).answer().id();
        InteractionId deletion = sent.get(2).answer().id();
        assertThat(sent.get(3).request().sequence().orElseThrow().follows())
                .describedAs("the read after the deletion names what it follows")
                .containsExactly(creation, deletion);
        assertThat(sent.get(3).request().parameterValues())
                .describedAs("the second read is the first one again")
                .isEqualTo(sent.get(1).request().parameterValues());
    }

    @Test
    @DisplayName("a deletion the API refused deletes nothing, so nothing is asked about after it")
    void a_refused_deletion_ends_the_series() {
        List<Sent> sent = run(Sequences.Shape.READ_AFTER_DELETE, ADD_OWNER, request ->
                switch (method(request)) {
                    case POST -> reply(request, 201, "{\"id\":895}");
                    case DELETE -> reply(request, 409, "{\"message\":\"has pets\"}");
                    default -> reply(request, 200, "{\"id\":895}");
                });

        assertThat(sent).extracting(each -> method(each.request()))
                .containsExactly(HttpMethod.POST, HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    @DisplayName("with nothing in the reply to go by, the identifier is read from the Location")
    void the_identifier_from_the_location_header() {
        List<Sent> sent = run(Sequences.Shape.DELETE_TWICE, ADD_OWNER, request ->
                method(request) == HttpMethod.POST
                        ? reply(request, 201, "", "Location", "/api/owners/895")
                        : reply(request, 204, ""));

        assertThat(sent).hasSize(3);
        ParameterValue owner = sent.get(1).request().parameterValue("ownerId",
                ParameterLocation.PATH).orElseThrow();
        assertThat(owner.value())
                .describedAs("a number, because the gap is declared as one; and matched by the end "
                        + "of the address, since pet-clinic leaves out the base it is served under")
                .isEqualTo(JsonValue.of(new BigDecimal("895")));
        assertThat(owner.origin()).isEqualTo(new ValueOrigin.Derived(sent.get(0).answer().id(),
                "the identifier in the Location header POST /owners answered with, earlier in "
                        + "this series"));
    }

    @Test
    @DisplayName("deleteTwice deletes the same thing twice, and the second expects nothing in "
            + "particular")
    void delete_twice() {
        List<Sent> sent = run(Sequences.Shape.DELETE_TWICE, ADD_OWNER, request ->
                method(request) == HttpMethod.POST
                        ? reply(request, 201, "{\"id\":895}")
                        : reply(request, deleted(request) ? 404 : 204, ""));

        assertThat(sent).extracting(each -> method(each.request()))
                .containsExactly(HttpMethod.POST, HttpMethod.DELETE, HttpMethod.DELETE);
        assertThat(sent.get(2).request().intent())
                .describedAs("a second deletion may answer 404 or a success; only a failure is "
                        + "wrong")
                .isEqualTo(Intent.UNKNOWN);
        assertThat(sent.get(2).request().parameterValues())
                .isEqualTo(sent.get(1).request().parameterValues());
    }

    @Test
    @DisplayName("an identifier that cannot be read ends the series before anything is sent")
    void no_identifier_no_series() {
        List<Sent> sent = run(Sequences.Shape.DELETE_TWICE, ADD_OWNER, request ->
                reply(request, 201, "{\"firstName\":\"George\"}"));

        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("a pet's identifier is its id, never its owner's, and it is deleted where pets are")
    void a_pet_is_not_its_owner() {
        Operation addPet = operation(PET_CLINIC, HttpMethod.POST, "/owners/{ownerId}/pets");

        List<Sent> sent = run(Sequences.Shape.DELETE_TWICE, addPet, request ->
                method(request) == HttpMethod.POST
                        ? reply(request, 201, "{\"name\":\"Leo\",\"id\":14,\"ownerId\":11}")
                        : reply(request, 204, ""));

        assertThat(sent.get(1).request().operation())
                .isEqualTo(operation(PET_CLINIC, HttpMethod.DELETE, "/pets/{petId}").id());
        assertThat(sent.get(1).request().parameterValue("petId", ParameterLocation.PATH)
                .orElseThrow().value()).isEqualTo(JsonValue.of(new BigDecimal("14")));
    }

    @Test
    @DisplayName("writeUnderDeleted deletes the thing, then adds or changes something under it")
    void a_write_under_what_was_deleted() {
        List<Sent> sent = run(Sequences.Shape.WRITE_UNDER_DELETED, ADD_OWNER, request ->
                switch (method(request)) {
                    case POST -> path(request).equals("/owners")
                            ? reply(request, 201, "{\"id\":895}")
                            : reply(request, 404, "{\"message\":\"no such owner\"}");
                    case DELETE -> reply(request, 204, "");
                    default -> reply(request, 404, "");
                });

        assertThat(sent).hasSize(3);
        TestCase write = sent.get(2).request();
        assertThat(path(write)).startsWith("/owners/{ownerId}/pets");
        assertThat(write.parameterValue("ownerId", ParameterLocation.PATH).orElseThrow().value())
                .isEqualTo(JsonValue.of(new BigDecimal("895")));
        assertThat(write.sequence().orElseThrow().follows())
                .containsExactly(sent.get(0).answer().id(), sent.get(1).answer().id());
        assertThat(write.intent())
                .describedAs("adding or changing something under a thing the API deleted should "
                        + "be refused")
                .isEqualTo(method(write) == HttpMethod.DELETE
                        ? Intent.UNKNOWN : Intent.REFUSAL_EXPECTED);
    }

    @Test
    @DisplayName("putTwice sends the same replacement twice, reading after each, with the body's "
            + "own identifier the thing's")
    void the_same_replacement_twice() {
        Operation addPetType = operation(PET_CLINIC, HttpMethod.POST, "/pettypes");

        List<Sent> sent = run(Sequences.Shape.PUT_TWICE, addPetType, request ->
                switch (method(request)) {
                    case POST -> reply(request, 201, "{\"name\":\"cat\",\"id\":835}");
                    case PUT -> reply(request, 204, "");
                    default -> reply(request, 200, "{\"name\":\"cat\",\"id\":835}");
                });

        assertThat(sent).extracting(each -> method(each.request()))
                .containsExactly(HttpMethod.POST, HttpMethod.PUT, HttpMethod.GET, HttpMethod.PUT,
                        HttpMethod.GET);
        TestCase put = sent.get(1).request();
        assertThat(put.parameterValue("petTypeId", ParameterLocation.PATH).orElseThrow().value())
                .isEqualTo(JsonValue.of(new BigDecimal("835")));
        assertThat(((JsonValue.JsonObject) put.body().orElseThrow().value()).member("id"))
                .describedAs("PetType declares its id as something a request may send, so the "
                        + "replacement says which thing it replaces with the thing's own")
                .contains(JsonValue.of(new BigDecimal("835")));
        assertThat(sent.get(3).request().body()).isEqualTo(put.body());
        assertThat(sent.get(4).request().sequence().orElseThrow().follows())
                .describedAs("the last read names the read it should agree with")
                .containsExactly(sent.get(0).answer().id(), sent.get(2).answer().id(),
                        sent.get(3).answer().id());
    }

    @Test
    @DisplayName("safeGet reads the thing, reads around it, and reads it again")
    void reading_around_a_thing() {
        List<Sent> sent = run(Sequences.Shape.SAFE_GET, ADD_OWNER, request ->
                method(request) == HttpMethod.POST
                        ? reply(request, 201, "{\"id\":895}")
                        : reply(request, 200, "{\"id\":895}"));

        assertThat(sent).extracting(each -> method(each.request()) + " " + path(each.request()))
                .containsExactly(
                        "POST /owners",
                        "GET /owners/{ownerId}",
                        "GET /owners",
                        "GET /owners/{ownerId}");
        assertThat(sent.get(3).request().parameterValues())
                .isEqualTo(sent.get(1).request().parameterValues());
        assertThat(sent).extracting(each -> each.request().intent())
                .containsOnly(Intent.UNKNOWN);
    }

    @Test
    @DisplayName("an answer that never came is no answer: a step the question needs ends the series")
    void nothing_came_back() {
        RandomTestCaseGenerator generator = only(Sequences.Shape.READ_AFTER_DELETE);
        Sequences series = generator.sequences().orElseThrow();
        TestCase first = firstStepOf(generator, ADD_OWNER);

        Optional<Sequences.Next> read = series.heard(first,
                Optional.of(reply(first, 201, "{\"id\":895}")));
        TestCase built = series.build(read.orElseThrow()).orElseThrow();

        assertThat(series.heard(built, Optional.empty())).isEmpty();
    }

    @Test
    @DisplayName("with every series switched off, no creation starts one")
    void none_switched_on() {
        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(PET_CLINIC, SEED,
                List.of(), onlySeries(), Settings.defaults().withSequences(
                        SequenceSettings.noneSent()));

        assertThat(generator.sequences()).isEmpty();
        assertThat(generator.generate(ADD_OWNER).orElseThrow().sequence()).isEmpty();
    }

    // --- driving a series ----------------------------------------------------------------------

    /** One step as it was sent, and what came back. */
    private record Sent(TestCase request, Interaction answer) {
    }

    /** Every step a series sends against an API that answers as told, until the series is over. */
    private static List<Sent> run(Sequences.Shape shape, Operation creation,
            Function<TestCase, Interaction> api) {
        RandomTestCaseGenerator generator = only(shape);
        Sequences series = generator.sequences().orElseThrow();
        List<Sent> sent = new ArrayList<>();
        TestCase step = firstStepOf(generator, creation);
        assertThat(step.sequence().orElseThrow().shape()).isEqualTo(shape.named());
        while (true) {
            Interaction answer = api.apply(step);
            sent.add(new Sent(step, answer));
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

    private static TestCase firstStepOf(RandomTestCaseGenerator generator, Operation creation) {
        return generator.generate(creation).orElseThrow();
    }

    /** A generator whose every request of a creation starts this one series. */
    private static RandomTestCaseGenerator only(Sequences.Shape shape) {
        SequenceSettings on = new SequenceSettings(
                shape == Sequences.Shape.READ_AFTER_DELETE,
                shape == Sequences.Shape.DELETE_TWICE,
                shape == Sequences.Shape.WRITE_UNDER_DELETED,
                shape == Sequences.Shape.PUT_TWICE,
                shape == Sequences.Shape.SAFE_GET,
                shape == Sequences.Shape.CREATE_TWICE);
        return new RandomTestCaseGenerator(PET_CLINIC, SEED, List.of(), onlySeries(),
                Settings.defaults().withSequences(on));
    }

    /** A plan that sends a series from every creation, values from the document and invention. */
    private static Campaign onlySeries() {
        return new Campaign(List.of(new Campaign.PlannedStrategy("sequences", 100, List.of(
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.ENUM)),
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.EXAMPLE)),
                new Campaign.Entry.Single(new Campaign.Source.Builtin(Campaign.Builtin.RANDOM))),
                false, true)), WhichOperations.everything());
    }

    private static Function<TestCase, Interaction> created(String body) {
        return request -> reply(request, method(request) == HttpMethod.POST ? 201 : 200, body);
    }

    /** Whether a step comes after the series deleted its thing. */
    private static boolean deleted(TestCase request) {
        return request.intent() == Intent.REFUSAL_EXPECTED
                || request.sequence().map(step -> step.step() > 2
                        && step.shape().equals("deleteTwice")).orElse(false);
    }

    private static Interaction reply(TestCase request, int status, String body,
            String... header) {
        List<Header> headers = new ArrayList<>();
        headers.add(Header.of("Content-Type", "application/json"));
        for (int at = 0; at + 1 < header.length; at += 2) {
            headers.add(Header.of(header[at], header[at + 1]));
        }
        HttpResponseRecord response = new HttpResponseRecord(StatusLine.of(status), headers,
                body.isEmpty() ? Optional.empty()
                        : Optional.of(Payload.text(body, "application/json")));
        return Interaction.answered(request, HttpRequestRecord.of(method(request),
                "http://localhost/petclinic/api" + path(request)), response, Instant.EPOCH,
                Duration.ofMillis(1));
    }

    private static HttpMethod method(TestCase request) {
        return PET_CLINIC.operation(request.operation()).orElseThrow().method();
    }

    private static String path(TestCase request) {
        return PET_CLINIC.operation(request.operation()).orElseThrow().path();
    }
}
