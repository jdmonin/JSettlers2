/**
 * Local (StringConnection) network system.  Version 1.0.4.
 * Copyright (C) 2007-2008 Jeremy D Monin <jeremy@nand.net>.
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
 * The author of this program can be reached at jeremy@nand.net
 **/
package soc.server.genericServer;

import java.io.EOFException;
import java.util.Date;

/**
 * StringConnection allows clients and servers to communicate,
 * with no difference between local and actual networked traffic.
 * 
 * @author Jeremy D Monin <jeremy@nand.net>
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release
 *  1.0.1 - 2008-06-28 - add getConnectTime
 *  1.0.2 - 2008-07-30 - no change in this file
 *  1.0.3 - 2008-08-08 - add disconnectSoft, getVersion, setVersion
 *  1.0.4 - 2008-09-04 - add appData
 *</PRE>
 */
public interface StringConnection
{

    /**
     * @return Hostname of the remote end of the connection
     */
    public abstract String host();

    /**
     * Send data over the connection.
     *
     * @param str Data to send
     *
     * @throws IllegalStateException if not yet accepted by server
     */
    public abstract void put(String str)
        throws IllegalStateException;

    /** For server-side thread which reads and treats incoming messages */
    public abstract void run();

    /** Are we currently connected and active? */
    public abstract boolean isConnected();

    /** Start ability to read from the net; called only by the server.
     * (In a network-based subclass, another thread may be started by this method.)
     * 
     * @return true if able to connect, false if an error occurred.
     */    
    public abstract boolean connect(); 

    /** Close the socket, set EOF */
    public abstract void disconnect();

    /**
     * Accept no further input, allow output to drain, don't immediately close the socket. 
     * Once called, {@link #isConnected()} will return false, even if output is still being
     * sent to the other side. 
     */
    public abstract void disconnectSoft();

    /**
     * The optional key data used to name this connection.
     *
     * @return The key data for this connection, or null.
     * @see #getAppData()
     */
    public abstract Object getData();

    /**
     * The optional app-specific changeable data for this connection.
     * Not used anywhere in the generic server, only in your app.
     *
     * @return The app-specific data for this connection.
     * @see #getData()
     */
    public abstract Object getAppData();

    /**
     * Set the optional key data for this connection.
     *
     * This is anything your application wants to associate with the connection.
     * The StringConnection system uses this data to name the connection,
     * so it should not change once set.  After setting, call
     * {@link Server#nameConnection(StringConnection)}.
     *
     * @param data The new key data, or null
     * @see #setAppData(Object)
     */
    public abstract void setData(Object data);

    /**
     * Set the app-specific non-key data for this connection.
     *
     * This is anything your application wants to associate with the connection.
     * The StringConnection system itself does not reference or use this data.
     * You can change it as often as you'd like, or not use it.
     *
     * @param data The new data, or null
     * @see #setData(Object)
     */
    public abstract void setAppData(Object data);

    /**
     * @return Any error encountered, or null
     */
    public abstract Exception getError();

    /**
     * @return Time of connection to server, or of object creation if that time's not available
     *
     * @see #connect()
     */
    public abstract Date getConnectTime();

    /**
     * Give the version number (if known) of the remote end of this connection.
     * The meaning of this number is application-defined.
     * @return Version number, or 0 if unknown.
     */
    public abstract int getVersion();

    /**
     * Set the version number of the remote end of this connection.
     * The meaning of this number is application-defined.
     * @param version Version number, or 0 if unknown.
     */
    public abstract void setVersion(int version);
}
