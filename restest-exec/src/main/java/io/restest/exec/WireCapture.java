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
package io.restest.exec;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.model.HttpMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Records what a request actually looked like when it left the machine, rather than what we asked
 * for.
 *
 * <p>The two differ every time. An HTTP client fills in headers of its own on the way out -
 * {@code Host}, {@code Content-Length}, {@code Accept-Encoding}, the user agent - and a report that
 * showed only the headers we set would not reproduce the request. Since the whole point of this tool
 * is to hand somebody a failing request they can run themselves, the recorded request has to be the
 * real one.
 *
 * <p>This sits at the last point before the socket, so everything the client adds is already there.
 * Each request carries its own place to write the result into, so two requests in flight at the same
 * time cannot overwrite each other's record.
 */
final class WireCapture implements Interceptor {

    /**
     * Where one request's record is written, and where the request we were originally given is kept
     * until then.
     *
     * <p>The body is taken from the original rather than read back off the wire: it is the same
     * bytes either way, and reading a request body a second time is not something every kind of body
     * allows.
     */
    static final class Slot {

        private final HttpRequestRecord asked;
        private volatile HttpRequestRecord sent;

        Slot(HttpRequestRecord asked) {
            this.asked = Objects.requireNonNull(asked, "asked");
        }

        /** What went out, or what we asked for if the request never reached the socket. */
        HttpRequestRecord sent() {
            HttpRequestRecord captured = sent;
            return captured == null ? asked : captured;
        }
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Slot slot = request.tag(Slot.class);
        if (slot != null) {
            slot.sent = record(request, slot.asked);
        }
        return chain.proceed(request);
    }

    private static HttpRequestRecord record(Request request, HttpRequestRecord asked) {
        List<Header> headers = new ArrayList<>();
        okhttp3.Headers sent = request.headers();
        for (int i = 0; i < sent.size(); i++) {
            headers.add(Header.of(sent.name(i), sent.value(i)));
        }
        HttpMethod method = HttpMethod.named(request.method()).orElse(asked.method());
        return new HttpRequestRecord(method, request.url().toString(), headers, asked.body());
    }
}
