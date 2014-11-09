/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2009-2011,2013-2014 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.message.SOCChannels;
import soc.message.SOCCreateAccount;
import soc.message.SOCMessage;
import soc.message.SOCRejectConnection;
import soc.message.SOCStatusMessage;
import soc.message.SOCVersion;

import soc.util.Version;

import java.applet.Applet;
import java.applet.AppletContext;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.Socket;


/**
 * Applet/Standalone client for connecting to the SOCServer and
 * making user accounts.
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, you have to
 * specify the server's hostname and port on the command line.
 *
 * @author Robert S Thomas
 */
public class SOCAccountClient extends Applet implements Runnable, ActionListener
{
    private static final String MAIN_PANEL = "main";
    private static final String MESSAGE_PANEL = "message";

    protected TextField nick;
    protected TextField pass;
    protected TextField pass2;
    protected TextField email;
    protected TextField status;
    protected Button submit;
    protected Label messageLabel;
    protected AppletContext ac;
    protected boolean submitLock;

    protected CardLayout cardLayout;
    
    protected String host;
    protected int port;
    protected Socket s;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected Thread reader = null;
    protected Exception ex = null;
    protected boolean connected = false;

    /**
     * the nickname
     */
    protected String nickname = null;

    /**
     * the password
     */
    protected String password = null;

    /**
     * the second password
     */
    protected String password2 = null;

    /**
     * the email address
     */
    protected String emailAddress = null;

    /**
     * Create a SOCAccountClient connecting to localhost port 8880
     */
    public SOCAccountClient()
    {
        this(null, 8880);
    }

    /**
     * Constructor for connecting to the specified host, on the specified port.
     * Must call 'init' to start up and do layout.
     *
     * @param h  host
     * @param p  port
     */
    public SOCAccountClient(String h, int p)
    {
        host = h;
        port = p;
    }

    /**
     * init the visual elements
     */
    protected void initVisualElements()
    {
        char pwchar = SOCPlayerClient.isJavaOnOSX ? '\u2022' : '*';

        setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        nick = new TextField(20);
        pass = new TextField(10);
        pass.setEchoChar(pwchar);
        pass2 = new TextField(10);
        pass2.setEchoChar(pwchar);
        email = new TextField(50);
        status = new TextField(50);
        status.setEditable(false);
        submit = new Button("Create Account");
        submitLock = false;

        submit.addActionListener(this);

        ac = null;

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        Panel mainPane = new Panel(gbl);

        c.fill = GridBagConstraints.BOTH;

        Label l;

        l = new Label("To create an account, please enter your information.");
        l.setAlignment(Label.CENTER);
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label("Your Nickname:");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);
        new AWTToolTip("This will be your username.", l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(nick, c);
        mainPane.add(nick);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label("Password:");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(pass, c);
        mainPane.add(pass);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label("Password (again):");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(pass2, c);
        mainPane.add(pass2);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label("Email (optional):");
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(email, c);
        mainPane.add(email);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new Label();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(submit, c);
        mainPane.add(submit);

        l = new Label();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(status, c);
        mainPane.add(status);

        // message label that takes up the whole pane
        messageLabel = new Label("", Label.CENTER);

        Panel messagePane = new Panel(new BorderLayout());
        messagePane.add(messageLabel, BorderLayout.CENTER);
        
        // all together now...
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        add(messagePane, MESSAGE_PANEL); // shown first
        add(mainPane, MAIN_PANEL);        
    }

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
     * Initialize the applet
     */
    public synchronized void init()
    {
        Version.printVersionText(System.out, "Java Settlers Account Client ");

        String param = null;
        int intValue;
            
        intValue = getHexParameter("background"); 
        if (intValue != -1)
            setBackground(new Color(intValue));

        intValue = getHexParameter("foreground");
        if (intValue != -1)
            setForeground(new Color(intValue));

        initVisualElements(); // after the background is set

        System.out.println("Getting host...");
        host = getCodeBase().getHost();
        if (host.equals(""))
            host = null;  // localhost

        try {
            param = getParameter("PORT");
            if (param != null)
                port = Integer.parseInt(param);
        }
        catch (Exception e) {
            System.err.println("Invalid port: " + param);
        }

        connect();
    }

    /**
     * Attempts to connect to the server. See {@link #connected} for success or
     * failure.
     * @throws IllegalStateException if already connected 
     */
    public synchronized void connect()
    {
        String hostString = (host != null ? host : "localhost") + ":" + port;
        if (connected)
        {
            throw new IllegalStateException("Already connected to " +
                                            hostString);
        }
        System.out.println("Connecting to " + hostString);
        messageLabel.setText("Connecting to server...");

        try
        {
            s = new Socket(host, port);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            connected = true;
            (reader = new Thread(this)).start();
            // send VERSION right away (1.1.07 and later)
            put(SOCVersion.toCmd(Version.versionNumber(), Version.version(), Version.buildnum()));
        }
        catch (Exception e)
        {
            ex = e;
            String msg = "Could not connect to the server: " + ex;
            System.err.println(msg);
            messageLabel.setText(msg);
        }
    }

