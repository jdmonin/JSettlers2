/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2013-2014,2016 Jeremy D Monin <jeremy@nand.net>
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
 * This message means that someone is joining a game.
 *<P>
 * Once the client has successfully joined or created a game or channel, the
 * password field can be left blank in later join/create requests.  All server
 * versions ignore the password field after a successful request.
 *<P>
 * v1.1.07: This template class is copied from {@link SOCJoinGame} to
 * share functionality with the new {@link SOCNewGameWithOptions}. - JDM
 *<P>
 * v2.0.00: Don't implement SOCMessageForGame, to avoid server looking
 * for a GameHandler for our new game which doesn't exist yet.
 *
 * @author Robert S Thomas
 * @since 1.1.07
 */
public abstract class SOCMessageTemplateJoinGame extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * symbol to represent a null or empty password over the network, to avoid 2 adjacent field-delimiter characters
     */
    protected static final String NULLPASS = "\t";

    /**
     * Nickname of the joining member
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
     * Server host name to which the client is connecting.
     * Since the client is already connected when it sends the message, this is only informational.
     */
    protected String host;

    /**
     * Create a Join message.
     *
     * @param nn  nickname
     * @param pw  optional password, or ""
     * @param hn  server host name
     * @param ga  name of the game
     */
    public SOCMessageTemplateJoinGame(String nn, String pw, String hn, String ga)
    {
        messageType = JOINGAME;
        nickname = nn;
        password = pw;
        game = ga;
        host = hn;
    }

    /**
     * @return the nickname
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
     * @return the server host name to which client is connecting
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
        if ((password == null) || (password.length() == 0) || password.equals("\t"))
            pwmask = "|password empty";
        else
            pwmask = "|password=***";

        String s = classname + ":nickname=" + nickname + pwmask + "|host=" + host + "|game=" + game;
        if (otherParams != null)
            s = s + "|" + otherParams;
        return s;
    }

}
