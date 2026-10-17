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
package io.restest.core.settings;

/**
 * Which checks a run makes on what the API sends back.
 *
 * <p>Every attempt is put to a few checks, and each one that finds something wrong reports a fault:
 * a reply of 500, which says the API fell over; a reply of another server error, such as 503; a
 * reply whose body is not the shape the API's own document says it would be; a reply of 501, "not
 * implemented", to an operation the document declares; a success in answer to a request RESTest
 * broke on purpose, which the API should have turned away; a request the API had on an open
 * connection and never answered before RESTest stopped waiting; and a reply whose status code, or
 * whose content type, the document does not declare for that operation. A check can be wrong
 * about one API - a document that describes its error replies badly makes every one of them look
 * broken - and a person whose build goes red for that reason needs to turn the one check off without
 * turning the tool off. So each check has a switch here, named for what is wrong with what came back,
 * or did not, and on unless somebody says otherwise.
 *
 * <p>A check switched off is not made at all. No check decides what a run sends: switching one off
 * changes which replies are called faults, and so whether the run ends saying it found any.
 *
 * <p>This is one group of the settings a run is handed, {@link Settings}, read when the rules the
 * run judges replies by are chosen.
 *
 * @param internalServerError report a reply of 500, which says the API fell over while handling the
 *     request
 * @param otherServerError report a reply of a server error other than 500 and 501, such as 502, 503
 *     or 504
 * @param replyOffSchema report a reply whose body is not the shape the document gives for its status
 *     code and media type
 * @param notImplemented report a reply of 501, "not implemented", to an operation the document
 *     declares
 * @param invalidDataAccepted report a request broken on purpose, with one thing in it that the
 *     document forbids, which the API accepted
 * @param noReplyInTime report a request that got no reply, on a connection open to the API, before
 *     RESTest stopped waiting
 * @param undeclaredStatusCode report a reply whose status code, below 500, the document does not
 *     declare for the operation
 * @param undeclaredContentType report a reply with a body, and a status code below 500 the document
 *     declares, whose content type the document does not declare for that status code
 */
public record CheckSettings(boolean internalServerError, boolean otherServerError,
        boolean replyOffSchema, boolean notImplemented, boolean invalidDataAccepted,
        boolean noReplyInTime, boolean undeclaredStatusCode, boolean undeclaredContentType) {

    private static final CheckSettings DEFAULTS =
            new CheckSettings(true, true, true, true, true, true, true, true);

    /** What a run does when nobody has said otherwise: every check is made. */
    public static CheckSettings defaults() {
        return DEFAULTS;
    }
}
