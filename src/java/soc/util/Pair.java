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

/**
 * DOCUMENT ME!
 *
 * @author $author$
 * @version $Revision$
 */
public class Pair
{
    private Object a;
    private Object b;

    /**
     * Creates a new Pair object.
     *
     * @param i DOCUMENT ME!
     * @param j DOCUMENT ME!
     */
    public Pair(Object i, Object j)
    {
        a = i;
        b = j;
    }

    /**
     * DOCUMENT ME!
     *
     * @param ip DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public boolean equals(Pair ip)
    {
        if (((ip.a == a) && (ip.b == b)) || ((ip.a == b) && (ip.b == a)))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Object getA()
    {
        return a;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Object getB()
    {
        return b;
    }

    /**
     * DOCUMENT ME!
     *
     * @param val DOCUMENT ME!
     */
    public void setA(Object val)
    {
        a = val;
    }

    /**
     * DOCUMENT ME!
     *
     * @param val DOCUMENT ME!
     */
    public void setB(Object val)
    {
        b = val;
    }
}
