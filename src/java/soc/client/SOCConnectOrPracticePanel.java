/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file copyright (C) 2008-2009,2012-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2013 Paul Bilnoski <paul@bilnoski.net>
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import soc.client.SOCPlayerClient.ClientNetwork;
import soc.client.SOCPlayerClient.GameAwtDisplay;
import soc.util.SOCStringManager;
import soc.util.Version;


/**
 * This is the dialog for standalone client startup (JAR or otherwise)
 * if no command-line arguments.  Give choice of connect to server, start local server,
 * or create practice game.  Prompt for parameters for connect or start-server.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
@SuppressWarnings("serial")
public class SOCConnectOrPracticePanel extends Panel
    implements ActionListener, KeyListener
{
    private final GameAwtDisplay gd;
    private final ClientNetwork clientNetwork;

    /** Welcome message, or error after disconnect */
    private Label topText;

    /** "Practice" */
    private Button prac;

    /** "Connect to server" */
    private Button connserv;
    /** Contains GUI elements for details in {@link #connserv} */
    private Panel panel_conn;
    private TextField conn_servhost, conn_servport, conn_user, conn_pass;
    private Button conn_connect, conn_cancel;

    /** "Start a server" */
    private Button runserv;
    /** Contains GUI elements for details in {@link #runserv}, or null if can't run. */
    private Panel panel_run;
    private TextField run_servport;
    private Button run_startserv, run_cancel;

    /**
     * Do we have security to run a TCP server?
     * Determined by calling {@link #checkCanLaunchServer()}.
     */
    private final boolean canLaunchServer;

    private static final Color HEADER_LABEL_BG = new Color(220,255,220);
    private static final Color HEADER_LABEL_FG = new Color( 50, 80, 50);

    /**
     * i18n text strings; will use same locale as SOCPlayerClient's string manager.
     * @since 2.0.00
     */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /**
     * Creates a new SOCConnectOrPracticePanel.
     *
     * @param gd      Player client display
     */
    public SOCConnectOrPracticePanel(GameAwtDisplay gd)
    {
        super(new BorderLayout());

        this.gd = gd;
        SOCPlayerClient cli = gd.getClient();
        clientNetwork = cli.getNet();
        canLaunchServer = checkCanLaunchServer();

        // same Frame setup as in SOCPlayerClient.main
        setBackground(new Color(Integer.parseInt("61AF71",16)));
        setForeground(Color.black);

        addKeyListener(this);
        initInterfaceElements();
    }

    /**
     * Check with the {@link java.lang.SecurityManager} about being a tcp server.
     * Port {@link SOCPlayerClient.ClientNetwork#SOC_PORT_DEFAULT} and some subsequent ports are checked (to be above 1024).
     * @return True if we have perms to start a server and listen on a port
     */
    public static boolean checkCanLaunchServer()
    {
        try
        {
            SecurityManager sm = System.getSecurityManager();
            if (sm == null)
                return true;
            try
            {
                sm.checkAccept("localhost", ClientNetwork.SOC_PORT_DEFAULT);
                sm.checkListen(ClientNetwork.SOC_PORT_DEFAULT);
            }
            catch (SecurityException se)
            {
                return false;
            }
        }
        catch (SecurityException se)
        {
            // can't read security mgr; check it the hard way
            int port = ClientNetwork.SOC_PORT_DEFAULT;
            for (int i = 0; i <= 100; ++i)
            {
                ServerSocket ss = null;
                try
                {
                    ss = new ServerSocket(i + port);
                    ss.setReuseAddress(true);
                    ss.setSoTimeout(11);  // very short (11 ms)
                    ss.accept();  // will time out soon
                    ss.close();
                }
                catch (SocketTimeoutException ste)
                {
                    // Allowed to bind
                    try
                    {
                        ss.close();
                    }
                    catch (IOException ie) {}
                    return true;
                }
                catch (IOException ie)
                {
                    // maybe already bound: ok, try next port in loop
                }
                catch (SecurityException se2)
                {
                    return false;  // Not allowed to have a server socket
                }
            }
        }
        return false;
    }

    /**
     * Interface setup for constructor.
     * Most elements are part of a sub-panel occupying most of this Panel, and using FlowLayout.
     * The exception is a Label at bottom with the version and build number.
     */
    private void initInterfaceElements()
    {
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        Panel bp = new Panel(gbl);  // Actual button panel

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        topText = new Label(strings.get("pcli.cpp.welcomeheading"));  // "Welcome to JSettlers!  Please choose an option."
        topText.setAlignment(Label.CENTER);
        gbl.setConstraints(topText, gbc);
        bp.add(topText);

        /**
         * Interface setup: Connect to a Server
         */

        connserv = new Button(strings.get("pcli.cpp.connecttoaserv"));  // "Connect to a Server"
        gbl.setConstraints(connserv, gbc);
        bp.add(connserv);
        connserv.addActionListener(this);

        /**
         * Interface setup: Practice
         */
        prac = new Button(strings.get("pcli.main.practice"));  // "Practice" - same as SOCPlayerClient button
        gbl.setConstraints(prac, gbc);
        bp.add(prac);
        prac.addActionListener(this);

        /**
         * Interface setup: Start a Server
         */
        runserv = new Button(strings.get("pcli.cpp.startserv"));  // "Start a Server"
        gbl.setConstraints(runserv, gbc);
        if (! canLaunchServer)
            runserv.setEnabled(false);
        bp.add(runserv);

        /**
         * Interface setup: sub-panels (not initially visible)
         */
        panel_conn = initInterface_conn();  // panel_conn setup
        panel_conn.setVisible(false);
        gbl.setConstraints(panel_conn, gbc);
        bp.add (panel_conn);

        if (canLaunchServer)
        {
            runserv.addActionListener(this);
            panel_run = initInterface_run();  // panel_run setup
            panel_run.setVisible(false);
            gbl.setConstraints(panel_run, gbc);
            bp.add (panel_run);
        } else {
            panel_run = null;
        }

        // Final assembly setup
        add(bp, BorderLayout.CENTER);
        Label verl = new Label(strings.get("pcli.cpp.jsettlers.versionbuild", Version.version(), Version.buildnum()));
            // "JSettlers " + Version.version() + " build " + Version.buildnum()
        verl.setAlignment(Label.CENTER);
        verl.setForeground(new Color(252, 251, 243)); // off-white
        add(verl, BorderLayout.SOUTH);
    }

    /** panel_conn setup */
    private Panel initInterface_conn()
    {
        Panel pconn = new Panel();
        Label L;

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        pconn.setLayout(gbl);
        gbc.fill = GridBagConstraints.BOTH;

        // heading row
        L = new Label(strings.get("pcli.cpp.connecttoserv"));  // "Connect to Server"
        L.setAlignment(Label.CENTER);
        L.setBackground(HEADER_LABEL_BG);
        L.setForeground(HEADER_LABEL_FG);
        gbc.gridwidth = 4;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        L = new Label(" ");  // Spacing for rest of form's rows
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        pconn.add(L);

        // blank row
        L = new Label();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        pconn.add(L);

        L = new Label(strings.get("pcli.cpp.server"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_servhost = new TextField(20);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_servhost, gbc);
        conn_servhost.addKeyListener(this);   // for ESC/ENTER
        pconn.add(conn_servhost);

        L = new Label(strings.get("pcli.cpp.port"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_servport = new TextField(20);
        {
            String svp = Integer.toString(clientNetwork.getPort());
            conn_servport.setText(svp);
            conn_servport.setSelectionStart(0);
            conn_servport.setSelectionEnd(svp.length());
        }
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_servport, gbc);
        conn_servport.addKeyListener(this);   // for ESC/ENTER
        pconn.add(conn_servport);

        L = new Label(strings.get("pcli.cpp.nickname"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_user = new TextField(20);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_user, gbc);
        conn_user.addKeyListener(this);
        pconn.add(conn_user);

        L = new Label(strings.get("pcli.cpp.password"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_pass = new TextField(20);
        if (SOCPlayerClient.isJavaOnOSX)
            conn_pass.setEchoChar('\u2022');  // round bullet (option-8)
        else
            conn_pass.setEchoChar('*');
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_pass, gbc);
        conn_pass.addKeyListener(this);
        pconn.add(conn_pass);

        L = new Label(" ");
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_connect = new Button(strings.get("pcli.cpp.connect"));
        conn_connect.addActionListener(this);
        conn_connect.addKeyListener(this);  // for win32 keyboard-focus
        gbl.setConstraints(conn_connect, gbc);
        pconn.add(conn_connect);

        conn_cancel = new Button(strings.get("base.cancel"));
        conn_cancel.addActionListener(this);
        conn_cancel.addKeyListener(this);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_cancel, gbc);
        pconn.add(conn_cancel);

        return pconn;
    }

    /** panel_run setup */
    private Panel initInterface_run()
    {
        Panel prun = new Panel();
        Label L;

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        prun.setLayout(gbl);
        gbc.fill = GridBagConstraints.BOTH;

        // heading row
        L = new Label(strings.get("pcli.cpp.startserv"));  // "Start a Server"
        L.setAlignment(Label.CENTER);
        L.setBackground(HEADER_LABEL_BG);
        L.setForeground(HEADER_LABEL_FG);
        gbc.gridwidth = 4;
        gbl.setConstraints(L, gbc);
        prun.add(L);
        L = new Label(" ");  // Spacing for rest of form's rows
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        prun.add(L);

        // blank row
        L = new Label();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        prun.add(L);

        // Port#
        L = new Label(strings.get("pcli.cpp.port"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        prun.add(L);
        run_servport = new TextField(15);
        {
            String svp = Integer.toString(clientNetwork.getPort());
            run_servport.setText(svp);
            run_servport.setSelectionStart(0);
            run_servport.setSelectionEnd(svp.length());
        }
        gbc.gridwidth = 2;
        gbl.setConstraints(run_servport, gbc);
        run_servport.addKeyListener(this);  // for ESC/ENTER
        prun.add(run_servport);
        L = new Label(" ");  // Spacing for rest of form's rows
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        prun.add(L);

        L = new Label(" ");
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        prun.add(L);
        run_startserv = new Button(" " + strings.get("pcli.cpp.start") + " ");
        run_startserv.addActionListener(this);
        run_startserv.addKeyListener(this);  // for win32 keyboard-focus
        gbl.setConstraints(run_startserv, gbc);
        prun.add(run_startserv);

        run_cancel = new Button(strings.get("base.cancel"));
        run_cancel.addActionListener(this);
        run_cancel.addKeyListener(this);
        gbl.setConstraints(run_cancel, gbc);
        prun.add(run_cancel);

        return prun;
    }

    /**
     * A local server has been started; disable other options ("Connect", etc) but
     * not Practice.  Called from client, once the server is started in
     * {@link SOCPlayerClient.GameAwtDisplay#startLocalTCPServer(int)}.
     */
    public void startedLocalServer()
    {
        connserv.setEnabled(false);
        conn_connect.setEnabled(false);
        run_startserv.setEnabled(false);
        run_cancel.setEnabled(false);
    }

    /**
     * Set the line of text displayed at the top of the panel.
     * @param newText  New text to display
     * @since 1.1.16
     */
    public void setTopText(final String newText)
    {
        topText.setText(newText);
        validate();
    }

    /**
     * Parse a server TCP port number from a text field.
     * If the field is empty after trimming whitespace, use this client's default from
     * {@link SOCPlayerClient.ClientNetwork#getPort() clientNetwork.getPort()},
     * which is usually {@link SOCPlayerClient.ClientNetwork#SOC_PORT_DEFAULT}.
     * @param tf  Text field with the port number, such as {@link #conn_servport} or {@link #run_servport}
     * @return the port number, or {@code clientNetwork.getPort()} if empty,
     *         or 0 if cannot be parsed or if outside the valid range 1-65535
     * @since 1.1.19
     */
    private final int parsePortNumberOrDefault(final TextField tf)
    {
        int srport;
        try {
            final String ptext = tf.getText().trim();
            if (ptext.length() > 0)
                srport = Integer.parseInt(ptext);
            else
                srport = clientNetwork.getPort();  // text field is empty, use default (usually == SOC_PORT_DEFAULT)

            if ((srport <= 0) || (srport > 65535))
                srport = 0;  // TODO show error
        }
        catch (NumberFormatException e)
        {
            // TODO show error?
            srport = 0;
        }

        return srport;
    }

    /** React to button clicks */
    public void actionPerformed(ActionEvent ae)
    {
        try {

        Object src = ae.getSource();
        if (src == prac)
        {
            // Ask client to set up and start a practice game
            gd.clickPracticeButton();
            return;
        }

        if (src == connserv)
        {
            // Show fields to get details to connect to server later
            panel_conn.setVisible(true);
            if ((panel_run != null) && panel_run.isVisible())
            {
                panel_run.setVisible(false);
                runserv.setVisible(true);
            }
            connserv.setVisible(false);
            conn_servhost.requestFocus();
            validate();
            return;
        }

        if (src == conn_connect)
        {
            // After clicking connserv, actually connect to server
            clickConnConnect();
            return;
        }

        if (src == conn_cancel)
        {
            // Hide fields used to connect to server
            clickConnCancel();
            return;
        }

        if (src == runserv)
        {
            // Show fields to get details to start a TCP server
            panel_run.setVisible(true);
            if ((panel_conn != null) && panel_conn.isVisible())
            {
                panel_conn.setVisible(false);
                connserv.setVisible(true);
            }
            runserv.setVisible(false);
            run_servport.requestFocus();
            {
                // Convenience: type-to-replace port value
                String svpText = run_servport.getText();
                if ((svpText != null) && (svpText.trim().length() > 0))
                {
                    run_servport.setSelectionStart(0);
                    run_servport.setSelectionEnd(svpText.length());
                }
            }
            validate();
            return;
        }

        if (src == run_startserv)
        {
            // After clicking runserv, actually start a server
            clickRunStartserv();
            return;
        }

        if (src == run_cancel)
        {
            // Hide fields used to start a server
            clickRunCancel();
            return;
        }

        }  // try
        catch(Throwable thr)
        {
            System.err.println("-- Error caught in AWT event thread: " + thr + " --");
            thr.printStackTrace();
            while (thr.getCause() != null)
            {
                thr = thr.getCause();
                System.err.println(" --> Cause: " + thr + " --");
                thr.printStackTrace();
            }
            System.err.println("-- Error stack trace end --");
            System.err.println();
        }

    }

    /** "Connect..." from connect setup; check fields, set WAIT_CURSOR, ask cli to connect  */
    private void clickConnConnect()
    {
        // TODO Check contents of fields
        String cserv = conn_servhost.getText().trim();
        if (cserv.length() == 0)
            cserv = null;  // localhost
        final int cport = parsePortNumberOrDefault(conn_servport);
        if (cport == 0)
        {
            return;  // <--- Early return: Couldn't parse port number ---
        }

        // Copy fields, show MAIN_PANEL, and connect in client
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        gd.getClient().connect(cserv, cport, conn_user.getText(), conn_pass.getText());
    }

    /** Hide fields used to connect to server. Called by client after a network error. */
    public void clickConnCancel()
    {
        panel_conn.setVisible(false);
        connserv.setVisible(true);
        validate();
    }

    /** Actually start a server, on port from {@link #run_servport} */
    private void clickRunStartserv()
    {
        // After clicking runserv, actually start a server
        final int srport = parsePortNumberOrDefault(run_servport);
        if (srport > 0)
            gd.startLocalTCPServer(srport);
    }

    /** Hide fields used to start a server */
    private void clickRunCancel()
    {
        panel_run.setVisible(false);
        runserv.setVisible(true);
        validate();
    }

    /** Handle Enter or Esc key (KeyListener) */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        try {

        boolean panelConnShowing = (panel_conn != null) && (panel_conn.isVisible());
        boolean panelRunShowing  = (panel_run != null)  && (panel_run.isVisible());

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            if (panelConnShowing)
                clickConnConnect();
            else if (panelRunShowing)
                clickRunStartserv();
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            if (panelConnShowing)
                clickConnCancel();
            else if (panelRunShowing)
                clickRunCancel();
            break;
        }  // switch(e)

        }  // try
        catch(Throwable thr)
        {
            System.err.println("-- Error caught in AWT event thread: " + thr + " --");
            thr.printStackTrace();
            while (thr.getCause() != null)
            {
                thr = thr.getCause();
                System.err.println(" --> Cause: " + thr + " --");
                thr.printStackTrace();
            }
            System.err.println("-- Error stack trace end --");
            System.err.println();
        }
    }

    /** Stub required by KeyListener */
    public void keyReleased(KeyEvent arg0) { }

    /** Stub required by KeyListener */
    public void keyTyped(KeyEvent arg0) { }

}
