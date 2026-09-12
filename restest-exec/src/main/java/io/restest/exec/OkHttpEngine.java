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

import io.restest.core.exec.EngineSettings;
import io.restest.core.exec.EngineStatistics;
import io.restest.core.exec.HttpEngine;
import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.HttpResponseRecord;
import io.restest.core.execution.Interaction;
import io.restest.core.execution.Payload;
import io.restest.core.execution.StatusLine;
import io.restest.core.execution.TestCase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends requests to the API under test, using OkHttp, and reports exactly what was sent and exactly
 * what came back.
 *
 * <p>This is the only class in RESTest that opens a socket. It is built once for a run and used
 * until the run ends, from as many threads as the caller likes. Each request runs on a virtual
 * thread, which is a thread so cheap that thousands of them waiting on a slow API cost almost
 * nothing - so the tool can keep several questions in the air at once without a pool of real threads
 * to size and tune.
 *
 * <p>Three things it promises, each of them a reaction to a way this can go wrong:
 *
 * <ul>
 *   <li><strong>Nothing the API does is an error.</strong> Refused, timed out, hung up on mid-reply:
 *       each one comes back as an interaction that says so, and the run carries on to the next
 *       request. An API broken enough to hang up is exactly what the tool is looking for.
 *   <li><strong>What is recorded is what happened.</strong> The request is captured on its way out,
 *       after the HTTP client has added its own headers, so the recorded request is one somebody can
 *       re-send and get the same answer. The reply is recorded as the API meant it: if it arrived
 *       compressed, what is kept is the text inside, not the compressed bytes, because that is what
 *       the request asked for and what everything downstream has to read. The two headers that
 *       describe the packaging rather than the content - {@code Content-Encoding} and the length that
 *       goes with it - are consequently not part of the record when a reply arrived compressed.
 *   <li><strong>It keeps track of its own idleness.</strong> Every run can say what share of the time
 *       it had nothing in flight, which is the project's measure of whether the tool, rather than the
 *       API, is the slow one.
 * </ul>
 *
 * <p>Two engines can run side by side in one program - two runs, two APIs, two sets of settings -
 * without sharing anything or interfering with each other.
 */
public final class OkHttpEngine implements HttpEngine {

    private static final int CHUNK = 8192;

    private final EngineSettings settings;
    private final OkHttpClient client;
    private final ExecutorService requests;
    private final EngineActivity activity;
    private final ConcurrencyLimiter limiter;
    private final LongSupplier nanoTime;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** An engine with settings that work against an unknown API with nothing configured. */
    public OkHttpEngine() {
        this(EngineSettings.defaults());
    }

    /**
     * An engine with the given settings.
     *
     * @param settings timeouts, how many requests may be in flight, and how much of a reply to keep
     */
    public OkHttpEngine(EngineSettings settings) {
        this(settings, System::nanoTime);
    }

