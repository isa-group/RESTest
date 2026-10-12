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
 * <p>Every reply is put to a few checks, and each one that finds something wrong reports a fault: a
 * reply of 500, which says the API fell over, or a reply whose body is not the shape the API's own
 * document says it would be. A check can be wrong about one API - a document that describes its
 * error replies badly makes every one of them look broken - and a person whose build goes red for
 * that reason needs to turn the one check off without turning the tool off. So each check has a
 * switch here, named for what is wrong with a reply it reports, and on unless somebody says
 * otherwise.
 *
 * <p>A check switched off is not made at all. That changes nothing about which requests a run
 * builds; it changes which replies are called faults, and so whether the run ends saying it found
 * any.
 *
 * @param internalServerError report a reply of 500, which says the API fell over while handling the
 *     request
 * @param replyOffSchema report a reply whose body is not the shape the document gives for its status
 *     code and media type
 */
public record CheckSettings(boolean internalServerError, boolean replyOffSchema) {

    private static final CheckSettings DEFAULTS = new CheckSettings(true, true);

    /** What a run does when nobody has said otherwise: every check is made. */
    public static CheckSettings defaults() {
        return DEFAULTS;
    }
}
