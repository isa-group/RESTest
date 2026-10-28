# RESTest's own container image: the command line, on a plain Java 21 runtime.
#
#     docker build --tag restest .
#     docker run --rm restest version
#     docker run --rm -v "$PWD:/work" --user "$(id -u):$(id -g)" \
#         --add-host=host.docker.internal:host-gateway \
#         restest run openapi.yaml --url http://host.docker.internal:8080
#
# The folder mounted at /work is where the document is read from and the results written to;
# --user makes them belong to whoever ran it on Linux; host.docker.internal is the machine Docker runs
# on, which Linux needs --add-host to know.
#
# Two stages. The first compiles RESTest from this checkout and assembles the folder a release
# archive unpacks into (dist/assemble.sh); the second copies that folder onto a Java runtime and
# starts the archive's own launcher. So the image needs nothing but Docker to build, from any
# checkout, and it starts RESTest exactly as a release archive does.
#
# The first stage runs on the processor of the machine that builds, whatever the image is for: what it
# makes is Java, the same on every processor. The second only copies files, so building it for another
# processor needs no emulation.
#
# Java 21 at run time, because it is the oldest release RESTest supports. Both base images are pinned
# by content, so that the same commit always builds on the same Java; moving them to a newer patch of
# Java is a step of every release (docs/ci.md).
#
# Nothing in it knows how the tool is measured.

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk@sha256:3ab7fc8d0b4ade8fef96903fb36767c1dbb4fd1535dcfad85b59c17afbbb5857 AS build

WORKDIR /src
COPY . .
# The Maven cache outlives the build, so a second build downloads nothing; it never reaches the image.
# Tests are neither compiled nor run: continuous integration runs them on every commit.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --quiet -Dmaven.test.skip=true package -pl restest-cli -am \
 && dist/assemble.sh /dist \
 && mv /dist/restest-* /dist/restest

FROM eclipse-temurin:21-jre@sha256:f37956012e0cb88400e5b3689aeae2d2d735364c1922ae2225536163b5a99f56

COPY --from=build /dist/restest /opt/restest

# Where a run's document is read from and its files written to: mount a folder here.
WORKDIR /work

# The launcher ends with `exec java`, so Java is the container's own process and `docker stop` reaches
# it: the run then stops as it does on Ctrl-C, writes what it found, and answers 143.
ENTRYPOINT ["/opt/restest/bin/restest"]

# Which RESTest this is, from which commit, built when: the release workflow says so. A build of any
# other checkout leaves them empty, rather than keep the base image's own, which name a version of
# Ubuntu and the day Ubuntu was built.
ARG VERSION=
ARG REVISION=
ARG CREATED=
LABEL org.opencontainers.image.title="RESTest" \
      org.opencontainers.image.description="Black-box testing for REST APIs, from an OpenAPI document" \
      org.opencontainers.image.url="https://github.com/isa-group/RESTest" \
      org.opencontainers.image.source="https://github.com/isa-group/RESTest" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.revision="$REVISION" \
      org.opencontainers.image.created="$CREATED"
