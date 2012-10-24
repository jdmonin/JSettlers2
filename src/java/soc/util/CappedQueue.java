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
package soc.util;

import java.util.Vector;


/**
 * This queue has a size limit
 */
public class CappedQueue<T>
{
    // Internal storage for the queue'd objects
    private Vector<T> vec = new Vector<T>();

    // The max size for this queue
    private int sizeLimit;

    /**
     * constructor
     */
    public CappedQueue()
    {
        sizeLimit = 2000;
    }

    /**
     * constructor
     *
     * @param s  the size limit
     */
    public CappedQueue(int s)
    {
        sizeLimit = s;
    }

    /**
     * DOCUMENT ME!
     *
     * @param o DOCUMENT ME!
     *
     * @throws CutoffExceededException DOCUMENT ME!
     */
    synchronized public void put(T o) throws CutoffExceededException
    {
        //D.ebugPrintln(">put-> "+o);
        // Add the element
        vec.addElement(o);

        // There might be threads waiting for the new object --
        // give them a chance to get it
        notifyAll();

        if (vec.size() == sizeLimit)
        {
            throw new CutoffExceededException("CappedQueue sizeLimit exceeded");
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    synchronized public T get()
    {
        while (true)
        {
            if (vec.size() > 0)
            {
                // There's an available object!
                T o = vec.elementAt(0);

                //D.ebugPrintln("<-get< "+o);
                // Remove it from our internal list, so someone else
                // doesn't get it.
                vec.removeElementAt(0);

                // Return the object
                return o;
            }
            else
            {
                // There aren't any objects available.  Do a wait(),
                // and when we wake up, check again to see if there
                // are any.
                try
                {
                    wait();
                }
                catch (InterruptedException ie) {}
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    synchronized public boolean empty()
    {
        return vec.isEmpty();
    }
}
