/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.game.SOCResourceSet;

import java.util.Vector;


/**
 * This is a tree that contains possible
 * trade offers and how they're related
 * to each other.  Also contains a flag
 * for wheather or not this offer should
 * be expanded to other offers.
 *
 * @author Robert S. Thomas
 */
/*package*/ class SOCTradeTree
{
    SOCResourceSet resourceSet;
    SOCTradeTree parent;
    Vector<SOCTradeTree> children;
    boolean needsToBeExpanded;

    /**
     * this is a constructor
     *
     * @param set     the set of resources
     * @param par     the parent of this node
     */
    public SOCTradeTree(SOCResourceSet set, SOCTradeTree par)
    {
        resourceSet = set;
        needsToBeExpanded = true;
        children = new Vector<SOCTradeTree>();

        if (par != null)
        {
            par.addChild(this);
        }
        else
        {
            parent = null;
        }
    }

    /**
     * this is a constructor
     *
     * @param set     the set of resources
     */
    public SOCTradeTree(SOCResourceSet set)
    {
        resourceSet = set;
        parent = null;
        needsToBeExpanded = false;
        children = new Vector<SOCTradeTree>();
    }

    /**
     * @return the resource set
     */
    public SOCResourceSet getResourceSet()
    {
        return resourceSet;
    }

    /**
     * @return the parent
     */
    public SOCTradeTree getParent()
    {
        return parent;
    }

    /**
     * @return the needsToBeExpanded flag
     */
    public boolean needsToBeExpanded()
    {
        return needsToBeExpanded;
    }

    /**
     * @return the list of children
     */
    public Vector<SOCTradeTree> getChildren()
    {
        return children;
    }

    /**
     * set the parent
     *
     * @param p  the parent node
     */
    public void setParent(SOCTradeTree p)
    {
        parent = p;
    }

    /**
     * set the needs to be expanded flag
     *
     * @param value  the value of the flag
     */
    public void setNeedsToBeExpanded(boolean value)
    {
        needsToBeExpanded = value;
    }

    /**
     * add a child to this node
     *
     * @param child  the node to be added
     */
    public void addChild(SOCTradeTree child)
    {
        children.addElement(child);
        child.setParent(this);
    }
}
