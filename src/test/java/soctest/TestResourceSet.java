package soctest;

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestResourceSet
{
    @Test
    public void total_test()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        assertEquals(5, rs.getTotal());
    }

    @Test
    public void removeOneResource_removesOneResource()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        rs.subtract(SOCResourceConstants.CLAY, 1);
        assertEquals(4, rs.getTotal());
    }

    @Test
    public void removeTwoResources_doesNotThrowException()
    {
        SOCResourceSet rs = new SOCResourceSet(1,1,1,1,1,0);
        rs.subtract(SOCResourceConstants.CLAY, 2);
    }
}
