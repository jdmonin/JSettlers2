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

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;


/**
 * This class is used to record debugging information
 * that can be accessed during run-time through a client.
 * The debugging information is in the form of a vector
 * of strings.  Each debugging vector is stored in a
 * table under a key that is supplied by the user.
 */
public class DebugRecorder
{
    public static final int NOT_RECORDING = 0;
    public static final int RECORDING = 1;
    private Map records;
    private Object currentKey;
    private Vector currentRecord;
    private int state;
    private boolean on;

    /**
     * constructor
     */
    public DebugRecorder()
    {
        state = NOT_RECORDING;
        on = false;
    }

    /**
     * turn the recorder on
     */
    public void turnOn()
    {
        if (records == null)
        {
            records = Collections.synchronizedMap(new Hashtable());
        }

        on = true;
    }

    /**
     * turn the recorder off
     */
    public void turnOff()
    {
        on = false;
        currentRecord = null;
        currentKey = null;
        records.clear();
        state = NOT_RECORDING;
    }

    /**
     * @return true if the recorder is on
     */
    public boolean isOn()
    {
        return on;
    }

    /**
     * Start recording the current plan information
     *
     * @param key  the key to use to index this recording
     */
    public void startRecording(Object key)
    {
        state = RECORDING;
        currentKey = key;
        currentRecord = new Vector();
    }

    /**
     * stop recording and store the vector in the table
     */
    public void stopRecording()
    {
        state = NOT_RECORDING;

        if ((currentKey != null) && (currentRecord != null))
        {
            records.put(currentKey, currentRecord);
        }
    }

    /**
     * suspend recording
     */
    public void suspend()
    {
        state = NOT_RECORDING;
    }

    /**
     * resume recording
     */
    public void resume()
    {
        state = RECORDING;
    }

    /**
     * Add a string to the current record
     *
     * @param s  the string to add
     */
    public void record(String s)
    {
        if (state == RECORDING)
        {
            currentRecord.addElement(s);
        }
    }

    /**
     * Get a record from the table
     *
     * @param key  the key for the record
     * @return the record
     */
    public Vector getRecord(Object key)
    {
        return (Vector) records.get(key);
    }

    /**
     * Store a record in the table
     *
     * @param key  the key for the record
     * @param rec  the record (a vector of strings)
     */
    public void putRecord(Object key, Vector rec)
    {
        if ((key != null) && (rec != null))
        {
            records.put(key, rec);
        }
    }

    /**
     * Clear the record table
     */
    public void eraseAllRecords()
    {
        records.clear();
    }
}
