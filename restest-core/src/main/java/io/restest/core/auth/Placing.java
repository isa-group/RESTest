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
package io.restest.core.auth;

import io.restest.core.execution.BodyValue;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Payload;
import io.restest.core.execution.TestCase;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Puts keys into a request that has already been built, in the part of it each one goes in.
 *
 * <p>This is the last thing done to a request before it goes out, and the only place a key is ever
 * written into one. A header of the key's name is replaced; a parameter of the key's name in the
 * query is replaced, and the key added in the way the rest of RESTest writes an address; a cookie
 * goes into the request's one {@code Cookie} header, as it was given, since servers read a cookie
 * back exactly as it was sent; and a field of a web form goes at the end of the form.
 *
 * <p>A field is only added to a body that really is a form written from its fields. A body sent as
 * text of its own - one a change to an accepted request broke on purpose, or labelled as a form
 * while holding something else - is sent exactly as that change made it, so that what the test case
 * says it changed is what was changed.
 */
final class Placing {

    private Placing() {
    }

    /** The request with every key in its place. */
    static HttpRequestRecord placed(List<Credential> credentials, TestCase testCase,
            HttpRequestRecord request) {
        if (credentials.isEmpty()) {
            return request;
        }
        String url = request.url();
        List<Header> headers = new ArrayList<>(request.headers());
        Optional<Payload> body = request.body();
        for (Credential credential : credentials) {
            String name = credential.place().name();
            String value = credential.secret().value();
            switch (credential.place().where()) {
                case HEADER -> {
                    headers.removeIf(header -> header.name().equalsIgnoreCase(name));
                    headers.add(Header.of(name, value));
                }
                case QUERY -> url = withParameter(url, name, value);
                case COOKIE -> headers = withCookie(headers, name, value);
                case FORM_FIELD -> body = withField(body, testCase, name, value);
            }
        }
        return new HttpRequestRecord(request.method(), url, headers, body);
    }

    /**
     * The address with the parameter in its query, and no other of that name: before a fragment if
     * the address has one, since what follows a {@code #} is never sent.
     */
    static String withParameter(String url, String name, String value) {
        int hash = url.indexOf('#');
        String fragment = hash < 0 ? "" : url.substring(hash);
        String beforeTheFragment = hash < 0 ? url : url.substring(0, hash);
        int question = beforeTheFragment.indexOf('?');
        String address = question < 0 ? beforeTheFragment : beforeTheFragment.substring(0, question);
        String query = question < 0 ? "" : beforeTheFragment.substring(question + 1);
        List<String> pairs = withoutTheName(query, name, "&");
        pairs.add(Encoding.percent(name) + "=" + Encoding.percent(value));
        return address + "?" + String.join("&", pairs) + fragment;
    }

    private static List<Header> withCookie(List<Header> headers, String name, String value) {
        List<Header> written = new ArrayList<>(headers);
        for (int i = 0; i < written.size(); i++) {
            Header header = written.get(i);
            if (header.name().equalsIgnoreCase("Cookie")) {
                List<String> cookies = new ArrayList<>();
                for (String cookie : header.value().split(";")) {
                    String trimmed = cookie.trim();
                    if (!trimmed.isEmpty() && !nameOf(trimmed).equals(name)) {
                        cookies.add(trimmed);
                    }
                }
                cookies.add(name + "=" + value);
                written.set(i, Header.of(header.name(), String.join("; ", cookies)));
                return written;
            }
        }
        written.add(Header.of("Cookie", name + "=" + value));
        return written;
    }

    private static Optional<Payload> withField(Optional<Payload> body, TestCase testCase,
            String name, String value) {
        Optional<BodyValue> chosen = testCase.body();
        boolean aFormOfFields = body.isPresent() && chosen.isPresent()
                && ModelToFillIn.FORM.equals(chosen.get().mediaType())
                && chosen.get().sentAs().isEmpty();
        if (!aFormOfFields) {
            return body;
        }
        Payload payload = body.get();
        String text = new String(payload.content(), StandardCharsets.UTF_8);
        List<String> fields = withoutTheName(text, name, "&");
        fields.add(Encoding.percent(name) + "=" + Encoding.percent(value));
        return Optional.of(Payload.of(String.join("&", fields).getBytes(StandardCharsets.UTF_8),
                payload.mediaType()));
    }

    /** The pieces of a query or a form, less any piece under the given name. */
    private static List<String> withoutTheName(String written, String name, String separator) {
        List<String> pieces = new ArrayList<>();
        if (written.isEmpty()) {
            return pieces;
        }
        for (String piece : Arrays.asList(written.split(separator, -1))) {
            if (piece.isEmpty() || !Encoding.decoded(nameOf(piece)).equals(name)) {
                pieces.add(piece);
            }
        }
        return pieces;
    }

    private static String nameOf(String piece) {
        int equals = piece.indexOf('=');
        return equals < 0 ? piece : piece.substring(0, equals);
    }
}
