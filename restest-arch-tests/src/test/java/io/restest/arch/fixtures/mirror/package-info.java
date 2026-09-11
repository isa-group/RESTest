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
 * A miniature of the nine-module package layout, used only to prove that the rules in
 * {@code ArchitectureRules} report the violations they claim to.
 *
 * <p>Every class below breaks a rule on purpose. Nothing here is production code, nothing depends on
 * it, and the production checks exclude it by importing with
 * {@code ImportOption.Predefined.DO_NOT_INCLUDE_TESTS} - these classes compile to
 * {@code target/test-classes}.
 *
 * <p>The mirror exists because the location-based rules name module packages in their own text. A
 * violation of "only spec may see the parser" has to live in something shaped like a module, so the
 * rules take a root package and this tree supplies an alternative one.
 */
package io.restest.arch.fixtures.mirror;
