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

import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import io.restest.core.model.ResponseModel;
import io.restest.core.oracle.FaultCategory;
import io.restest.core.oracle.Finding;
import io.restest.core.oracle.RestestFault;
import io.restest.core.oracle.WhyNotJudged;
import io.restest.core.schema.CanonicalSchema;
import java.util.Map;
import java.util.Optional;

/**
 * Reports a reply whose content type the API's document does not declare for its status code.
 *
 * <p>A document says, for each status code an operation answers with, what kind of body comes with
 * it: {@code application/json}, say. A program reading the reply relies on that. An API that sends
 * its errors as {@code application/problem+json}, or an HTML error page, where the document promises
 * JSON, has drifted from what it tells its users, and so has one that sends a body without saying
 * what it is. This rule says so.
 *
 * <p>The comparison is the one HTTP makes: case and parameters such as {@code charset} do not count,
 * and a range the document declares, {@code application/*}, covers every type it names. A type
 * that only resembles a declared one does not: {@code application/problem+json} is not
 * {@code application/json}, though both are JSON, because a program asking for the one has not been
 * told to expect the other.
 *
 * <p>Only a reply with a body, whose status code the document declares, is looked at: a code the
 * document leaves out is another rule's to report. A document declaring <code>*&#47;*</code> has
 * promised no type in particular, so nothing a reply calls itself contradicts it. Server errors, and
 * every reply {@link HeldToTheDocument} keeps out, are left alone too.
 *
 * <p>Two kinds of reply it cannot judge, and says so. One whose status code the document declares
 * with no content type at all, having described that reply only in words. And one whose type is not
 * among those it read for an operation it could read only in part, since the type may be declared
 * in the part it could not read; a type it did read is held to as on any other operation.
 */
public final class UndeclaredContentTypeOracle implements SaysWhyNot {

    /** What a document declares when any type at all will do. */
    private static final String ANYTHING = "*/*";

    /**
     * How much of what the API called its body is quoted. The API writes it, so it is cut short
     * rather than copied whole into every fault.
     */
    private static final int LONGEST_QUOTED = 100;

    @Override
    public String name() {
        return "undeclared-content-type";
    }

    @Override
    public String description() {
        return "reports a reply with a body whose content type the document does not declare for "
                + "its status code, below 500";
    }

    @Override
    public FaultCategory kind() {
        return RestestFault.UNDECLARED_CONTENT_TYPE;
    }

    @Override
    public String what() {
        return "the content type";
    }

    @Override
    public Verdict verdict(Interaction interaction, ApiModel api) {
        return interaction.response()
                .filter(ResponseSchemaOracle::hasBody)
                .flatMap(response -> HeldToTheDocument.operationFor(interaction, response, api)
                        .map(operation -> verdict(interaction, response, operation, api)))
                .orElse(Verdict.NOTHING);
    }

    /**
     * In this order: a status code the document does not declare is another rule's to report; a
     * type the document declares, or <code>*&#47;*</code>, is fine, even on an operation read only in
     * part; then a type that may be declared in the part RESTest could not read, and a status code
     * declared with no type at all, cannot be judged; anything else is a type the document leaves
     * out.
     */
    private Verdict verdict(Interaction interaction, HttpResponseRecord response,
            Operation operation, ApiModel api) {
        Optional<ResponseModel> answered = operation.responseFor(response.statusCode());
        if (answered.isEmpty()) {
            return Verdict.NOTHING;
        }
        ResponseModel declared = answered.get();
        Map<String, CanonicalSchema> types = declared.content();
        if (types.containsKey(ANYTHING)) {
            return Verdict.NOTHING;
        }
        Optional<String> said = HeldToTheDocument.contentType(
                response.headerValues("Content-Type"));
        if (said.isPresent() && declared.declaredContentTypeFor(said.get()).isPresent()) {
            return Verdict.NOTHING;
        }
        if (api.readInPart(operation.id())) {
            return Verdict.couldNot(WhyNotJudged.READ_IN_PART);
        }
        if (types.isEmpty()) {
            return Verdict.couldNot(WhyNotJudged.NO_CONTENT_TYPE_DECLARED);
        }
        String came = said.map(value -> " as " + quoted(value) + ", which the document does not "
                        + "declare for that status; it declares ")
                .orElse(" with a body and no Content-Type; the document declares ");
        return Verdict.of(Finding.of(kind(), interaction,
                "the API answered " + response.statusCode() + came
                        + String.join(", ", types.keySet())));
    }

    /**
     * What the API called its body, as it can safely be written on a screen and in a report:
     * characters that would move a terminal's cursor or change its colours, and invisible ones that
     * would change the order or the look of what is shown around them, are replaced, and a value
     * longer than anybody needs to recognise it is cut short.
     */
    static String quoted(String value) {
        StringBuilder written = new StringBuilder();
        value.codePoints().limit(LONGEST_QUOTED).forEach(letter ->
                written.appendCodePoint(Character.isISOControl(letter)
                        || Character.getType(letter) == Character.FORMAT ? '?' : letter));
        if (value.codePointCount(0, value.length()) > LONGEST_QUOTED) {
            written.append("...");
        }
        return written.toString();
    }
}
