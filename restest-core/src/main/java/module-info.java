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

/**
 * The domain model: what an API is, in types we own.
 *
 * <p>Four packages are exported and one is not. {@code io.restest.core.internal} holds plumbing
 * shared between the other four and is deliberately kept internal, so that it can change without
 * changing anything a consumer compiled against.
 *
 * <p>The module requires nothing. That is the point of ADR-0004: a consumer depending on
 * {@code restest-core} takes on the model and no parser, no HTTP client, no solver and no database
 * driver.
 */
module io.restest.core {
    exports io.restest.core.execution;
    exports io.restest.core.json;
    exports io.restest.core.model;
    exports io.restest.core.schema;
}
