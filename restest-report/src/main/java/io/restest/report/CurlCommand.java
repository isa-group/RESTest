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
package io.restest.report;

import io.restest.core.execution.Header;
import io.restest.core.execution.HttpRequestRecord;
import io.restest.core.execution.Payload;
import io.restest.core.model.HttpMethod;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes out a request as a {@code curl} command anybody can paste into a terminal.
 *
 * <p>This is what turns "RESTest says your API has a bug" into something a person can act on in ten
 * seconds. Every fault RESTest reports comes with the exact command that produced it, so the first
 * thing anyone does with a report - check whether it is really true - costs one paste.
 *
 * <p>Being able to write requests out like this, rather than running them this way, is deliberate.
 * RESTest sends requests itself and keeps exactly what went over the wire; a {@code curl} command
 * is one of the ways it can show that afterwards, alongside the other reports. It is a description
 * of what happened, never the way it happened.
 *
 * <p>Quoting is done properly, because a report that cannot be pasted is a report nobody uses.
 * Every value goes inside single quotes, and a value containing a single quote is broken around it
 * the way a shell requires.
 */
public final class CurlCommand {

    /**
     * Headers left out, because they are the HTTP client's own housekeeping rather than anything a
     * test asked for, and repeating them makes the command worse.
     *
     * <p>Three of them would simply be wrong. {@code Content-Length}, {@code Host} and
     * {@code Connection} are worked out by whoever sends the request, and a stale one breaks it.
     *
     * <p>The fourth is worth naming on its own. {@code Accept-Encoding: gzip} is added by RESTest's
     * own client, which then quietly unpacks what comes back. {@code curl} would not, so the
     * command would answer with a screenful of compressed bytes, and a command whose reply cannot
     * be read is not one anybody can check a fault with.
     */
    private static final List<String> THE_CLIENT_S_OWN_HOUSEKEEPING =
            List.of("content-length", "host", "connection", "accept-encoding");

    private CurlCommand() {
    }

    /** The command that repeats this request, exactly as it was sent. */
    public static String of(HttpRequestRecord request) {
        Objects.requireNonNull(request, "request");
        StringBuilder command = new StringBuilder("curl ")
                .append(headOf(request.method()))
                .append(' ')
                .append(quoted(request.url()));
        for (Header header : request.headers()) {
            String name = header.name().toLowerCase(Locale.ROOT);
            if (THE_CLIENT_S_OWN_HOUSEKEEPING.contains(name)) {
                continue;
            }
            command.append(" -H ").append(quoted(header.name() + ": " + header.value()));
        }
        request.body().ifPresent(body -> command.append(' ').append(bodyOf(body)));
        return command.toString();
    }

    /**
     * How to ask for this method, and to show what came back.
     *
     * <p>{@code -i} prints the status line and headers as well as the body, which is usually the
     * whole point: a fault is often a status code. {@code HEAD} is the exception and needs
     * {@code --head} rather than {@code -X HEAD}, because with the latter {@code curl} goes on
     * waiting for a body the server is never going to send.
     */
    private static String headOf(HttpMethod method) {
        return method == HttpMethod.HEAD ? "--head" : "-i -X " + method.name();
    }

    /**
     * The part of the command that carries the body.
     *
     * <p>A body that is text goes in as it stands, which is what anyone reading the command wants
     * to see. A body that is not - a picture, a compressed file - cannot be written between quotes
     * at all, so it is written as the letters and digits it encodes to, with the command decoding
     * them back on the way past.
     *
     * <p>That second form is a best effort and not a promise. A shell drops zero bytes and trailing
     * blank lines on their way through, so a body holding either arrives slightly changed. Sending
     * one of those faithfully needs a file beside the command rather than a command on its own.
     * RESTest does not invent request bodies at all yet; when it does, this is the line that has to
     * grow a file alongside it.
     */
    private static String bodyOf(Payload body) {
        return asText(body.content())
                .map(text -> "--data-binary " + quoted(text))
                .orElseGet(() -> "--data-binary \"$(printf %s "
                        + quoted(Base64.getEncoder().encodeToString(body.content()))
                        + " | base64 -d)\"");
    }

    /** The bytes as text, or nothing at all if they are not text. */
    private static Optional<String> asText(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException notText) {
            return Optional.empty();
        }
    }

    /**
     * One value, quoted so that a shell passes it on untouched.
     *
     * <p>Single quotes protect everything except a single quote itself, which no amount of quoting
     * can escape from inside them. The way out is to close the quotes, write the quote on its own,
     * and open them again - which is what {@code '\''} does, and why it looks like that.
     */
    private static String quoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
