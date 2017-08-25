package soc.game.hex;

import soc.game.SOCBoard;

/**
 * Representation of a hex type
 *
 * The idea is to replace public api signatures from int or int[] to
 * a typed item. This communicates more clearly and allows for a distinction
 * between instances and classes of instances within the game model. This is
 * useful, since we want to know things like "get me all the hexes where the
 * type equals $hexType".
 * This is work in progress and will be removed when integers are replaced by
 * types.
 *
 * An enum here disables custom hex types, as extending enums is impossible.
 * By exposing a public .ctor, public api users can still provide their own
 * hex types.
 *
 */
public final class HexType
{
    public static final HexType DESERT = new HexType(6);

    /**
     * These constants where administered by {@link SOCBoard}. The static numbers
     * have been moved to their respective {@link IHex} implementation.
     * The concept is represented by this class and the number by this field.
     */
    private final int _typeId;

    public HexType(final int typeId)
    {
        _typeId = typeId;
    }

    public int typeId()
    {
        return  _typeId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        HexType hexType = (HexType) o;
        return _typeId == hexType._typeId;
    }

    @Override
    public int hashCode()
    {
        return _typeId;
    }
}
