package de.femtopedia.studip.shib;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Simple representation of a Pair of Key and Value.
 *
 * @param <A> Key type
 * @param <B> Value type
 */
@Data
@AllArgsConstructor
public class Pair<A, B> {

    /**
     * The key of the pair.
     */
    private A key;

    /**
     * The value of the pair.
     */
    private B value;

}
