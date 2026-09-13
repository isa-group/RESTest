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
 * The rules that decide whether what an API sent back was acceptable.
 *
 * <p>Generating a request is half of testing. This module is the other half: given one attempt -
 * the request that went out and the reply that came back - it says whether anything was wrong with
 * it, and names each fault by a number from a catalogue several testing tools share, so that a
 * RESTest result can be put beside somebody else's honestly.
 *
 * <p>Two rules live here so far. One reports an API that fell over. The other checks a reply's body
 * against the shape its own specification promised - using the specification document itself, and
 * an off-the-shelf checker confined to this module, so that nothing is judged by RESTest's reading
 * of a document rather than by the document.
 *
 * <p>A rule is one class and nothing else: this module offers them as a service, and the tool finds
 * them by looking rather than by a list somebody has to remember to update.
 */
module io.restest.oracles {
    requires io.restest.core;
    requires com.networknt.schema;

    exports io.restest.oracles;

    uses io.restest.core.oracle.Oracle;
    provides io.restest.core.oracle.Oracle with
            io.restest.oracles.ServerErrorOracle,
            io.restest.oracles.ResponseSchemaOracle;
}
