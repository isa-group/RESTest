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
package io.restest.core.spec;

import io.restest.core.model.ApiModel;

/**
 * Reads an API's specification document and turns it into an {@link ApiModel}.
 *
 * <p>This is the only door into RESTest for an OpenAPI document. Everything on the other side of it
 * - test generation, execution, reporting - works against {@link ApiModel} and never opens a
 * specification document itself, so which document formats are understood, and how a particular
 * quirk of one of them is read, is a fact about the one class that implements this interface, not
 * about the rest of the tool.
 *
 * <p>A specification is written by people, about a system that changed since it was written, so it is
 * never assumed to be perfect. A document this method cannot read at all - the wrong format, a
 * declared version nobody implements, a location nothing answers at - is not a reason to stop: it
 * comes back as an {@link ApiModel} with no operations and an issue explaining why. A document that is
 * mostly fine but has one broken operation is read as far as it can be, and only that operation is
 * missing, with the same explanation attached. A caller that wants to know whether everything was
 * read can ask {@link ApiModel#isComplete()}; a caller that wants to know what to try instead does not
 * have to catch anything to find out.
 */
public interface SpecificationParser {

    /**
     * Reads the document at the given location.
     *
     * @param location where the document is: a file path, a path relative to the classpath, or a URL.
     *     Never {@code null}
     * @return the API the document describes, however much of it could be read
     */
    ApiModel parse(String location);
}
