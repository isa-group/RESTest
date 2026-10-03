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
package io.restest.core.model;

import io.restest.core.json.JsonValue;
import io.restest.core.schema.CanonicalSchema;
import java.util.List;
import java.util.Objects;

/**
 * What an operation accepts as a request body in one particular media type: the shape a body has to
 * have, and the sample bodies the document writes down beside it.
 *
 * <p>The samples are the ones the document writes against the media type itself. They are kept
 * apart from the ones written inside the shape, and the difference is the same one a
 * {@link Parameter} makes: a sample written beside the media type is a sample of <em>this
 * operation's</em> body, while one written inside the shape is a sample of that shape wherever it is
 * used, by any operation. Both are useful, and choosing between them belongs to whoever picks a
 * value to send rather than here.
 *
 * <p>A sample is what the document's author wrote, not a promise that the API will accept it. It may
 * even disagree with the shape beside it, since nothing checks that an author's example obeys their
 * own rules.
 *
 * @param schema the shape a body of this media type has to satisfy
 * @param examples the sample bodies the document writes beside this media type, in the order it
 *     wrote them and with repeats removed
 */
public record BodyContent(CanonicalSchema schema, List<JsonValue> examples) {

    public BodyContent {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(examples, "examples");
        examples = List.copyOf(examples);
    }

    /**
     * A shape with no sample written beside it, which is what most documents offer.
     *
     * @param schema the shape a body of this media type has to satisfy
     * @return the content
     */
    public static BodyContent of(CanonicalSchema schema) {
        return new BodyContent(schema, List.of());
    }
}
