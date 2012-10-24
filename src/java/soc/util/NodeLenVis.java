/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2011-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
package soc.util;

import java.util.Vector;

import soc.game.SOCRoad;


/**
 * State object for iteratively tracing longest road:
 * A node, path length so far, visited nodes, and optionally the road or ship
 * that led to this node.
 *
 * @author Robert S Thomas &lt;thomas@infolab.northwestern.edu&gt;
 */
public class NodeLenVis<T>
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
    public Vector<T> vis;

    /**
     * the road or ship that lead us to this node,
     * if {@link #len} &gt; 0 and {@link soc.game.SOCGame#hasSeaBoard}.
     * If <tt>len</tt> == 0, <tt>inboundRoad</tt> is null because we're just starting the segment.
     * @since 2.0.00
     */
    public SOCRoad inboundRoad;

    /**
     * Creates a new NodeLenVis object.
     *
     * @param n  Node coordinate
     * @param l  Length so far
     * @param v  Vector of nodes visited so far, as {@link Integer}s
     */
    public NodeLenVis(int n, int l, Vector<T> v)
    {
        this (n, l, v, null);
    }

    /**
     * Creates a new NodeLenVis object.
     *
     * @param n  Node coordinate
     * @param l  Length so far
     * @param v  Vector of nodes visited so far, as {@link Integer}s
     * @param rs  Road or ship that led to this node, if <tt>l</tt> &gt; 0;
     *            only needed if {@link soc.game.SOCGame#hasSeaBoard}
     * @since 2.0.00
     */
    public NodeLenVis(int n, int l, Vector<T> v, SOCRoad rs)
    {
        node = n;
        len = l;
        vis = v;
        inboundRoad = rs;
    }

    /**
     * Get a string representation of this NodeLenVis.
     * The inbound road or ship, if any, is not included.
     *
     * @return A string in the form of: <tt>NodeLenVis:n=<em>node</em>|l=<em>len</em>|vis=<em>vis</em></tt>
     */
    @Override
    public String toString()
    {
        String s = "NodeLenVis:n=" + Integer.toHexString(node) + "|l=" + len + "|vis=" + vis;

        return s;
    }
}
