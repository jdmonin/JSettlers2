/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>.
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
 * This is a pair of connections, one is sitting at the
 * game and the other is leaving.
 */
/*package*/ class SOCReplaceRequest
{
    private Connection arriving;
    private Connection leaving;
    private SOCSitDown sdMes;

    /**
     * Make a new request
     * @param arriv  the arriving connection
     * @param leave  the leaving connection
     * @param sm the SITDOWN message
     */
    public SOCReplaceRequest(Connection arriv, Connection leave, SOCSitDown sm)
    {
        arriving = arriv;
        leaving = leave;
        sdMes = sm;
    }

    /**
     * @return the arriving connection
     */
    public Connection getArriving()
    {
        return arriving;
    }

    /**
     * @return the leaving connection
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
