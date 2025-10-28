package de.femtopedia.studip.shib;

import lombok.Data;

/**
 * Simple representation of a Pair of Key and Value.
 *
 * @param <A> Key type
 * @param <B> Value type
 */
@Data
public class Pair<A, B> {

    /**
     * The key of the pair.
     */
    private final A key;

    /**
     * The value of the pair.
     */
    private final B value;

}
