/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2018 Jeremy D Monin <jeremy@nand.net>
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


/**
 * A thread-safe synchronized FIFO queue.
 */
public class Queue<T>
{
    /** Internal storage for the queued objects */
    private final Vector<T> vec = new Vector<T>();

    /**
     * Add a new element to the end of the queue.
     *<P>
     * If any threads are blocked waiting in {@link #get()}, they are all notified and may unblock
     * (via {@link Object#notifyAll()}). Only one will get the newly added element;
     * the rest will see an empty queue and return to blocking (via {@link Object#wait()}).
     *
     * @param o  Object to add to the end of the queue
     */
    synchronized public void put(T o)
    {
        //D.ebugPrintln(">put-> "+o);
        // Add the element
        vec.addElement(o);

        // There might be threads waiting for the new object --
        // give them a chance to get it
        notifyAll();
    }

    /**
     * Get first (oldest) element in the queue; if empty, block waiting for an element to be added.
     *
     * @return First (oldest) element previously added by {@link #put(Object)}
     */
    synchronized public T get()
    {
        while (true)
        {
            if (! vec.isEmpty())
            {
                // There's an available object!
                // Remove it from our internal list, so someone else
                // doesn't get it.

                return vec.remove(0);
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
     * Is the queue currently empty?
     *
     * @return true if empty
     * @see #size()
     */
    synchronized public boolean empty()
    {
        return vec.isEmpty();
    }

    /**
     * Remove all elements from the queue.
     */
    synchronized public void clear()
    {
        vec.removeAllElements();
    }

    /**
     * Get current size of the queue.
     *
     * @return This queue's current size
     * @see #empty()
     */
    synchronized public int size()
    {
        return vec.size();
    }

}
