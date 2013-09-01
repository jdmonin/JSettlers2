/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2009-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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
 **/
package soc.client;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;

import soc.client.SOCPlayerClient.GameAwtDisplay;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCScenario;
import soc.message.SOCMessage;
import soc.message.SOCStatusMessage;
import soc.util.Version;

/**
 * This is the dialog for options to set in a new game.
 * Prompt for name and options.
 *<P>
 * Also used for showing a game's options (read-only) during game play.
 *<P>
 * If this window already exists and you'd like to make it topmost,
 * call {@link #setVisible(boolean)} instead of {@link #requestFocus()}.
 *<P>
 * Game option "SC" (Scenarios) gets special rendering. Internally it's {@link SOCGameOption#OTYPE_STR},
 * but it's presented as a checkbox and {@link Choice}.
 *<P>
 * This class also contains the "Scenario Info" popup window, called from
 * this dialog's Scenario Info button, and from {@link SOCPlayerInterface}
 * when first joining a game with a scenario.
 * See {@link #showScenarioInfoDialog(String, Hashtable, int, GameAwtDisplay, Frame)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class NewGameOptionsFrame extends Frame
    implements ActionListener, KeyListener, ItemListener, TextListener, MouseListener
{
    /**
     * Maximum range (min-max value) for integer-type options
     * to be rendered using a value popup, instead of a textfield.
     * @see #initOption_int(SOCGameOption)
     */
    public static final int INTFIELD_POPUP_MAXRANGE = 21;

    private final GameAwtDisplay gameDisplay;

    /** should this be sent to the remote tcp server, or local practice server? */
    private final boolean forPractice;

    /** is this for display only? */
    private final boolean readOnly;

    /** Contains this game's {@link SOCGameOption}s, or null if none.
     *  Unknowns (OTYPE_UNKNOWN) are removed in initInterface_options.
     *<P>
     * The opts' values are updated from controls when the user hits the Create Game button,
     * and sent to the server to create the game.  If there are {@link SOCGameOption.ChangeListener}s,
     * they are updated as soon as the user changes them in the controls, then re-updated when
     * Create is hit.
     * @see #readOptsValuesFromControls(boolean)
     */
    private Hashtable<String, SOCGameOption> opts;

    /** Key = AWT control; value = {@link SOCGameOption} within {@link #opts}. Empty if opts is null.  */
    private Hashtable<Component, SOCGameOption> controlsOpts;

    /**
     * AWT control for each gameopt, for handling {@link SOCGameOption#refreshDisplay()}
     * if called by {@link SOCGameOption.ChangeListener}s.
     * Key = option key; value = Component.
     * Null if {@link #readOnly}.
     * For game options with 2 input controls (OTYPE_INTBOOL, OTYPE_ENUMBOOL),
     * the TextField/Choice is found here, and the boolean Checkbox is found in {@link #boolOptCheckboxes}.
     * @since 1.1.13
     * @see #fireOptionChangeListener(soc.game.SOCGameOption.ChangeListener, SOCGameOption, Object, Object)
     */
    private Hashtable<String, Component> optsControls;

    /** Key = {@link SOCGameOption#optKey}; value = {@link Checkbox} if bool/intbool option.
      * Empty if none, null if readOnly.
      * Used to quickly find an option's associated checkbox.
      */
    private Hashtable<String, Checkbox> boolOptCheckboxes;

    /**
     * Scenario choice dropdown, if {@link #opts} contains the {@code "SC"} game option, or null.
     * When an item is selected, {@link #itemStateChanged(ItemEvent)} reacts specially for this control
     * to update {@code "SC"} within {@link #opts} and enable/disable {@link #scenInfo}.
     * @since 2.0.00
     */
    private Choice scenChoice;

    /**
     * Scenario Info button, for info window about {@link #scenChoice}'s selected scenario, or null.
     * @see #clickScenarioInfo()
     * @since 2.0.00
     */
    private Button scenInfo;

    /** create is null if readOnly */
    private Button create;
    private Button cancel;
    private TextField gameName;
    /** msgText is null if readOnly */
    private TextField msgText;

    // // TODO refactor; these are from connectorprac panel
    private static final Color NGOF_BG = new Color(Integer.parseInt("61AF71",16));
    private static final Color HEADER_LABEL_BG = new Color(220,255,220);
    private static final Color HEADER_LABEL_FG = new Color( 50, 80, 50);

    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Creates a new NewGameOptionsFrame.
     * Once created, reset the mouse cursor from hourglass to normal, and clear main panel's status text.
     *
     * @param cli      Player client interface
     * @param gaName   Requested name of game (can change in this frame),
     *                 or null for blank or (forPractice)
     *                 to use {@link SOCPlayerClient#DEFAULT_PRACTICE_GAMENAME}.
     * @param opts     Set of {@link SOCGameOption}s; its values will be changed when "New Game" button
     *                 is pressed, so the next OptionsFrame will default to the values the user has chosen.
     *                 To preserve them, call {@link SOCGameOption#cloneOptions(Hashtable)} beforehand.
     *                 Null if server doesn't support game options.
     *                 Unknown options ({@link SOCGameOption#OTYPE_UNKNOWN}) will be removed.
     *                 If not <tt>readOnly</tt>, each option's {@link SOCGameOption#userChanged userChanged}
     *                 flag will be cleared, to reset status from any previously shown NewGameOptionsFrame.
     * @param forPractice Will this game be on local practice server, vs remote tcp server?
     * @param readOnly    Is this display-only (for use during a game), or can it be changed (making a new game)?
     */
    public NewGameOptionsFrame
        (GameAwtDisplay gd, String gaName, Hashtable<String, SOCGameOption> opts, boolean forPractice, boolean readOnly)
    {
        super( readOnly
                ? (strings.get("game.options.title", gaName))
                :
                   (forPractice
                    ? strings.get("game.options.title.newpractice")
                    : strings.get("game.options.title.new")));

        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));  // include padding insets around edges of frame

        this.gameDisplay = gd;
        SOCPlayerClient cli = gd.getClient();
        this.opts = opts;
        this.forPractice = forPractice;
        this.readOnly = readOnly;
        controlsOpts = new Hashtable<Component, SOCGameOption>();
        if (! readOnly)
        {
            optsControls = new Hashtable<String, Component>();
            boolOptCheckboxes = new Hashtable<String, Checkbox>();
        }
        if ((gaName == null) && forPractice)
        {
            if (cli.numPracticeGames == 0)
                gaName = SOCPlayerClient.DEFAULT_PRACTICE_GAMENAME;
            else
                gaName = SOCPlayerClient.DEFAULT_PRACTICE_GAMENAME + " " + (1 + cli.numPracticeGames);
        }

        // same Frame setup as in SOCPlayerClient.main
        setBackground(NGOF_BG);
        setForeground(Color.black);

        addKeyListener(this);
        initInterfaceElements(gaName);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { clickCancel(); }
            });

        /**
         * complete - reset mouse cursor from hourglass to normal
         * (was set to hourglass before calling this constructor)
         */
        gd.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        gd.status.setText("");  // clear "Talking to server..."
    }

    /**
     * Creates and shows a new NewGameOptionsFrame.
     * Once created, reset the mouse cursor from hourglass to normal, and clear main panel's status text.
     * See {@link #NewGameOptionsFrame(SOCPlayerClient, String, Hashtable, boolean, boolean) constructor}
     * for notes about <tt>opts</tt> and other parameters.
     * @return the new frame
     */
    public static NewGameOptionsFrame createAndShow
        (GameAwtDisplay cli, String gaName, Hashtable<String, SOCGameOption> opts, boolean forPractice, boolean readOnly)
    {
        NewGameOptionsFrame ngof = new NewGameOptionsFrame(cli, gaName, opts, forPractice, readOnly);
        ngof.pack();
        ngof.setVisible(true);
        return ngof;
    }
    
    /**
     * Interface setup for constructor. Assumes BorderLayout.
     * Most elements are part of a sub-panel occupying most of this Frame, and using GridBagLayout.
     */
    private void initInterfaceElements(final String gaName)
    {
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();

        Panel bp = new Panel(gbl);  // Actual button panel
        bp.setForeground(getForeground());
        bp.setBackground(NGOF_BG);  // If this is omitted, firefox 3.5+ applet uses themed bg-color (seen OS X)

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        if (! readOnly)
        {
            msgText = new TextField(strings.get("game.options.prompt"));
            msgText.setEditable(false);
            msgText.setForeground(LABEL_TXT_COLOR);
            msgText.setBackground(getBackground());
            gbl.setConstraints(msgText, gbc);
            bp.add(msgText);
        }

        /**
         * Interface setup: Game name
         */
        Label L;

        L = new Label(strings.get("game.options.name"));
        L.setAlignment(Label.RIGHT);
        L.setBackground(HEADER_LABEL_BG);
        L.setForeground(HEADER_LABEL_FG);
        gbc.gridwidth = 2;
        gbl.setConstraints(L, gbc);
        bp.add(L);

        gameName = new TextField(20);
        if (gaName != null)
            gameName.setText(gaName);
        if (readOnly)
        {
            gameName.setEnabled(false);
        } else {
            gameName.addTextListener(this);    // Will enable buttons when field is not empty
            gameName.addKeyListener(this);     // for ESC/ENTER
        }
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(gameName, gbc);
        bp.add(gameName);

        /**
         * Interface setup: Options
         */
        initInterface_Options(bp, gbl, gbc);

        /**
         * Interface setup: Buttons
         */

        if (readOnly)
        {
            cancel = new Button(strings.get("base.ok"));
            cancel.setEnabled(true);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
        } else {
            cancel = new Button(strings.get("base.cancel"));
            cancel.addKeyListener(this);  // for win32 keyboard-focus
            gbc.gridwidth = 2;
        }
        gbl.setConstraints(cancel, gbc);
        bp.add(cancel);
        cancel.addActionListener(this);
        
        if (! readOnly)
        {
            create = new Button(strings.get("game.options.oknew"));  // "Create Game"
            AskDialog.styleAsDefault(create);
            create.addActionListener(this);
            create.addKeyListener(this);
            create.setEnabled(! readOnly);
            if ((gaName == null) || (gaName.length() == 0))
                create.setEnabled(false);  // Will enable when gameName not empty
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(create, gbc);
            bp.add(create);
        }

        // Final assembly setup
        bp.validate();
        add(bp);
    }

    private final static Color LABEL_TXT_COLOR = new Color(252, 251, 243); // off-white

    /**
     * Interface setup: Options.
     * One row per option, except for 3-letter options which group with 2-letter ones.
     * Boolean checkboxes go on the left edge; text and int/enum values are to right of checkboxes.
     *<P>
     * When showing options to create a new game, option keys starting with '_' are hidden
     * unless the player nickname is "debug".  This prevents unwanted changes to those options,
     * which are set at the server during game creation.  When the options are shown read-only
     * during a game, these options are shown and not hidden.
     *<P>
     * Options which have {@link SOCGameOption#FLAG_INTERNAL_GAME_PROPERTY} are always hidden.
     * If not {@link #readOnly}, they're removed from opts.  Unknown opts are always removed.
     *<P>
     * This is called from constructor, so this is a new NGOF being shown.
     * If not read-only, clear {@link SOCGameOption#userChanged} flag for
     * each option in {@link #opts}.
     *<P>
     * If options are null, put a label with {@link #TXT_SERVER_TOO_OLD}.
     */
    private void initInterface_Options(Panel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        final boolean hideUnderscoreOpts = (! readOnly) && (! gameDisplay.nick.getText().equalsIgnoreCase("debug"));

        Label L;

        if (opts == null)
        {
            L = new Label(strings.get("game.options.not"));  // "This server version does not support game options."
            L.setForeground(LABEL_TXT_COLOR);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(L, gbc);
            bp.add(L);
            return;  // <---- Early return: no options ----
        }
        else if (! readOnly)
        {
            for (SOCGameOption opt : opts.values())
                opt.userChanged = false;  // clear flag from any previously shown NGOF
        }

        gbc.anchor = GridBagConstraints.WEST;

        // Look for options that should be on the same
        // line as other options (based on key length)
        // instead of at the start of a line.
        // TODO: for now these are on subsequent lines
        //   instead of sharing the same line.
        Hashtable<String,String> sameLineOpts = new Hashtable<String,String>();  // key=on-same-line opt, value=opt to start line
        {
            Enumeration<String> okeys = opts.keys();
            while (okeys.hasMoreElements())
            {
                final String kf3 = okeys.nextElement();
                if (kf3.length() <= 2)
                    continue;
                final String kf2 = kf3.substring(0, 2);
                if (opts.containsKey(kf2))
                    sameLineOpts.put(kf3, kf2);
            }
        }

        // Sort and lay out options; remove unknowns and internal-onlys from opts.
        // TreeSet sorts game options by description, using gameopt.compareTo.
        // The array lets us remove from opts without disrupting an iterator.
        SOCGameOption[] optArr = new TreeSet<SOCGameOption>(opts.values()).toArray(new SOCGameOption[0]);
        for (int i = 0; i < optArr.length; ++i)
        {
            SOCGameOption op = optArr[i];
            if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
            {
                opts.remove(op.optKey);
                continue;  // <-- Removed, Go to next entry --
            }

            if (op.hasFlag(SOCGameOption.FLAG_INTERNAL_GAME_PROPERTY))
            {
                if (! readOnly)
                    opts.remove(op.optKey);  // ignore internal-property options when requesting new game from client
                continue;  // <-- Don't show internal-property options
            }

            if (hideUnderscoreOpts && (op.optKey.charAt(0) == '_'))
                continue;  // <-- Don't show options starting with '_'

            if (sameLineOpts.containsKey(op.optKey))
                continue;  // <-- Shares a line, Go to next entry --
            final boolean sharesLine = sameLineOpts.containsValue(op.optKey);

            initInterface_OptLine(op, bp, gbl, gbc);
            if (sharesLine)
            {
                // Group them under this one.
                // TODO group on same line, not following lines, if there's only 1.
                Enumeration<String> linekeys = sameLineOpts.keys();
                while (linekeys.hasMoreElements())
                {
                    final String kf3 = linekeys.nextElement();
                    final String kf2 = sameLineOpts.get(kf3);
                    if ((kf2 == null) || ! kf2.equals(op.optKey))
                        continue;  // <-- Goes with a a different option --

                    final SOCGameOption op3 = opts.get(kf3);
                    if (op3 != null)
                        initInterface_OptLine(op3, bp, gbl, gbc);
                }
            }

        }  // for(opts)
    }

    /**
     * Set up one game option in one line of the panel.
     * Based on the option type, create the appropriate AWT component
     * and call {@link #initInterface_Opt1(SOCGameOption, Component, boolean, boolean, Panel, GridBagLayout, GridBagConstraints)}.
     *<P>
     * Special handling: Scenario (option {@code "SC"}) gets a checkbox, label, dropdown, and a second line with
     * an Info button. (Sets {@link #scenChoice}, {@link #scenInfo}).
     *
     * @param op  Option data
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints
     */
    private void initInterface_OptLine(SOCGameOption op, Panel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        if (op.optKey.equals("SC"))
        {
            // special handling: Scenario
            // TODO server negotiation
            Map<String, SOCScenario> allSc = SOCScenario.getAllKnownScenarios();
            if ((allSc == null) || allSc.isEmpty())
                return;

            int i = 0, sel = 0;
            Choice ch = new Choice();
            ch.add("(none)");   //I18N?
            for (final SOCScenario sc : allSc.values())
            {
                ++i;
                ch.add(sc.scKey + ": " + sc.scDesc);  // scenarioKeyFromDisplayText() must be able to extract the key
                if (sc.scKey.equals(op.getStringValue()))
                    sel = i;
            }
            if (sel != 0)
            {
                ch.select(sel);
                op.setBoolValue(true);
            }

            scenChoice = ch;
            initInterface_Opt1(op, ch, true, true, bp, gbl, gbc);
                // adds ch, and a checkbox which will toggle this OTYPE_STR's op.boolValue

            if ((! readOnly) || opts.containsKey("SC"))
            {
                // 2nd line: right-justified "Scenario Info..." button

                Label blank = new Label();
                gbc.gridwidth = 1;
                gbl.setConstraints(blank, gbc);
                bp.add(blank);
                scenInfo = new Button(/*I*/"Scenario Info..."/*18N*/);
                scenInfo.addActionListener(this);
                scenInfo.addKeyListener(this);
                scenInfo.setEnabled(sel != 0);  // disable if "(none)" is selected scenario option

                gbc.gridwidth = GridBagConstraints.REMAINDER;
                final int oldAnchor = gbc.anchor, oldFill = gbc.fill;
                gbc.fill = GridBagConstraints.NONE;
                gbc.anchor = GridBagConstraints.EAST;
                gbl.setConstraints(scenInfo, gbc);
                bp.add(scenInfo);
                gbc.fill = oldFill;
                gbc.anchor = oldAnchor;
            }

            return;
        }

        switch (op.optType)  // OTYPE_*
        {
        case SOCGameOption.OTYPE_BOOL:
            {
                Checkbox cb = new Checkbox();
                initInterface_Opt1(op, cb, true, false, bp, gbl, gbc);
                cb.addItemListener(this);
            }
            break;

        case SOCGameOption.OTYPE_INT:
        case SOCGameOption.OTYPE_INTBOOL:
            {
                final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_INTBOOL);
                initInterface_Opt1(op, initOption_int(op), hasCheckbox, true, bp, gbl, gbc);
            }
            break;

        case SOCGameOption.OTYPE_ENUM:
        case SOCGameOption.OTYPE_ENUMBOOL:
            // Choice (popup menu)
            {
                final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_ENUMBOOL);
                initInterface_Opt1(op, initOption_enum(op), hasCheckbox, true, bp, gbl, gbc);
            }
            break;

        case SOCGameOption.OTYPE_STR:
        case SOCGameOption.OTYPE_STRHIDE:
            {
                int txtwid = op.maxIntValue;  // used as max length
                if (txtwid > 20)
                    txtwid = 20;
                final boolean doHide = (op.optType == SOCGameOption.OTYPE_STRHIDE);
                String txtcontent = (doHide ? "" : op.getStringValue());
                TextField txtc = new TextField(txtcontent, txtwid);
                if (doHide)
                {
                    if (SOCPlayerClient.isJavaOnOSX)
                        txtc.setEchoChar('\u2022');  // round bullet (option-8)
                    else
                        txtc.setEchoChar('*');
                }
                if (! readOnly)
                {
                    txtc.addKeyListener(this);  // for ESC/ENTER
                    txtc.addTextListener(this); // for gameopt.ChangeListener and userChanged
                }
                initInterface_Opt1(op, txtc, false, false, bp, gbl, gbc);
            }
            break;

            // default: unknown, ignore; see above
        }
    }

    /**
     * Add one GridBagLayout row with this game option (component and label(s)).
     * The option's descriptive text may have "#" as a placeholder for where
     * int/enum value is specified (IntTextField or Choice-dropdown).
     * @param op  Option data
     * @param oc  Component with option choices (popup menu, textfield, etc).
     *            If oc is a {@link TextField} or {@link Choice}, and hasCB,
     *            changing the component's value will set the checkbox.
     *            <tt>oc</tt> will be added to {@link #optsControls} and {@link #controlsOpts}.
     * @param hasCB  Add a checkbox?  If oc is {@link Checkbox}, set this true;
     *            it won't add a second checkbox.
     *            The checkbox will be added to {@link #boolOptCheckboxes} and {@link #controlsOpts}.
     * @param allowPH  Allow the "#" placeholder within option desc?
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints; gridwidth will be set to 1 and then REMAINDER
     */
    private void initInterface_Opt1(SOCGameOption op, Component oc,
            boolean hasCB, boolean allowPH,
            Panel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        Label L;

        gbc.gridwidth = 1;
        if (hasCB)
        {
            Checkbox cb;
            if (oc instanceof Checkbox)
                cb = (Checkbox) oc;
            else
                cb = new Checkbox();
            controlsOpts.put(cb, op);
            cb.setState(op.getBoolValue());
            cb.setEnabled(! readOnly);
            gbl.setConstraints(cb, gbc);
            bp.add(cb);
            if (! readOnly)
            {
                boolOptCheckboxes.put(op.optKey, cb);
                cb.addItemListener(this);  // for op's ChangeListener and userChanged
            }
        } else {
            L = new Label();  // to fill checkbox's column
            gbl.setConstraints(L, gbc);
            bp.add(L);
        }

        final int placeholderIdx = allowPH ? op.optDesc.indexOf('#') : -1;
        Panel optp = new Panel();  // with FlowLayout
        try
        {
            FlowLayout fl = (FlowLayout) (optp.getLayout());
            fl.setAlignment(FlowLayout.LEFT);
            fl.setVgap(0);
            fl.setHgap(0);
        }
        catch (Throwable fle) {}

        // Any text to the left of placeholder in optDesc?
        if (placeholderIdx > 0)
        {
            L = new Label(op.optDesc.substring(0, placeholderIdx - 1));
            L.setForeground(LABEL_TXT_COLOR);
            optp.add(L);
            if (hasCB && ! readOnly)
            {
                controlsOpts.put(L, op);
                L.addMouseListener(this);  // Click label to toggle checkbox
            }
        }

        // TextField or Choice at placeholder position
        if (! (oc instanceof Checkbox))
        {
            controlsOpts.put(oc, op);
            oc.setEnabled(! readOnly);
            optp.add(oc);
            if (hasCB && ! readOnly)
            {
                if (oc instanceof TextField)
                {
                    ((TextField) oc).addTextListener(this);  // for enable/disable
                    ((TextField) oc).addKeyListener(this);   // for ESC/ENTER
                } else if (oc instanceof Choice)
                {
                    ((Choice) oc).addItemListener(this);  // for related cb, and op.ChangeListener and userChanged
                }
            }
        }
        if (! readOnly)
            optsControls.put(op.optKey, oc);

        // Any text to the right of placeholder?  Also creates
        // the text label if there is no placeholder (placeholderIdx == -1).
        if (placeholderIdx + 1 < op.optDesc.length())
        {
            L = new Label(op.optDesc.substring(placeholderIdx + 1));
            L.setForeground(LABEL_TXT_COLOR);
            optp.add(L);
            if (hasCB && ! readOnly)
            {
                controlsOpts.put(L, op);
                L.addMouseListener(this);  // Click label to toggle checkbox
            }
        }

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(optp, gbc);
        bp.add(optp);
    }

    /**
     * Natural log of 10. For use in {@link #initOption_int(SOCGameOption)}, to determine
     * number of digits needed for the option in a textfield
     * (not available in java 1.4)
     */
    private static final double LOG_10 = Math.log(10.0);

    /**
     * Based on this game option's type, present its intvalue either as
     * a numeric textfield, or a popup menu if min/max are near each other.
     * The maximum min/max distance which creates a popup is {@link #INTFIELD_POPUP_MAXRANGE}.
     * @param op A SOCGameOption with an integer value, that is,
     *           of type {@link SOCGameOption#OTYPE_INT OTYPE_INT}
     *           or {@link SOCGameOption#OTYPE_INTBOOL OTYPE_INTBOOL}
     * @return an IntTextField or {@link java.awt.Choice} (popup menu)
     */
    private Component initOption_int(SOCGameOption op)
    {
        // OTYPE_* - if a new type is added, update this method's javadoc.

        int optrange = op.maxIntValue - op.minIntValue;
        Component c;
        if ((optrange > INTFIELD_POPUP_MAXRANGE) || (optrange < 0))
        {
            // IntTextField with width based on number of digits in min/max .
            // Math.log10 isn't available in java 1.4, so we calculate it for now.
            int amaxv = Math.abs(op.maxIntValue);
            int aminv = Math.abs(op.minIntValue);
            final int magn;
            if (amaxv > aminv)
                magn = amaxv;
            else
                magn = aminv;
            int twidth = 1 + (int) Math.ceil(Math.log(magn)/LOG_10);
            if (twidth < 3)
                twidth = 3;
            c = new IntTextField(op.getIntValue(), twidth);
            ((TextField) c).addTextListener(this);  // for op.ChangeListener and userChanged
        } else {
            Choice ch = new Choice();
            for (int i = op.minIntValue; i <= op.maxIntValue; ++i)
                ch.add(Integer.toString(i));

            int defaultIdx = op.getIntValue() - op.minIntValue;
            if (defaultIdx > 0)
                ch.select(defaultIdx);
            ch.addItemListener(this);  // for op.ChangeListener and userChanged
            c = ch;
        }
        return c;
    }

    /**
     * Create a popup menu for the choices of this enum.
     * @param op Game option, of type {@link SOCGameOption#OTYPE_ENUM OTYPE_ENUM}
     *           or {@link SOCGameOption#OTYPE_ENUMBOOL OTYPE_ENUMBOOL}
     */
    private Choice initOption_enum(SOCGameOption op)
    {
        Choice ch = new Choice();
        final String[] chs = op.enumVals;
        for (int i = 0; i < chs.length; ++i)
            ch.add(chs[i]);

        int defaultIdx = op.getIntValue() - 1;  // enum numbering is 1-based
        if (defaultIdx > 0)
            ch.select(defaultIdx);
        ch.addItemListener(this);  // for op.ChangeListener and userChanged
        return ch;
    }

    /**
     * When the window is shown, request focus on game name textfield.
     * To make this window topmost, call {@code setVisible(true)} instead of {@link #requestFocus()}.
     */
    @Override
    public void setVisible(boolean b)
    {
        super.setVisible(b);
        if (b)
        {
            EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    toFront();  // needed on win32 at least
                    gameName.requestFocus();
                }
            });
        }
    }

    /** React to button clicks */
    public void actionPerformed(ActionEvent ae)
    {
        try
        {
            
            Object src = ae.getSource();
            if (src == create)
            {
                // Check options, ask client to set up and start a practice game
                clickCreate(true);
                return;
            }
            else if (src == cancel)
            {
                clickCancel();
                return;
            }
            else if (src == scenInfo)
            {
                clickScenarioInfo();
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

    /** "Connect..." from connect setup; check fields, etc */
    private void clickCreate(final boolean checkOptionsMinVers)
    {
        String gmName = gameName.getText().trim();
        if (gmName.length() == 0)
        {
            return;  // Should not happen (button disabled by TextListener)
        }
        if (! SOCMessage.isSingleLineAndSafe(gmName))
        {
            msgText.setText(SOCStatusMessage.MSG_SV_NEWGAME_NAME_REJECTED);
            gameName.requestFocusInWindow();
            return;  // Not a valid game name
        }

        SOCPlayerClient cl = gameDisplay.getClient();
        /**
         * Is this game name already used?
         * Always check remote server for the requested game name.
         * Check practice game names only if creating another practice game.
         */
        boolean gameExists;
        if (forPractice)
            gameExists = (cl.getNet().practiceServer != null) && (-1 != cl.getNet().practiceServer.getGameState(gmName));
        else
            gameExists = false;
        if (cl.serverGames != null)
            gameExists = gameExists || cl.serverGames.isGame(gmName);
        if (gameExists)
        {
            NotifyDialog.createAndShow(gameDisplay, this, SOCStatusMessage.MSG_SV_NEWGAME_ALREADY_EXISTS, null, true);
            return;
        }

        if (gameDisplay.readValidNicknameAndPassword())
        {
            if (readOptsValuesFromControls(checkOptionsMinVers))
            {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));  // Immediate feedback in this frame
                gameDisplay.askStartGameWithOptions(gmName, forPractice, opts);  // Also sets WAIT_CURSOR, in main client frame
            } else {
                return;  // readOptsValues will put the err msg in dia's status line
            }
        } else {
            // Nickname field is also checked before this dialog is displayed,
            // so the user must have gone back and changed it.
            // Can't correct the problem from within this dialog, since the
            // nickname field (and hint message) is in SOCPlayerClient's panel.
            NotifyDialog.createAndShow(gameDisplay, this, strings.get("game.options.nickerror"), null, true);
            return;
        }

        dispose();
    }

    /** Dismiss the frame */
    private void clickCancel()
    {
        dispose();
    }

    /**
     * The "Scenario Info" button was clicked.
     * Reads the current scenario, if any, from {@link #scenChoice}.
     * Calls {@link #showScenarioInfoDialog(String, Hashtable, int, GameAwtDisplay, Frame)}.
     * @since 2.0.00
     */
    private void clickScenarioInfo()
    {
        if (scenChoice == null)
            return;  // should not happen, scenChoice is created before scenInfo

        final String scKey = scenarioKeyFromDisplayText(scenChoice.getSelectedItem());
        if (scKey.length() == 0)
            return;

        showScenarioInfoDialog(scKey, null, SOCGame.VP_WINNER_STANDARD, gameDisplay, this);
    }

    /** Dismiss the frame, and clear client's {@link SOCPlayerClient#newGameOptsFrame}
     *  ref to this frame
     */
    @Override
    public void dispose()
    {
        if (this == gameDisplay.newGameOptsFrame)
            gameDisplay.newGameOptsFrame = null;
        super.dispose();
    }

    /**
     * Read option values from controls, as prep to request the new game.
     * If there is a problem (out of range, bad character in integer field, etc),
     * set {@link #msgText} and set focus on the field.
     * @param checkOptionsMinVers Warn the user if the options will require a
     *           minimum client version?  Won't do so if {@link #forPractice} is set,
     *           because this isn't a problem for local practice games.
     * @return true if all were read OK, false if a problem (such as NumberFormatException)
     */
    private boolean readOptsValuesFromControls(final boolean checkOptionsMinVers)
    {
        if (readOnly)
            return false;  // shouldn't be called in that case

        boolean allOK = true;
        for (Enumeration<Component> e = controlsOpts.keys(); e.hasMoreElements(); )
        {
            Component ctrl = e.nextElement();
            if (ctrl instanceof Label)
                continue;
            SOCGameOption op = controlsOpts.get(ctrl);

            if (op.optKey.equals("SC"))
            {
                // Special case: AWT event listeners have already set its value from controls
                if (! op.getBoolValue())
                    op.setStringValue("");
                continue;
            }

            // OTYPE_* - new option types may have new AWT control objects, or
            //           may use the same controls with different contents as these.

            if (ctrl instanceof Checkbox)
            {
                op.setBoolValue(((Checkbox)ctrl).getState());
            }
            else if (ctrl instanceof TextField)
            {
                String txt = ((TextField) ctrl).getText().trim();
                if ((op.optType == SOCGameOption.OTYPE_STR)
                    || (op.optType == SOCGameOption.OTYPE_STRHIDE))
                {
                    try
                    {
                        op.setStringValue(txt);
                    } catch (IllegalArgumentException ex)
                    {
                        allOK = false;
                        msgText.setText(strings.get("game.options.singleline"));  // only a single line of text allowed
                        ctrl.requestFocusInWindow();
                    }
                } else {
                    try   // OTYPE_INT, OTYPE_INTBOOL
                    {
                        int iv = Integer.parseInt(txt);
                        op.setIntValue(iv);
                        if (iv != op.getIntValue())
                        {
                            allOK = false;
                            msgText.setText
                                (strings.get("game.options.outofrange", op.minIntValue, op.maxIntValue));
                            ctrl.requestFocusInWindow();
                        }
                    } catch (NumberFormatException ex)
                    {
                        allOK = false;
                        msgText.setText(strings.get("game.options.onlydigits"));
                        ctrl.requestFocusInWindow();
                    }
                }
            }
            else if (ctrl instanceof Choice)
            {
                // this works with OTYPE_INT, OTYPE_INTBOOL, OTYPE_ENUM, OTYPE_ENUMBOOL
                int chIdx = ((Choice) ctrl).getSelectedIndex();  // 0 to n-1
                if (chIdx != -1)
                    op.setIntValue(chIdx + op.minIntValue);
                else
                    allOK = false;
            }

        }  // for(opts)

        if (allOK && checkOptionsMinVers && ! forPractice)
        {
            int optsVers = SOCGameOption.optionsMinimumVersion(controlsOpts);
            if ((optsVers > -1) && (optsVers > Version.versionNumberMaximumNoWarn()))
            {
                allOK = false;
                new VersionConfirmDialog(this, optsVers).setVisible(true);
            }
        }

        return allOK;
    }

    /** Handle Enter or Esc key (KeyListener) */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        try
        {
            switch (e.getKeyCode())
            {
            case KeyEvent.VK_ENTER:
                clickCreate(true);
                break;

            case KeyEvent.VK_CANCEL:
            case KeyEvent.VK_ESCAPE:
                clickCancel();
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

    /**
     * When gamename contents change, enable/disable buttons as appropriate. (TextListener)
     * Also handles {@link SOCGameOption#OTYPE_INTBOOL} textfield/checkbox combos.
     * Also sets {@link SOCGameOption#userChanged}.
     * @param e textevent from {@link #gameName}, or from a TextField in {@link #controlsOpts}
     */
    public void textValueChanged(TextEvent e)
    {
        if (readOnly)
            return;
        Object srcObj = e.getSource();
        if (! (srcObj instanceof TextField))
            return;
        final String newText = ((TextField) srcObj).getText().trim();
        final boolean notEmpty = (newText.length() > 0);
        if (srcObj == gameName)
        {
            if (notEmpty != create.isEnabled())
                create.setEnabled(notEmpty);  // enable "create" btn only if game name filled in
        }
        else
        {
            // Check for a ChangeListener for OTYPE_STR and OTYPE_STRHIDE,
            // OTYPE_INT and OTYPE_INTBOOL.
            // if source is OTYPE_INTBOOL, check its checkbox vs notEmpty.
            SOCGameOption opt = controlsOpts.get(srcObj);
            if (opt == null)
                return;
            final String oldText = opt.getStringValue();
            boolean validChange = false;
            boolean otypeIsInt;
            int oldIntValue = 0;

            if ((opt.optType == SOCGameOption.OTYPE_STR)
                 || (opt.optType == SOCGameOption.OTYPE_STRHIDE))
            {
                otypeIsInt = false;
                try
                {
                    opt.setStringValue(newText);
                    validChange = true;
                } catch (IllegalArgumentException ex)
                { }
            } else {
                otypeIsInt = true;
                try   // OTYPE_INT, OTYPE_INTBOOL
                {
                    final int iv = Integer.parseInt(newText);
                    oldIntValue = opt.getIntValue();
                    opt.setIntValue(iv);  // ignored if outside min,max range
                    if (iv == opt.getIntValue())
                        validChange = true;
                } catch (NumberFormatException ex)
                { }
            }

            if (validChange && ! opt.userChanged)
                opt.userChanged = true;

            // If this string or int option also has a bool checkbox,
            // set or clear that based on string/int not empty.
            boolean cbSet = false;
            Checkbox cb = boolOptCheckboxes.get(opt.optKey);
            if ((cb != null) && (notEmpty != cb.getState()))
            {
                cb.setState(notEmpty);
                opt.setBoolValue(notEmpty);
                cbSet = true;
            }
 
            SOCGameOption.ChangeListener cl = opt.getChangeListener();
            if (cl == null)
                return;

            // If both bool and int fields are changed, update both before
            // calling fireOptionChangeListener.  Boolean is called before int.
            if (cbSet)
            {
                // ChangeListener for checkbox
                final Boolean newValue = (notEmpty) ? Boolean.TRUE : Boolean.FALSE;
                final Boolean oldValue = (notEmpty) ? Boolean.FALSE : Boolean.TRUE;
                fireOptionChangeListener(cl, opt, oldValue, newValue);
            }
            // ChangeListener for text field
            if (validChange)
            {
                if (otypeIsInt)
                    fireOptionChangeListener(cl, opt, new Integer(oldIntValue), new Integer(opt.getIntValue()));
                else
                    fireOptionChangeListener(cl, opt, oldText, newText);
            }
        }
    }

    /**
     * Called when a Choice or Checkbox value changes (ItemListener).
     * Used for these things:
     *<UL>
     * <LI>
     * Set {@link SOCGameOption#userChanged}
     * <LI>
     * Check Choices or Checkboxes to see if their game option has a {@link ChangeListener}.
     * <LI>
     * Set the checkbox when the popup-menu Choice value is changed for a
     * {@link SOCGameOption#OTYPE_INTBOOL} or {@link SOCGameOption#OTYPE_ENUMBOOL}.
     *</UL>
     * @param e itemevent from a Choice or Checkbox in {@link #controlsOpts}
     */
    public void itemStateChanged(ItemEvent e)
    {
        final Object ctrl = e.getSource();
        boolean choiceSetCB = false;
        SOCGameOption opt = controlsOpts.get(ctrl);
        if (opt == null)
            return;

        Checkbox cb = boolOptCheckboxes.get(opt.optKey);
        if ((cb != null) && (cb != ctrl))
        {
            // If the user picked a choice, also set the checkbox
            boolean wantsSet;

            if (! (opt.optKey.equals("SC") && (ctrl instanceof Choice)))
            {
                wantsSet = true;  // any item sets it
            } else {
                // Special case for "SC", an OTYPE_STR with a Choice and Checkbox
                Choice ch = (Choice) ctrl;
                wantsSet = (ch.getSelectedIndex() != 0);  // Special case, first item clears it
                opt.setBoolValue(wantsSet);
                if (wantsSet)
                {
                    final String chText = ch.getSelectedItem();
                    opt.setStringValue(scenarioKeyFromDisplayText(chText));
                } else {
                    opt.setStringValue("");
                }

                if (scenInfo != null)
                    scenInfo.setEnabled(wantsSet);
            }

            if (wantsSet != cb.getState())
            {
                cb.setState(wantsSet);
                choiceSetCB = true;
            }
        }

        if (! opt.userChanged)
            opt.userChanged = true;

        SOCGameOption.ChangeListener cl = opt.getChangeListener();
        if (cl == null)
            return;

        // If both bool and int fields are changed, update both before
        // calling fireOptionChangeListener.  Boolean is called before int.
        final boolean fireBooleanListener;
        final Object boolOldValue, boolNewValue;

        if (choiceSetCB || (ctrl instanceof Checkbox))
        {
            fireBooleanListener = true;
            final boolean becameChecked = (e.getStateChange() == ItemEvent.SELECTED);
            boolNewValue = (becameChecked) ? Boolean.TRUE : Boolean.FALSE;
            boolOldValue = (becameChecked) ? Boolean.FALSE : Boolean.TRUE;
            opt.setBoolValue(becameChecked);
        } else {
            fireBooleanListener = false;
            boolNewValue = null;
            boolOldValue = null;
        }

        if (ctrl instanceof Choice)
        {
            int chIdx = ((Choice) ctrl).getSelectedIndex();  // 0 to n-1
            if (chIdx != -1)
            {
                final int nv = chIdx + opt.minIntValue;
                Integer newValue = new Integer(nv);
                Integer oldValue = new Integer(opt.getIntValue());
                opt.setIntValue(nv);
                if (fireBooleanListener)
                    fireOptionChangeListener(cl, opt, boolOldValue, boolNewValue);
                fireOptionChangeListener(cl, opt, oldValue, newValue);
            }
        }
        else if (fireBooleanListener)
            fireOptionChangeListener(cl, opt, boolOldValue, boolNewValue);
    }

    /**
     * Get a scenario key, such as "{@link SOCScenario#K_SC_4ISL SC_4ISL}",
     * from the displayed name+description built by initInterface_OptLine.
     * @param chText  Scenario name & desc, such as "SC_4ISL: (description here)", or "(none)"
     * @return  Scenario key name, such as "SC_4ISL", or "" if the delimiter ':' wasn't found
     * @since 2.0.00
     */
    private static final String scenarioKeyFromDisplayText(String chText)
    {
        final int i = chText.indexOf(':');
        if (i <= 0)
            return "";
        else
            return chText.substring(0, i);
    }

    /**
     * Handle firing a game option's ChangeListener, and refreshing related
     * gameopts' values on-screen if needed.
     * If <tt>oldValue</tt>.equals(<tt>newValue</tt>), nothing happens and
     * the ChangeListener is not called.
     * @param cl  The ChangeListener; must not be null
     * @param op  The game option
     * @param oldValue  Old value, string or boxed primitive
     * @param newValue  New value, string or boxed primitive
     * @since 1.1.13
     */
    private void fireOptionChangeListener(SOCGameOption.ChangeListener cl, SOCGameOption opt, final Object oldValue, final Object newValue)
    {
        if (oldValue.equals(newValue))
            return;  // <--- Early return: Value didn't change ---

        try
        {
            cl.valueChanged(opt, oldValue, newValue, opts);
        } catch (Throwable thr) {
            System.err.println("-- Error caught in ChangeListener: " + thr.toString() + " --");
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

        Vector<SOCGameOption> refresh = SOCGameOption.getAndClearRefreshList();
        if (refresh == null)
            return;  // <--- Early return: Nothing else changed ---

        // Refresh each one now, depending on type:
        if (optsControls == null)
            return;  // should only be null if readOnly, and thus no changes to values anyway
        for (int i = refresh.size() - 1; i >= 0; --i)
        {
            final SOCGameOption op = refresh.elementAt(i);
            final Component opComp = optsControls.get(op.optKey);

            switch (op.optType)  // OTYPE_*
            {
            case SOCGameOption.OTYPE_BOOL:
                ((Checkbox) opComp).setState(op.getBoolValue());
                break;
    
            case SOCGameOption.OTYPE_INT:
            case SOCGameOption.OTYPE_INTBOOL:
                {
                    if (opComp instanceof TextField)
                        ((TextField) opComp).setText(Integer.toString(op.getIntValue()));
                    else
                        ((Choice) opComp).select(op.getIntValue() - op.minIntValue);
                    final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_INTBOOL);
                    if (hasCheckbox)
                    {
                        Checkbox cb = boolOptCheckboxes.get(op.optKey);
                        if (cb != null)
                            cb.setState(op.getBoolValue());
                    }
                }
                break;
    
            case SOCGameOption.OTYPE_ENUM:
            case SOCGameOption.OTYPE_ENUMBOOL:
                {
                    ((Choice) opComp).select(op.getIntValue() - op.minIntValue);
                    final boolean hasCheckbox = (op.optType == SOCGameOption.OTYPE_ENUMBOOL);
                    if (hasCheckbox)
                    {
                        Checkbox cb = boolOptCheckboxes.get(op.optKey);
                        if (cb != null)
                            cb.setState(op.getBoolValue());
                    }
                }
                break;
    
            case SOCGameOption.OTYPE_STR:
            case SOCGameOption.OTYPE_STRHIDE:
                ((TextField) opComp).setText(op.getStringValue());
                break;
    
                // default: unknown, see above
            }
        }
    }

    /** when an option with a boolValue's label is clicked, toggle its checkbox */
    public void mouseClicked(MouseEvent e)
    {
        SOCGameOption opt = controlsOpts.get(e.getSource());
        if (opt == null)
            return;
        Checkbox cb = boolOptCheckboxes.get(opt.optKey);
        if (cb == null)
            return;
        final boolean becameChecked = ! cb.getState();
        cb.setState(becameChecked);
        opt.setBoolValue(becameChecked);
        if (! opt.userChanged)
            opt.userChanged = true;
        SOCGameOption.ChangeListener cl = opt.getChangeListener();
        if (cl == null)
            return;
        final Boolean newValue = (becameChecked) ? Boolean.TRUE : Boolean.FALSE;
        final Boolean oldValue = (becameChecked) ? Boolean.FALSE : Boolean.TRUE;
        fireOptionChangeListener(cl, opt, oldValue, newValue);
    }

    /** required stub for MouseListener */
    public void mouseEntered(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseExited(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mousePressed(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseReleased(MouseEvent e) {}

    /**
     * Show a popup window with this scenario's description, special rules, and number of victory points to win.
     * @param gameSc  A scenario keyname for {@link SOCScenario#getScenario(String)}, or null to do nothing
     * @param gameOpts  All game options if current game, or null to extract from {@code gameSc}'s {@link SOCScenario#scOpts}
     * @param vpWinner  Number of victory points to win, or {@link SOCGame#VP_WINNER_STANDARD}.
     * @param cli     required for {@link AskDialog} constructor
     * @param parent  required for {@link AskDialog} constructor
     * @since 2.0.00
     */
    public static void showScenarioInfoDialog
        (final String gameSc, Hashtable<String, SOCGameOption> gameOpts, final int vpWinner,
         final GameAwtDisplay cli, final Frame parent)
    {
        if (gameSc == null)
            return;

        SOCScenario sc = SOCScenario.getScenario(gameSc);
        if (sc == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append(/*I*/"Game Scenario: "/*18N*/);
        sb.append(sc.scDesc);
        sb.append('\n');

        if (sc.scLongDesc != null)
        {
            sb.append('\n');
            sb.append(sc.scLongDesc);
        }

        // Check game for any other _SC_ game opts in effect:

        final String scenOptName = "_" + sc.scKey;  // "_SC_CLVI"
        final String optDescScenPrefix = /*I*/"Scenarios: "/*18N*/;
        //      I18N note: showScenarioInfoDialog() assumes these scenario game options
        //      all start with the text "Scenarios: "; when localizing, be sure to
        //      keep a consistent prefix that showScenarioInfoDialog() knows to look for.

        if ((gameOpts == null) && (sc.scOpts != null))
            gameOpts = SOCGameOption.parseOptionsToHash(sc.scOpts);

        if (gameOpts != null)
        {
            for (SOCGameOption sgo : gameOpts.values())
            {
                if (sgo.optKey.equals(scenOptName))
                    continue;  // scenario's dedicated game option; we already showed its name from scDesc
                if (! sgo.optKey.startsWith("_SC_"))
                    continue;

                String optDesc = sgo.optDesc;
                if (optDesc.startsWith(optDescScenPrefix))
                    optDesc = optDesc.substring(optDescScenPrefix.length()).trim();
                sb.append('\n');
                sb.append(optDesc);
            }
        }

        if (vpWinner != SOCGame.VP_WINNER_STANDARD)
        {
            sb.append('\n');
            sb.append(/*I*/"Victory Points to win: "/*18N*/);
            sb.append(vpWinner);
        }

        final String scenStr = sb.toString();
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                NotifyDialog.createAndShow(cli, parent, scenStr, null, true);
            }
        });

    }


    /**
     * A textfield that accepts only nonnegative-integer characters.
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    public class IntTextField extends TextField implements KeyListener
    {
        IntTextField(int initVal, int width)
        {
            super(Integer.toString(initVal), width);
            addKeyListener(this);
        }

        /**
         * Parse the value of this textfield
         * @return value, or 0 if can't parse it
         */
        public int getIntValue()
        {
            String txt = getText().trim();
            if (txt.length() == 0)
                return 0;
            try
            {
                return Integer.parseInt(txt);
            }
            catch (NumberFormatException e)
            {
                return 0;
            }
        }

        /** stub for KeyListener */
        public void keyPressed(KeyEvent e) {}

        /** stub for KeyListener */
        public void keyReleased(KeyEvent e) {}

        /** reject entered characters which aren't digits */
        public void keyTyped(KeyEvent e)
        {
            // TODO this is not working

            switch (e.getKeyCode())
            {
            case KeyEvent.VK_ENTER:
                clickCreate(true);
                break;

            case KeyEvent.VK_CANCEL:
            case KeyEvent.VK_ESCAPE:
                clickCancel();
                break;

            default:
                {
                final char c = e.getKeyChar();
                if (c == KeyEvent.CHAR_UNDEFINED)  // ctrl characters, arrows, etc
                    return;
                if (! Character.isDigit(c))
                    e.consume();  // ignore non-digits
                }
            }  // switch(e)
        }

    }  // public inner class IntTextField


    /**
     * This is the modal dialog to ask user if these options' required
     * minimum client version is OK.
     *
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    private class VersionConfirmDialog extends AskDialog
    {
        /**
         * Creates a new VersionConfirmDialog.
         *
         * @param ngof  Parent options-frame, which contains these options
         * @param minVers  Minimum required version for these options
         */
        public VersionConfirmDialog(NewGameOptionsFrame ngof, int minVers)
        {
            super(gameDisplay, ngof, strings.get("game.options.verconfirm.title"),
                strings.get("game.options.verconfirm.prompt", Version.version(minVers)),
                strings.get("game.options.verconfirm.create"), 
                strings.get("game.options.verconfirm.change"), true, false);
        }

        /**
         * React to the Create button.
         */
        @Override
        public void button1Chosen()
        {
            clickCreate(false);
        }

        /**
         * React to the Change button.
         */
        @Override
        public void button2Chosen()
        {
            dispose();
        }

        /**
         * React to the dialog window closed by user, or Esc pressed. (same as Change button)
         */
        @Override
        public void windowCloseChosen()
        {
            button2Chosen();
        }

    }  // private inner class VersionConfirmDialog


}  // public class NewGameOptionsFrame
