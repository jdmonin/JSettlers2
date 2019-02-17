/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007-2010,2013-2014,2016-2017,2019 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.client;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/**
 * This is the generic modal dialog to ask players a two- or three-choice question;
 * to present a one-button message, see {@link NotifyDialog}.
 *<P>
 * <b>Since 1.1.07:</b>
 * If dialog text contains \n, multiple lines will be created.
 * If title bar text contains \n, only its first line (before \n) is used.
 *<P>
 * To react to button presses, override the abstract methods
 * {@link #button1Chosen()}, {@link #button2Chosen()},
 * {@link #windowCloseChosen()}, and (for a three-choice
 * question) override {@link #button3Chosen()}.
 *<P>
 * If {@link #setNonBlockingDialogDismissListener(soc.client.PlayerClientListener.NonBlockingDialogDismissListener) setNonBlockingDialogDismissListener(..)}
 * was called, AskDialog will fire that listener callback after {@link #button1Chosen()}, {@link #windowCloseChosen()},
 * or similar methods.
 *<P>
 * For convenience with {@link java.awt.EventQueue#invokeLater(Runnable)},
 * contains a {@link #run()} method which calls {@link #setVisible(boolean) setVisible(true)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
@SuppressWarnings("serial")
public abstract class AskDialog extends JDialog
    implements ActionListener, WindowListener, KeyListener, MouseListener, Runnable
{
    /**
     * Border width around {@link #msg}.
     */
    private static final int MSG_BORDER = 5;

    /**
     * Player client's main display; passed to constructor, not null.
     * Used for actions in subclasses when dialog buttons are chosen.
     *<P>
     * Before v2.0.00 this field was {@code pcli}.
     *
     * @see #pi
     */
    protected final MainDisplay md;

    /**
     * Player interface; passed to constructor; may be null if the
     * question is related to the entire client, and not to a specific game
     * @see #md
     */
    protected SOCPlayerInterface pi;

    /**
     * Optional listener. Fired from multiple places via {@link #callbackDiaDismissListener(boolean)}.
     * @since 2.0.00
     */
    private PlayerClientListener.NonBlockingDialogDismissListener nbddListener;

    /** Prompt message Label, or Panel for multi-line prompt ({@link #isMsgMultiLine}), or null */
    protected JComponent msg;

    /**
     * Button area, for resizing multi-line dialog height in {@link #checkSizeAndFocus()}.
     * @since 2.0.00
     */
    protected final JPanel pBtns;

    /**
     * Is the prompt multi-line?
     * If so, assume it might be larger than usual, and adjust height
     * when {@link #checkSizeAndFocus()} is called.
     * @since 2.0.00
     */
    protected final boolean isMsgMultiLine;

    /**
     * Has {@link #checkSizeAndFocus()} already set the size
     * of a multi-line dialog?
     * @since 2.0.00
     */
    private boolean isSizeCheckedAlready;

    /** Button for first choice.
     *
     * @see #button1Chosen()
     */
    protected final JButton choice1But;

    /** Button for second choice, or null.
     *
     * @see #button2Chosen()
     */
    protected final JButton choice2But;

    /** Optional button for third choice, or null.
     *
     * @see #button3Chosen()
     */
    protected JButton choice3But;

    /** Default button (0 for none, or button 1, 2, or 3) */
    protected final int choiceDefault;

    /** Desired size (visible size inside of insets) **/
    protected int wantW, wantH;

    /** Padding beyond desired size; not known until windowOpened() **/
    protected int padW, padH;

    /** Have we requested focus yet? */
    protected boolean didReqFocus;

    /**
     * Creates a new AskDialog with two buttons, about a specific game.
     *
     * @param cli      Player client interface; will be used for actions in subclasses when dialog buttons are chosen.
     * @param gamePI   Current game's player interface;
     *                 Cannot be null, use the other constructor if not asking
     *                 about a specific game.
     * @param titlebar Title bar text; if text contains \n, only the portion before \n is used.
     *                 If begins with \n, title is "JSettlers" instead.
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param default1 First choice is default
     * @param default2 Second choice is default
     *
     * @throws IllegalArgumentException If both default1 and default2 are true,
     *    or if any of these is null: cli, gamePI, prompt, choice1, choice2.
     */
    public AskDialog(MainDisplay cli, SOCPlayerInterface gamePI,
        String titlebar, String prompt, String choice1, String choice2,
        boolean default1, boolean default2)
        throws IllegalArgumentException
    {
        this(cli, (Frame) gamePI,
            titlebar, prompt, choice1, choice2,
            default1, default2);
        if (gamePI != null)
            pi = gamePI;
        else
            throw new IllegalArgumentException("gamePI cannot be null");
    }

    /**
     * Creates a new AskDialog with one button, not about a specific game.
     * For use by {@link NotifyDialog}.
     * parentFr cannot be null; use {@link #getParentFrame(Component)} to find it.
     * @since 1.1.06
     */
    protected AskDialog(MainDisplay cli, Frame parentFr,
        String titlebar, String prompt, String btnText,
        boolean hasDefault)
        throws IllegalArgumentException
    {
        this (cli,
              parentFr,
              titlebar, prompt,
              btnText, null, null,
              (hasDefault ? 1 : 0)
              );
    }

    /**
     * Creates a new AskDialog with two buttons, not about a specific game.
     *
     * @param cli      Player client interface; will be used for actions in subclasses when dialog buttons are chosen
     * @param parentFr SOCPlayerClient or other parent frame
     * @param titlebar Title bar text; if text contains \n, only the portion before \n is used.
     *            If begins with \n, title is "JSettlers" instead.
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param default1 First choice is default
     * @param default2 Second choice is default
     *
     * @throws IllegalArgumentException If both default1 and default2 are true,
     *    or if any of these is null: cli, gamePI, prompt, choice1, choice2.
     */
    public AskDialog(MainDisplay cli, Frame parentFr,
        String titlebar, String prompt, String choice1, String choice2,
        boolean default1, boolean default2)
        throws IllegalArgumentException
    {
        this (cli, parentFr, titlebar, prompt,
              choice1, choice2, null,
              (default1 ? 1 : (default2 ? 2 : 0))
              );
        if (default1 && default2)
            throw new IllegalArgumentException("Cannot have 2 default buttons");
    }

    /**
     * Creates a new AskDialog with three buttons, about a specific game.
     * Also can create with two.
     *
     * @param cli      Player client interface; will be used for actions in subclasses when dialog buttons are chosen
     * @param gamePI   Current game's player interface
     * @param titlebar Title bar text; if text contains \n, only the portion before \n is used.
     *            If begins with \n, title is "JSettlers" instead.
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param choice3  Third choice button text, or null if 2 buttons
     * @param defaultChoice  Default button (1, 2, 3, or 0 for none)
     *
     * @throws IllegalArgumentException If defaultChoice out of range 0..3,
     *    or if any of these is null: cli, gamePI, prompt, choice1, choice2,
     *    or if choice3 is null and defaultChoice is 3.
     */
    public AskDialog(MainDisplay cli, SOCPlayerInterface gamePI,
        String titlebar, String prompt, String choice1, String choice2, String choice3,
        int defaultChoice)
        throws IllegalArgumentException
    {
        this(cli, (Frame) gamePI,
             titlebar, prompt, choice1, choice2, choice3,
             defaultChoice);
        if (gamePI != null)
            pi = gamePI;
        else
            throw new IllegalArgumentException("gamePI cannot be null");
    }

    /**
     * Creates a new AskDialog with one, two, or three buttons, not about
     * a specific game.
     *
     * @param md       Player client's main display; will be used for actions in subclasses when dialog buttons are chosen
     * @param parentFr SOCPlayerClient or other parent frame
     * @param titlebar Title bar text; if text contains \n, only the portion before \n is used.
     *              If begins with \n, title is "JSettlers" instead.
     * @param prompt   Prompting text shown above buttons, or null.
     *              Can be multi-line, use "\n" within your string to separate them.
     * @param choice1  First choice button text
     * @param choice2  Second choice button text, or null if 1 button
     * @param choice3  Third choice button text, or null if 1 or 2 buttons
     * @param defaultChoice  Default button (1, 2, 3, or 0 for none)
     *
     * @throws IllegalArgumentException If defaultChoice out of range 0..3,
     *    or if any of these is null: cli, parentFr, prompt, choice1, choice2,
     *    or if choice3 is null and defaultChoice is 3.
     */
    public AskDialog(MainDisplay md, Frame parentFr,
        String titlebar, String prompt, String choice1, String choice2, String choice3,
        int defaultChoice)
        throws IllegalArgumentException
    {
        super(parentFr, firstLine(titlebar), true);

        if (md == null)
            throw new IllegalArgumentException("md cannot be null");
        if (parentFr == null)
            throw new IllegalArgumentException("parentFr cannot be null");
        if (choice1 == null)
            throw new IllegalArgumentException("choice1 cannot be null");
        if ((defaultChoice < 0) || (defaultChoice > 3))
            throw new IllegalArgumentException("defaultChoice out of range: " + defaultChoice);
        if ((choice3 == null) && (defaultChoice == 3))
            throw new IllegalArgumentException("defaultChoice cannot be 3 when choice3 null");
        if ((choice2 == null) && (defaultChoice > 1))
            throw new IllegalArgumentException("defaultChoice must be 1 when choice2 null");

        this.md = md;
        pi = null;
        setBackground(SOCPlayerInterface.DIALOG_BG_GOLDENROD);
        setForeground(Color.BLACK);
        setFont(new Font("Dialog", Font.PLAIN, 12));

        getRootPane().setBackground(null);  // inherit
        getContentPane().setBackground(null);

        choice1But = new JButton(choice1);
        choice1But.setBackground(null);  // needed on win32 to avoid gray corners
        if (choice2 != null)
        {
            choice2But = new JButton(choice2);
            choice2But.setBackground(null);
            if (choice3 != null)
            {
                choice3But = new JButton(choice3);
                choice3But.setBackground(null);
            } else {
                choice3But = null;
            }
        } else {
            choice2But = null;
            choice3But = null;
        }
        choiceDefault = defaultChoice;
        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

        int promptMultiLine = prompt.indexOf('\n');
        if (promptMultiLine == 0)
        {
            // In some calls from subclasses, \n as first character has
            // side effect of not using prompt as window title
            // (this constructor is called with titlebar == prompt).
            // Remove leading \n and check if there are any further newlines:
            prompt = prompt.substring(1);
            promptMultiLine = prompt.indexOf('\n');
        }
        int promptMaxWid;
        int promptLines = 1;
        if (promptMultiLine == -1)
        {
            isMsgMultiLine = false;
            msg = new JLabel(prompt, SwingConstants.CENTER);
            msg.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(msg);
            promptMaxWid = getFontMetrics(msg.getFont()).stringWidth(prompt);
        } else {
            promptMaxWid = 0;
            try
            {
                // Try to get maximum width of each line, in case they're all short,
                // and also use JTextArea to do automatic wrapping of long lines.

                StringTokenizer st = new StringTokenizer(prompt, "\n");
                Font ourfont = getFont();
                FontMetrics fm = ourfont != null ? getFontMetrics(getFont()) : null;
                if (fm != null)
                {
                    while (st.hasMoreTokens())
                    {
                        final String promptline = st.nextToken();
                        int mwid = fm.stringWidth(promptline);
                        if (mwid > promptMaxWid)
                            promptMaxWid = mwid;
                    }
                }

                JTextArea pmsg = new JTextArea(prompt);
                pmsg.setEditable(false);
                // override fixed-width font in JFrame on win32
                if (ourfont != null)
                    pmsg.setFont(ourfont);
                else
                    pmsg.setFont(new Font("Dialog", Font.PLAIN, 12));
                pmsg.setLineWrap(true);
                pmsg.setWrapStyleWord(true);
                pmsg.setBackground(getBackground());  // avoid white background
                pmsg.setForeground(null);
                JScrollPane pScroll = new JScrollPane(pmsg);
                pScroll.setOpaque(false);
                msg = pScroll;
                add(pScroll);

                final int maxWid80pct = (4 * parentFr.getWidth()) / 5;
                if (promptMaxWid > maxWid80pct)
                    promptMaxWid = maxWid80pct;  // text must wrap, it's too wide otherwise

            } catch (Throwable t) {
                // fallback to 1 long line
                msg = new JLabel(prompt, SwingConstants.CENTER);
                msg.setAlignmentX(Component.CENTER_ALIGNMENT);
                add(msg);
                promptMaxWid = getFontMetrics(msg.getFont()).stringWidth(prompt);
                promptMultiLine = -1;  // force msgIsMultiLine to be false
            }
            isMsgMultiLine = (promptMultiLine != -1);
        }
        msg.setBorder(BorderFactory.createEmptyBorder(MSG_BORDER, MSG_BORDER, MSG_BORDER, MSG_BORDER));

        wantW = (2 * MSG_BORDER) + promptMaxWid;
        if (wantW < 280)
            wantW = 280;
        if ((choice3 != null) && (wantW < (280+80)))
            wantW = (280 + 80);
        wantH = 41 + (promptLines * getFontMetrics(msg.getFont()).getHeight());  // includes assumed button height
        padW = 0;  // Won't be able to call getInsets and know the values, until windowOpened()
        padH = 0;
        if (isMsgMultiLine)
        {
            // to be adjusted in windowOpened()
            setSize(wantW + 6, wantH + 20);
        }
        setLocationRelativeTo(parentFr);

        pBtns = new JPanel();
        pBtns.setOpaque(true);
        pBtns.setBackground(null);  // avoid gray bg on win32
        pBtns.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 0));  // horiz border 3 pixels
        final int pbboarder = ColorSquare.HEIGHT / 2;
        pBtns.setBorder
            (BorderFactory.createEmptyBorder(pbboarder, pbboarder, pbboarder, pbboarder));

        pBtns.add(choice1But);
        choice1But.addActionListener(this);

        if (choice2But != null)
        {
            pBtns.add(choice2But);
            choice2But.addActionListener(this);

            if (choice3But != null)
            {
                pBtns.add(choice3But);
                choice3But.addActionListener(this);
            }
        }

        add(pBtns);

        // Now that we've added buttons to the dialog layout,
        // we can get their font and adjust style of default button.
        final JButton dfltB;
        switch (choiceDefault)
        {
        case 1:
            dfltB = choice1But;  break;
        case 2:
            dfltB = choice2But;  break;
        case 3:
            dfltB = choice3But;  break;
        default:
            // 0, no button is default
            dfltB = null;
        }
        getRootPane().setDefaultButton(dfltB);

        addWindowListener(this);  // To handle close-button
        addMouseListener(this);   // for mouseEntered size-check
        addKeyListener(this);     // To handle Enter, Esc keys.
        choice1But.addKeyListener(this);  // (win32: Keyboard focus will be on these buttons)
        if (choice2But != null)
        {
            choice2But.addKeyListener(this);
            if (choice3But != null)
                choice3But.addKeyListener(this);
        }

        if (! isMsgMultiLine)
        {
            validate();
            pack();
        }
    }

    /**
     * Adjust size (vs insets) and set focus to the default button (if any).
     */
    protected void checkSizeAndFocus()
    {
        // Can't call getInsets and know the values, until windowOpened().
        padW = getInsets().left + getInsets().right + 6;
        padH = getInsets().top + getInsets().bottom;
        if ((padW > 0) || (padH > 0))
        {
            if (isMsgMultiLine && ! isSizeCheckedAlready)
            {
                wantH = 12 + msg.getPreferredSize().height + pBtns.getPreferredSize().height;
                setSize(wantW + padW + 20, wantH + padH);
                validate();
                isSizeCheckedAlready = true;
            }
        }

        if (didReqFocus)
            return;

        switch (choiceDefault)
        {
        case 1:
            choice1But.requestFocus();
            break;
        case 2:
            choice2But.requestFocus();
            break;
        case 3:
            choice3But.requestFocus();
            break;
        }
        didReqFocus = true;
    }

    /**
     * A button has been chosen by the user.
     * Call button1Chosen, button2Chosen or button3chosen, and dispose of this dialog.
     */
    public void actionPerformed(ActionEvent e)
    {
        try
        {
            Object target = e.getSource();

            if (target == choice1But)
            {
                dispose();
                button1Chosen();  // <--- Callback for button 1 ---
            }
            else if (target == choice2But)
            {
                dispose();
                button2Chosen();  // <--- Callback for button 2 ---
            }
            else if (target == choice3But)
            {
                dispose();
                button3Chosen();  // <--- Callback for button 3 ---
            }

            callbackDiaDismissListener(false);
        } catch (Throwable thr) {
            if (pi != null)
            {
                pi.chatPrintStackTrace(thr);
            } else {
                System.err.println("-- Exception in AskDialog.actionPerformed: " + thr.toString() + " --");
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
    }

    /**
     * Button 1 has been chosen by the user. React accordingly.
     * actionPerformed has already called dialog.dispose().
     */
    public abstract void button1Chosen();

    /**
     * Button 2 has been chosen by the user. React accordingly.
     * actionPerformed has already called dialog.dispose().
     */
    public abstract void button2Chosen();

    /**
     * The optional button 3 has been chosen by the user. React accordingly.
     * actionPerformed has already called dialog.dispose().
     * Please override this empty stub if you have a third button.
     */
    public void button3Chosen() { }

    /**
     * The dialog window was closed by the user, or ESC was pressed. React accordingly.
     * AskDialog has already called dialog.dispose().
     */
    public abstract void windowCloseChosen();

    /**
     * Dialog close requested by user. Dispose and call {@link #windowCloseChosen()}.
     */
    public void windowClosing(WindowEvent e)
    {
        dispose();
        windowCloseChosen();  // <--- Callback for close/ESC ---
        callbackDiaDismissListener(true);
    }

    /** Window is appearing - check the size and the default button keyboard focus */
    public void windowOpened(WindowEvent e)
    {
        checkSizeAndFocus();
    }

    /** Stub required by WindowListener */
    public void windowActivated(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowClosed(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowDeactivated(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowDeiconified(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowIconified(WindowEvent e) { }

    /** Handle Enter or Esc key */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            if (choiceDefault != 0)
            {
                dispose();
                e.consume();
                switch (choiceDefault)
                {
                case 1:
                    button1Chosen();  // <--- Callback for button 1 ---
                    break;
                case 2:
                    button2Chosen();  // <--- Callback for button 2 ---
                    break;
                case 3:
                    button3Chosen();  // <--- Callback for button 3 ---
                    break;
                }  // switch

                callbackDiaDismissListener(false);
            }
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            dispose();
            e.consume();
            windowCloseChosen();  // <--- Callback for close/ESC ---
            callbackDiaDismissListener(true);
            break;
        }
    }

    /** Stub required by KeyListener */
    public void keyReleased(KeyEvent arg0) { }

    /** Stub required by KeyListener */
    public void keyTyped(KeyEvent arg0) { }

    /** Check versus minimum size: calls (@link #checkSizeAndFocus()} */
    public void mouseEntered(MouseEvent e)
    {
        checkSizeAndFocus();  // vs. minimum size
    }

    /** Stub required by MouseListener */
    public void mouseExited(MouseEvent e) {}

    /** Stub required by MouseListener */
    public void mouseClicked(MouseEvent e) {}

    /** Stub required by MouseListener */
    public void mousePressed(MouseEvent e) {}

    /** Stub required by MouseListener */
    public void mouseReleased(MouseEvent e) {}

    /**
     * Gets the top-level frame of c.
     * All windows and applets should have one.
     * @param c The Component.
     * @return The parent-frame
     * @since 1.1.06
     * @throws IllegalStateException if we find a null parent
     *         before a Frame, or if any parent == itself
     */
    public static Frame getParentFrame(Component c)
        throws IllegalStateException
    {
        String throwMsg = null;
        Component last;
        while (! (c instanceof Frame))
        {
            last = c;
            c = c.getParent();
            if (c == null)
                throwMsg = "Assert failed, parent should not be null; last: ";
            else if (c == last)
                throwMsg = "Assert failed, parent == itself: ";
            if (throwMsg != null)
                throw new IllegalStateException
                    (throwMsg + last.getClass().getName() + " " + last);
        }
        return (Frame) c;
    }

    /**
     * Extract the first line (up to \n) if {@code f} is multi-line.
     * Used for setting the dialog title.
     * @param f  A string, possibly containing \n.
     *     See return javadoc for behavior if {@code f} starts with \n.
     * @return  {@code f}'s first line, or all of {@code f} if no \n;
     *     if {@code f} starts with \n, returns "JSettlers" to avoid an empty title.
     * @since 1.1.07
     */
    public static String firstLine(String f)
    {
        final int i = f.indexOf("\n");

        if (i == -1)
            return f;
        else if (i == 0)  // avoid blank title: added in v1.1.20
            return "JSettlers";
        else
            return f.substring(0, i);
    }

    /**
     * Call our optional {@link PlayerClientListener.NonBlockingDialogDismissListener listener},
     * if any. Call this after the button/cancel callback. Uses {@link EventQueue#invokeLater(Runnable)}
     * to ensure the callback is on the proper thread.
     * @param wasCanceled  Detail param to pass to
     *     {@link PlayerClientListener.NonBlockingDialogDismissListener#dialogDismissed(Object, boolean)}
     * @since 2.0.00
     */
    protected final void callbackDiaDismissListener(final boolean wasCanceled)
    {
        if (nbddListener != null)
            EventQueue.invokeLater(new Runnable()
            {
                public void run() { nbddListener.dialogDismissed(AskDialog.this, wasCanceled); }
            });
    }

    /**
     * Set or clear the optional {@link PlayerClientListener.NonBlockingDialogDismissListener listener}
     * for when this dialog is no longer visible.
     * @param li  Listener, or {@code null} to clear
     */
    public void setNonBlockingDialogDismissListener
        (PlayerClientListener.NonBlockingDialogDismissListener li)
    {
        nbddListener = li;
    }

    /**
     * In the AWT event thread, show ourselves. Do not call directly;
     * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
     * This method just calls {@link #setVisible(boolean) setVisible(true)}, and if any error occurs
     * calls {@link Throwable#printStackTrace()}.
     * @since 2.0.00
     */
    public void run()
    {
        try
        {
            setVisible(true);
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }

}
