package soc.game.hex;

/**
 * Represents a hexagon tile on the board.
 *
 * Currently, this interface is defined to cater to {@link HexType}. It seems
 * logical to define the {@link HexType} within the different hex implementations
 * themself. As such, I need a simple interface definition so I can implement the
 * IHex types which have an associated {@link HexType}.
 *
 * For the future, this concept can contain more stuff what makes sense, like for
 * example a port or a location.
 */
public interface IHex {
    HexType hexType();
//    Location location();
//    Port port();
}
