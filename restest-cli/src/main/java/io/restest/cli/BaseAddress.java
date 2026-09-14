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
package io.restest.cli;

import io.restest.core.model.ApiModel;
import io.restest.core.model.Server;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Works out where to send the requests, and refuses anything the run could not send them to.
 *
 * <p>A person can say where the API lives, and if they do that is the answer. Otherwise the answer
 * comes from the document, which usually names one or more addresses the API is served from. Neither
 * source can be trusted blindly: a document may name a template nobody filled in, an address that is
 * only a path, or nothing at all, and a person may paste an address with a query string on the end.
 *
 * <p>The point of checking here is *when* the complaint arrives. Without it, an unusable address is
 * discovered once per request, deep inside the part that assembles them, after the run has opened
 * its files and started announcing itself - so a single mistake becomes hundreds of identical
 * failures in a report instead of one sentence before anything happened.
 */
final class BaseAddress {

    private BaseAddress() {
    }

    /**
     * The address requests will be sent to.
     *
     * @param given what the person typed, or {@code null} if they said nothing
     * @param model the API as the document describes it, whose declared addresses are the fallback
     * @return the address, with any trailing slash left as it was written
     * @throws IllegalArgumentException if what was given cannot be used, or nothing usable was found
     */
    static String resolve(String given, ApiModel model) {
        if (given != null && !given.isBlank()) {
            return checked(given.trim());
        }
        return model.servers().stream()
                .filter(Server::isResolved)
                .map(Server::resolvedUrl)
                .filter(BaseAddress::isUsable)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(explainMissing(model)));
    }

    private static String checked(String address) {
        if (!isUsable(address)) {
            throw new IllegalArgumentException("'" + address + "' is not somewhere requests can be "
                    + "sent. Give a whole web address with a host and nothing after the path, such "
                    + "as http://localhost:8080/api/v3");
        }
        return address;
    }

    /**
     * Whether requests can actually be sent to this address.
     *
     * <p>Three things are asked of it, and each has been seen in a real document or on a real
     * command line. It has to say which protocol to speak, and it has to be one we speak. It has to
     * name a host, which rules out the lone {@code /} that reading a document with no declared
     * address produces. And it must carry neither a query string nor a fragment, because every
     * request adds its own and two query strings on one address is not an address.
     */
    private static boolean isUsable(String address) {
        URI parsed;
        try {
            parsed = new URI(address);
        } catch (URISyntaxException notAnAddress) {
            return false;
        }
        String protocol = parsed.getScheme() == null
                ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        return ("http".equals(protocol) || "https".equals(protocol))
                && parsed.getHost() != null
                && parsed.getRawQuery() == null
                && parsed.getRawFragment() == null;
    }

    private static String explainMissing(ApiModel model) {
        Optional<String> unusable = model.servers().stream()
                .map(Server::url)
                .findFirst();
        String because = unusable
                .map(url -> "the document names '" + url + "', which is not one")
                .orElse("the document names none");
        return "there is nowhere to send the requests: " + because + ". Say where the API is "
                + "running with --url, for instance --url http://localhost:8080";
    }
}
