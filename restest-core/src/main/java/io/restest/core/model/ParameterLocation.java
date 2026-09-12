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

/**
 * Where a parameter travels in the request.
 *
 * <p>These four are what OpenAPI 3.x defines, and a parameter is identified by its name together
 * with one of them: {@code id} in the query string and {@code id} in the path are two different
 * parameters, which is why {@link Operation#parameter(String, ParameterLocation)} asks for both.
 *
 * <p>The older OpenAPI 2.0 format has two more locations, {@code body} and {@code formData}, and
 * they are deliberately absent here: a body is not treated as a parameter in this model, it is
 * {@link Operation#requestBody()}. A document written in that older format must be converted into
 * this shape when it is read, and it is worth writing down how, because only one of the two
 * conversions is obvious:
 *
 * <ul>
 *   <li>{@code in: body} becomes a {@link RequestBodyModel} whose schema is the parameter's. The
 *       parameter's own name is dropped, and nothing is lost by dropping it - 2.0 never puts that
 *       name on the wire.</li>
 *   <li>{@code in: formData} becomes a {@link RequestBodyModel} of
 *       {@code application/x-www-form-urlencoded}, or of {@code multipart/form-data} when any of
 *       the parameters is a file, whose schema is an object whose <em>properties are those
 *       parameters</em>, named and required as each declared. Here the names are the field names
 *       and must survive.</li>
 * </ul>
 *
 * <p>Neither conversion can collide with a parameter of the same name: a query {@code id} and a form
 * field {@code id} end up in different components of the operation, as they do on the wire.
 */
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
