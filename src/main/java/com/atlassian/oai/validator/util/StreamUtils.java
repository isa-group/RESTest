package com.atlassian.oai.validator.util;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {

    private StreamUtils() { }

    public static <T> Stream<T> stream(final Iterator<T> it) {
        final Iterable<T> iterable = () -> it;
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
