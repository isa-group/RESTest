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
 * This is the module that describes, in plain data, what a REST API and a test against it look
 * like: the operations an API offers, the requests and responses of one test attempt, and the
 * shapes ({@code schema}) that data must follow. Every other part of RESTest is built on top of
 * these definitions.
 *
 * <p>Six packages are exported and one is not. {@code io.restest.core.internal} holds small
 * helpers shared between the other six and is deliberately kept internal, so it can change freely
 * without affecting anything built on top of this module.
 *
 * <p>Two of the six have names close enough to be worth telling apart on sight.
 * {@code io.restest.core.execution} is data: one test attempt, the request sent, the reply received.
 * {@code io.restest.core.exec} is the door that produces that data - the interface an HTTP client
 * implements - and it is named after the module that implements it, {@code restest-exec}, exactly as
 * {@code io.restest.core.spec} is named after {@code restest-spec}.
 *
 * <p>The module requires nothing: no parser, no HTTP client, no constraint solver, no database
 * driver. It defines the vocabulary that the parser, the test generator, the HTTP engine and the
 * reporting code all share, without needing any of them itself - so depending on this module never
 * pulls in anything heavier than this module itself.
 */
module io.restest.core {
    exports io.restest.core.exec;
    exports io.restest.core.execution;
    exports io.restest.core.json;
    exports io.restest.core.model;
    exports io.restest.core.schema;
    exports io.restest.core.spec;
}
