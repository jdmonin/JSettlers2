/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2017,2020 Jeremy D Monin <jeremy@nand.net>.
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

import soc.message.SOCSitDown;

import soc.server.genericServer.Connection;


/**
 * This is a pair of connections, one is sitting at a game and the other is leaving;
 * the arriving connection might be taking over the leaving one's seat.
 * Server can then have info about one when dealing with messages/events from the other.
 */
/*package*/ class SOCReplaceRequest
{
    private final Connection arriving;
    private final Connection leaving;
    private final SOCSitDown sdMes;

    /**
     * Make a new request
     * @param arriv  the arriving connection; not null
     * @param leave  the leaving connection; not null
     * @param sm the SITDOWN message
     * @throws IllegalArgumentException if {@code arriv} or {@code leave} is {@code null}
     */
    public SOCReplaceRequest(Connection arriv, Connection leave, SOCSitDown sm)
        throws IllegalArgumentException
    {
        if (arriv == null)
            throw new IllegalArgumentException("arriving");
        if (leave == null)
            throw new IllegalArgumentException("leaving");

        arriving = arriv;
        leaving = leave;
        sdMes = sm;
    }

    /**
     * @return the arriving connection; not null
     */
    public Connection getArriving()
    {
        return arriving;
    }

    /**
     * @return the leaving connection; not null
     */
    public Connection getLeaving()
    {
        return leaving;
    }

    /**
     * @return the SITDOWN message
     */
    public SOCSitDown getSitDownMessage()
    {
        return sdMes;
    }

}
