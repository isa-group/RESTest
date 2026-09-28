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
package io.restest.gen;

import io.restest.core.model.HttpMethod;
import io.restest.core.model.Operation;
import io.restest.core.model.OperationId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What each of an API's creations makes, and which of its other operations are about the thing
 * made.
 *
 * <p>A run that wants to delete something and read it again, or add something under a thing that
 * no longer exists, has to know, for a creation such as {@code POST /owners}, where the thing it
 * creates lives afterwards and what else takes its identifier. That is read off the addresses
 * alone, the way a person reading the document would read them:
 *
 * <ul>
 *   <li>the thing's <b>own address</b> is the creation's address with one more gap:
 *       {@code /owners/{ownerId}} for {@code POST /owners}. Gaps match gaps wherever they are,
 *       whatever they are called, so {@code /owners/{id}/pets/{petId}} is where
 *       {@code POST /owners/{ownerId}/pets} puts a pet;</li>
 *   <li>it may also have an address elsewhere, about the same kind of thing, behind a gap named the
 *       way identifiers are: {@code /pets/{petId}} is a pet's own address too. The kind is spelt the
 *       way the memory of what the API returned spells it, plural and singular alike, but read more
 *       strictly than the memory reads it, since a series deletes what it finds there: from the
 *       fixed part before the gap, and from the gap's own name only when nothing fixed comes before
 *       it. So {@code /user/repository_invitations/{invitation_id}} is about repository
 *       invitations, not about the invitations {@code POST /orgs/{org}/invitations} makes;</li>
 *   <li>an operation <b>under</b> the thing is one whose address carries on past one of its own
 *       addresses: {@code POST /owners/{ownerId}/pets} is under an owner.</li>
 * </ul>
 *
 * <p>A {@code POST} whose address ends in a gap, such as {@code POST /pet/{petId}}, is taken to make
 * nothing with an address of its own. The gap already names a thing that exists - the request
 * changes it, or does something to it - and what it answers with is that thing, which the run did
 * not make.
 *
 * <p>The addresses cannot tell a {@code POST} that attaches a thing which already exists from one
 * that makes a thing under another. Adding one of an organisation's teams to those allowed on a
 * branch, {@code POST .../restrictions/teams}, reads like adding a pet under an owner, and is taken
 * to make a team that lives at {@code /teams/{team_id}}; what keeps a series off the team is its
 * reply, which {@link Sequences} reads.
 *
 * <p>Only operations the run can attempt are considered, on either side, so an operation a plan set
 * aside is never sent as part of a series. {@link Sequences} decides what to send about the things
 * made; this only says where they are.
 */
final class Creations {

    /** The methods an address can be asked, in the order an address's operations are kept. */
    private static final List<HttpMethod> ON_AN_ADDRESS = List.of(HttpMethod.GET, HttpMethod.HEAD,
            HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.POST);

    private final Map<OperationId, Creation> byCreation;

    private Creations(Map<OperationId, Creation> byCreation) {
        this.byCreation = byCreation;
    }

    /**
     * What every creation among these operations makes.
     *
     * @param operations the operations a run can attempt, in the order the document declares them
     * @return the answer for every {@code POST} among them
     */
    static Creations among(List<Operation> operations) {
        Objects.requireNonNull(operations, "operations");
        Map<OperationId, Creation> byCreation = new LinkedHashMap<>();
        for (Operation creation : operations) {
            if (creation.method() == HttpMethod.POST) {
                byCreation.put(creation.id(), creationOf(creation, operations));
            }
        }
        return new Creations(Collections.unmodifiableMap(byCreation));
    }

    /**
     * What a creation makes, when the operation is one.
     *
     * @param operation an operation of the run
     * @return what it creates, or nothing for an operation that is not a {@code POST}
     */
    Optional<Creation> of(Operation operation) {
        return Optional.ofNullable(byCreation.get(Objects.requireNonNull(operation, "operation").id()));
    }

    private static Creation creationOf(Operation creation, List<Operation> operations) {
        List<String> theirs = partsOf(creation.path());
        if (!theirs.isEmpty() && gapNamed(theirs.get(theirs.size() - 1)).isPresent()) {
            return new Creation(creation, List.of(), List.of(), Optional.empty());
        }
        List<Address> own = new ArrayList<>();
        // Its address with one more gap first: that is where the creation says the thing goes,
        // and the gaps before it are the ones the creation itself was sent.
        addressAt(theirs, operations).ifPresent(own::add);
        ObservedValues.kindOfThingAt(creation.path()).ifPresent(kind ->
                own.addAll(addressesOfKind(kind, own, operations)));
        List<Under> under = new ArrayList<>();
        for (Operation operation : operations) {
            if (operation.id().equals(creation.id())) {
                continue;
            }
            for (Address address : own) {
                Optional<Under> found = under(operation, address, theirs);
                if (found.isPresent()) {
                    under.add(found.get());
                    break;
                }
            }
        }
        // The list the thing joins, read at the creation's own address: GET /owners beside
        // POST /owners.
        Optional<Operation> list = operations.stream()
                .filter(operation -> operation.method() == HttpMethod.GET
                        && operation.path().equals(creation.path()))
                .findFirst();
        return new Creation(creation, own, under, list);
    }

