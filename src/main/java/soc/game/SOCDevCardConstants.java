/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2012-2013,2016-2017 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.game;

/**
 * This is a list of constants for representing
 * types of development cards in Settlers of Catan.
 *<P>
 * To instantiate a single card using these types, use {@link SOCDevCard}.
 * To track a player's set of cards and special items, use {@link SOCInventory}.
 *<P>
 * {@link #UNKNOWN} is the lowest card type value, lower than
 * the lowest "known" {@link #MIN_KNOWN}. {@link #MAXPLUSONE} is
 * one past the highest card type value.
 *<P>
 * If you add values here, be sure to update javadocs at
 * SOCGameHandler.DEBUG_COMMANDS_HELP_DEV_TYPES, .debugGiveDevCard and .sendGameStateOVER ,
 * and the code in SOCDevCard.getCardTypeName and handpanel.updateDevCards .
 * If you add VP card types, update {@link SOCDevCard#isVPCard(int)}.
 *<P>
 * In version 2.0.00, the values for {@link #KNIGHT} and {@link #UNKNOWN}
 * were swapped to make room for new card types.  For compatibility with
 * older versions, constants {@link #KNIGHT_FOR_VERS_1_X} and
 * {@link #UNKNOWN_FOR_VERS_1_X} were added.  See {@link #VERSION_FOR_NEW_TYPES}.
 *
 * @see SOCDevCard#getCardTypeName(int, SOCGame, boolean, soc.util.SOCStringManager)
 */
public interface SOCDevCardConstants
{
    /**
     * First version number (2.0.00) that has the new card types and
     * has swapped values for {@link #UNKNOWN} and {@link #KNIGHT}.
     * At server, check against {@link SOCGame#clientVersionLowest}
     * before sending these constants to clients.
     * Send older clients {@link #UNKNOWN_FOR_VERS_1_X} and
     * {@link #KNIGHT_FOR_VERS_1_X} instead.
     */
    public static final int VERSION_FOR_NEW_TYPES = 2000;

    /** Previous value for {@link #UNKNOWN} card type, for version 1.x clients or servers */
    public static final int UNKNOWN_FOR_VERS_1_X = 9;

    /** Previous value for {@link #KNIGHT} knight/robber card type, for version 1.x clients or servers */
    public static final int KNIGHT_FOR_VERS_1_X = 0;

    /**
     * Minimum valid card type constant ({@link #UNKNOWN}).
     * Lower than {@link #MIN_KNOWN}.
     */
    public static final int MIN = 0;

    /**
     * Dev-card of unknown type, for reporting to other players.
     * Lower than {@link #MIN_KNOWN}.
     */
    public static final int UNKNOWN = 0; // unknown card

    /**
     * Minimum known card type ({@link #ROADS}).
     * Higher than {@link #UNKNOWN}.
     * @since 2.0.00
     */
    public static final int MIN_KNOWN = 1;

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

    /** Knight, robber card.
     *<P>
     * In game scenario <tt>SC_PIRI</tt>, used for "Warship" cards to convert normal ships to warships.
     */
    public static final int KNIGHT = 9;

    public static final int MAXPLUSONE = 10;

}
