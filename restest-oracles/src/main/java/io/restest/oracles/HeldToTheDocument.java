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
import io.restest.core.execution.StatusCodes;
import io.restest.core.model.ApiModel;
import io.restest.core.model.Operation;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a reply can fairly be held to what the document declares about its operation,
 * before {@link UndeclaredStatusOracle} and {@link UndeclaredContentTypeOracle} say the document
 * left something out.
 *
 * <p>Saying "the document does not declare this" is only fair when the request was one the
 * document has something to say about. So a reply is left alone, whatever it is, in three cases:
 *
 * <ul>
 *   <li>Its status code is a server error, which other rules report, so that a reply is never
 *       counted twice over one status code; or a code HTTP gives no meaning to.</li>
 *   <li>Its operation is not one RESTest read from the document.</li>
 *   <li>RESTest sent its body under a media type the operation does not accept, to see whether the
 *       API turns it away. A document is not expected to describe how an API refuses a body it never
 *       promised to take.</li>
 * </ul>
 *
 * <p>None of these is a reply a rule could not judge: there was nothing for it to judge. An
 * operation one of whose replies RESTest could not read - one given as a reference to a reply the
 * document never defines, say - is different, and each rule deals with it itself: what the replies
 * it read declare can still be held to, while what looks undeclared may be declared in the reply it
 * could not read. An operation RESTest only had to give another name, because the document gives
 * its name to another operation too, was read whole and is held to it like any other.
 */
final class HeldToTheDocument {

    private HeldToTheDocument() {
    }

    /**
     * The operation the reply in this attempt can be held to, or nothing when there is nothing to
     * hold it to.
     *
     * @param interaction the attempt
     * @param response the well-formed reply it got
     * @param api what the document declares
     * @return the operation the reply answered, when the reply can be held to it
     */
    static Optional<Operation> operationFor(Interaction interaction, HttpResponseRecord response,
            ApiModel api) {
        int code = response.statusCode();
        if (!StatusCodes.isHttpStatus(code) || StatusCodes.isServerError(code)) {
            return Optional.empty();
        }
        return api.operation(interaction.testCase().operation())
                .filter(operation -> !bodyWentAsSomethingNotAccepted(interaction, operation));
    }

    /** The first {@code Content-Type} of a request or a reply, unless there is none or it is blank. */
    static Optional<String> contentType(List<String> contentTypes) {
        return contentTypes.stream().findFirst().filter(value -> !value.isBlank());
    }

    /**
     * Whether the request carried a body under a media type the operation does not accept - the
     * exact type, a range such as {@code application/*}, or <code>*&#47;*</code> - whatever case and
     * parameters either was written with. A request with no body, no media type, or to an operation
     * that declares no body, is not one of these.
     */
    private static boolean bodyWentAsSomethingNotAccepted(Interaction interaction,
            Operation operation) {
        return interaction.request().body().isPresent()
                && operation.requestBody()
                        .flatMap(accepted -> contentType(
                                interaction.request().headerValues("Content-Type"))
                                .map(mediaType -> accepted.contentFor(mediaType).isEmpty()))
                        .orElse(false);
    }
}