    /** The address one gap past the creation's own, when some operation lives there. */
    private static Optional<Address> addressAt(List<String> creation, List<Operation> operations) {
        Map<HttpMethod, Operation> at = new EnumMap<>(HttpMethod.class);
        String gap = null;
        String path = null;
        Map<String, String> shared = new LinkedHashMap<>();
        for (Operation operation : operations) {
            List<String> parts = partsOf(operation.path());
            if (parts.size() != creation.size() + 1 || !sameShape(parts, creation)) {
                continue;
            }
            Optional<String> last = gapNamed(parts.get(parts.size() - 1));
            if (last.isEmpty() || !ON_AN_ADDRESS.contains(operation.method())
                    || at.containsKey(operation.method())) {
                continue;
            }
            if (path == null) {
                path = operation.path();
                gap = last.get();
                shared.putAll(sharedGaps(parts, creation, creation.size()));
            } else if (!partsOf(path).equals(parts)) {
                // Another spelling of the same address - {id} here, {ownerId} there - is the
                // same place, but its gap names differ; only the first spelling is kept, so that
                // every operation kept here fills the same gap.
                continue;
            }
            at.put(operation.method(), operation);
        }
        return path == null ? Optional.empty()
                : Optional.of(new Address(path, gap, shared, at, true));
    }

    /**
     * The addresses elsewhere that are about the same kind of thing, behind a gap named like an
     * identifier: {@code /pets/{petId}} for what {@code POST /owners/{ownerId}/pets} makes. Such an
     * address shares none of the creation's gaps, since nothing says its own gaps are theirs.
     */
    private static List<Address> addressesOfKind(String kind, List<Address> already,
            List<Operation> operations) {
        Map<String, Map<HttpMethod, Operation>> byPath = new LinkedHashMap<>();
        Map<String, String> gapOf = new LinkedHashMap<>();
        for (Operation operation : operations) {
            if (!ON_AN_ADDRESS.contains(operation.method())
                    || already.stream().anyMatch(own -> own.path().equals(operation.path()))) {
                continue;
            }
            List<String> parts = partsOf(operation.path());
            if (parts.isEmpty()) {
                continue;
            }
            Optional<String> last = gapNamed(parts.get(parts.size() - 1));
            if (last.isEmpty() || !ObservedValues.looksLikeAnIdentifier(last.get())
                    || !kindOfThingFor(operation.path(), last.get()).filter(kind::equals)
                            .isPresent()) {
                continue;
            }
            byPath.computeIfAbsent(operation.path(), ignored -> new EnumMap<>(HttpMethod.class))
                    .putIfAbsent(operation.method(), operation);
            gapOf.putIfAbsent(operation.path(), last.get());
        }
        List<Address> found = new ArrayList<>();
        byPath.forEach((path, at) -> found.add(new Address(path, gapOf.get(path), Map.of(), at,
                false)));
        return found;
    }

    /**
     * The kind of thing whose identifier goes in a gap: the one the fixed part before it names, and
     * only when nothing fixed comes before it, the one the gap's own name names. The fixed part
     * wins because it is the address's own word for what it holds; a gap's name is often shorter.
     */
    private static Optional<String> kindOfThingFor(String path, String gap) {
        return ObservedValues.kindOfThingBefore(path, gap)
                .or(() -> ObservedValues.kindOfThingInTheName(gap));
    }

    /**
     * The operation as one under an address of the thing, when its address carries on past it.
     *
     * @param operation any operation of the run
     * @param address one of the thing's own addresses
     * @param creation the parts of the creation's own address
     */
    private static Optional<Under> under(Operation operation, Address address,
            List<String> creation) {
        List<String> parts = partsOf(operation.path());
        List<String> own = partsOf(address.path());
        if (parts.size() <= own.size() || !sameShape(parts, own)) {
            return Optional.empty();
        }
        String gap = gapNamed(parts.get(own.size() - 1)).orElseThrow();
        // Only the gaps before the thing's own are shared with the creation, and only when the
        // address was found by being the creation's with one more gap.
        Map<String, String> shared = address.byPrefix()
                ? sharedGaps(parts, creation, creation.size())
                : Map.of();
        return Optional.of(new Under(operation, gap, shared));
    }

    /**
     * Whether the first parts of one address have the shape of another: the same fixed words in the
     * same places, and a gap wherever the other has one.
     */
    private static boolean sameShape(List<String> longer, List<String> prefix) {
        if (longer.size() < prefix.size()) {
            return false;
        }
        for (int at = 0; at < prefix.size(); at++) {
            boolean theirsIsAGap = gapNamed(prefix.get(at)).isPresent();
            boolean oursIsAGap = gapNamed(longer.get(at)).isPresent();
            if (theirsIsAGap != oursIsAGap
                    || !theirsIsAGap && !prefix.get(at).equals(longer.get(at))) {
                return false;
            }
        }
        return true;
    }

