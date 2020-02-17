package com.atlassian.oai.validator.report;

import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ResourceBundle;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Resolves a message key to a {@link ValidationReport.Message} object.
 * <p>
 * Message Strings are resolved from the <code>messages</code> resource bundle.
 * <p>
 * Message levels are resolved using a configured {@link LevelResolver}.
 *
 * @see LevelResolver
 */
public class MessageResolver {

    private static final Logger log = getLogger(MessageResolver.class);

    private final ResourceBundle messages = ResourceBundle.getBundle("swagger/validation/messages");
    private final LevelResolver levelResolver;

    /**
     * Create a new instance with the default {@link LevelResolver} (all messages will be emitted at the ERROR level).
     *
     * @see LevelResolver#defaultResolver()
     */
    public MessageResolver() {
        this(LevelResolver.defaultResolver());
    }

    /**
     * Create a new instance with the provided {{@link LevelResolver}}.
     */
    public MessageResolver(final LevelResolver levelResolver) {
        this.levelResolver = levelResolver == null ? LevelResolver.defaultResolver() : levelResolver;
    }

    /**
     * Get the message with the given key.
     * <p>
     * If no message is found for the key will return <code>null</code>.
     *
     * @param key The key of the message to retrieve.
     * @param args Arguments to use when resolving the message String.
     *
     * @return The message for the given key, or <code>null</code> if no message is found
     */
    @Nullable
    public ValidationReport.Message get(@Nonnull final String key, final Object... args) {
        requireNonNull(key, "A message key is required.");
        final ValidationReport.Level level = levelResolver.getLevel(key);
        if (!messages.containsKey(key)) {
            log.warn("No message key found for '{}'", key);
            return null;
        }
        return new ImmutableMessage(key, level, format(messages.getString(key), args));
    }

    /**
     * Create a message with the given key and message.
     * <p>
     * Used when translating validation messages from other sources (e.g. JSON schema validation)
     * where a message has already been generated.
     * <p>
     * Uses the configured {@link LevelResolver} to resolve the message level.
     *
     * @param key The key to include in the message.
     * @param message The message to include.
     * @param additionalInfo Additional information to include in the message (if any).
     *
     * @return A message that contains the given key and message string.
     * The level will be set by the configured {@link LevelResolver}.
     */
    public ValidationReport.Message create(@Nonnull final String key, final String message, final String... additionalInfo) {
        requireNonNull(key, "A message key is required.");
        final ValidationReport.Level level = levelResolver.getLevel(key);
        return new ImmutableMessage(key, level, message, additionalInfo);
    }

    /**
     * Get the level that the given message key would be resolved at when using the {@link #get} or
     * {@link #create} methods.
     * <p>
     * Used in a small number of places where optimisations can be made if a validation will be ignored.
     *
     * @param key The message key to test
     *
     * @return The level the given message key will be resolved at, as determined by the configured {@link LevelResolver}.
     */
    public ValidationReport.Level getLevel(@Nonnull final String key) {
        return levelResolver.getLevel(key);
    }

    /**
     * Determine if the message with the given key would be resolved with a level of {@link ValidationReport.Level#IGNORE}.
     * <p>
     * Used in a small number of places where optimisations can be made if a validation will be ignored.
     *
     * @param key The message key to test
     *
     * @return <code>true</code> if the given message key will be ignored; <code>false</code> otherwise.
     */
    public boolean isIgnored(@Nonnull final String key) {
        return getLevel(key) == ValidationReport.Level.IGNORE;
    }

}
