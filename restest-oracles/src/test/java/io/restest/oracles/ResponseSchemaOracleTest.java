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

import io.restest.core.model.ApiModel;
import io.restest.core.model.OperationId;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.WfcFault;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResponseSchemaOracleTest {

    private static final OperationId LIST_PETS = OperationId.of("GET /pets");
    private static final OperationId ONE_PET = OperationId.of("GET /pets/{petId}");
    private static final OperationId KINDS = OperationId.of("GET /kinds");
    private static final OperationId RAW = OperationId.of("GET /raw");
    private static final OperationId UNSAID = OperationId.of("GET /unsaid");
    private static final String JSON = "application/json";

    private final ResponseSchemaOracle oracle = new ResponseSchemaOracle();
    private final ApiModel pets = Specifications.pets();

    @Nested
    @DisplayName("a reply that does not match what the document promised")
    class Mismatches {

        @Test
        @DisplayName("a field of the wrong kind names the field and what was expected")
        void a_field_of_the_wrong_type_is_reported() {
            List<Finding> found = oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": \"seven\", \"name\": \"Rex\"}"), pets);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).category()).isEqualTo(WfcFault.SCHEMA_INVALID_RESPONSE);
            assertThat(found.get(0).operation()).isEqualTo(ONE_PET);
            assertThat(found.get(0).details()).anySatisfy(detail ->
                    assertThat(detail).contains("/id").contains("integer"));
        }

        @Test
        @DisplayName("a field the document says must be there, and is not, is reported")
        void a_missing_required_field_is_reported() {
            List<Finding> found = oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": 7}"), pets);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).details()).anySatisfy(detail ->
                    assertThat(detail).contains("name"));
        }

        @Test
        @DisplayName("a field the document did not allow at all is reported")
        void an_undeclared_field_is_reported() {
            List<Finding> found = oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": 7, \"name\": \"Rex\", \"colour\": \"brown\"}"), pets);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).details()).anySatisfy(detail ->
                    assertThat(detail).contains("colour"));
        }

        @Test
        @DisplayName("a list whose entries are wrong names the entry, not just the list")
        void a_bad_entry_in_a_list_is_located() {
            List<Finding> found = oracle.judge(Attempts.answered(LIST_PETS, "/pets", 200, JSON,
                    "[{\"id\": 1, \"name\": \"Rex\"}, {\"id\": 2}]"), pets);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).details()).anySatisfy(detail ->
                    assertThat(detail).contains("/1"));
        }

        @Test
        @DisplayName("a body that is not JSON at all, where JSON was promised, is reported")
        void a_body_that_is_not_json_is_reported() {
            List<Finding> found = oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "<html><body>Sorry</body></html>"), pets);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).summary()).contains("not JSON");
        }

        @Test
        @DisplayName("a body that is JSON and then carries on being something else is reported")
        void a_body_with_anything_after_the_json_is_reported() {
            // Three real ways a reply goes wrong after it has started well: the handler wrote the
            // payload twice, an error page was appended, a warning was printed after the answer.
            // Each of them is a body no client can read, and each has to be said out loud - a
            // reader that stopped at the end of the first value would call all three correct.
            for (String body : List.of(
                    "{\"id\": 7, \"name\": \"Rex\"}{\"id\": 8, \"name\": \"Bo\"}",
                    "{\"id\": 7, \"name\": \"Rex\"}\n<html>Fatal error</html>",
                    "{\"id\": 7, \"name\": \"Rex\"}\nWarning: connection reused")) {
                List<Finding> found = oracle.judge(
                        Attempts.answered(ONE_PET, "/pets/7", 200, JSON, body), pets);

                assertThat(found)
                        .describedAs("a reply that carries on after its JSON ends: %s", body)
                        .hasSize(1);
                assertThat(found.get(0).summary()).contains("not JSON");
            }
        }

        @Test
        @DisplayName("the fault carries the request that caused it, so a report can repeat it")
        void the_finding_carries_its_own_evidence() {
            Finding finding = oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": \"seven\"}"), pets).get(0);

            assertThat(finding.interaction().request().url()).isEqualTo(Attempts.BASE + "/pets/7");
            assertThat(finding.summary()).contains("200").contains(JSON);
        }
    }

    @Nested
    @DisplayName("a reply the document is happy with")
    class Matches {

        @Test
        @DisplayName("a reply the document writes once and points at is still checked")
        void a_reply_declared_by_reference_is_followed() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /byreference"),
                    "/byreference", 200, JSON, "{\"id\": 7, \"name\": \"Rex\"}"), pets))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /byreference"),
                    "/byreference", 200, JSON, "{\"id\": \"seven\"}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("a name holding a per-cent sign that is not an escape is followed as written")
        void a_name_with_a_stray_per_cent_sign_is_followed() {
            // Two spellings of a trail of names cannot be told apart by looking. Following this one
            // as the document wrote it is the only reading that leads anywhere, and pasting it into
            // a web address unexamined is what used to throw and lose the operation without a word.
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /strayescape"),
                    "/strayescape", 200, JSON, "{\"id\": \"seven\"}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("a pointer written the way a web address requires is followed too")
        void a_pointer_written_for_a_web_address_is_followed() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /encodedref"),
                    "/encodedref", 200, JSON, "{\"id\": \"seven\"}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("a media type holding a plus sign is looked for under its own name")
        void a_media_type_with_a_plus_sign_is_found() {
            // A plus sign is legal in a web address and is read back as a space, so a name written
            // with one and then escaped anywhere else in the same name goes looking for something
            // that was never filed. RFC 7807's media type is the everyday case.
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /problem"), "/problem",
                    200, "application/problem+json", "{\"title\": \"gone wrong\"}"), pets))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /problem"), "/problem",
                    200, "application/problem+json", "{}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("a reference to a reference is followed too")
        void a_chain_of_references_is_followed() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /bychain"), "/bychain",
                    200, JSON, "{\"id\": \"seven\"}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("something stated in a declared format is not held to that format's rules")
        void a_declared_format_is_not_asserted() {
            // A Java service returning a date with no time zone writes it exactly like this, and a
            // document generated from that service calls it a date-time. Both are ordinary; neither
            // is a fault, and a tool that says otherwise is one nobody leaves switched on.
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /seen"), "/seen", 200,
                    JSON, "{\"at\": \"2026-09-13T10:30:00\"}"), pets)).isEmpty();
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /seen"), "/seen", 200,
                    JSON, "{\"at\": \"not a date at all\"}"), pets)).isEmpty();
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /seen"), "/seen", 200,
                    JSON, "{\"at\": 7}"), pets)).hasSize(1);
        }

        @Test
        @DisplayName("a reply of exactly the promised shape passes")
        void a_matching_reply_passes() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": 7, \"name\": \"Rex\", \"tag\": \"dog\"}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a field the document says may be absent-valued may be absent-valued")
        void a_nullable_field_may_be_null() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": 7, \"name\": null}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a shape written as a choice of two is judged as a choice of two")
        void a_choice_of_shapes_is_honoured() {
            assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "\"dog\""), pets))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "4"), pets))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "true"), pets))
                    .hasSize(1);
        }

        @Test
        @DisplayName("the shape declared for anything else is used when nothing more exact applies")
        void the_catch_all_reply_is_used() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 404, JSON,
                    "{\"message\": \"no such pet\"}"), pets)).isEmpty();
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 404, JSON, "{}"), pets))
                    .hasSize(1);
        }

        @Test
        @DisplayName("a document written in the newer OpenAPI is read by its own rules")
        void a_3_1_document_is_read_as_3_1() {
            ApiModel kinds = Specifications.kinds31();

            assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "null"), kinds))
                    .isEmpty();
            assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "3"), kinds))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("what cannot be judged is never reported")
    class Silence {

        @Test
        @DisplayName("a request that got no answer says nothing about any shape")
        void a_request_with_no_answer_is_left_alone() {
            assertThat(oracle.judge(Attempts.neverAnswered(ONE_PET, "/pets/7"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply that is not JSON is not held to a JSON shape")
        void a_reply_of_another_kind_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(RAW, "/raw", 200, "text/plain", "hello"),
                    pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply that says nothing about its own kind is left alone")
        void a_reply_without_a_content_type_is_left_alone() {
            assertThat(oracle.judge(Attempts.answeredWithoutContentType(ONE_PET, "/pets/7", 200),
                    pets)).isEmpty();
        }

        @Test
        @DisplayName("a status the document says nothing about is left alone")
        void an_undeclared_status_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(LIST_PETS, "/pets", 418, JSON, "{}"), pets))
                    .isEmpty();
        }

        @Test
        @DisplayName("a reply whose shape the document leaves out is left alone")
        void a_declared_reply_with_no_shape_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(UNSAID, "/unsaid", 200, JSON,
                    "anything at all, and not even JSON"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply kept only in part is not judged on the part that was kept")
        void a_truncated_reply_is_left_alone() {
            assertThat(oracle.judge(Attempts.answeredWithPartOfTheBody(ONE_PET, "/pets/7", 200,
                    JSON, "{\"id\": 7, \"na", 4096), pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply insisting on some other character set is not read wrongly and blamed")
        void a_reply_in_another_character_set_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                    "application/json; charset=iso-8859-1", "{\"id\": \"seven\"}"), pets))
                    .isEmpty();
        }

        @Test
        @DisplayName("an API whose document was not kept has nothing to be judged against")
        void an_api_without_its_document_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200, JSON,
                    "{\"id\": \"seven\"}"), Specifications.petsWithoutItsDocument())).isEmpty();
        }

        @Test
        @DisplayName("a reply the document declares without saying what comes back is left alone")
        void a_declared_reply_with_no_content_at_all_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /noreply"), "/noreply",
                    200, JSON, "{\"anything\": true}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a JSON reply where the document only ever promised XML is left alone")
        void a_media_type_the_document_never_declared_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /onlyxml"), "/onlyxml",
                    200, JSON, "{\"anything\": true}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a shape the document names and never defines is left alone, not guessed at")
        void a_shape_the_document_never_defines_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /dangling"), "/dangling",
                    200, JSON, "{\"anything\": true}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a media type carrying something other than a character set is still judged")
        void a_media_type_with_another_parameter_is_still_judged() {
            assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                    "application/json; boundary=x", "{\"id\": 7, \"name\": \"Rex\"}"), pets))
                    .isEmpty();
        }

        @Test
        @DisplayName("replies pointing round at each other for ever are given up on, not followed")
        void a_cycle_of_pointers_is_given_up_on() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /cycle"), "/cycle", 200,
                    JSON, "{\"id\": \"seven\"}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a pointer at something the document never declares is left alone")
        void a_pointer_at_nothing_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /nowhere"), "/nowhere",
                    200, JSON, "{\"id\": \"seven\"}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply pointing into another document is left alone, not guessed at")
        void a_reference_to_another_document_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /elsewhere"),
                    "/elsewhere", 200, JSON, "{\"id\": \"seven\"}"), pets)).isEmpty();
        }

        @Test
        @DisplayName("a reply with no body is left alone, because HTTP has replies with no body")
        void a_reply_with_no_body_is_left_alone() {
            assertThat(oracle.judge(
                    Attempts.answeredWithoutBody(ONE_PET, "/pets/7", 200, JSON), pets)).isEmpty();
        }

        @Test
        @DisplayName("an operation the model does not know about is left alone")
        void an_unknown_operation_is_left_alone() {
            assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /elsewhere"),
                    "/elsewhere", 200, JSON, "{\"id\": \"seven\"}"), pets)).isEmpty();
        }
    }

    @Test
    @DisplayName("a second API is judged by its own document, not by the one seen before it")
    void a_second_api_is_judged_by_its_own_document() {
        ApiModel newer = Specifications.kinds31();
        ApiModel older = Specifications.kinds30();

        // The same trail through two documents, pointing at two different shapes: text-or-nothing
        // in one, a whole number in the other. Anything remembering a shape by its trail alone
        // would answer the second question with the first document's answer.
        assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "null"), newer))
                .isEmpty();
        assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "null"), older))
                .hasSize(1);
        assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "3"), older))
                .isEmpty();
        assertThat(oracle.judge(Attempts.answered(KINDS, "/kinds", 200, JSON, "3"), newer))
                .hasSize(1);
    }

    @Test
    @DisplayName("a media type naming a parameter with no value at all is still judged")
    void a_parameter_without_a_value_is_ignored() {
        assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                "application/json; odd", "{\"id\": \"seven\"}"), pets)).hasSize(1);
        assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                "application/json; CHARSET=UTF-8", "{\"id\": \"seven\"}"), pets)).hasSize(1);
    }

    @Test
    @DisplayName("a complaint about a body is quoted as its first line, whatever it looks like")
    void a_complaint_is_quoted_as_one_line() {
        assertThat(ResponseSchemaOracle.firstLineOf(
                new IllegalStateException("first line\nat [Source: REDACTED]")))
                .isEqualTo("first line");
        assertThat(ResponseSchemaOracle.firstLineOf(new IllegalStateException((String) null)))
                .isEqualTo("IllegalStateException");
        assertThat(ResponseSchemaOracle.firstLineOf(new IllegalStateException("  \n  ")))
                .isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("the rule says what it is called and what it checks")
    void the_rule_describes_itself() {
        assertThat(oracle.name()).isEqualTo("response-schema");
        assertThat(oracle.description()).contains("specification");
    }

    @Test
    @DisplayName("a charset of utf-8, however written, is read as usual")
    void an_explicit_utf8_charset_is_still_judged() {
        assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                "application/json; charset=UTF-8", "{\"id\": \"seven\"}"), pets)).hasSize(1);
        assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                "application/json; charset=\"utf-8\"", "{\"id\": \"seven\"}"), pets))
                .hasSize(1);
        assertThat(oracle.judge(Attempts.answered(ONE_PET, "/pets/7", 200,
                "application/json; charset=", "{\"id\": \"seven\"}"), pets)).hasSize(1);
    }

    @Test
    @DisplayName("a document that writes a charset into the type it declares is still matched")
    void a_declared_media_type_carrying_a_charset_is_matched() {
        assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /charsetkey"), "/charsetkey",
                200, JSON, "7"), pets)).isEmpty();
        assertThat(oracle.judge(Attempts.answered(OperationId.of("GET /charsetkey"), "/charsetkey",
                200, JSON, "\"seven\""), pets)).hasSize(1);
    }
}