    /** The constructor the tests use, so they can hand the engine a clock they control. */
    OkHttpEngine(EngineSettings settings, LongSupplier nanoTime) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.activity = new EngineActivity(nanoTime);
        this.limiter = new ConcurrencyLimiter(settings);
        this.requests = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("restest-request-", 0).factory());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(settings.connectTimeout())
                .readTimeout(settings.readTimeout())
                .writeTimeout(settings.writeTimeout())
                .followRedirects(settings.followRedirects())
                .followSslRedirects(settings.followRedirects())
                // A retried request is a second request the caller never asked for: it doubles the
                // load on an API that is already struggling and makes the recorded timing describe
                // an attempt that is not the one reported. A failure is more useful than a retry
                // here, because the failure is the finding.
                .retryOnConnectionFailure(false)
                .addNetworkInterceptor(new WireCapture())
                .build();
    }

    @Override
    public CompletableFuture<Interaction> sendAsync(TestCase testCase, HttpRequestRecord request) {
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(request, "request");
        refuseIfClosed();
        try {
            return CompletableFuture.supplyAsync(() -> send(testCase, request), requests);
        } catch (RejectedExecutionException e) {
            // close() from another thread, between the check above and here.
            throw new IllegalStateException(
                    "this engine is closed and cannot send anything else", e);
        }
    }

    @Override
    public Interaction send(TestCase testCase, HttpRequestRecord request) {
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(request, "request");
        refuseIfClosed();
        WireCapture.Slot slot = new WireCapture.Slot(request);
        Request outgoing;
        try {
            outgoing = build(request, slot);
        } catch (RuntimeException e) {
            activity.requestNeverSent();
            return Interaction.transportFailure(testCase, request,
                    "the request could not be assembled, so nothing was sent: " + reason(e),
                    Instant.now(), Duration.ZERO);
        }

        try {
            limiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activity.requestNeverSent();
            return Interaction.transportFailure(testCase, request,
                    "the run was interrupted before this request was sent", Instant.now(),
                    Duration.ZERO);
        }

        // The slot has to come back even if something no method here catches goes wrong on the way:
        // a slot that is never returned is one fewer request this engine can ever have in flight
        // again, and enough of them would leave it waiting for ever on a slot nobody holds.
        activity.requestStarted();
        Interaction interaction = null;
        try {
            interaction = attempt(testCase, request, outgoing, slot);
            return interaction;
        } finally {
            Duration elapsed = interaction == null ? Duration.ZERO : interaction.elapsed();
            activity.requestFinished(elapsed);
            limiter.release();
            if (interaction != null) {
                limiter.observe(elapsed.toNanos(), !interaction.isAnswered());
            }
        }
    }

    @Override
    public EngineStatistics statistics() {
        return activity.snapshot(limiter.limit());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        requests.shutdown();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void refuseIfClosed() {
        if (closed.get()) {
            throw new IllegalStateException("this engine is closed and cannot send anything else");
        }
    }

    /** One attempt, from the first byte out to the last byte in, never throwing. */
    private Interaction attempt(TestCase testCase, HttpRequestRecord asked, Request outgoing,
            WireCapture.Slot slot) {
        Instant sentAt = Instant.now();
        long startedAt = nanoTime.getAsLong();
        try (Response response = client.newCall(outgoing).execute()) {
            StatusLine statusLine = statusLineOf(response);
            List<Header> headers = headersOf(response);
            Reply reply = read(response.body());
            if (reply.failure() != null) {
                return Interaction.malformedResponse(testCase, slot.sent(), reply.failure(),
                        Optional.of(statusLine), headers, reply.payload(), sentAt,
                        since(startedAt));
            }
            return Interaction.answered(testCase, slot.sent(),
                    new HttpResponseRecord(statusLine, headers, reply.payload()), sentAt,
                    since(startedAt));
        } catch (IOException | RuntimeException e) {
            return Interaction.transportFailure(testCase, slot.sent(), reason(e), sentAt,
                    since(startedAt));
        }
    }

    /**
     * What came back in the body: as much of it as we keep, how much there was, and what went wrong
     * if the reply stopped early.
     */
    private record Reply(Optional<Payload> payload, String failure) {
    }

    /**
     * Reads the reply body, keeping at most what the settings allow.
     *
     * <p>A reply larger than that is still read to the end, so that the record can say how big it
     * really was, but only the first part is kept: a run that stored every megabyte of every reply
     * would run out of memory long before it ran out of budget.
     */
    private Reply read(ResponseBody body) {
        boolean typeDeclared = body.contentType() != null;
        String mediaType = typeDeclared ? body.contentType().toString() : Payload.UNKNOWN_MEDIA_TYPE;
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK];
        long delivered = 0;
        long cap = settings.maxRetainedResponseBytes();
        try (InputStream bytes = body.byteStream()) {
            int read;
            while ((read = bytes.read(chunk)) != -1) {
                delivered += read;
                long room = cap - kept.size();
                if (room > 0) {
                    kept.write(chunk, 0, (int) Math.min(read, room));
                }
            }
        } catch (IOException e) {
            return new Reply(payload(kept, delivered, mediaType, typeDeclared),
                    "the reply stopped after " + delivered + " bytes: " + reason(e));
        }
        return new Reply(payload(kept, delivered, mediaType, typeDeclared), null);
    }

    /**
     * What to record as the body.
     *
     * <p>"No body at all" and "a body the API said would be JSON and then sent empty" are different
     * facts about an API, and only the second one is a possible bug, so they are recorded
     * differently: a reply that named a content type keeps an empty body of that type, and one that
     * named none has no body.
     */
    private static Optional<Payload> payload(ByteArrayOutputStream kept, long delivered,
            String mediaType, boolean typeDeclared) {
        if (delivered == 0) {
            return typeDeclared ? Optional.of(Payload.empty(mediaType)) : Optional.empty();
        }
        byte[] retained = kept.toByteArray();
        return Optional.of(delivered > retained.length
                ? Payload.partial(retained, mediaType, delivered)
                : Payload.of(retained, mediaType));
    }

    private Request build(HttpRequestRecord request, WireCapture.Slot slot) {
        HttpUrl url = HttpUrl.parse(request.url());
        if (url == null) {
            throw new IllegalArgumentException(
                    "'" + request.url() + "' is not an address this engine can send a request to");
        }
        Request.Builder outgoing = new Request.Builder()
                .url(url)
                .tag(WireCapture.Slot.class, slot);
        request.headers().forEach(header -> outgoing.addHeader(header.name(), header.value()));
        if (request.headerValues("User-Agent").isEmpty()) {
            outgoing.header("User-Agent", settings.userAgent());
        }
        return outgoing.method(request.method().name(), bodyOf(request)).build();
    }

    /**
     * The body to send, or {@code null} where HTTP has none.
     *
     * <p>An empty body is not the same as no body: {@code POST} without one is a request no client
     * will send, so a POST with nothing to say still gets an empty body rather than being refused.
     */
    private static RequestBody bodyOf(HttpRequestRecord request) {
        if (request.body().isPresent()) {
            Payload payload = request.body().get();
            return RequestBody.create(payload.content(), MediaType.parse(payload.mediaType()));
        }
        return switch (request.method()) {
            case POST, PUT, PATCH -> RequestBody.create(new byte[0], null);
            default -> null;
        };
    }

    private static StatusLine statusLineOf(Response response) {
        String reason = response.message();
        return new StatusLine(response.code(),
                reason.isBlank() ? Optional.empty() : Optional.of(reason),
                Optional.of(response.protocol().toString()));
    }

    private static List<Header> headersOf(Response response) {
        okhttp3.Headers received = response.headers();
        List<Header> headers = new ArrayList<>(received.size());
        for (int i = 0; i < received.size(); i++) {
            headers.add(Header.of(received.name(i), received.value(i)));
        }
        return headers;
    }

    private Duration since(long startedAt) {
        return Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - startedAt));
    }

    /** Something a person can read, for a failure whose exception may carry no message at all. */
    private static String reason(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
