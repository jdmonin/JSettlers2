package soc.game;

/**
 * Represents an immutable set of resources
 *
 * To construct a mutable set, see {@link SOCResourceSet}.
 */
public interface ResourceSet
{
    /**
     * How many resources of this type are contained in the set?
     * @return the number of a kind of resource
     *
     * @param resourceType  the type of resource, like {@link SOCResourceConstants#CLAY}
     * @see #contains(int)
     */
    int getAmount(int resourceType);

    /**
     * Does the set contain any resources of this type?
     * @param resourceType  the type of resource, like {@link SOCResourceConstants#CLAY}
     * @return true if the set's amount of this resource &gt; 0
     * @see #getAmount(int)
     * @see #contains(ResourceSet)
     */
    boolean contains(int resourceType);

    /**
     * Get the number of known resource types contained in this set:
     * {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD},
     * excluding {@link SOCResourceConstants#UNKNOWN} or {@link SOCResourceConstants#GOLD_LOCAL}.
     * An empty set returns 0, a set containing only wheat returns 1,
     * that same set after adding wood and sheep returns 3, etc.
     * @return  The number of resource types in this set with nonzero resource counts.
     * @since 2.0.00
     */
    int resourceTypeCount();

    /**
     * Get the total number of resources in this set
     * @return the total number of resources
     */
    int getTotal();

    /**
     * @return true if this contains at least the resources in other
     *
     * @param other  the sub set, can be {@code null} for an empty resource subset
     * @see #contains(int)
     */
    boolean contains(ResourceSet other);
}
