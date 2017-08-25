package soc.game.hex;

/**
 * Convenience implementation for basic hex types
 */
public class HexBase implements IHex
{
    private HexType _hexType;

    protected HexBase(HexType hexType)
    {
        _hexType = hexType;
    }

    @Override
    public HexType hexType()
    {
        return _hexType;
    }
}
