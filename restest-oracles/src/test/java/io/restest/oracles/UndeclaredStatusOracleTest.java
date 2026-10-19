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
package io.restest.oracles;

import static org.assertj.core.api.Assertions.assertThat;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Intent;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.model.ApiModel;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.model.SpecificationIssue;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.RestestFault;
import io.restest.core.oracle.WhyNotJudged;
import io.restest.core.schema.AnySchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UndeclaredStatusOracleTest {

    /** Declares 200 and 404, so anything else below 500 is not declared. */
    private static final Operation LIST_PETS = Operation.of(HttpMethod.GET, "/pets")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of()),
                    ResponseModel.empty("404")));

    /** Takes a JSON body, and declares only 201. */
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(AnySchema.of(), true))
            .withResponses(List.of(ResponseModel.empty("201")));

    /** Takes any kind of application body, and declares only 201. */
    private static final Operation ADD_OWNER = Operation.of(HttpMethod.POST, "/owners")
            .withRequestBody(RequestBodyModel.ofShapes(true,
                    Map.of("application/*", AnySchema.of())))
            .withResponses(List.of(ResponseModel.empty("201")));

    /** Declares every success at once, by its range. */
    private static final Operation LIST_VISITS = Operation.of(HttpMethod.GET, "/visits")
            .withResponses(List.of(ResponseModel.json("2XX", AnySchema.of())));

    /** Declares 200, and a reply for everything else. */
    private static final Operation LIST_OWNERS = Operation.of(HttpMethod.GET, "/owners")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of()),
                    ResponseModel.empty("default")));

    /** Declares no reply at all. */
    private static final Operation HEALTH = Operation.of(HttpMethod.GET, "/health");

    /** Declares 200, but the document says more about it that could not be read. */
    private static final Operation LIST_VETS = Operation.of(HttpMethod.GET, "/vets")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /**
     * Declares 200, and is named by its method and path because the document gives its name to
     * another operation too; nothing else about it was set aside.
     */
    private static final Operation LIST_CLINICS = Operation.of(HttpMethod.GET, "/clinics")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /** Declares no reply at all, because the only one the document gives could not be read. */
    private static final Operation LIST_KENNELS = Operation.of(HttpMethod.GET, "/kennels");

    private static final ApiModel API = ApiModel.of("Pets", "1.0.0", List.of(LIST_PETS, ADD_PET,
                    ADD_OWNER, LIST_VISITS, LIST_OWNERS, HEALTH, LIST_VETS, LIST_CLINICS,
                    LIST_KENNELS))
            .withIssues(List.of(SpecificationIssue.degraded("paths./vets.get", LIST_VETS.id(),
                    "the response '404' is a reference that does not resolve to anything the "
                            + "document declares"),
                    SpecificationIssue.degraded("paths./kennels.get", LIST_KENNELS.id(),
                            "the response '200' is a reference that does not resolve to anything "
                                    + "the document declares"),
                    SpecificationIssue.renamed("paths./clinics.get", LIST_CLINICS.id(),
                            "renamed to stay unique")));

    private final UndeclaredStatusOracle oracle = new UndeclaredStatusOracle();

    private List<Finding> judge(Operation operation, int status) {
        return oracle.judge(answered(operation, status), API);
    }

    /** Why the rule could not judge a reply of this status, when it could not. */
    private Optional<WhyNotJudged> why(Operation operation, int status) {
        return oracle.verdict(answered(operation, status), API).notJudged();
    }

    private static Interaction answered(Operation operation, int status) {
        return Attempts.answered(operation.id(), operation.path(), status, "application/json",
                "{}");
    }

    @Nested
    @DisplayName("a status code the document declares")
    class Declared {

        @Test
        @DisplayName("by its number is not reported")
        void by_its_number() {
            assertThat(judge(LIST_PETS, 200)).isEmpty();
            assertThat(judge(LIST_PETS, 404)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 201, 204, 299})
        @DisplayName("by its range is not reported")
        void by_its_range(int status) {
            assertThat(judge(LIST_VISITS, status)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {201, 302, 400, 418, 499})
        @DisplayName("by a default reply, which covers every code the document does not name, is not "
                + "reported")
        void by_a_default(int status) {
            assertThat(judge(LIST_OWNERS, status)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a status code the document does not declare")
    class Undeclared {

        @ParameterizedTest
        @ValueSource(ints = {100, 201, 302, 400, 499})
        @DisplayName("below 500 is reported, saying what came and what the document declares")
        void is_reported(int status) {
            List<Finding> found = judge(LIST_PETS, status);

            assertThat(found).hasSize(1);
            Finding finding = found.get(0);
            assertThat(finding.category()).isEqualTo(RestestFault.UNDECLARED_STATUS_CODE);
            assertThat(finding.operation()).isEqualTo(LIST_PETS.id());
            assertThat(finding.summary()).isEqualTo("the API answered " + status + ", which the "
                    + "document does not declare for this operation; it declares 200, 404");
            assertThat(finding.details()).isEmpty();
        }

        @Test
        @DisplayName("names what the document declares in the document's own order and words")
        void names_ranges_and_default_as_declared() {
            Operation odd = Operation.of(HttpMethod.GET, "/odd")
                    .withResponses(List.of(ResponseModel.empty("404"), ResponseModel.empty("2xx")));

            assertThat(oracle.judge(Attempts.answered(odd.id(), "/odd", 302, "text/html", "<p>"),
                    ApiModel.of("Odd", "1", List.of(odd))))
                    .extracting(Finding::summary)
                    .containsExactly("the API answered 302, which the document does not declare "
                            + "for this operation; it declares 404, 2XX");
        }

        @Test
        @DisplayName("is reported for a request broken on purpose too: refusing it with a code the "
                + "document leaves out is still a code it leaves out")
        void is_reported_for_a_request_broken_on_purpose() {
            Interaction refused = Attempts.answered(Attempts.changed(LIST_PETS.id(),
                    Intent.REFUSAL_EXPECTED, "wrongType", "sent a word where a number goes"),
                    "/pets", 400);

            assertThat(oracle.judge(refused, API)).hasSize(1);
        }

        @Test
        @DisplayName("is reported from its status line, though only part of the body was kept")
        void is_reported_though_the_body_was_kept_in_part() {
            assertThat(oracle.judge(Attempts.answeredWithPartOfTheBody(LIST_PETS.id(), "/pets",
                    201, "application/json", "{\"na", 5000), API)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("left alone")
    class LeftAlone {

        @ParameterizedTest
        @ValueSource(ints = {99, 500, 501, 503, 599, 600})
        @DisplayName("a server error, which other rules report, and a code outside 100 to 599")
        void a_server_error_and_a_code_outside_the_range(int status) {
            assertThat(judge(LIST_PETS, status)).isEmpty();
            assertThat(why(LIST_PETS, status)).describedAs("nothing for this rule to judge")
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 400, 404})
        @DisplayName("an operation whose document declares no reply at all, since it promised none, "
                + "which the rule says it could not judge")
        void an_operation_that_declares_nothing(int status) {
            assertThat(judge(HEALTH, status)).isEmpty();
            assertThat(why(HEALTH, status)).contains(WhyNotJudged.NO_REPLY_DECLARED);
        }

        @Test
        @DisplayName("an operation RESTest could not read in full, where a reply it could not read "
                + "would look undeclared; the others in the same document are still judged")
        void an_operation_read_in_part() {
            assertThat(judge(LIST_VETS, 404)).isEmpty();
            assertThat(why(LIST_VETS, 404)).contains(WhyNotJudged.READ_IN_PART);
            assertThat(judge(LIST_PETS, 400)).hasSize(1);
        }

        @Test
        @DisplayName("an operation read in part that seems to declare no reply says it was read in "
                + "part, since the reply it declares may be the part that could not be read")
        void read_in_part_comes_before_declaring_nothing() {
            assertThat(why(LIST_KENNELS, 200)).contains(WhyNotJudged.READ_IN_PART);
        }

        @Test
        @DisplayName("but a code an operation read in part does declare is judged, and is fine")
        void a_declared_code_on_an_operation_read_in_part_is_judged() {
            assertThat(judge(LIST_VETS, 200)).isEmpty();
            assertThat(why(LIST_VETS, 200)).isEmpty();
        }

        @Test
        @DisplayName("but not an operation RESTest only had to give another name, because the "
                + "document gives its name to another operation too: it was read whole")
        void not_an_operation_only_renamed() {
            assertThat(judge(LIST_CLINICS, 400)).hasSize(1);
        }

        @Test
        @DisplayName("an operation the document does not declare")
        void an_operation_the_document_does_not_declare() {
            Interaction unknown = Attempts.answered(OperationId.of("GET /cats"), "/cats", 400,
                    "application/json", "{}");

            assertThat(oracle.judge(unknown, API)).isEmpty();
            assertThat(oracle.verdict(unknown, API).notJudged()).isEmpty();
        }

        @Test
        @DisplayName("but not an operation that only shares its name with one RESTest could not read "
                + "at all, which is not among the operations")
        void not_an_operation_named_by_one_that_was_skipped() {
            ApiModel withASkippedNamesake = API.withIssues(List.of(SpecificationIssue.skipped(
                    "paths./pets/{petId}.get", LIST_PETS.id(), "the operation could not be "
                            + "represented")));

            assertThat(oracle.judge(Attempts.answered(LIST_PETS.id(), "/pets", 400,
                    "application/json", "{}"), withASkippedNamesake)).hasSize(1);
        }

        @Test
        @DisplayName("a reply that broke off, and a request that got none")
        void no_well_formed_reply() {
            assertThat(oracle.judge(Attempts.brokenOff(LIST_PETS.id(), "/pets", 400), API))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.neverAnswered(LIST_PETS.id(), "/pets"), API))
                    .isEmpty();
            assertThat(oracle.verdict(Attempts.brokenOff(LIST_PETS.id(), "/pets", 400), API)
                    .notJudged()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a request whose body went as a kind the operation does not accept")
    class BodyNotAccepted {

        private List<Finding> judge(Operation operation, HttpRequestRecord request) {
            return oracle.judge(Attempts.replied(Attempts.testCase(operation.id()), request, 415,
                    List.of(Header.of("Content-Type", "text/html")),
                    Attempts.body("<p>Unsupported</p>", "text/html")), API);
        }

        @Test
        @DisplayName("is left alone: a document is not expected to say how a body it never took is "
                + "refused")
        void is_left_alone() {
            assertThat(judge(ADD_PET, Attempts.sentWithBody(HttpMethod.POST, "/pets",
                    "text/plain"))).isEmpty();
            assertThat(oracle.verdict(Attempts.replied(Attempts.testCase(ADD_PET.id()),
                    Attempts.sentWithBody(HttpMethod.POST, "/pets", "text/plain"), 415,
                    List.of(), Optional.empty()), API).notJudged())
                    .describedAs("not one the rule could not judge: nothing to judge").isEmpty();
        }

        @Test
        @DisplayName("is not a body under the accepted kind written differently")
        void the_accepted_kind_written_differently_is_judged() {
            assertThat(judge(ADD_PET, Attempts.sentWithBody(HttpMethod.POST, "/pets",
                    "Application/JSON; charset=UTF-8"))).hasSize(1);
        }

        @Test
        @DisplayName("is not a body under a kind a range the operation accepts covers")
        void a_kind_a_range_covers_is_judged() {
            assertThat(judge(ADD_OWNER, Attempts.sentWithBody(HttpMethod.POST, "/owners",
                    "application/xml"))).hasSize(1);
        }

        @Test
        @DisplayName("is not a request with no body")
        void a_request_with_no_body_is_judged() {
            assertThat(judge(ADD_PET, Attempts.sentWithoutBody(HttpMethod.POST, "/pets")))
                    .hasSize(1);
        }

        @Test
        @DisplayName("is not a body whose media type was left empty")
        void a_body_with_an_empty_media_type_is_judged() {
            HttpRequestRecord leftEmpty = new HttpRequestRecord(HttpMethod.POST,
                    Attempts.BASE + "/pets", List.of(Header.of("Content-Type", " ")),
                    Optional.of(Payload.text("{}", "application/json")));

            assertThat(judge(ADD_PET, leftEmpty)).hasSize(1);
        }

        @Test
        @DisplayName("is not a body to an operation that declares none")
        void a_body_to_an_operation_that_takes_none_is_judged() {
            assertThat(oracle.judge(Attempts.replied(Attempts.testCase(LIST_PETS.id()),
                    Attempts.sentWithBody(HttpMethod.GET, "/pets", "text/plain"), 400,
                    List.of(), Optional.empty()), API)).hasSize(1);
        }
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("undeclared-status-code");
        assertThat(oracle.kind()).isEqualTo(RestestFault.UNDECLARED_STATUS_CODE);
        assertThat(oracle.what()).isEqualTo("the status code");
        assertThat(oracle.description()).contains("status code").contains("500");
    }
}
