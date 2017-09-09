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
