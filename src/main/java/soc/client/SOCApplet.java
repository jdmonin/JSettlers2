/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015,2018-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2013 Luis A. Ramirez <lartkma@gmail.com>
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

package soc.client;

import java.applet.Applet;
import java.awt.Color;

import javax.swing.UIManager;

import soc.util.Version;

/**
 * Applet methods to display the main screen (list of games), separated out from main GUI class.
 * @author paulbilnoski
 * @since 2.0.00
 */
public class SOCApplet extends Applet
{
    private static final long serialVersionUID = 2000L;  // for v2.0.00

    SOCPlayerClient client;
    SwingMainDisplay mainDisplay;

    /**
     * Retrieve a parameter and translate to a hex value.
     *
     * @param name a parameter name. null is ignored
     * @return the parameter parsed as a hex value or -1 on error
     */
    public int getHexParameter(String name)
    {
        String value = null;
        int iValue = -1;
        try
        {
            value = getParameter(name);
            if (value != null)
            {
                iValue = Integer.parseInt(value, 16);
            }
        }
        catch (Exception e)
        {
            System.err.println("Invalid " + name + ": " + value);
        }

        return iValue;
    }

    /**
     * Called when the applet should start it's work.
     */
    @Override
    public void start()
    {
        if (! mainDisplay.hasConnectOrPractice)
            mainDisplay.nick.requestFocus();
    }

    /**
     * Initialize the applet.
     * Calls {@link ClientNetwork#connect(String, int) connect}
     * ({@link #getCodeBase()}.{@link java.net.URL#getHost() getHost()},
     * {@link #getParameter(String) getParameter("PORT")}).
     * Default port is {@link ClientNetwork#SOC_PORT_DEFAULT}.
     */
    @Override
    public synchronized void init()
    {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        client = new SOCPlayerClient();
        mainDisplay = new SwingMainDisplay(false, client, 1);
        client.setMainDisplay(mainDisplay);

        Version.printVersionText(System.out, "Java Settlers Client ");  // I18N: Not localizing console output yet

        String param = null;
        int intValue;

        intValue = getHexParameter("background");
        if (intValue != -1)
                setBackground(new Color(intValue));

        intValue = getHexParameter("foreground");
        if (intValue != -1)
            setForeground(new Color(intValue));

        mainDisplay.initVisualElements(); // after the background is set
        add(mainDisplay);

        param = getParameter("suggestion");
        if (param != null)
            mainDisplay.channel.setText(param); // after visuals initialized

        param = getParameter("nickname");  // for use with dynamically-generated html
        if (param != null)
            mainDisplay.nick.setText(param);

        System.out.println("Getting host...");  // I18N: Not localizing console output yet
        String host = getCodeBase().getHost();
        if (host == null || host.equals(""))
            //host = null;  // localhost
            host = "127.0.0.1"; // localhost - don't use "localhost" because Java 6 applets do not work

        int port = ClientNetwork.SOC_PORT_DEFAULT;
        try {
            param = getParameter("PORT");
            if (param != null)
                port = Integer.parseInt(param);
        }
        catch (Exception e) {
            System.err.println("Invalid port: " + param);
        }

        client.getNet().connect(host, port);
    }

    /**
     * applet info, of the form similar to that seen at server startup:
     * SOCPlayerClient (Java Settlers Client) 1.1.07, build JM20091027, 2001-2004 Robb Thomas, portions 2007-2009 Jeremy D Monin.
     * Version and copyright info is from the {@link Version} utility class.
     */
    @Override
    public String getAppletInfo()
    {
        return /*I*/"SOCPlayerClient (Java Settlers Client) " + Version.version() +
        ", build " + Version.buildnum() + ", " + Version.copyright()/*18N*/;
    }

    /**
     * When the applet is destroyed, calls {@link SOCPlayerClient#shutdownFromNetwork()}.
     */
    @Override
    public void destroy()
    {
        client.shutdownFromNetwork();
        client = null;
    }

}  // class SOCApplet
