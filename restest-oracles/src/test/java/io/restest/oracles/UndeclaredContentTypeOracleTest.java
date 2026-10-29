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
import io.restest.core.schema.CanonicalSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UndeclaredContentTypeOracleTest {

    /** Answers JSON for 200, 400 and 500, and nothing at all for 404. */
    private static final Operation ADD_PET = Operation.of(HttpMethod.POST, "/pets")
            .withRequestBody(RequestBodyModel.json(AnySchema.of(), true))
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of()),
                    ResponseModel.json("400", AnySchema.of()),
                    ResponseModel.json("500", AnySchema.of()),
                    ResponseModel.empty("404")));

    /** Answers any kind of application body, and a JSON or XML one on failure. */
    private static final Operation LIST_OWNERS = Operation.of(HttpMethod.GET, "/owners")
            .withResponses(List.of(
                    new ResponseModel("200", Map.of("application/*", AnySchema.of()), Map.of(),
                            Optional.empty()),
                    new ResponseModel("4XX", jsonThenXml(), Map.of(), Optional.empty())));

    /** Answers anything at all, as a document with no stated type does. */
    private static final Operation LIST_VETS = Operation.of(HttpMethod.GET, "/vets")
            .withResponses(List.of(new ResponseModel("200", Map.of("*/*", AnySchema.of()),
                    Map.of(), Optional.empty())));

    /** Answers JSON for 200, but the document says more about it that could not be read. */
    private static final Operation LIST_VISITS = Operation.of(HttpMethod.GET, "/visits")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /**
     * Answers JSON for 200, and is named by its method and path because the document gives its name
     * to another operation too; nothing else about it was set aside.
     */
    private static final Operation LIST_CLINICS = Operation.of(HttpMethod.GET, "/clinics")
            .withResponses(List.of(ResponseModel.json("200", AnySchema.of())));

    /** Answers 200 with no type the document gives, and the document says more that was not read. */
    private static final Operation LIST_KENNELS = Operation.of(HttpMethod.GET, "/kennels")
            .withResponses(List.of(ResponseModel.empty("200")));

    private static final ApiModel API = ApiModel.of("Pets", "1.0.0",
                    List.of(ADD_PET, LIST_OWNERS, LIST_VETS, LIST_VISITS, LIST_CLINICS,
                            LIST_KENNELS))
            .withIssues(List.of(SpecificationIssue.degraded("paths./visits.get.responses.400",
                    LIST_VISITS.id(), "the response '400' could not be represented"),
                    SpecificationIssue.degraded("paths./kennels.get.responses.200",
                            LIST_KENNELS.id(), "the response '200' could not be represented"),
                    SpecificationIssue.renamed("paths./clinics.get", LIST_CLINICS.id(),
                            "renamed to stay unique")));

    private final UndeclaredContentTypeOracle oracle = new UndeclaredContentTypeOracle();

    /** Two types, in the order a document would list them. */
    private static Map<String, CanonicalSchema> jsonThenXml() {
        Map<String, CanonicalSchema> types = new LinkedHashMap<>();
        types.put("application/json", AnySchema.of());
        types.put("application/xml", AnySchema.of());
        return types;
    }

    /** A reply to a request with no body, of this status, with these headers and this body. */
    private List<Finding> judge(Operation operation, int status, List<Header> headers,
            Optional<Payload> body) {
        return oracle.judge(replied(operation, status, headers, body), API);
    }

    private static Interaction replied(Operation operation, int status, List<Header> headers,
            Optional<Payload> body) {
        return Attempts.replied(Attempts.testCase(operation.id()),
                Attempts.sentWithoutBody(operation.method(), operation.path()), status, headers,
                body);
    }

    /** Why the rule could not judge a reply of this status and type, with a body, if it could not. */
    private Optional<WhyNotJudged> why(Operation operation, int status, String contentType) {
        return oracle.verdict(replied(operation, status,
                        List.of(Header.of("Content-Type", contentType)),
                        Attempts.body("{}", "application/octet-stream")), API)
                .notJudged();
    }

    /** A reply of this status, with these headers and a body. */
    private List<Finding> judge(Operation operation, int status, List<Header> headers) {
        return judge(operation, status, headers, Attempts.body("{}", "application/octet-stream"));
    }

    private List<Finding> judge(Operation operation, int status, String contentType) {
        return judge(operation, status, List.of(Header.of("Content-Type", contentType)));
    }

    @Nested
    @DisplayName("a content type the document declares for the status code")
    class Declared {

        @ParameterizedTest
        @ValueSource(strings = {"application/json", "APPLICATION/JSON",
                "application/json; charset=utf-8", "application/json;charset=ISO-8859-1"})
        @DisplayName("is not reported, whatever its case and parameters")
        void is_not_reported(String contentType) {
            assertThat(judge(ADD_PET, 200, contentType)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"application/json", "application/xml", "application/pdf"})
        @DisplayName("by a range is not reported")
        void by_a_range(String contentType) {
            assertThat(judge(LIST_OWNERS, 200, contentType)).isEmpty();
        }

        @Test
        @DisplayName("for the range of the status code is not reported")
        void for_the_range_of_the_status_code() {
            assertThat(judge(LIST_OWNERS, 404, "application/xml")).isEmpty();
        }
    }

    @Nested
    @DisplayName("a content type the document does not declare for the status code")
    class Undeclared {

        @Test
        @DisplayName("is reported, saying what came and what the document declares")
        void is_reported() {
            List<Finding> found = judge(ADD_PET, 400, "application/problem+json");

            assertThat(found).hasSize(1);
            Finding finding = found.get(0);
            assertThat(finding.category()).isEqualTo(RestestFault.UNDECLARED_CONTENT_TYPE);
            assertThat(finding.operation()).isEqualTo(ADD_PET.id());
            assertThat(finding.summary()).isEqualTo("the API answered 400 as "
                    + "application/problem+json, which the document does not declare for that "
                    + "status; it declares application/json");
            assertThat(finding.details()).isEmpty();
        }

        @Test
        @DisplayName("is reported against every type the status code's range declares")
        void is_reported_against_a_range() {
            assertThat(judge(LIST_OWNERS, 409, "text/html; charset=utf-8"))
                    .extracting(Finding::summary)
                    .containsExactly("the API answered 409 as text/html; charset=utf-8, which the "
                            + "document does not declare for that status; it declares "
                            + "application/json, application/xml");
        }

        @ParameterizedTest
        @ValueSource(strings = {"text/html", "text/json", "application/vnd.pets+json"})
        @DisplayName("is reported when it only resembles one the document declares")
        void is_reported_when_it_only_resembles_one(String contentType) {
            assertThat(judge(ADD_PET, 200, contentType)).hasSize(1);
        }

        @Test
        @DisplayName("is reported when a body comes with no content type at all")
        void a_body_with_no_content_type() {
            assertThat(judge(ADD_PET, 200, List.of()))
                    .extracting(Finding::summary)
                    .containsExactly("the API answered 200 with a body and no Content-Type; the "
                            + "document declares application/json");
        }

        @Test
        @DisplayName("is reported when a body comes with an empty content type, as with none")
        void a_body_with_an_empty_content_type() {
            assertThat(judge(ADD_PET, 200, " "))
                    .extracting(Finding::summary)
                    .containsExactly("the API answered 200 with a body and no Content-Type; the "
                            + "document declares application/json");
        }

        @Test
        @DisplayName("is reported, as it was written, when it is not a content type at all")
        void one_that_is_not_a_content_type() {
            assertThat(judge(ADD_PET, 200, "garbage")).extracting(Finding::summary)
                    .containsExactly("the API answered 200 as garbage, which the document does "
                            + "not declare for that status; it declares application/json");
        }

        @Test
        @DisplayName("is judged by the first content type when the reply names several")
        void the_first_of_several_counts() {
            assertThat(judge(ADD_PET, 200, List.of(Header.of("Content-Type", "text/html"),
                    Header.of("Content-Type", "application/json")))).hasSize(1);
            assertThat(judge(ADD_PET, 200, List.of(Header.of("Content-Type", "application/json"),
                    Header.of("Content-Type", "text/html")))).isEmpty();
        }

        @Test
        @DisplayName("is quoted cut short, with what would control a terminal replaced")
        void is_quoted_safely() {
            String written = "text/" + "x".repeat(200) + "\u001b[31m";

            assertThat(judge(ADD_PET, 200, written)).extracting(Finding::summary)
                    .singleElement().asString()
                    .contains("as text/" + "x".repeat(95) + "...,")
                    .doesNotContain("\u001b");
            assertThat(UndeclaredContentTypeOracle.quoted("a\u001b[2Jb\tc"))
                    .isEqualTo("a?[2Jb?c");
            assertThat(UndeclaredContentTypeOracle.quoted("text/\u202Elmth\u200B"))
                    .describedAs("what would reverse the text after it, or hide in it, is replaced")
                    .isEqualTo("text/?lmth?");
            assertThat(UndeclaredContentTypeOracle.quoted("x".repeat(100)))
                    .describedAs("a value as long as what is quoted is quoted whole")
                    .isEqualTo("x".repeat(100));
            assertThat(UndeclaredContentTypeOracle.quoted("x".repeat(101)))
                    .isEqualTo("x".repeat(100) + "...");
        }

        @Test
        @DisplayName("is reported from what was kept, though only part of the body was")
        void is_reported_though_the_body_was_kept_in_part() {
            assertThat(oracle.judge(Attempts.answeredWithPartOfTheBody(ADD_PET.id(), "/pets", 200,
                    "text/html", "<html>", 5000), API)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("left alone")
    class LeftAlone {

        @ParameterizedTest
        @ValueSource(ints = {500, 503})
        @DisplayName("a server error, which other rules report")
        void a_server_error(int status) {
            assertThat(judge(ADD_PET, status, "application/problem+json")).isEmpty();
            assertThat(why(ADD_PET, status, "application/problem+json")).isEmpty();
        }

        @Test
        @DisplayName("a reply just below 500 is still judged, and 500 is not")
        void the_edge_of_the_server_errors() {
            assertThat(judge(LIST_OWNERS, 499, "text/html")).hasSize(1);
            assertThat(judge(ADD_PET, 500, "text/html")).isEmpty();
        }

        @Test
        @DisplayName("a reply with no body, whatever type it names: there is no body to call "
                + "anything")
        void a_reply_with_no_body() {
            assertThat(judge(ADD_PET, 200, List.of(Header.of("Content-Type", "text/html")),
                    Optional.empty()))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answeredWithoutBody(ADD_PET.id(), "/pets", 200,
                    "text/html"), API)).isEmpty();
            assertThat(oracle.verdict(Attempts.answeredWithoutBody(ADD_PET.id(), "/pets", 200,
                    "text/html"), API).notJudged()).isEmpty();
        }

        @Test
        @DisplayName("a status code the document does not declare, which is the other rule's")
        void a_status_code_the_document_does_not_declare() {
            assertThat(judge(ADD_PET, 201, "text/html")).isEmpty();
            assertThat(why(ADD_PET, 201, "text/html")).isEmpty();
        }

        @Test
        @DisplayName("a status code the document declares with no content type, though a body "
                + "came, which the rule says it could not judge")
        void a_status_code_declared_with_no_content() {
            assertThat(judge(ADD_PET, 404, "text/html")).isEmpty();
            assertThat(judge(ADD_PET, 404, List.of())).isEmpty();
            assertThat(why(ADD_PET, 404, "text/html"))
                    .contains(WhyNotJudged.NO_CONTENT_TYPE_DECLARED);
            assertThat(oracle.verdict(replied(ADD_PET, 404, List.of(),
                    Attempts.body("{}", "application/octet-stream")), API).notJudged())
                    .describedAs("with no Content-Type at all")
                    .contains(WhyNotJudged.NO_CONTENT_TYPE_DECLARED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"text/html", "garbage", " "})
        @DisplayName("a reply to an operation that declares any type at all, which nothing "
                + "contradicts")
        void anything_goes(String contentType) {
            assertThat(judge(LIST_VETS, 200, contentType)).isEmpty();
            assertThat(judge(LIST_VETS, 200, List.of())).isEmpty();
            assertThat(why(LIST_VETS, 200, contentType)).describedAs("judged, and fine").isEmpty();
        }

        @Test
        @DisplayName("an operation RESTest could not read in full, which the rule says it could not "
                + "judge, unless what came is a type it did read")
        void an_operation_read_in_part() {
            assertThat(judge(LIST_VISITS, 200, "text/html")).isEmpty();
            assertThat(why(LIST_VISITS, 200, "text/html")).contains(WhyNotJudged.READ_IN_PART);
            assertThat(judge(LIST_VISITS, 200, "application/json")).isEmpty();
            assertThat(why(LIST_VISITS, 200, "application/json")).isEmpty();
        }

        @Test
        @DisplayName("an operation read in part whose status code seems to come with no type says it "
                + "was read in part, since the type may be in the part that could not be read")
        void read_in_part_comes_before_declaring_no_type() {
            assertThat(why(LIST_KENNELS, 200, "text/html")).contains(WhyNotJudged.READ_IN_PART);
        }

        @Test
        @DisplayName("but not an operation RESTest only had to give another name, because the "
                + "document gives its name to another operation too: it was read whole")
        void not_an_operation_only_renamed() {
            assertThat(judge(LIST_CLINICS, 200, "text/html")).hasSize(1);
        }

        @Test
        @DisplayName("an operation the document does not declare")
        void an_operation_the_document_does_not_declare() {
            Interaction unknown = Attempts.answered(OperationId.of("GET /cats"), "/cats", 200,
                    "text/html", "<p>");

            assertThat(oracle.judge(unknown, API)).isEmpty();
            assertThat(oracle.verdict(unknown, API).notJudged()).isEmpty();
        }

        @Test
        @DisplayName("a reply that broke off, and a request that got none")
        void no_well_formed_reply() {
            assertThat(oracle.judge(Attempts.brokenOff(ADD_PET.id(), "/pets", 200), API))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.neverAnswered(ADD_PET.id(), "/pets"), API)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a request whose body went as a kind the operation does not accept")
    class BodyNotAccepted {

        private List<Finding> judge(HttpRequestRecord request) {
            return oracle.judge(Attempts.replied(Attempts.testCase(ADD_PET.id()), request, 400,
                    List.of(Header.of("Content-Type", "text/html")),
                    Attempts.body("<p>Bad request</p>", "text/html")), API);
        }

        @Test
        @DisplayName("is left alone")
        void is_left_alone() {
            assertThat(judge(Attempts.sentWithBody(HttpMethod.POST, "/pets", "application/xml")))
                    .isEmpty();
        }

        @Test
        @DisplayName("is not a body under the accepted kind written differently")
        void the_accepted_kind_written_differently_is_judged() {
            assertThat(judge(Attempts.sentWithBody(HttpMethod.POST, "/pets",
                    "Application/JSON; charset=UTF-8"))).hasSize(1);
        }
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("undeclared-content-type");
        assertThat(oracle.kind()).isEqualTo(RestestFault.UNDECLARED_CONTENT_TYPE);
        assertThat(oracle.what()).isEqualTo("the content type");
        assertThat(oracle.description()).contains("content type").contains("500");
    }
}