    /**
     * Handle mouse clicks and keyboard
     */
    public void actionPerformed(ActionEvent e)
    {
        Object target = e.getSource();

        if (target == submit)
        {
            String n = nick.getText().trim();

            if (n.length() > 20)
            {
                nickname = n.substring(0, 20);
            }
            else
            {
                nickname = n;
            }
            if (! SOCMessage.isSingleLineAndSafe(nickname))
            {
                status.setText(SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED);
                nick.requestFocusInWindow();
                return;  // Not a valid username
            }

            String p1 = pass.getText().trim();

            if (p1.length() > 20)
            {
                password = p1.substring(0, 20);
            }
            else
            {
                password = p1;
            }

            String p2 = pass2.getText().trim();

            if (p2.length() > 20)
            {
                password2 = p2.substring(0, 20);
            }
            else
            {
                password2 = p2;
            }

            emailAddress = email.getText().trim();

            //
            // make sure all the info is ok
            //
            if (nickname.length() == 0)
            {
                status.setText("You must enter a nickname.");
                nick.requestFocus();
            }
            else if (password.length() == 0)
            {
                status.setText("You must enter a password.");
                pass.requestFocus();
            }
            else if (!password.equals(password2))
            {
                pass.requestFocus();
                status.setText("Your passwords don't match.");
            }
            else if (!submitLock)
            {
                submitLock = true;
                status.setText("Creating account ...");
                put(SOCCreateAccount.toCmd(nickname, password, host, emailAddress));
            }
        }
    }

    /**
     * continuously read from the net in a separate thread
     */
    public void run()
    {
        try
        {
            while (connected)
            {
                String s = in.readUTF();
                treat((SOCMessage) SOCMessage.toMsg(s));
            }
        }
        catch (IOException e)
        {
            // purposefully closing the socket brings us here too
            if (connected)
            {
                ex = e;
                System.out.println("could not read from the net: " + ex);
                destroy();
            }
        }
    }

    /**
     * write a message to the net
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     */
    public synchronized boolean put(String s)
    {
        D.ebugPrintln("OUT - " + s);

        if ((ex != null) || !connected)
        {
            return false;
        }

        try
        {
            out.writeUTF(s);
        }
        catch (IOException e)
        {
            ex = e;
            System.err.println("could not write to the net: " + ex);
            destroy();

            return false;
        }

        return true;
    }

    /**
     * Treat the incoming messages
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Msg parsing error

        D.ebugPrintln(mes.toString());

        try
        {
            switch (mes.getType())
            {
            /**
             * list of channels on the server (first message from server)
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes);

                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes);

                break;

            /**
             * handle the reject connection message
             */
            case SOCMessage.REJECTCONNECTION:
                handleREJECTCONNECTION((SOCRejectConnection) mes);

                break;
            }
        }
        catch (Exception e)
        {
            System.out.println("SOCAccountClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * handle the "list of channels" message
     * @param mes  the message
     */
    protected void handleCHANNELS(SOCChannels mes)
    {
        //
        // this message indicates that we're connected to the server
        //
        cardLayout.show(this, MAIN_PANEL);
        validate();
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        disconnect();

        messageLabel.setText(mes.getText());
        cardLayout.show(this, MESSAGE_PANEL);
        validate();
    }

    /**
     * handle the "status message" message
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        status.setText(mes.getStatus());
        submitLock = false;
    }

    /**
     * disconnect from the net
     */
    protected synchronized void disconnect()
    {
        connected = false;

        // reader will die once 'connected' is false, and socket is closed

        try
        {
            s.close();
        }
        catch (Exception e)
        {
            ex = e;
        }
    }

    /**
     * applet info
     */
    public String getAppletInfo()
    {
        return "SOCAccountClient 0.1 by Robert S. Thomas.";
    }

    /** destroy the applet */
    public void destroy()
    {
        String err = "Sorry, the applet has been destroyed. " + ((ex == null) ? "Load the page again." : ex.toString());

        disconnect();
        
        messageLabel.setText(err);
        cardLayout.show(this, MESSAGE_PANEL);
        validate();
    }

    /**
     * for stand-alones
     */
    public static void usage()
    {
        System.err.println("usage: java soc.client.SOCAccountClient <host> <port>");
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        SOCAccountClient client = new SOCAccountClient();
        
        if (args.length != 2)
        {
            usage();
            System.exit(1);
        }

        try {
            client.host = args[0];
            client.port = Integer.parseInt(args[1]);
        } catch (NumberFormatException x) {
            usage();
            System.err.println("Invalid port: " + args[1]);
            System.exit(1);
        }

        Frame frame = new Frame("SOCAccountClient");
        frame.setBackground(new Color(Integer.parseInt("61AF71",16)));
        frame.setForeground(Color.black);
        // Add a listener for the close event
        frame.addWindowListener(client.createWindowAdapter());

        client.initVisualElements(); // after the background is set
        
        frame.add(client, BorderLayout.CENTER);
        frame.setSize(600, 350);
        frame.setVisible(true);

        client.connect();
    }

    private WindowAdapter createWindowAdapter()
    {
        return new MyWindowAdapter();
    }
    
    private class MyWindowAdapter extends WindowAdapter
    {
        public void windowClosing(WindowEvent evt)
        {
            System.exit(0);
        }

        public void windowOpened(WindowEvent evt)
        {
            nick.requestFocus();
        }
    }
}
