/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.util;

import java.util.Vector;


/**
 * DOCUMENT ME!
 *
 * @author $author$
 * @version $Revision$
 */
public class NodeLenVis
{
    /**
     * the coordinates of a node
     */
    public int node;

    /**
     * the current length of the path we're going down
     */
    public int len;

    /**
     * nodes that we have visited along the way
     */
    public Vector vis;

    /**
     * Creates a new NodeLenVis object.
     *
     * @param n DOCUMENT ME!
     * @param l DOCUMENT ME!
     * @param v DOCUMENT ME!
     */
    public NodeLenVis(int n, int l, Vector v)
    {
        node = n;
        len = l;
        vis = v;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public String toString()
    {
        String s = "NodeLenVis:n=" + Integer.toHexString(node) + "|l=" + len + "|vis=" + vis;

        return s;
    }
}
