/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
package soc.client;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.Enumeration;
import java.util.Hashtable;

import soc.game.SOCGameOption;
import soc.message.SOCStatusMessage;

/**
 * This is the dialog for options to set in a new game.
 * Prompt for name and options.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class NewGameOptionsFrame extends Frame
    implements ActionListener, KeyListener, TextListener, MouseListener
{
    /**
     * Maximum range (min-max value) for integer-type options
     * to be rendered using a value popup, instead of a textfield. 
     * @see #initOption_intfield(SOCGameOption)
     */
    public static final int INTFIELD_POPUP_MAXRANGE = 21;

    private static final String TXT_SERVER_TOO_OLD
        = "This server version does not support game options.";

    private SOCPlayerClient cl;

    /** should this be sent to the remote tcp server, or local practice server? */
    private boolean forPractice;

    /** is this for display only? */
    private boolean readOnly;

    /** Contains this game's {@link SOCGameOption}s, or null if none */
    private Hashtable opts;

    /** Key = AWT control; value = {@link SOCGameOption}. Empty if opts is null.  */
    private Hashtable controlsOpts;

    /** Key = {@link SOCGameOption#optKey}; value = {@link Checkbox} if bool/intbool option.
      * Empty if none, null if readOnly.
      */
    private Hashtable boolOptCheckboxes;

    /** create is null if readOnly */
    private Button create;
    private Button cancel;
    private TextField gameName;
    /** msgText is null if readOnly */
    private TextField msgText;

    // // TODO refactor; these are from connectorprac panel
    private static final Color HEADER_LABEL_BG = new Color(220,255,220);
    private static final Color HEADER_LABEL_FG = new Color( 50, 80, 50);

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
     * @param forPractice Will this game be on local practice server, vs remote tcp server?
     * @param readOnly    Is this display-only (for use during a game), or can it be changed?
     */
    public NewGameOptionsFrame
        (SOCPlayerClient cli, String gaName, Hashtable opts, boolean forPractice, boolean readOnly)
    {
        super( readOnly
                ? ("Current game options: " + gaName)
                :
                   (forPractice
                    ? "New Game options: Practice game"
                    : "New Game options"));

        setLayout(new BorderLayout());

        this.cl = cli;
        this.opts = opts;
        this.forPractice = forPractice;
        this.readOnly = readOnly;
        controlsOpts = new Hashtable();
        if (! readOnly)
            boolOptCheckboxes = new Hashtable();
        if ((gaName == null) && forPractice)
        {
            if (cli.numPracticeGames == 0)
                gaName = SOCPlayerClient.DEFAULT_PRACTICE_GAMENAME;
            else
                gaName = SOCPlayerClient.DEFAULT_PRACTICE_GAMENAME + " " + (1 + cli.numPracticeGames);
        }

        // same Frame setup as in SOCPlayerClient.main
        setBackground(new Color(Integer.parseInt("61AF71",16)));
        setForeground(Color.black);

        addKeyListener(this);
        initInterfaceElements(gaName);

        /**
         * complete - reset mouse cursor from hourglass to normal
         * (was set to hourglass before calling this constructor)
         */
        cli.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        cli.status.setText("");  // clear "Talking to server..."        
    }

    /**
     * Creates and shows a new NewGameOptionsFrame.
     * Once created, reset the mouse cursor from hourglass to normal, and clear main panel's status text.
     * See constructor for parameters.
     */
    public static void createAndShow
        (SOCPlayerClient cli, String gaName, Hashtable opts, boolean forPractice, boolean readOnly)
    {
        NewGameOptionsFrame ngof = new NewGameOptionsFrame(cli, gaName, opts, forPractice, readOnly);
        ngof.pack();
        ngof.show();
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

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        if (! readOnly)
        {
            msgText = new TextField();
            msgText.setEditable(false);
            msgText.setBackground(getBackground());
            gbl.setConstraints(msgText, gbc);
            bp.add(msgText);
        }

        /**
         * Interface setup: Game name
         */
        Label L;

        L = new Label("Game name");
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
            gameName.setEnabled(false);
        else
            gameName.addTextListener(this);    // Will enable buttons when field is not empty
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
            cancel = new Button("OK");
            cancel.setEnabled(true);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
        } else {
            cancel = new Button("Cancel");
            cancel.addKeyListener(this);  // for win32 keyboard-focus
            gbc.gridwidth = 2;
        }
        gbl.setConstraints(cancel, gbc);
        bp.add(cancel);
        cancel.addActionListener(this);
        
        if (! readOnly)
        {
            create = new Button("Create Game");
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
        add(bp, BorderLayout.CENTER);
    }

    private final static Color LABEL_TXT_COLOR = new Color(252, 251, 243); // off-white

    /**
     * Interface setup: Options. 
     * One row per option.
     * If options are null, put a label with {@link #TXT_SERVER_TOO_OLD}.
     */
    private void initInterface_Options(Panel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        Label L;

        if (opts == null)
        {
            L = new Label(TXT_SERVER_TOO_OLD);
            L.setForeground(LABEL_TXT_COLOR);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(L, gbc);
            bp.add(L);
            return;
        }

        // TODO sort options

        gbc.anchor = GridBagConstraints.WEST;
        for (Enumeration e = opts.keys(); e.hasMoreElements(); )
        {
            SOCGameOption op = (SOCGameOption) opts.get(e.nextElement());
            if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
                continue;  // <-- Must skip this one --

            switch (op.optType)  // OTYPE_*
            {
            case SOCGameOption.OTYPE_BOOL:
                Checkbox cb = new Checkbox();
                cb.setState(op.getBoolValue());
                // cb.addActionListener(this);
                initInterface_Opt1(op, cb, bp, gbl, gbc);
                break;

            case SOCGameOption.OTYPE_INT:
                // IntTextField or Choice (popup menu)
                initInterface_Opt1(op, initOption_int(op), bp, gbl, gbc);
                break;

            case SOCGameOption.OTYPE_INTBOOL:
                // Special handling: descriptive text may have
                // "#" as a placeholder for where textfield goes

                Checkbox cb2 = new Checkbox();
                controlsOpts.put(cb2, op);
                cb2.setState(op.getBoolValue());
                cb2.setEnabled(! readOnly);
                // cb2.addActionListener(this);
                gbc.gridwidth = 1;
                gbl.setConstraints(cb2, gbc);
                bp.add(cb2);

                final int placeholderIdx = op.optDesc.indexOf('#');
                Panel optp = new Panel();  // with FlowLayout
                try
                {
                    FlowLayout fl = (FlowLayout) (optp.getLayout());
                    fl.setAlignment(FlowLayout.LEFT);
                    fl.setVgap(0);
                    fl.setHgap(0);
                }
                catch (Throwable fle) {}

                if (placeholderIdx > 0)
                {
                    L = new Label(op.optDesc.substring(0, placeholderIdx - 1).trim());
                    L.setForeground(LABEL_TXT_COLOR);
                    optp.add(L);
                    if (! readOnly)
                    {
                        controlsOpts.put(L, op);
                        boolOptCheckboxes.put(op.optKey, cb2);
                        L.addMouseListener(this);
                    }
                }

                Component intbc = initOption_int(op);
                controlsOpts.put(intbc, op);
                intbc.setEnabled(! readOnly);
                optp.add(intbc);

                if (placeholderIdx + 1 < op.optDesc.length())
                {
                    L = new Label(op.optDesc.substring(placeholderIdx + 1).trim());
                    L.setForeground(LABEL_TXT_COLOR);
                    optp.add(L);
                    if (! readOnly)
                    {
                        controlsOpts.put(L, op);
                        boolOptCheckboxes.put(op.optKey, cb2);
                        L.addMouseListener(this);
                    }
                }

                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbl.setConstraints(optp, gbc);
                bp.add(optp);

                break;

            case SOCGameOption.OTYPE_ENUM:
                // Choice (popup menu)
                initInterface_Opt1(op, initOption_enum(op), bp, gbl, gbc);
                break;

            case SOCGameOption.OTYPE_STR:
            case SOCGameOption.OTYPE_STRHIDE:
                {
                    int txtwid = op.maxIntValue;
                    if (txtwid > 20)
                        txtwid = 20;
                    boolean doHide = (op.optType == SOCGameOption.OTYPE_STRHIDE);
                    String txtcontent = (doHide ? "" : op.getStringValue());
                    TextField txtc = new TextField(txtcontent, txtwid);
                    if (doHide)
                    {
                        if (SOCPlayerClient.isJavaOnOSX)
                            txtc.setEchoChar('\u2022');  // round bullet (option-8)
                        else
                            txtc.setEchoChar('*');
                    }
                    controlsOpts.put(txtc, op);
                    txtc.setEnabled(! readOnly);
                    // tf.addActionListener(this);
                    gbc.gridwidth = 1;
                    gbl.setConstraints(txtc, gbc);
                    bp.add(txtc);
                }

                gbc.gridwidth = GridBagConstraints.REMAINDER;
                L = new Label(op.optDesc);
                L.setForeground(LABEL_TXT_COLOR);
                gbl.setConstraints(L, gbc);
                bp.add(L);
                break;

                // default: unknown, see above
            }

        }  // for(opts)
    }

    /**
     * Add one GridBagLayout row with this game option (component and label).
     * @param op  Option data
     * @param oc  Component with option choices (popup menu, textfield, etc)
     * @param bp  Add to this panel
     * @param gbl Use this layout
     * @param gbc Use these constraints; gridwidth will be set to 1 and then REMAINDER
     */
    private void initInterface_Opt1(SOCGameOption op, Component oc,
            Panel bp, GridBagLayout gbl, GridBagConstraints gbc)
    {
        controlsOpts.put(oc, op);
        oc.setEnabled(! readOnly);
        gbc.gridwidth = 1;
        gbl.setConstraints(oc, gbc);
        bp.add(oc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        Label L = new Label(op.optDesc);
        L.setForeground(LABEL_TXT_COLOR);
        gbl.setConstraints(L, gbc);
        bp.add(L);

        if ((! readOnly) && (oc instanceof Checkbox))
        {
            controlsOpts.put(L, op);
            boolOptCheckboxes.put(op.optKey, oc);
            L.addMouseListener(this);
        }
    }

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
            c = new IntTextField(op.getIntValue(), 3); // TODO: width based on min/max's magnitude            
        } else {
            Choice ch = new Choice();
            for (int i = op.minIntValue; i <= op.maxIntValue; ++i)
                ch.add(Integer.toString(i));

            int defaultIdx = op.getIntValue() - op.minIntValue;
            if (defaultIdx > 0)
                ch.select(defaultIdx);
            c = ch;
        }
        return c;
    }

    /**
     * Create a popup menu for the choices of this enum.
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
        return ch;
    }

    /**
     * Show the window, and request focus on game name textfield.
     */
    public void show()
    {
        super.show();
        gameName.requestFocus();
    }

    /** React to button clicks */
    public void actionPerformed(ActionEvent ae)
    {
        try
        {
            
            Object src = ae.getSource();
            if (src == create)
            {
                // Ask client to set up and start a practice game
                clickCreate();
            }

            if (src == cancel)
            {
                clickCancel();
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
    private void clickCreate()
    {
        String gmName = gameName.getText().trim();
        if (gmName.length() == 0)
        {
            return;  // Should not happen (button disabled by TextListener)
        }

        boolean gameExists;
        if (forPractice)
            gameExists = (cl.practiceServer != null) && (-1 != cl.practiceServer.getGameState(gmName));
        else
            gameExists = (cl.serverGames != null) && cl.serverGames.isGame(gmName);
        if (gameExists)
        {
            NotifyDialog.createAndShow(cl, this, SOCStatusMessage.MSG_SV_NEWGAME_ALREADY_EXISTS, null, true);
            return;
        }

        if (cl.readValidNicknameAndPassword()
            && readOptsValuesFromControls())
        {
            cl.askStartGameWithOptions(gmName, forPractice, opts);
        } else {
            return;  // TODO print an err msg
        }

        dispose();
    }

    /** Dismiss the panel */
    private void clickCancel()
    {
        dispose();
    }

    /**
     * Read option values from controls, as prep to request the new game.
     * @return true if all were read OK, false if a problem (such as NumberFormatException)
     */
    private boolean readOptsValuesFromControls()
    {
        if (readOnly)
            return false;  // shouldn't be called in the first place
        boolean allOK = true;
        for (Enumeration e = controlsOpts.keys(); e.hasMoreElements(); )
        {
            Object ctrl = e.nextElement();
            SOCGameOption op = (SOCGameOption) controlsOpts.get(ctrl);
            
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
                    op.setStringValue(txt);
                } else {
                    try   // OTYPE_INT, OTYPE_INTBOOL
                    {
                        op.setIntValue(Integer.parseInt(txt));
                    } catch (NumberFormatException ex)
                    {
                        allOK = false;
                    }
                }
            }
            else if (ctrl instanceof Choice)
            {
                // this works with OTYPE_INT, OTYPE_INTBOOL, OTYPE_ENUM
                int chIdx = ((Choice) ctrl).getSelectedIndex();  // 0 to n-1
                if (chIdx != -1)
                    op.setIntValue(chIdx + op.minIntValue);
                else
                    allOK = false;
            }

        }  // for(opts)

        return allOK;
    }

    /** Handle Enter or Esc key */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        try
        {
            switch (e.getKeyCode())
            {
            case KeyEvent.VK_ENTER:
                clickCreate();
    
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
     * @param e textevent from {@link #gameName}
     * @since 1.1.07
     */
    public void textValueChanged(TextEvent e)
    {
        if (readOnly)
            return;
        boolean notEmpty = (gameName.getText().trim().length() > 0);
        if (notEmpty != create.isEnabled())
        {
            create.setEnabled(notEmpty);
        }
    }

    /**
     * A textfield that accepts only nonnegative-integer characters.
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    public static class IntTextField extends TextField implements KeyListener
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
            final char c = e.getKeyChar();
            if (c == KeyEvent.CHAR_UNDEFINED)  // ctrl characters, arrows, etc
                return;
            if (! Character.isDigit(c))
                e.consume();  // ignore non-digits
        }

    }  // public static class IntTextField

    /** when an option with a boolValue's label is clicked, toggle its checkbox */
    public void mouseClicked(MouseEvent e)
    {
        SOCGameOption opt = (SOCGameOption) controlsOpts.get(e.getSource());
        if (opt == null)
            return;
        Checkbox cb = (Checkbox) boolOptCheckboxes.get(opt.optKey);
        if (cb == null)
            return;
        cb.setState(! cb.getState());
    }

    /** required stub for MouseListener */
    public void mouseEntered(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseExited(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mousePressed(MouseEvent e) {}

    /** required stub for MouseListener */
    public void mouseReleased(MouseEvent e) {}

}  // public class NewGameOptionsFrame
