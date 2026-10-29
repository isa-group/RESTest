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
package io.restest.core.oracle;

/**
 * Why a check could not judge a reply it would otherwise have judged.
 *
 * <p>A check that cannot tell whether a reply is right says nothing against the API: guessing
 * would report working APIs as broken. But saying nothing at all made a run that could not check
 * most of what came back look exactly like a run that checked everything and found it fine. So a
 * check that cannot judge a reply says which of these stopped it, and the run counts them, so that
 * a person reading "no faults found" knows how much was looked at.
 *
 * <p>The list is closed. Each entry has a word that {@code report.json} writes and a person can
 * search for, and a phrase the screen prints after it, written so that it reads the same for one
 * reply as for thousands. See {@link CouldNotJudge} for what carries one of these.
 */
public enum WhyNotJudged {

    /** The document declares the reply, but says nothing about the shape of its body. */
    NO_SHAPE_DECLARED("noShapeDeclared", "the document gives no shape"),

    /** The document gives a shape for a body that is not JSON, which is all RESTest checks. */
    NOT_JSON("notJson", "the body is not JSON, the only kind RESTest checks against a shape"),

    /** The body was longer than RESTest keeps whole, so there was no whole body to judge. */
    CUT_SHORT("cutShort", "the body was longer than RESTest keeps whole "
            + "(engine.maxRetainedResponseBytes)"),

    /** The reply says it is written in a character set other than UTF-8, which JSON is not. */
    NOT_UTF8("notUtf8", "the reply names a character set other than UTF-8"),

    /**
     * The document gives a shape RESTest cannot use: one that points at something the document does
     * not define or at another file, one the checker cannot finish with, or a document that could
     * not be kept to check against.
     */
    SHAPE_UNUSABLE("shapeUnusable", "the document gives a shape RESTest cannot use"),

    /**
     * A key handed over with {@code --auth} came back in the body and was hidden there, and the body
     * could then no longer be read as JSON - so what is wrong with it may be the hiding's doing.
     */
    KEY_HIDDEN("keyHidden", "a key handed over with --auth was hidden in the body, which could "
            + "then not be read as JSON"),

    /** The document declares no reply at all for the operation, so it promised none. */
    NO_REPLY_DECLARED("noReplyDeclared", "the document declares no reply for the operation"),

    /**
     * RESTest could not read a reply the document declares for the operation - one given as a
     * reference to a reply the document never defines, say - so what looks undeclared may be
     * declared in the reply that could not be read.
     */
    READ_IN_PART("readInPart",
            "RESTest could not read a reply the document declares for the operation"),

    /** The document declares the status code with no content type, describing it only in words. */
    NO_CONTENT_TYPE_DECLARED("noContentTypeDeclared",
            "the document declares the status code with no content type");

    private final String word;
    private final String inWords;

    WhyNotJudged(String word, String inWords) {
        this.word = word;
        this.inWords = inWords;
    }

    /** The word {@code report.json} writes for this reason: {@code noShapeDeclared}. */
    public String word() {
        return word;
    }

    /** What the screen says after the count: {@code the document gives no shape}. */
    public String inWords() {
        return inWords;
    }
}
