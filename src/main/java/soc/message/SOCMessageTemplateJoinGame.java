/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2013-2014,2016-2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;


/**
 * This message means that someone is joining or creating a game.
 *<P>
 * Once the client has successfully joined or created a game or channel, the
 * nickname and password fields can be left blank or "-" in its later join/create requests.
 * All server versions ignore the password field after a successful request.
 *<P>
 * v1.1.07: This template class is copied from {@link SOCJoinGame} to
 * share functionality with the new {@link SOCNewGameWithOptions}. - JDM
 *<P>
 * v2.0.00: No longer implements {@link SOCMessageForGame}, to avoid server looking
 * for a {@code GameHandler} for our new game which doesn't exist yet.
 * Suggest {@link SOCMessage#EMPTYSTR} for unused host parameter.
 *
 * @author Robert S Thomas
 * @since 1.1.07
 */
public abstract class SOCMessageTemplateJoinGame extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Nickname of the joining member when announced from server, or "-" from client if already auth'd to server.
     * Server has always ignored this field from client after auth, can send "-" but not blank.
     */
    protected String nickname;

    /**
     * Optional password, or "" if none
     */
    protected String password;

    /**
     * Name of game
     */
    protected String game;

    /**
     * Unused; server host name to which the client is connected; see {@link #getHost()}
     */
    protected String host;

    /**
     * Create a Join message. Subclasses should set {@link #messageType} after calling.
     *
     * @param nn  player's nickname when announced from server, or "-" from client if already auth'd to server
     * @param pw  optional password, or ""
     * @param hn  unused; optional server host name, or "-" or {@link SOCMessage#EMPTYSTR}
     * @param ga  name of the game
     */
    public SOCMessageTemplateJoinGame(String nn, String pw, String hn, String ga)
    {
        messageType = JOINGAME;
        nickname = nn;
        password = (pw != null) ? pw : "";
        game = ga;
        host = hn;
    }

    /**
     * Nickname of the joining member when announced from server, or "-" from client if already auth'd to server.
     * Server has always ignored this field from client after auth, can send "-" but not blank.
     * @return the nickname, or "-" from client if already auth'd to server
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the optional password, or "" if none
     */
    public String getPassword()
    {
        return password;
    }

    /**
     * Get the optional server host name to which client is connected; unused, ignored and not used by any server version.
     * Since the client is already connected when it sends the message, this is only informational.
     * Is always {@link SOCMessage#EMPTYSTR} when sent by v2.0.00 or newer server or client.
     * @return the unused optional server host name to which client is connected, or "-" or {@link SOCMessage#EMPTYSTR}
     */
    public String getHost()
    {
        return host;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @param classname message name calling this class, appears in returned string
     * @param otherParams null, or other parameters to append in the returned string,
     *           of the form "p1=x|p2=y"
     * @return a human readable form of the message
     */
    public String toString(String classname, String otherParams)
    {
        final String pwmask;
        if ((password == null) || (password.length() == 0) || password.equals(EMPTYSTR))
            pwmask = "|password empty";
        else
            pwmask = "|password=***";

        String s = classname + ":nickname=" + nickname + pwmask + "|host=" + host + "|game=" + game;
        if (otherParams != null)
            s = s + "|" + otherParams;

        return s;
    }

}
