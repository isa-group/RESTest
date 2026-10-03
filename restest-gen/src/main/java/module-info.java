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
module io.restest.gen {
    requires io.restest.core;
    requires io.restest.idl;
    // An automatic module: the jar carries no descriptor of its own, so this name comes from its
    // file name. See ADR-0022, which weighs that against writing a regular-expression engine here.
    requires rgxgen;

    exports io.restest.gen;
}
