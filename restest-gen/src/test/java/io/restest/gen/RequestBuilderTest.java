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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.ParameterValue;
import io.restest.core.execution.TestCase;
import io.restest.core.execution.ValueOrigin;
import io.restest.core.json.JsonValue;
import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.Parameter;
import io.restest.core.model.ParameterLocation;
import io.restest.core.model.ParameterStyle;
import io.restest.core.model.RequestBodyModel;
import io.restest.core.model.ResponseModel;
import io.restest.core.schema.ArraySchema;
import io.restest.core.schema.CanonicalSchema;
import io.restest.core.schema.NumberKind;
import io.restest.core.schema.NumberSchema;
import io.restest.core.schema.ObjectSchema;
import io.restest.core.schema.StringSchema;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A test case names values; a request is what actually goes on the wire. Getting from one to the
 * other is where a wrongly written URL turns a good value into a request the API cannot answer.
 */
class RequestBuilderTest {

    private static final String BASE = "https://example.com/api/v3";

    @Test
    @DisplayName("a value in the path is put where the gap is")
    void a_path_parameter_fills_its_gap() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}",
                List.of(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        HttpRequestRecord request = build(operation,
                value("petId", ParameterLocation.PATH, JsonValue.of("p-1")));

        assertThat(request.url()).isEqualTo("https://example.com/api/v3/pets/p-1");
        assertThat(request.method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    @DisplayName("a value that would change the shape of the URL is encoded instead")
    void a_path_value_is_encoded() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}",
                List.of(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        HttpRequestRecord request = build(operation,
                value("petId", ParameterLocation.PATH, JsonValue.of("a/b cé")));

        assertThat(request.url())
                .describedAs("a slash in a value must not become another part of the path")
                .isEqualTo("https://example.com/api/v3/pets/a%2Fb%20c%C3%A9");
    }

    @Test
    @DisplayName("values in the query string are named and encoded")
    void query_parameters_are_written_after_the_question_mark() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, false, StringSchema.of()),
                Parameter.of("limit", ParameterLocation.QUERY, false,
                        NumberSchema.of(NumberKind.INTEGER))));

        HttpRequestRecord request = build(operation,
                value("status", ParameterLocation.QUERY, JsonValue.of("for sale")),
                value("limit", ParameterLocation.QUERY, JsonValue.of(20)));

        assertThat(request.url())
                .isEqualTo("https://example.com/api/v3/pets?status=for%20sale&limit=20");
    }

    @Test
    @DisplayName("a list in the query string is repeated or joined, as the specification asks")
    void a_list_is_written_the_way_the_document_asks() {
        Parameter exploded = new Parameter("tags", ParameterLocation.QUERY, false,
                ArraySchema.of(StringSchema.of()), ParameterStyle.FORM, true, Optional.empty(),
                Optional.empty(), List.of());
        Parameter joined = new Parameter("tags", ParameterLocation.QUERY, false,
                ArraySchema.of(StringSchema.of()), ParameterStyle.FORM, false, Optional.empty(),
                Optional.empty(), List.of());
        JsonValue tags = JsonValue.array(JsonValue.of("cat"), JsonValue.of("dog"));

        assertThat(build(Operation.of(HttpMethod.GET, "/pets", List.of(exploded)),
                value("tags", ParameterLocation.QUERY, tags)).url())
                .isEqualTo("https://example.com/api/v3/pets?tags=cat&tags=dog");
        assertThat(build(Operation.of(HttpMethod.GET, "/pets", List.of(joined)),
                value("tags", ParameterLocation.QUERY, tags)).url())
                .isEqualTo("https://example.com/api/v3/pets?tags=cat%2Cdog");
    }

    @Test
    @DisplayName("an object in the query string becomes its own named values when exploded")
    void an_object_is_written_as_its_members() {
        Parameter filter = new Parameter("filter", ParameterLocation.QUERY, false,
                ObjectSchema.of(Map.of()), ParameterStyle.FORM, true, Optional.empty(),
                Optional.empty(), List.of());
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("colour", JsonValue.of("black"));
        members.put("age", JsonValue.of(3));

        HttpRequestRecord request = build(
                Operation.of(HttpMethod.GET, "/pets", List.of(filter)),
                value("filter", ParameterLocation.QUERY, JsonValue.object(members)));

        assertThat(request.url()).isEqualTo("https://example.com/api/v3/pets?colour=black&age=3");
    }

    @Test
    @DisplayName("a header parameter becomes a header, and a cookie parameter becomes a cookie")
    void headers_and_cookies_are_set() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("X-Trace", ParameterLocation.HEADER, false, StringSchema.of()),
                Parameter.of("session", ParameterLocation.COOKIE, false, StringSchema.of()),
                Parameter.of("theme", ParameterLocation.COOKIE, false, StringSchema.of())));

        HttpRequestRecord request = build(operation,
                value("X-Trace", ParameterLocation.HEADER, JsonValue.of("abc-123")),
                value("session", ParameterLocation.COOKIE, JsonValue.of("s-1")),
                value("theme", ParameterLocation.COOKIE, JsonValue.of("dark")));

        assertThat(request.headerValues("X-Trace")).containsExactly("abc-123");
        assertThat(request.headerValues("Cookie"))
                .describedAs("every cookie travels in one header, as HTTP requires")
                .containsExactly("session=s-1; theme=dark");
        assertThat(request.url()).isEqualTo("https://example.com/api/v3/pets");
    }

    @Test
    @DisplayName("an operation with nothing to fill in is just its address")
    void an_operation_without_parameters_is_just_its_url() {
        assertThat(build(Operation.of(HttpMethod.GET, "/pets")).url())
                .isEqualTo("https://example.com/api/v3/pets");
    }

    @Test
    @DisplayName("a base address written with a trailing slash does not produce a doubled one")
    void a_trailing_slash_on_the_base_address_is_tolerated() {
        HttpRequestRecord request = RequestBuilder.build(Operation.of(HttpMethod.GET, "/pets"),
                TestCase.of(Operation.of(HttpMethod.GET, "/pets").id(), List.of()),
                "https://example.com/api/v3/");

        assertThat(request.url()).isEqualTo("https://example.com/api/v3/pets");
    }

    @Test
    @DisplayName("an optional value that was not chosen simply does not appear")
    void a_parameter_without_a_value_is_left_out() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("status", ParameterLocation.QUERY, false, StringSchema.of())));

        assertThat(build(operation).url()).isEqualTo("https://example.com/api/v3/pets");
    }

    @Test
    @DisplayName("a gap in the path with no value to fill it is refused, not papered over")
    void a_missing_path_value_is_refused() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}",
                List.of(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> build(operation))
                .withMessageContaining("petId");
    }

    @Test
    @DisplayName("a parameter written in a way this does not understand is named, not guessed at")
    void an_unsupported_style_is_named() {
        Parameter deepObject = new Parameter("filter", ParameterLocation.QUERY, false,
                ObjectSchema.of(Map.of()), ParameterStyle.DEEP_OBJECT, true, Optional.empty(),
                Optional.empty(), List.of());
        Parameter asJson = Parameter.ofContent("filter", ParameterLocation.QUERY, false,
                ObjectSchema.of(Map.of()), "application/json");

        assertThat(RequestBuilder.whatCannotBeAssembled(
                Operation.of(HttpMethod.GET, "/pets", List.of(deepObject))))
                .hasValueSatisfying(reason -> assertThat(reason).contains("DEEP_OBJECT"));
        assertThat(RequestBuilder.whatCannotBeAssembled(
                Operation.of(HttpMethod.GET, "/pets", List.of(asJson))))
                .hasValueSatisfying(reason -> assertThat(reason).contains("application/json"));
        assertThat(RequestBuilder.whatCannotBeAssembled(Operation.of(HttpMethod.GET, "/pets")))
                .isEmpty();
    }

    @Test
    @DisplayName("a GET or a HEAD that insists on a body cannot be assembled, and says so plainly")
    void a_body_insisted_on_by_a_get_or_a_head_is_named() {
        RequestBodyModel required = RequestBodyModel.json(
                ObjectSchema.of(Map.of("name", StringSchema.of())), true);

        assertThat(RequestBuilder.whatCannotBeAssembled(
                Operation.of(HttpMethod.GET, "/pets/search").withRequestBody(required)))
                .hasValue("it requires a request body, and a GET request cannot carry one");
        assertThat(RequestBuilder.whatCannotBeAssembled(
                Operation.of(HttpMethod.HEAD, "/pets/search").withRequestBody(required)))
                .hasValue("it requires a request body, and a HEAD request cannot carry one");
    }

    @Test
    @DisplayName("a body a GET merely accepts stops nothing, because the request can go without it")
    void a_body_a_get_merely_accepts_stops_nothing() {
        Operation search = Operation.of(HttpMethod.GET, "/pets/search").withRequestBody(
                RequestBodyModel.json(ObjectSchema.of(Map.of("name", StringSchema.of())), false));

        assertThat(RequestBuilder.whatCannotBeAssembled(search)).isEmpty();
    }

    @Test
    @DisplayName("every other method has room for the body it insists on")
    void every_other_method_may_insist_on_a_body() {
        RequestBodyModel required = RequestBodyModel.json(StringSchema.of(), true);

        for (HttpMethod method : HttpMethod.values()) {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                continue;
            }
            assertThat(RequestBuilder.whatCannotBeAssembled(
                    Operation.of(method, "/pets").withRequestBody(required)))
                    .describedAs("a %s request with the body its document requires", method)
                    .isEmpty();
        }
    }

    // --- what review found this builder getting wrong ------------------------------------------

    @Test
    @DisplayName("an empty value in the path is refused, because it would address something else")
    void an_empty_path_value_is_refused() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets/{petId}/tags",
                List.of(Parameter.of("petId", ParameterLocation.PATH, true, StringSchema.of())));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> build(operation,
                        value("petId", ParameterLocation.PATH, JsonValue.of(""))))
                .withMessageContaining("would address a different resource");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> build(operation,
                        value("petId", ParameterLocation.PATH, JsonValue.NULL)))
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("an address that already carries a query string of its own is refused")
    void a_base_address_with_a_query_string_is_refused() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets");
        TestCase testCase = TestCase.of(operation.id(), List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestBuilder.build(operation, testCase,
                        "https://example.com/api?key=secret"))
                .withMessageContaining("query string");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestBuilder.build(operation, testCase, "  "))
                .withMessageContaining("address");
    }

    @Test
    @DisplayName("several trailing slashes on the address do not become part of the path")
    void repeated_trailing_slashes_are_trimmed() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets");

        assertThat(RequestBuilder.build(operation, TestCase.of(operation.id(), List.of()),
                "https://example.com/api//").url())
                .isEqualTo("https://example.com/api/pets");
    }

    @Test
    @DisplayName("a header value that would split the request in two is refused")
    void a_header_value_containing_a_line_break_is_refused() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("X-Trace", ParameterLocation.HEADER, false, StringSchema.of())));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> build(operation, value("X-Trace", ParameterLocation.HEADER,
                        JsonValue.of("a\r\nX-Injected: 1"))))
                .withMessageContaining("no header can carry");
    }

    @Test
    @DisplayName("a value only a header could not carry is turned down for a header and nowhere else")
    void what_a_header_cannot_carry_is_not_chosen_for_one() {
        // The engine refuses a header value outside printable ASCII and the request is never sent.
        // Asking the same question here is what keeps a list of deliberately awkward values from
        // spending a run on requests that cannot leave the machine - and it is asked of the header
        // only, because a query string is percent-encoded and carries anything.
        for (String refused : List.of("\u001b[31m", "\ud83d\ude42", "مرحبا", "a\nb", "\u0000")) {
            assertThat(RequestBuilder.canBeSentFrom(JsonValue.of(refused), ParameterLocation.HEADER))
                    .describedAs("a header cannot carry %s", refused).isFalse();
            assertThat(RequestBuilder.canBeSentFrom(JsonValue.of(refused), ParameterLocation.QUERY))
                    .describedAs("a query string carries %s, encoded", refused).isTrue();
        }
        for (String carried : List.of("", " ", "\t", "plain", "'\"<>", "../..")) {
            assertThat(RequestBuilder.canBeSentFrom(JsonValue.of(carried), ParameterLocation.HEADER))
                    .describedAs("a header carries %s", carried).isTrue();
        }
    }

    @Test
    @DisplayName("a cookie value containing a separator does not become two cookies")
    void a_cookie_value_is_encoded() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("session", ParameterLocation.COOKIE, false, StringSchema.of())));

        HttpRequestRecord request = build(operation,
                value("session", ParameterLocation.COOKIE, JsonValue.of("a; theme=dark")));

        assertThat(request.headerValues("Cookie")).containsExactly("session=a%3B%20theme%3Ddark");
    }

    @Test
    @DisplayName("a JSON body travels as JSON, and the request says so")
    void a_json_body_is_written_out() {
        Operation operation = Operation.of(HttpMethod.POST, "/pets")
                .withRequestBody(RequestBodyModel.json(ObjectSchema.of(Map.of()), true));
        BodyValue body = new BodyValue("application/json",
                JsonValue.object(Map.of("name", JsonValue.of("Bobby"))),
                new ValueOrigin.Generated("test"));

        HttpRequestRecord request = RequestBuilder.build(operation,
                TestCase.of(operation.id(), List.of(), body), BASE);

        assertThat(new String(request.body().orElseThrow().content(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"Bobby\"}");
        assertThat(request.body().orElseThrow().mediaType()).isEqualTo("application/json");
        assertThat(request.headerValues("Content-Type")).containsExactly("application/json");
    }

    @Test
    @DisplayName("a form body travels as the fields of a form, encoded")
    void a_form_body_is_written_out() {
        Operation operation = Operation.of(HttpMethod.POST, "/features")
                .withRequestBody(RequestBodyModel.ofShapes(true,
                        Map.of("application/x-www-form-urlencoded", ObjectSchema.of(Map.of()))));
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("name", JsonValue.of("a name with spaces & an ampersand"));
        fields.put("tags", JsonValue.array(List.of(JsonValue.of("one"), JsonValue.of("two"))));
        BodyValue body = new BodyValue("application/x-www-form-urlencoded",
                JsonValue.object(fields), new ValueOrigin.Generated("test"));

        HttpRequestRecord request = RequestBuilder.build(operation,
                TestCase.of(operation.id(), List.of(), body), BASE);

        assertThat(new String(request.body().orElseThrow().content(), StandardCharsets.UTF_8))
                .isEqualTo("name=a%20name%20with%20spaces%20%26%20an%20ampersand&tags=one&tags=two");
        assertThat(request.headerValues("Content-Type"))
                .containsExactly("application/x-www-form-urlencoded");
    }

    @Test
    @DisplayName("a body that is not an object cannot be written as a web form")
    void a_form_body_has_to_be_an_object() {
        Operation operation = Operation.of(HttpMethod.POST, "/features")
                .withRequestBody(RequestBodyModel.ofShapes(true,
                        Map.of("application/x-www-form-urlencoded", StringSchema.of())));
        BodyValue body = new BodyValue("application/x-www-form-urlencoded",
                JsonValue.of("just a word"), new ValueOrigin.Generated("test"));

        assertThatIllegalArgumentException().isThrownBy(() -> RequestBuilder.build(operation,
                TestCase.of(operation.id(), List.of(), body), BASE));
    }

    @Test
    @DisplayName("the request says what it will accept, from what the operation says it returns")
    void the_request_declares_what_it_accepts() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets")
                .withResponses(List.of(
                        new ResponseModel("200", Map.of("application/vnd.example.v2+json",
                                ObjectSchema.of(Map.of())), Map.of(), Optional.empty()),
                        new ResponseModel("404", Map.of("application/problem+json",
                                ObjectSchema.of(Map.of())), Map.of(), Optional.empty())));

        assertThat(build(operation).headerValues("Accept"))
                .describedAs("only what a successful answer would be written in")
                .containsExactly("application/vnd.example.v2+json");
    }

    @Test
    @DisplayName("what we can read back is asked for first, and the rest at a lower quality")
    void a_reply_we_could_judge_is_preferred() {
        Map<String, CanonicalSchema> served = new LinkedHashMap<>();
        served.put("application/xml", ObjectSchema.of(Map.of()));
        served.put("application/json", ObjectSchema.of(Map.of()));
        Operation operation = Operation.of(HttpMethod.GET, "/pets")
                .withResponses(List.of(new ResponseModel("200", served, Map.of(),
                        Optional.empty())));

        assertThat(build(operation).headerValues("Accept"))
                .describedAs("several documents in the corpus offer XML before JSON, and a reply "
                        + "in XML is one no oracle here can judge against the declared shape")
                .containsExactly("application/json, application/xml;q=0.5");
    }

    @Test
    @DisplayName("an API that serves nothing we can read is still asked for what it does serve")
    void a_reply_we_cannot_judge_is_still_asked_for() {
        Operation operation = Operation.of(HttpMethod.GET, "/report")
                .withResponses(List.of(new ResponseModel("200",
                        Map.of("text/csv", StringSchema.of()), Map.of(), Optional.empty())));

        assertThat(build(operation).headerValues("Accept"))
                .describedAs("asking for nothing but JSON would earn a 406 from an API that never "
                        + "offered any")
                .containsExactly("text/csv");
    }

    @Test
    @DisplayName("an operation that declares nothing it returns asks for nothing in particular")
    void a_request_may_say_nothing_about_what_it_accepts() {
        assertThat(build(Operation.of(HttpMethod.GET, "/pets")).headerValues("Accept")).isEmpty();
    }

    @Test
    @DisplayName("a document that declares an Accept header of its own keeps the value it chose")
    void a_declared_accept_header_is_not_overwritten() {
        Operation operation = Operation.of(HttpMethod.GET, "/pets", List.of(
                Parameter.of("Accept", ParameterLocation.HEADER, true, StringSchema.of())))
                .withResponses(List.of(ResponseModel.json("200", ObjectSchema.of(Map.of()))));

        HttpRequestRecord request = build(operation,
                value("Accept", ParameterLocation.HEADER, JsonValue.of("text/csv")));

        assertThat(request.headerValues("Accept")).containsExactly("text/csv");
    }

    @Test
    @DisplayName("JSON is preferred, a range is answered with JSON, and a file upload with nothing")
    void the_media_type_to_send_is_chosen_from_what_the_document_offers() {
        Map<String, CanonicalSchema> both = new LinkedHashMap<>();
        both.put("application/xml", ObjectSchema.of(Map.of()));
        both.put("application/json", ObjectSchema.of(Map.of()));

        assertThat(RequestBuilder.mediaTypeToSend(RequestBodyModel.ofShapes(true, both)))
                .describedAs("JSON wherever it is offered, whatever order the document wrote")
                .contains("application/json");
        assertThat(RequestBuilder.mediaTypeToSend(RequestBodyModel.ofShapes(true,
                Map.of("application/vnd.example.v2+json", ObjectSchema.of(Map.of())))))
                .describedAs("a versioned JSON media type is JSON, and is declared as written")
                .contains("application/vnd.example.v2+json");
        assertThat(RequestBuilder.mediaTypeToSend(RequestBodyModel.ofShapes(true,
                Map.of("*/*", ObjectSchema.of(Map.of())))))
                .describedAs("a range is a promise to accept several things; one has to be named")
                .contains("application/json");
        assertThat(RequestBuilder.mediaTypeToSend(RequestBodyModel.ofShapes(true,
                Map.of("application/x-www-form-urlencoded", ObjectSchema.of(Map.of())))))
                .contains("application/x-www-form-urlencoded");
        assertThat(RequestBuilder.mediaTypeToSend(RequestBodyModel.ofShapes(true,
                Map.of("multipart/form-data", ObjectSchema.of(Map.of())))))
                .describedAs("a file upload is not written yet, and is not guessed at")
                .isEmpty();
    }

    private static HttpRequestRecord build(Operation operation, ParameterValue... values) {
        return RequestBuilder.build(operation, TestCase.of(operation.id(), List.of(values)), BASE);
    }

    private static ParameterValue value(String name, ParameterLocation location, JsonValue value) {
        return ParameterValue.of(name, location, value, new ValueOrigin.Generated("test"));
    }
}
