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
package io.restest.core.schema;

/**
 * Whether a number must be whole.
 *
 * <p>OpenAPI spells these as two types, {@code integer} and {@code number}, but they carry exactly
 * the same constraints - minimum, maximum, multiple-of - so they are one record here with this as a
 * component. Two records would mean every generator, every oracle and every report handling both
 * and keeping them in step.
 */
public enum NumberKind {
    /** A whole number: {@code type: integer}. */
    INTEGER,
    /** Any number: {@code type: number}. */
    NUMBER
}
