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
package soc.message;

import java.util.StringTokenizer;


/**
 * This message is a request to create an account
 *
 * @author Robert S Thomas
 */
public class SOCCreateAccount extends SOCMessage
{
    /**
     * symbol to represent a null email
     */
    private static String NULLEMAIL = "\t";

    /**
     * Nickname
     */
    private String nickname;

    /**
     * Password
     */
    private String password;

    /**
     * Host name
     */
    private String host;

    /**
     * Email address
     */
    private String email;

    /**
     * Create a CreateAccount message.
     *
     * @param nn  nickname
     * @param pw  password
     * @param hn  host name
     * @param em  email
     */
    public SOCCreateAccount(String nn, String pw, String hn, String em)
    {
        messageType = CREATEACCOUNT;
        nickname = nn;
        password = pw;
        email = em;
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
     * @return the email address
     */
    public String getEmail()
    {
        return email;
    }

    /**
     * CREATEACCOUNT sep nickname sep2 password sep2 host sep2 email
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, host, email);
    }

    /**
     * CREATEACCOUNT sep nickname sep2 password sep2 host sep2 email
     *
     * @param nn  the nickname
     * @param pw  the password
     * @param hn  the host name
     * @param em  the email
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String em)
    {
        String tempem;

        if (em == null)
        {
            tempem = NULLEMAIL;
        }
        else
        {
            tempem = new String(em);

            if (tempem.equals(""))
            {
                tempem = NULLEMAIL;
            }
        }

        return CREATEACCOUNT + sep + nn + sep2 + pw + sep2 + hn + sep2 + tempem;
    }

    /**
     * Parse the command String into a CreateAccount message
     *
     * @param s   the String to parse
     * @return    a CreateAccount message, or null of the data is garbled
     */
    public static SOCCreateAccount parseDataStr(String s)
    {
        String nn;
        String pw;
        String hn;
        String em;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            pw = st.nextToken();
            hn = st.nextToken();
            em = st.nextToken();

            if (em.equals(NULLEMAIL))
            {
                em = "";
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCCreateAccount(nn, pw, hn, em);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCCreateAccount:nickname=" + nickname + "|password=" + password + "|host=" + host + "|email=" + email;

        return s;
    }
}
