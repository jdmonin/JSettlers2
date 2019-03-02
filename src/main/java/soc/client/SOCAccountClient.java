/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2009-2011,2013-2019 Jeremy D Monin <jeremy@nand.net>
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

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.message.SOCAuthRequest;
import soc.message.SOCChannels;
import soc.message.SOCCreateAccount;
import soc.message.SOCMessage;
import soc.message.SOCRejectConnection;
import soc.message.SOCStatusMessage;
import soc.message.SOCVersion;

import soc.util.I18n;
import soc.util.SOCFeatureSet;
import soc.util.Version;

import java.applet.Applet;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.Socket;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;


/**
 * Applet/Standalone client for connecting to the SOCServer and
 * making user accounts.
 *<P>
 * This account-management client will connect to server version v1.1.19 and higher
 * ({@link #SRV_VERSION_MIN}). The required minimum version simplifies logic and
 * assumptions about available {@link SOCFeatureSet}. If needed to manage older servers,
 * older client JARs can be downloaded or built from git release tags.
 *<P>
 * To connect the applet to a non-default server port, you must specify it as the
 * {@code port} parameter in the html applet tag. If you run {@code SOCAccountClient}
 * as a stand-alone app, specify the server's hostname and port on the command line.
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCAccountClient extends Applet
    implements Runnable, ActionListener, KeyListener
{
    /**
     * Minimum server version (v1.1.19) to which this account-management client will connect;
     * see class javadoc.
     *<P>
     * Same format as {@link soc.util.Version#versionNumber()}.
     * Same value as {@link SOCFeatureSet#VERSION_FOR_SERVERFEATURES}.
     * @since 1.1.20
     */
    public static final int SRV_VERSION_MIN = SOCFeatureSet.VERSION_FOR_SERVERFEATURES;  // v1.1.19 (1119)

    /**
     * CardLayout string for the main panel while connected to a server:
     * Has fields to enter {@link #nick}, {@link #pass}, etc,
     * {@link #status} display and {@link #submit} button.
     */
    private static final String MAIN_PANEL = "main";

    /**
     * CardLayout string for the message panel when not connected to a server;
     * message text is shown in {@link #messageLabel}.
     */
    private static final String MESSAGE_PANEL = "message";

    /** CardLayout string for {@link #connPanel}. */
    private static final String CONN_PANEL = "conn";

    // Most of these dialog fields are set up in initVisualElements().

    /**
     * Account info dialog prompt/label.
     * @since 1.1.20
     */
    private JLabel promptLabel;

    /**
     * Nickname field label.
     * @since 1.1.20
     */
    private JLabel nickLabel;

    /** Name of new user to be created. */
    private JTextField nick;
    private JPasswordField pass;
    private JPasswordField pass2;
    private JTextField email;

    private JTextField status;
    private JButton submit;
    private JLabel messageLabel;

    private boolean submitLock;

    /**
     * Connect and Authenticate panel ({@link #CONN_PANEL}), for when
     * server needs authentication to create a user account.
     * Created in {@link #initInterface_conn()}.
     * @since 1.1.19
     */
    private JPanel connPanel;

    /**
     * Username, password, and status fields on {@link #connPanel}.
     * @since 1.1.19
     */
    private JTextField conn_user, conn_pass, conn_status;

    /**
     * Connect and Cancel buttons on {@link #connPanel}.
     * @since 1.1.19
     */
    private JButton conn_connect, conn_cancel;

    /**
     * If true, a username/password {@link SOCAuthRequest} has been sent to the server from {@link #connPanel}.
     * Used by {@link #handleSTATUSMESSAGE(SOCStatusMessage)} to show error results using {@code connPanel}
     * instead of the main panel.
     * @since 1.1.19
     */
    private boolean conn_sentAuth;

    private CardLayout cardLayout;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    private final int displayScale;

    protected String host;
    protected int port;
    protected Socket s;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected Thread reader = null;
    protected Exception ex = null;
    protected boolean connected = false;

    /**
     * Server version number for remote server, sent soon after connect.
     * @since 1.1.19
     */
    protected int sVersion;

    /**
     * Server's active optional features, sent soon after connect, or null if unknown.
     * @since 1.1.19
     */
    protected SOCFeatureSet sFeatures;

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
     * Locale for i18n message lookups used for {@link #strings}.
     * Also sent to server when connecting.
     * Override if needed in the constructor by reading the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE}
     * system property {@code "jsettlers.locale"}.
     * @since 2.0.00
     */
    final Locale cliLocale;

    /**
     * True if contents of incoming and outgoing network message traffic should be debug-printed.
     * Set if optional system property {@link SOCDisplaylessPlayerClient#PROP_JSETTLERS_DEBUG_TRAFFIC} is set.
     * @since 1.2.00
     */
    private boolean debugTraffic;

    /**
     * i18n text strings. Set in constructor based on {@link #cliLocale}.
     * @since 2.0.00
     */
    private final soc.util.SOCStringManager strings;

    /**
     * Create a SOCAccountClient connecting to localhost port 8880
     * @param displayScaleFactor  Display scaling factor to use (1 if not high-DPI); caller should call
     *     {@link SwingMainDisplay#checkDisplayScaleFactor(Component)} with the Frame to which this display will be added
     * @throws IllegalArgumentException if {@code displayScaleFactor} &lt; 1
     */
    public SOCAccountClient(final int displayScaleFactor)
    {
        this(null, 8880, displayScaleFactor);
    }

    /**
     * Constructor for connecting to the specified host, on the specified port.
     * Must call 'init' to start up and do layout.
     *<P>
     * The {@code SOCAccountClient} GUI's own localized strings use the
     * current user's default locale, unless overridden by setting the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE} system property {@code "jsettlers.locale"}.
     *
     * @param h  host
     * @param p  port
     * @param displayScaleFactor  Display scaling factor to use (1 if not high-DPI); caller should call
     *     {@link SwingMainDisplay#checkDisplayScaleFactor(Component)} with the Frame to which this display will be added
     * @throws IllegalArgumentException if {@code displayScaleFactor} &lt; 1
     */
    public SOCAccountClient(String h, int p, final int displayScaleFactor)
        throws IllegalArgumentException
    {
        if (displayScaleFactor < 1)
            throw new IllegalArgumentException("displayScaleFactor");

        host = h;
        port = p;
        displayScale = displayScaleFactor;

        String jsLocale = System.getProperty(I18n.PROP_JSETTLERS_LOCALE);
        Locale lo = null;
        if (jsLocale != null)
        {
            try
            {
                lo = I18n.parseLocale(jsLocale.trim());
            } catch (IllegalArgumentException e) {
                System.err.println("Could not parse locale " + jsLocale);
            }
        }
        if (lo != null)
            cliLocale = lo;
        else
            cliLocale = Locale.getDefault();

        strings = soc.util.SOCStringManager.getClientManager(cliLocale);

        if (null != System.getProperty(SOCDisplaylessPlayerClient.PROP_JSETTLERS_DEBUG_TRAFFIC))
            debugTraffic = true;  // set flag if debug prop has any value at all
    }

    /**
     * init the visual elements at startup: {@link #MESSAGE_PANEL}, {@link #MAIN_PANEL}.
     * Labels' text assumes self-registration (Open Registration); if this is not the case
     * call {@link #updateLabelsIfNotOpenReg()} afterwards.
     * @see #initInterface_conn()
     */
    protected void initVisualElements()
    {
        nick = new JTextField(20);
        pass = new JPasswordField(10);
        pass2 = new JPasswordField(10);
        email = new JTextField(50);
        status = new JTextField(50);
        status.setEditable(false);
        submit = new JButton(strings.get("account.okcreate"));  // "Create Account"
        submitLock = false;

        submit.addActionListener(this);

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        JPanel mainPane = new JPanel(gbl);

        c.fill = GridBagConstraints.BOTH;

        JLabel l;

        promptLabel = new JLabel(strings.get("account.create.prompt.enter_your_info"), SwingConstants.CENTER);
                // "To create an account, please enter your information."
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(promptLabel, c);
        mainPane.add(promptLabel);

        l = new JLabel(" ");  // spacer row
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        nickLabel = new JLabel(strings.get("account.create.nickname.your"));  // "Your Nickname:"
        nickLabel.setToolTipText(strings.get("account.create.nickname.your.tip"));  // "This will be your username."
        c.gridwidth = 1;
        gbl.setConstraints(nickLabel, c);
        mainPane.add(nickLabel);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(nick, c);
        mainPane.add(nick);

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel(strings.get("account.create.password"));  // "Password:"
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(pass, c);
        mainPane.add(pass);

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel(strings.get("account.create.password.again"));  // "Password (again):"
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(pass2, c);
        mainPane.add(pass2);

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel(strings.get("account.create.email"));  // "Email (optional):"
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(email, c);
        mainPane.add(email);

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        l = new JLabel();
        c.gridwidth = 1;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        int oldAnchor = c.anchor;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        c.ipadx = 20 * displayScale;
        gbl.setConstraints(submit, c);
        mainPane.add(submit);
        c.fill = GridBagConstraints.BOTH;
        c.anchor = oldAnchor;
        c.ipadx = 0;

        l = new JLabel();
        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(l, c);
        mainPane.add(l);

        c.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(status, c);
        mainPane.add(status);

        // message label that takes up the whole pane
        messageLabel = new JLabel("", SwingConstants.CENTER);

        JPanel messagePane = new JPanel(new BorderLayout());
        messagePane.add(messageLabel, BorderLayout.CENTER);

        // all together now...
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        add(messagePane, MESSAGE_PANEL); // shown first
        add(mainPane, MAIN_PANEL);

        // Note: JFrame validate and pack seem to use mainPane's size, even if messagePane is taller; fields get cut off.
        // Workaround: Make sure mainPane is taller
    }

    /**
     * Connect setup for username and password authentication: {@link #connPanel} / {@link #CONN_PANEL}.
     * Called if server doesn't have {@link SOCFeatureSet#SERVER_OPEN_REG}.
     * Calls {@link #validate()} and {@link #conn_user}.{@link java.awt.Component#requestFocus() requestFocus()}.
     * @since 1.1.19
     * @see #initVisualElements()
     */
    private void initInterface_conn()
    {
        JPanel pconn = new JPanel();

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        pconn.setLayout(gbl);
        gbc.fill = GridBagConstraints.BOTH;

        JLabel L;

        // heading row
        L = new JLabel(strings.get("account.common.must_auth"), SwingConstants.CENTER);
            // "You must log in with a username and password before you can create accounts."
        gbc.gridwidth = 4;
        gbc.ipady = 12 * displayScale;  // space between this and next row
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        L = new JLabel();  // Spacing for rest of form's rows
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        gbc.ipady = 0;

        // rows for server, port, nickname, password:

        gbc.ipady = 2 * displayScale;

        L = new JLabel(strings.get("pcli.cpp.server"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        L = new JLabel(host);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.ipadx = 4 * displayScale;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        gbc.ipadx = 0;

        L = new JLabel(strings.get("pcli.cpp.port"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        L = new JLabel(Integer.toString(port));
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.ipadx = 4 * displayScale;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        gbc.ipadx = 0;

        L = new JLabel(strings.get("pcli.cpp.nickname"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_user = new JTextField(20);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_user, gbc);
        conn_user.addKeyListener(this);
        pconn.add(conn_user);

        L = new JLabel(strings.get("pcli.cpp.password"));
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        pconn.add(L);
        conn_pass = new JPasswordField(20);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_pass, gbc);
        conn_pass.addKeyListener(this);
        pconn.add(conn_pass);

        gbc.ipady = 0;

        // Connect and Cancel buttons shouldn't stretch entire width, so they get their own sub-Panel
        JPanel btnsRow = new JPanel();
        final int bsize = 4 * displayScale;
        btnsRow.setBorder(BorderFactory.createEmptyBorder(bsize, bsize, bsize, bsize));

        conn_connect = new JButton(strings.get("pcli.cpp.connect"));
        conn_connect.addActionListener(this);
        conn_connect.addKeyListener(this);  // for win32 keyboard-focus ESC/ENTER
        btnsRow.add(conn_connect);

        conn_cancel = new JButton(strings.get("base.cancel"));
        conn_cancel.addActionListener(this);
        conn_cancel.addKeyListener(this);
        btnsRow.add(conn_cancel);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.CENTER;
        gbl.setConstraints(btnsRow, gbc);
        pconn.add(btnsRow);

        conn_status = new JTextField(50);
        conn_status.setEditable(false);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(conn_status, gbc);
        pconn.add(conn_status);

        connPanel = pconn;

        add(pconn, CONN_PANEL);
        cardLayout.show(this, CONN_PANEL);
        validate();

        conn_user.requestFocus();
    }

    /**
     * Update account-info label texts if needed.
     * If server supports self-registration (Open Registration), does nothing.
     * Assumes {@link #initVisualElements()} has already been called.
     * @since 1.1.20
     */
    private void updateLabelsIfNotOpenReg()
    {
        if (sFeatures.isActive(SOCFeatureSet.SERVER_OPEN_REG))
            return;

        promptLabel.setText(strings.get("account.create.prompt.enter_its_info"));
            // "To create an account, please enter its information."
        nickLabel.setText(strings.get("account.create.nickname.its"));  // "Nickname:"
        nickLabel.setToolTipText(strings.get("account.create.nickname.its.tip"));
            // "This will be the new account's username."
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
                iValue = Integer.parseInt(value, 16);
        } catch (Exception e) {
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
        } catch (Exception e) {
            System.err.println("Invalid port: " + param);
        }

        connect();
    }

    /**
     * Attempts to connect to the server. See {@link #connected} for success or
     * failure.
     * @throws IllegalStateException if already connected
     *         or if {@link Version#versionNumber()} returns 0 (packaging error)
     */
    public synchronized void connect()
    {
        String hostString = (host != null ? host : "localhost") + ":" + port;
        if (connected)
        {
            throw new IllegalStateException("Already connected to " + hostString);
        }

        if (Version.versionNumber() == 0)
        {
            messageLabel.setText("Packaging error: Cannot determine JSettlers version");
                // I18N: Can't localize this, the i18n files are provided by the same packaging steps
                // which would create /resources/version.info
            throw new IllegalStateException("Packaging error: Cannot determine JSettlers version");
        }

        System.out.println("Connecting to " + hostString);
        messageLabel.setText(strings.get("pcli.message.connecting.serv"));  // "Connecting to server..."

        try
        {
            s = new Socket(host, port);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            connected = true;
            (reader = new Thread(this)).start();

            // send VERSION right away (1.1.07 and later)
            // Version msg includes locale in 2.0.00 and later clients; v1.x.xx servers will ignore that token.
            put(SOCVersion.toCmd
                (Version.versionNumber(), Version.version(), Version.buildnum(), null, cliLocale.toString()));
        } catch (Exception e) {
            ex = e;
            String msg = strings.get("pcli.error.couldnotconnect", ex);  // "Could not connect to the server: " + ex
            System.err.println(msg);
            messageLabel.setText(msg);
        }
    }

    /**
     * "Connect" button, from connect/authenticate panel; check nickname & password fields,
     * send auth request to server, set {@link #conn_sentAuth} flag.
     * When the server responds with {@link SOCStatusMessage#SV_OK}, the {@link #MAIN_PANEL} will be shown.
     * @since 1.1.19
     */
    private void clickConnConnect()
    {
        final String user = conn_user.getText().trim(), pw = conn_pass.getText();

        if (user.length() == 0)
        {
            conn_status.setText(strings.get("account.must_enter_nick"));  // "You must enter a nickname."
            conn_user.requestFocus();

            return;
        }

        if (pw.length() == 0)
        {
            conn_status.setText(strings.get("account.must_enter_pw"));  // "You must enter a password."
            conn_pass.requestFocus();

            return;
        }

        if (pw.length() > SOCAuthRequest.PASSWORD_LEN_MAX)
        {
            conn_status.setText(strings.get("account.common.password_too_long"));  // "That password is too long."
            conn_pass.requestFocus();

            return;
        }

        conn_sentAuth = true;
        put(SOCAuthRequest.toCmd
            (SOCAuthRequest.ROLE_USER_ADMIN, user, pw, SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, host));
    }

    /**
     * Disconnect from connect/auth panel, show "disconnected" message.
     * @since 1.1.19
     */
    private void clickConnCancel()
    {
        if ((connPanel != null) && connPanel.isVisible())
            connPanel.setVisible(false);

        disconnect();

        messageLabel.setText(strings.get("account.connect.canceled"));  // "Connection canceled."
        cardLayout.show(this, MESSAGE_PANEL);
        validate();
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
                nickname = n.substring(0, 20);
            else
                nickname = n;
            if (! SOCMessage.isSingleLineAndSafe(nickname))
            {
                status.setText(SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED);  // I18N
                nick.requestFocusInWindow();

                return;  // Not a valid username
            }

            getPasswordFields();  // set password, password2 from JPasswordFields pass, pass2

            emailAddress = email.getText().trim();

            //
            // make sure all the info is ok
            //
            if (nickname.length() == 0)
            {
                status.setText(strings.get("account.must_enter_nick"));  // "You must enter a nickname."
                nick.requestFocus();
            }
            else if (password.length() == 0)
            {
                status.setText(strings.get("account.must_enter_pw"));  // "You must enter a password."
                pass.requestFocus();
            }
            else if (password.length() > SOCAuthRequest.PASSWORD_LEN_MAX)
            {
                status.setText(strings.get("account.common.password_too_long"));  // "That password is too long."
                pass.requestFocus();
            }
            else if (! password.equals(password2))
            {
                pass.requestFocus();
                status.setText(strings.get("account.create.msg.passwords_dont_match"));  // "The passwords don't match."
            }
            else if (! submitLock)
            {
                submitLock = true;
                status.setText(strings.get("account.create.msg.creating"));  // "Creating account..."
                put(SOCCreateAccount.toCmd(nickname, password, host, emailAddress));
            }
        }
        else if (target == conn_connect)
        {
            clickConnConnect();
        }
        else if (target == conn_cancel)
        {
            clickConnCancel();
        }
    }

    /**
     * Set {@link #password} and {@link #password2} string fields from GUI fields {@link #pass} and {@link #pass2}.
     * Separate method to limit scope of SuppressWarnings.
     * @since 2.0.00
     */
    @SuppressWarnings("deprecation")
    private void getPasswordFields()
    {
        password = pass.getText().trim();
        password2 = pass2.getText().trim();
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
        } catch (IOException e) {
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
        if (debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintln("OUT - " + s);

        if ((ex != null) || !connected)
        {
            return false;
        }

        try
        {
            out.writeUTF(s);
        } catch (IOException e) {
            ex = e;
            System.err.println("could not write to the net: " + ex);
            destroy();

            return false;
        }

        return true;
    }

    /**
     * Treat the incoming messages.
     *<P>
     * If {@link SOCDisplaylessPlayerClient#PROP_JSETTLERS_DEBUG_TRAFFIC} is set, debug-prints message contents.
     *
     * @param mes    the message
     */
    public void treat(SOCMessage mes)
    {
        if (mes == null)
            return;  // Msg parsing error

        if (debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintln("IN - " + mes.toString());

        try
        {
            switch (mes.getType())
            {
            /**
             * server's version and feature report (among first messages from server)
             */
            case SOCMessage.VERSION:
                handleVERSION((SOCVersion) mes);
                break;

            /**
             * List of channels on the server: Among first messages from server, after VERSION.
             * Show {@link #MAIN_PANEL} if not already showing; see handleCHANNELS javadoc.
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
        } catch (Exception e) {
            System.out.println("SOCAccountClient treat ERROR - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle the "version" message: Server's version and feature report.
     * Will disconnect if server is older than {@link #SRV_VERSION_MIN}.
     *
     * @param mes  the message
     * @since 1.1.19
     */
    private void handleVERSION(SOCVersion mes)
    {
        sVersion = mes.getVersionNumber();
        if (sVersion < SRV_VERSION_MIN)
        {
            disconnect();

            messageLabel.setText
                (strings.get
                    ("account.server_version_minimum", Version.version(sVersion), Version.version(SRV_VERSION_MIN)));
                // "This server has old version {0}; this client works only with {1} and newer servers."
            cardLayout.show(this, MESSAGE_PANEL);
            validate();

            return;
        }

        sFeatures =
            (sVersion >= SOCFeatureSet.VERSION_FOR_SERVERFEATURES)
            ? new SOCFeatureSet(mes.feats)
            : new SOCFeatureSet(true, true);

        if (! sFeatures.isActive(SOCFeatureSet.SERVER_ACCOUNTS))
        {
            disconnect();

            messageLabel.setText(strings.get("account.common.no_accts"));  // "This server does not use accounts and passwords."
            cardLayout.show(this, MESSAGE_PANEL);
            validate();

            return;
        }

        if (! sFeatures.isActive(SOCFeatureSet.SERVER_OPEN_REG))
        {
            initInterface_conn();  // adds connPanel, sets it active, calls validate()
            updateLabelsIfNotOpenReg();  // update account-info label texts for use after authentication
        }
    }

    /**
     * Handle the "list of channels" message:
     * Server connection is complete, show {@link #MAIN_PANEL} unless {@link #connPanel} is already showing.
     * @param mes  the message
     */
    protected void handleCHANNELS(SOCChannels mes)
    {
        //
        // this message indicates that we're connected to the server
        //
        if ((connPanel != null) && connPanel.isVisible())
            return;

        cardLayout.show(this, MAIN_PANEL);
        validate();
        nick.requestFocus();
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
     * handle the "status message" message.
     * If this is in response to authrequest when connecting, show {@link #MAIN_PANEL}.
     * If we've just created a user ({@link SOCStatusMessage#SV_ACCT_CREATED_OK}),
     * clear the password fields: must re-enter if creating another.
     * @param mes  the message
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes)
    {
        int sv = mes.getStatusValue();
        String statusText = mes.getStatus();

        if (sv == SOCStatusMessage.SV_OK_SET_NICKNAME)
        {
            sv = SOCStatusMessage.SV_OK;

            final int i = statusText.indexOf(SOCMessage.sep2_char);
            if (i > 0)
            {
                nickname = statusText.substring(0, i);
                statusText = statusText.substring(i + 1);
            }
        }

        if ((connPanel != null) && connPanel.isVisible())
        {
            // Initial connect/authentication panel is showing.
            // This is either an initial STATUSMESSAGE from server, such as
            // when debug is on, or a response to the authrequest we've sent.

            if ((sv != SOCStatusMessage.SV_OK) || ! conn_sentAuth)
            {
                conn_status.setText(statusText);
                return;
            }

            connPanel.setVisible(false);
            cardLayout.show(this, MAIN_PANEL);
            validate();
            nick.requestFocus();
        }

        status.setText(statusText);
        if ((sv == SOCStatusMessage.SV_ACCT_CREATED_OK)
            || (sv == SOCStatusMessage.SV_ACCT_CREATED_OK_FIRST_ONE))
        {
            // Clear password fields: must re-enter if creating another
            pass.setText("");
            pass2.setText("");

            if (sv == SOCStatusMessage.SV_ACCT_CREATED_OK_FIRST_ONE)
                // auth to server with new account
                put(SOCAuthRequest.toCmd
                    (SOCAuthRequest.ROLE_USER_ADMIN, nickname, password,
                     SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, host));
        }

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
        } catch (Exception e) {
            ex = e;
        }
    }

    private WindowAdapter createWindowAdapter()
    {
        return new WindowAdapter()
        {
            public void windowClosing(WindowEvent evt)
            {
                System.exit(0);
            }

            public void windowOpened(WindowEvent evt)
            {
                nick.requestFocus();
            }
        };
    }

    /**
     * For Connect panel, handle Enter or Esc key (KeyListener).
     * @since 1.1.19
     */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            e.consume();
            clickConnConnect();
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            e.consume();
            clickConnCancel();
            break;
        }  // switch(e)
    }

    /** Stub required by KeyListener */
    public void keyReleased(KeyEvent e) { }

    /** Stub required by KeyListener */
    public void keyTyped(KeyEvent e) { }

    /**
     * applet info
     */
    public String getAppletInfo()
    {
        return "SOCAccountClient 0.1 by Robert S. Thomas.";
    }

    /** {@link #disconnect()} and destroy the applet or frame contents */
    public void destroy()
    {
        // account.msg.applet_destroyed
        final String detail =
            (ex == null)
            ? strings.get("account.msg.refresh")  // "Refresh the page to connect again."
            : ex.toString();
        String err = strings.get("account.msg.applet_destroyed", detail); // "Sorry, the applet has been destroyed. {0}"

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
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        JFrame frame = new JFrame("SOCAccountClient");
        final int displayScale = SwingMainDisplay.checkDisplayScaleFactor(frame);
        SwingMainDisplay.scaleUIManagerFonts(displayScale);
        final int bsize = 8 * displayScale;
        frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(bsize, bsize, bsize, bsize));

        SOCAccountClient client = new SOCAccountClient(displayScale);

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

        // Add a listener for the close event
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(client.createWindowAdapter());

        client.initVisualElements(); // after the background is set

        frame.add(client, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);

        client.connect();
    }

}
