package src.soctest.game.hex;

import org.junit.Test;
import soc.game.SOCBoard;
import soc.game.hex.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class HexTypeTest {
    @Test
    public void typeIds_areUnique() throws Exception
    {
        List<IHex> hexes = Arrays.asList(
                new Water(),
                new Clay(),
                new Wheat(),
                new Ore(),
                new Sheep(),
                new Wood(),
                new Goldmine(),
                new Fog()
        );

        assertEquals(8, hexes
                .stream()
                .map(h -> h.hexType().typeId())
                .distinct()
                .count());
    }


}