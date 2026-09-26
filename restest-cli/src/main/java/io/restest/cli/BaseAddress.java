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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Works out where to send the requests, and refuses anything the run could not send them to.
 *
 * <p>A person can say where the API lives, and if they do that is the answer. Otherwise the answer
 * comes from the document, which usually names one or more addresses the API is served from. Neither
 * source can be trusted blindly: a document may name a template nobody filled in, an address that is
 * only a path, or nothing at all, and a person may paste an address with a query string on the end.
 *
 * <p>The two sources are not quite either-or. Plenty of APIs are served from under a directory -
 * {@code http://localhost:9966/petclinic/api} - and somebody who says the API has moved to another
 * machine is telling us the machine, not that the directory has gone away. So an address given
 * without a path of its own keeps the one the document declares, and an address given with a path
 * replaces it entirely, because then the person has said what they mean.
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
     *     and whose declared directory is kept when the address given names none
     * @return the address, with any trailing slash left as it was written unless a directory was
     *     added after it
     * @throws IllegalArgumentException if what was given cannot be used, or nothing usable was found
     */
    static String resolve(String given, ApiModel model) {
        Objects.requireNonNull(model, "model");
        if (given != null && !given.isBlank()) {
            return checked(given.trim(), model);
        }
        return model.servers().stream()
                .filter(Server::isResolved)
                .map(Server::resolvedUrl)
                .filter(BaseAddress::isUsable)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(explainMissing(model)));
    }

    private static String checked(String address, ApiModel model) {
        if (!isUsable(address)) {
            throw new IllegalArgumentException("'" + address + "' is not somewhere requests can be "
                    + "sent. Give a whole web address with a host and nothing after the path, such "
                    + "as http://localhost:8080/api/v3");
        }
        String directory = namesNoDirectory(address) ? declaredDirectory(address, model) : "";
        if (directory.isEmpty()) {
            return address;
        }
        String machine = address;
        while (machine.endsWith("/")) {
            machine = machine.substring(0, machine.length() - 1);
        }
        return machine + directory;
    }

    /**
     * Whether the address says only which machine to talk to, and nothing about where under it the
     * API lives.
     *
     * <p>Slashes and nothing else count as nothing. Somebody typing a trailing slash is finishing an
     * address, not saying the API sits at the very top of the server - and if the number of slashes
     * decided it, the difference between keeping a directory and losing one would come down to a
     * typing habit.
     */
    private static boolean namesNoDirectory(String address) {
        String path = pathWithin(address);
        return path.chars().allMatch(slash -> slash == '/');
    }

    /**
     * The directory the document says the API is served from, or nothing if it says none.
     *
     * <p>Three readings, in this order, and the order is the whole of the rule.
     *
     * <p>If the document describes the very machine somebody has pointed us at, it has already
     * answered the question and its answer is used - including when the answer is "no directory".
     * A document that lists a production address under a directory and a local one at the root is
     * saying something precise about each, and somebody testing the local one should not have
     * production's directory bolted on. Only addresses that can be read at all are considered, here
     * as everywhere below, so a declared address this class would refuse cannot decide anything by
     * being listed first. It can only recognise a machine in an address that is written out whole:
     * one carrying a blank the document never filled in, or anything else no parser will take,
     * names no machine that could be compared, so it never matches, and what the document says
     * about it is used only if nothing else answers.
     *
     * <p>Otherwise the directory comes from the address the run would have used had nobody said
     * anything: the first one requests could actually have been sent to. If that names no directory
     * then there is none, whatever a later address says - an API that moves to another machine must
     * not also appear to move into a directory it was never in.
     *
     * <p>Only a document with no usable address at all is read further, and then the first
     * directory that can be read from any of its addresses is taken. Such a document cannot start a
     * run without somebody saying where the API is, so there is no second reading to disagree with,
     * and what it says about the directory is all there is to go on.
     */
    private static String declaredDirectory(String given, ApiModel model) {
        // Every declared address that can be read at all, with the document's own defaults filled
        // in. Not only the ones with nothing left to fill: an address still carrying a blank cannot
        // be sent to, which is what the readings below turn on, and the directory beside the blank
        // may still be plain.
        List<String> readable = model.servers().stream()
                .map(Server::resolvedUrl)
                .filter(BaseAddress::readable)
                .toList();

        Optional<String> theSameMachine = readable.stream()
                .filter(address -> namesTheSameMachine(address, given))
                .findFirst();
        if (theSameMachine.isPresent()) {
            return directoryWithin(theSameMachine.get());
        }

        // Exactly what the fallback above picks when nobody says anything, written the same way so
        // that the two cannot drift apart: an address with a blank left in it is never usable, but
        // saying so twice is cheaper than depending on it.
        Optional<String> wouldHaveBeenUsed = model.servers().stream()
                .filter(Server::isResolved)
                .map(Server::resolvedUrl)
                .filter(BaseAddress::isUsable)
                .findFirst();
        if (wouldHaveBeenUsed.isPresent()) {
            return directoryWithin(wouldHaveBeenUsed.get());
        }

        for (String address : readable) {
            String directory = directoryWithin(address);
            if (!directory.isEmpty()) {
                return directory;
            }
        }
        return "";
    }

    /** Whether two addresses name the same machine: same protocol, same host, same port. */
    private static boolean namesTheSameMachine(String declared, String given) {
        try {
            URI one = new URI(declared);
            URI other = new URI(given);
            Optional<String> host = hostOf(one);
            return host.isPresent()
                    && host.get().equalsIgnoreCase(hostOf(other).orElse(null))
                    && protocolOf(declared).equals(protocolOf(given))
                    && portOf(one) == portOf(other);
        } catch (URISyntaxException notAnAddress) {
            return false;
        }
    }

    /** The port an address names, or the one its protocol implies when it names none. */
    private static int portOf(URI address) {
        if (address.getPort() != -1) {
            return address.getPort();
        }
        String authority = address.getRawAuthority();
        if (address.getHost() == null && authority != null
                && authority.matches("[^@:\\[\\]]+:\\d{1,5}")) {
            return Integer.parseInt(authority.substring(authority.lastIndexOf(':') + 1));
        }
        return "https".equalsIgnoreCase(address.getScheme()) ? 443 : 80;
    }

    /**
     * The machine an address names.
     *
     * <p>{@link URI} names no host when the name has an underscore in it, which is how Docker
     * Compose service names are often written, although requests are sent to such a name without
     * any trouble. So when it names none, the host is read from the part between the protocol and
     * the path, provided that part holds nothing but a name and perhaps a port.
     */
    private static Optional<String> hostOf(URI address) {
        if (address.getHost() != null) {
            return Optional.of(address.getHost());
        }
        String authority = address.getRawAuthority();
        if (authority == null || !authority.matches("[^@:\\[\\]]+(:\\d{1,5})?")) {
            return Optional.empty();
        }
        int port = authority.indexOf(':');
        return Optional.of(port == -1 ? authority : authority.substring(0, port));
    }

    /**
     * Whether a declared address can be trusted to say where the API lives, even though requests
     * could not be sent to it as it stands.
     *
     * <p>Two shapes are meant here, and both are ordinary. An address that names no protocol -
     * {@code /api/v3}, or the {@code //api.example.com/v2} that reading an older document without
     * one produces - names no machine either, which is why a run cannot start from it, and says
     * perfectly clearly which directory it means. An address with a blank the document never filled
     * in is the same: nobody can send a request to it, and the directory in it may still be plain.
     *
     * <p>What is refused is an address served over something other than the web, since nothing
     * would be sent there anyway, and one carrying a query or a fragment, because this same class
     * refuses those when a person types them and reading half of one would be inconsistent.
     */
    private static boolean readable(String declared) {
        if (declared.isBlank() || declared.indexOf('?') >= 0 || declared.indexOf('#') >= 0) {
            return false;
        }
        String protocol = protocolOf(declared);
        if (protocol.isEmpty()) {
            return true;
        }
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            return false;
        }
        // An address that names a protocol has to name a machine after it. One written with a
        // slash missing, http:/example.com/v2, names none - and everything after the colon would
        // read as a directory made out of a machine's name.
        return declared.regionMatches(true, protocol.length(), "://", 0, 3);
    }

    /**
     * The protocol an address names, in lower case, or nothing when it names none.
     *
     * <p>Read without a parser, and it has to be: an address may carry a blank the document never
     * filled in, and one written {@code http:/example.com} with a slash missing still names a
     * protocol even though what follows is not a machine. Whatever comes before the first colon is
     * the protocol, unless a slash comes first, in which case the address begins with a path or
     * with a machine's name and names no protocol at all.
     */
    private static String protocolOf(String address) {
        int colon = address.indexOf(':');
        int slash = address.indexOf('/');
        return colon > 0 && (slash < 0 || colon < slash)
                ? address.substring(0, colon).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * The directory an address names, or nothing when it names none that could be used as one.
     *
     * <p>A path that does not start at the root is refused, since {@code api.example/v2} may mean a
     * machine or a directory and reading it as a directory would splice a machine's name into every
     * request. So is anything that would not be a legal path on its own: a blank nobody filled in,
     * a space, a half-written escape. Asking a server for one of those gets exactly the same
     * nothing as asking it for the wrong path, which is the mistake this whole check exists to
     * prevent.
     */
    private static String directoryWithin(String address) {
        String path = pathWithin(address);
        if (!path.startsWith("/")) {
            return "";
        }
        try {
            new URI(path);
        } catch (URISyntaxException notAPath) {
            return "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * The path part of an address, exactly as it was written, or nothing when it has none.
     *
     * <p>As it was written, and not as a parser would spell it out: a path may contain characters
     * written in the {@code %2F} form, and turning those back into the characters they stand for
     * would change which resource the address names, or bolt a query string onto an address that
     * had none.
     *
     * <p>An address no parser will accept is read by hand rather than given up on. A document may
     * leave a blank for somebody to fill - {@code https://{customer}.example.com/v2} - and while
     * nothing can be sent to that as it stands, the directory in it starts where the machine's name
     * ends, exactly as in any other address. Where the machine's name begins is what the two forms
     * below say, and everything before its first slash belongs to it, not to the path.
     */
    private static String pathWithin(String address) {
        try {
            String path = new URI(address).getRawPath();
            return path == null ? "" : path;
        } catch (URISyntaxException notAnAddress) {
            String afterTheMachine = address;
            int protocol = afterTheMachine.indexOf("://");
            if (protocol >= 0) {
                afterTheMachine = afterTheMachine.substring(protocol + 3);
            } else if (afterTheMachine.startsWith("//")) {
                afterTheMachine = afterTheMachine.substring(2);
            }
            int slash = afterTheMachine.indexOf('/');
            return slash < 0 ? "" : afterTheMachine.substring(slash);
        }
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
                && hostOf(parsed).isPresent()
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
