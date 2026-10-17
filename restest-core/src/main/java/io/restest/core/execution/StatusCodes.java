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
package io.restest.core.execution;

/**
 * Which status codes say that the API accepted a request, and which say that it failed while
 * handling one.
 *
 * <p>Every reply an API sends starts with a three-digit status code, and its first digit says what
 * kind of answer it is. A code from 200 to 299 is a success: the API did what it was asked. A code
 * from 500 to 599 is a server error: something went wrong inside the API, not in the request. Many
 * parts of RESTest ask one of these two questions - whether to learn from a request, whether a series
 * of requests can go on, whether a reply is a fault, what a report counts - and they all ask this
 * class, so that they cannot come to disagree about which codes are a success and which a server
 * error.
 *
 * <p>This class decides only the range. Which replies have a status code to ask about is decided by
 * where the caller reads it: {@link Interaction#statusCode()} counts a reply that broke off after its
 * status code arrived, while {@link HttpResponseRecord#statusCode()} belongs only to a reply that was
 * read whole.
 */
public final class StatusCodes {

    private StatusCodes() {
    }

    /**
     * Whether the code says the API accepted the request: 200 to 299.
     *
     * @param statusCode the status code the API answered
     * @return whether it is a success
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode <= 299;
    }

    /**
     * Whether the code says the API failed while handling the request: 500 to 599.
     *
     * <p>The upper end matters. Nothing stops an API answering 999, and real ones do; such a code is
     * wrong in a way of its own, and is not a server error.
     *
     * @param statusCode the status code the API answered
     * @return whether it is a server error
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode <= 599;
    }

    /**
     * Whether the code is one HTTP defines a meaning for: 100 to 599.
     *
     * <p>Below it and above it there is only an API sending a number nobody can read anything into,
     * which a document cannot declare either.
     *
     * @param statusCode the status code the API answered
     * @return whether HTTP gives it a meaning
     */
    public static boolean isHttpStatus(int statusCode) {
        return statusCode >= 100 && statusCode <= 599;
    }
}
