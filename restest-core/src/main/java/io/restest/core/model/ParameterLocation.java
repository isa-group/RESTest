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

/** Where a parameter travels in the request. */
public enum ParameterLocation {
    /** Inside the path itself, replacing a {@code {name}} template. */
    PATH,
    /** In the query string. */
    QUERY,
    /** As a request header. */
    HEADER,
    /** Inside the {@code Cookie} header. */
    COOKIE
}
