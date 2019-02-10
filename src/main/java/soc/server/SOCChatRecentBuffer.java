/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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
package soc.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Buffer of recently said things in a game's chat window.
 * Used for sending a recap to newly joining users.
 * {@link SOCGameListAtServer} links a game by name to its buffer.
 *<P>
 * Not thread-safe.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public final class SOCChatRecentBuffer
{
    /** Maximum number of entries in the buffer, before older entries are dropped. */
    public static final int BUFFER_SIZE = 9;

    /**
     * Buffer entries. Newest entry index is {@link #newest}; subtract to get older entries,
     * and wrap around if needed. If an element is {@code null}, it's unused because the buffer
     * is partially empty and not using the entire array yet.
     */
    private final Entry[] buf;

    /**
     * Index of newest entry in {@link #buf}, or -1 if empty.
     * Because there's no dequeue, don't need to track head and tail.
     */
    private int newest;

    /** Create a new, empty buffer of size {@link #BUFFER_SIZE}. */
    public SOCChatRecentBuffer()
    {
        buf = new Entry[BUFFER_SIZE];
        newest = -1;
    }

    /** Is this buffer empty? */
    public boolean isEmpty()
    {
        return (newest == -1);
    }

    /** Clear out the contents of this buffer. */
    public void clear()
    {
        newest = -1;
        Arrays.fill(buf, null);
    }

    /**
     * Get all entries, from oldest to newest.
     * @return All entries, or an empty List
     */
    public List<Entry> getAll()
    {
        List<Entry> ret = new ArrayList<Entry>();
        if (! isEmpty())
        {
            for (int i = newest + 1; i < buf.length; ++i)
                if (buf[i] != null)
                    ret.add(buf[i]);
            for (int i = 0; i <= newest; ++i)
                if (buf[i] != null)
                    ret.add(buf[i]);
        }

        return ret;
    }

    /**
     * Add a new entry to the buffer.
     * If there would be more than {@link #BUFFER_SIZE} entries, the oldest is dropped.
     *
     * @param nickname Username saying text; not {@code null}
     * @param text Text said by {@code nickname}; not {@code null}
     * @throws IllegalArgumentException if {@code nickname} or {@code text} is {@code null}
     */
    public void add(final String nickname, final String text)
        throws IllegalArgumentException
    {
        final Entry ent = new Entry(nickname, text);  // may throw IllegalArgumentException

        ++newest;
        if (newest >= buf.length)
            newest = 0;

        buf[newest] = ent;  // evicts oldest if needed
    }

    /** One entry in a buffer; see {@link Entry#Entry(String, String) constructor} */
    public static final class Entry
    {
        public final String nickname;
        public final String text;

        /**
         * Create an Entry.
         * @param nickname Username saying text; not {@code null}
         * @param text Text said by {@code nickname}; not {@code null}
         * @throws IllegalArgumentException if {@code nickname} or {@code text} is {@code null}
         */
        public Entry(final String nickname, final String text)
            throws IllegalArgumentException
        {
            if ((nickname == null) || (text == null))
                throw new IllegalArgumentException("null");

            this.nickname = nickname;
            this.text = text;
        }

        /**
         * Two Entries are equal if their corresponding {@link #text} and {@link #nickname} fields
         * are equal according to {@link String#equals(Object)}.
         */
        @Override
        public boolean equals(Object o)
        {
            if (o == null)
                return false;
            if (! (o instanceof Entry))
                return false;
            if (this == o)
                return true;
            return (this.nickname.equals(((Entry) o).nickname)
                && this.text.equals(((Entry) o).text));
        }
    }

}
