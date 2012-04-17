/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009,2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Skylar Bolton <iiagrer@gmail.com>
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
package soc.game;


/**
 * This is a list of constants for representing
 * types of development cards in Settlers of Catan.
 * {@link #MIN} is the lowest value (lowest "known"), {@link #MAX_KNOWN} is highest "known",
 * and {@link #UNKNOWN} is higher than MAX_KNOWN.
 *<P>
 * If you add values here, be sure to update javadocs at
 * server.giveDevCard,  .handleGAMETEXTMSG and .sendGameStateOVER ,
 * and handpanel.updateDevCards .
 */
public interface SOCDevCardConstants
{
    /** Minimum valid card type ({@link #KNIGHT}). */
    public static final int MIN = 0;

    /** knight, robber card */
    public static final int KNIGHT = 0;

    /** road building card */
    public static final int ROADS = 1;

    /**  discovery, year-of-plenty card */
    public static final int DISC = 2;

    /** monopoly card */
    public static final int MONO = 3;

    /** capitol, governors-house VP card */
    public static final int CAP = 4;

    /** library, market VP card */
    public static final int LIB = 5;

    /** university VP card */
    public static final int UNIV = 6;

    /** temple VP card */
    public static final int TEMP = 7;

    /** tower, chapel VP card */
    public static final int TOW = 8;

    /**
     * Destroy settlement or downgrade city.
     * For House Rule dev cards; game option <tt>"DH"</tt>.
     * @since 2.0.00
     */
    public static final int DESTROY = 9;

    /**
     * Swap settlement/city with another player.
     * For House Rule dev cards; game option <tt>"DH"</tt>.
     * @since 2.0.00
     */
    public static final int SWAP = 10;

    /** Maximum valid card type ({@link #SWAP}) */
    public static final int MAX_KNOWN = 10; 

    /** Dev-card of unknown type, for reporting to other players */ 
    public static final int UNKNOWN = 11; // unknown card
    // TODO should be 0
    // TODO keep backward-compat: is 9 before 2.0.00 added house rule cards

    public static final int MAXPLUSONE = 12;
}
