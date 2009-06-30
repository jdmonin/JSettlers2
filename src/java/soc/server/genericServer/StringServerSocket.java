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
import java.io.IOException;
import java.net.SocketException;

/**
 * StringServerSocket allows server applications to communicate with clients,
 * with no difference between local and actual networked traffic.
 * 
 * @author Jeremy D Monin <jeremy@nand.net>
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release
 *  1.0.3 - 2008-08-08 - add change history; no other changes in this file since 1.0.0
 *  1.0.4 - 2008-09-04 - no change in this file
 *</PRE>
 */
public interface StringServerSocket
{

    /**
     * For server to call.  Blocks waiting for next inbound connection.
     * 
     * @return The server-side peer to the inbound client connection
     * @throws IOException  if network has a problem accepting
     * @throws SocketException if our setEOF() has been called, thus
     *    new clients won't receive any data from us
     */
    public abstract StringConnection accept() throws SocketException, IOException;

    /**
     * Close down server socket immediately:
     * Do not let inbound data drain.
     * Accept no new inbound connections.
     * Send EOF marker in all current outbound connections.
     * Like java.net.ServerSocket, any thread currently blocked in
     * accept() must throw a SocketException.
     */
    public abstract void close() throws IOException;

}
