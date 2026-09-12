/**
 * The specification parser. The only module allowed to reference {@code io.swagger}.
 *
 * <p>Exports {@code io.restest.spec} so a caller can construct {@code SwaggerSpecificationParser}
 * directly. The {@code provides} declaration for {@code SpecificationParser} does not yet do
 * anything on its own - nothing in this project runs {@code ServiceLoader.load} against the
 * interface, and no consuming module declares {@code uses} - it is here so that when one does, this
 * module is already ready to be found rather than needing a second change alongside the first
 * consumer, at no cost while nothing looks for it.
 *
 * <p>Two of the required modules are not named by an import anywhere in this module's own code:
 * {@code swagger.parser} and {@code swagger.parser.core} are automatic modules (their jars carry no
 * {@code Automatic-Module-Name}, so the name is derived from the jar's filename - the build's own
 * warning about this is expected, not a defect to fix), and the parser library's own internal
 * delegation between its jars happens without this module's {@code requires} needing to mention
 * every one of them: an automatic module can read every other module on the path regardless of what
 * named this one. What is required here is exactly what this module's own source imports from -
 * confirmed by removing each clause in turn and recompiling.
 *
 * <p>One more filename-derived name is at stake here that this module does not control: a transitive
 * dependency of {@code swagger-parser} (the 1.x-era library it uses internally to convert an OAS 2.0
 * document) derives the identical automatic module name, {@code swagger.parser}, from its own jar's
 * filename, and is silently dropped from the module graph as a result - confirmed harmless for OAS
 * 2.0 conversion specifically (a real Swagger 2.0 corpus document converts and parses correctly end
 * to end), but a genuine, filename-derived collision nonetheless, and a real ceiling on how cleanly
 * this module can ever be published as its own, strict JPMS artifact.
 */
module io.restest.spec {
    requires io.restest.core;
    requires swagger.parser;
    requires swagger.parser.core;
    requires io.swagger.v3.oas.models;
    requires org.yaml.snakeyaml;

    exports io.restest.spec;

    provides io.restest.core.spec.SpecificationParser with io.restest.spec.SwaggerSpecificationParser;
}
