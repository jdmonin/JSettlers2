/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/

package soctest.message;

import java.util.ArrayList;
import java.util.List;

import soc.message.SOCMessage;
import soc.message.SOCMessageTemplateMs;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for template/abstract message types like {@link SOCMessageTemplateMs}
 * which aren't part of the main list tested in {@link TestToCmdToStringParse}.
 * @since 2.4.50
 */
public class TestTemplatesAbstracts
{

    /** Test {@link SOCMessageTemplateMs#toString()} and {@link SOCMessageTemplateMs#toString(List, String[])}. */
    @Test
    public void testSOCMessageTemplateMsToString()
    {
        final String[] NAMES_1 = {"a"}, NAMES_3 = {"a", "b", "c"};

        MessageMs msg = new MessageMs(null);
        assertEquals("MessageMs:(pa null)", msg.toString());
        assertEquals("MessageMs:(pa null)", msg.toString(null, null));

        List<String> pa = new ArrayList<>();
        msg = new MessageMs(pa);
        assertEquals("MessageMs:(pa empty)", msg.toString());
        assertEquals("MessageMs:(pa empty)", msg.toString(pa, null));
        assertEquals("MessageMs:(pa empty)", msg.toString(pa, NAMES_3));

        pa.add("xy");
        msg = new MessageMs(pa);
        assertEquals("MessageMs:p=xy", msg.toString());
        assertEquals("MessageMs:p=xy", msg.toString(pa, null));
        assertEquals("MessageMs:a=xy", msg.toString(pa, NAMES_1));
        assertEquals("MessageMs:a=xy", msg.toString(pa, NAMES_3));

        pa.add("z");
        msg = new MessageMs(pa);
        assertEquals("MessageMs:p=xy|p=z", msg.toString());
        assertEquals("MessageMs:p=xy|p=z", msg.toString(pa, null));
        assertEquals("MessageMs:a=xy|p=z", msg.toString(pa, NAMES_1));
        assertEquals("MessageMs:a=xy|b=z", msg.toString(pa, NAMES_3));

        pa.add(null);
        pa.add("w");
        msg = new MessageMs(pa);
        assertEquals("MessageMs:p=xy|p=z|(p null)|p=w", msg.toString());
        assertEquals("MessageMs:p=xy|p=z|(p null)|p=w", msg.toString(pa, null));
        assertEquals("MessageMs:a=xy|p=z|(p null)|p=w", msg.toString(pa, NAMES_1));
        assertEquals("MessageMs:a=xy|b=z|(c null)|p=w", msg.toString(pa, NAMES_3));

        pa.clear();
        pa.add(null);
        msg = new MessageMs(pa);
        assertEquals("MessageMs:(p null)", msg.toString());
        assertEquals("MessageMs:(p null)", msg.toString(pa, null));
        assertEquals("MessageMs:(a null)", msg.toString(pa, NAMES_3));

        pa.add("zw");
        msg = new MessageMs(pa);
        assertEquals("MessageMs:(p null)|p=zw", msg.toString());
        assertEquals("MessageMs:(p null)|p=zw", msg.toString(pa, null));
        assertEquals("MessageMs:(a null)|p=zw", msg.toString(pa, NAMES_1));
        assertEquals("MessageMs:(a null)|b=zw", msg.toString(pa, NAMES_3));
    }

    // TODO test SOCMessageTemplateMs.parseData_FindEmptyStrs

    /** Non-abstract subclass for tests. */
    @SuppressWarnings("serial")
    private static class MessageMs extends SOCMessageTemplateMs
    {
        /**
         * Constructor for tests.
         * @param pal List of parameters, or null if none.
         *     Sets {@link #pa} field to {@code pal}: Afterwards method calls on {@code pa} or {@code pal}
         *     will affect the same List object.
         *     <P>
         *     Does not convert {@link SOCMessage#EMPTYSTR} field values to "";
         *     see {@link SOCMessageTemplateMs#parseData_FindEmptyStrs(List)}.
         */
        public MessageMs(List<String> pal)
        {
            super(0, pal);
        }

        public String toString(List<String> params, String[] fieldNames)
        {
            return super.toString(params, fieldNames);
        }
    }

}
