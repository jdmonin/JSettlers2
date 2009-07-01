/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;


/**
 * This message means that someone is joining a game.
 *<P>
 * v1.1.07: This template class is copied from {@link SOCJoinGame} to
 * share functionality with the new {@link SOCNewGameWithOptions}. - JDM
 *
 * @author Robert S Thomas
 * @since 1.1.07
 */
public abstract class SOCMessageTemplateJoinGame extends SOCMessage
{
    /**
     * symbol to represent a null password
     */
    protected static final String NULLPASS = "\t";

    /**
     * Nickname of the joining member
     */
    protected String nickname;

    /**
     * Optional password
     */
    protected String password;

    /**
     * Name of game
     */
    protected String game;

    /**
     * Host name
     */
    protected String host;

    /**
     * Create a Join message.
     *
     * @param nn  nickname
     * @param pw  password
     * @param hn  host name
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
     * @return the password
     */
    public String getPassword()
    {
        return password;
    }

    /**
     * @return the host name
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
     * @return a human readable form of the message
     */
    public String toString(String classname)
    {
        String s = classname + ":nickname=" + nickname + "|password=***|host=" + host + "|game=" + game;
        return s;
    }
}