    /** The gaps of one address that stand where the other has gaps, among its first parts. */
    private static Map<String, String> sharedGaps(List<String> parts, List<String> creation,
            int howMany) {
        Map<String, String> shared = new LinkedHashMap<>();
        for (int at = 0; at < howMany; at++) {
            Optional<String> ours = gapNamed(parts.get(at));
            Optional<String> theirs = gapNamed(creation.get(at));
            if (ours.isPresent() && theirs.isPresent()) {
                shared.put(ours.get(), theirs.get());
            }
        }
        return Collections.unmodifiableMap(shared);
    }

    /** The name inside a part of an address that is one whole gap, such as {@code petId}. */
    static Optional<String> gapNamed(String part) {
        return part.length() > 2 && part.startsWith("{") && part.endsWith("}")
                && part.indexOf('{', 1) < 0
                ? Optional.of(part.substring(1, part.length() - 1)) : Optional.empty();
    }

    static List<String> partsOf(String path) {
        return Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toList();
    }

    /**
     * One creation and what is about the thing it makes.
     *
     * @param creation the {@code POST}
     * @param own the thing's own addresses, the creation's with one more gap first, then any found
     *     by kind, in the order the document declares them
     * @param under the operations whose addresses carry on past one of those, in document order
     * @param list the read of the list the thing joins, at the creation's own address, when the
     *     document declares one
     */
    record Creation(Operation creation, List<Address> own, List<Under> under,
            Optional<Operation> list) {

        Creation {
            Objects.requireNonNull(creation, "creation");
            Objects.requireNonNull(list, "list");
            own = List.copyOf(own);
            under = List.copyOf(under);
        }

        /**
         * The first of the thing's own addresses where every one of these methods is declared.
         *
         * @param methods what has to be askable there
         * @return the address, or nothing when no single address offers them all
         */
        Optional<Address> addressWith(HttpMethod... methods) {
            return own.stream()
                    .filter(address -> Arrays.stream(methods)
                            .allMatch(method -> address.operations().containsKey(method)))
                    .findFirst();
        }

        /**
         * The first operation with this method at any of the thing's own addresses.
         *
         * @param method the method
         * @return the operation and the address it is at, or nothing when none declares it
         */
        Optional<Placed> first(HttpMethod method) {
            for (Address address : own) {
                Operation operation = address.operations().get(method);
                if (operation != null) {
                    return Optional.of(new Placed(operation, address.gap(), address.shared()));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One of a thing's own addresses.
     *
     * @param path the address as the document writes it
     * @param gap the gap its identifier goes in, such as {@code ownerId}
     * @param shared the gaps before it that stand where the creation's own address has gaps, by
     *     the name each has here and the name it has in the creation's address; empty for an
     *     address found by kind
     * @param operations the operations declared at it, by method
     * @param byPrefix whether it is the creation's own address with one more gap, rather than one
     *     found by the kind of thing it is about
     */
    record Address(String path, String gap, Map<String, String> shared,
            Map<HttpMethod, Operation> operations, boolean byPrefix) {

        Address {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(gap, "gap");
            shared = Collections.unmodifiableMap(new LinkedHashMap<>(shared));
            Map<HttpMethod, Operation> copied = new EnumMap<>(HttpMethod.class);
            copied.putAll(operations);
            operations = Collections.unmodifiableMap(copied);
        }

        /** The operation declared at this address with this method, placed. */
        Optional<Placed> with(HttpMethod method) {
            return Optional.ofNullable(operations.get(method))
                    .map(operation -> new Placed(operation, gap, shared));
        }
    }

    /**
     * An operation that takes the thing's identifier, and where.
     *
     * @param operation the operation
     * @param gap the gap in its address the thing's identifier goes in
     * @param shared the gaps before it that take what the creation was sent, by the name each has
     *     here and the name it has in the creation's address
     */
    record Placed(Operation operation, String gap, Map<String, String> shared) {

        Placed {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(gap, "gap");
            shared = Collections.unmodifiableMap(new LinkedHashMap<>(shared));
        }
    }

    /**
     * An operation under the thing: its address carries on past one of the thing's own.
     *
     * @param operation the operation
     * @param gap the gap in its address the thing's identifier goes in
     * @param shared the gaps before that one that take what the creation was sent
     */
    record Under(Operation operation, String gap, Map<String, String> shared) {

        Under {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(gap, "gap");
            shared = Collections.unmodifiableMap(new LinkedHashMap<>(shared));
        }

        /** The same, as an operation that takes the thing's identifier. */
        Placed placed() {
            return new Placed(operation, gap, shared);
        }
    }
}
