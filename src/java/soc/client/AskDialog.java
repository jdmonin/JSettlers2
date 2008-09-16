/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007-2008 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;


/**
 * This is the generic dialog to ask players a two- or three-choice question.
 *<P>
 * To react to button presses, override the abstract methods
 * {@link #button1Chosen()}, {@link #button2Chosen()}, 
 * {@link #windowCloseChosen()}, and (for a three-choice
 * question) override {@link #button3Chosen()}.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public abstract class AskDialog extends Dialog
    implements ActionListener, WindowListener, KeyListener, MouseListener
{
    /** Player client; passed to constructor, not null */
    protected final SOCPlayerClient pcli;

    /**
     * Player interface; passed to constructor; may be null if the
     * question is related to the entire client, and not to a specific game
     */
    protected SOCPlayerInterface pi;

    /** Prompt message, or null */
    protected final Label msg;

    /** Button for first choice.
     *
     * @see #button1Chosen()
     */
    protected final Button choice1But;

    /** Button for second choice.
     *
     * @see #button2Chosen()
     */
    protected final Button choice2But;

    /** Optional button for third choice, or null.
     *
     * @see #button3Chosen()
     */
    protected Button choice3But;

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
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     * @param titlebar Title bar text
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param default1 First choice is default
     * @param default2 Second choice is default
     *
     * @throws IllegalArgumentException If both default1 and default2 are true,
     *    or if any of these is null: cli, gamePI, prompt, choice1, choice2.
     */
    public AskDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI,
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
     * Creates a new AskDialog with two buttons, not about a specific game.
     *
     * @param cli      Player client interface
     * @param parentFr SOCPlayerClient or other parent frame
     * @param titlebar Title bar text
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param default1 First choice is default
     * @param default2 Second choice is default
     *
     * @throws IllegalArgumentException If both default1 and default2 are true,
     *    or if any of these is null: cli, gamePI, prompt, choice1, choice2.
     */
    public AskDialog(SOCPlayerClient cli, Frame parentFr,
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
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     * @param titlebar Title bar text
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
    public AskDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI,
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
     * Creates a new AskDialog with three buttons, not about a specific game.
     * Also can create with two.
     *
     * @param cli      Player client interface
     * @param parentFr SOCPlayerClient or other parent frame
     * @param titlebar Title bar text
     * @param prompt   Prompting text shown above buttons, or null
     * @param choice1  First choice button text
     * @param choice2  Second choice button text
     * @param choice3  Third choice button text, or null if 2 buttons
     * @param defaultChoice  Default button (1, 2, 3, or 0 for none)
     *
     * @throws IllegalArgumentException If defaultChoice out of range 0..3,
     *    or if any of these is null: cli, parentFr, prompt, choice1, choice2,
     *    or if choice3 is null and defaultChoice is 3.
     */
    public AskDialog(SOCPlayerClient cli, Frame parentFr,
        String titlebar, String prompt, String choice1, String choice2, String choice3,
        int defaultChoice)
        throws IllegalArgumentException
    {
        super(parentFr, titlebar, true);

        if (cli == null)
            throw new IllegalArgumentException("cli cannot be null");
        if (parentFr == null)
            throw new IllegalArgumentException("parentFr cannot be null");
    	if (choice1 == null)
            throw new IllegalArgumentException("choice1 cannot be null");
    	if (choice2 == null)
            throw new IllegalArgumentException("choice2 cannot be null");
        if ((defaultChoice < 0) || (defaultChoice > 3)) 
            throw new IllegalArgumentException("defaultChoice out of range: " + defaultChoice);
        if ((choice3 == null) && (defaultChoice == 3))
            throw new IllegalArgumentException("defaultChoice cannot be 3 when choice3 null");

        pcli = cli;
        pi = null;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("Dialog", Font.PLAIN, 12));

        choice1But = new Button(choice1);
        choice2But = new Button(choice2);
        if (choice3 != null)
            choice3But = new Button(choice3);
        choiceDefault = defaultChoice;
        setLayout (new BorderLayout());

        msg = new Label(prompt, Label.CENTER);
        add(msg, BorderLayout.NORTH);

        wantW = 6 + getFontMetrics(msg.getFont()).stringWidth(prompt);
        if (wantW < 280)
            wantW = 280;
        if ((choice3 != null) && (wantW < (280+80)))
            wantW = (280 + 80);
        wantH = 40 + 2 * ColorSquare.HEIGHT;
        padW = 0;  // Won't be able to call getInsets and know the values, until windowOpened()
        padH = 0;
        setSize(wantW + 6, wantH + 20);
        setLocation(150, 100);

        Panel pBtns = new Panel();
        pBtns.setLayout(new FlowLayout(FlowLayout.CENTER));

        pBtns.add(choice1But);
        choice1But.addActionListener(this);

        pBtns.add(choice2But);
        choice2But.addActionListener(this);

        if (choice3But != null)
        {
            pBtns.add(choice3But);
            choice3But.addActionListener(this);            
        }

        add(pBtns, BorderLayout.CENTER);

        // Now that we've added buttons to the dialog layout,
        // we can get their font and adjust style of default button.
        switch (choiceDefault)
        {
        case 1:
            styleAsDefault(choice1But);
            break;
        case 2:
            styleAsDefault(choice2But);
            break;
        case 3:
            styleAsDefault(choice3But);
            break;
        default:
            // 0, no button is default
        }

        addWindowListener(this);  // To handle close-button
        addMouseListener(this);   // for mouseEntered size-check
        addKeyListener(this);     // To handle Enter, Esc keys.
        choice1But.addKeyListener(this);  // (win32: Keyboard focus will be on these buttons)
        choice2But.addKeyListener(this);
        if (choice3But != null)
            choice3But.addKeyListener(this);
    }

    /**
     * Adjust size (vs insets) and set focus to the default button (if any).
     */
    protected void checkSizeAndFocus()
    {
        // Can't call getInsets and know the values, until windowOpened().
        padW = getInsets().left + getInsets().right;
        padH = getInsets().top + getInsets().bottom;
        if ((padW > 0) || (padH > 0))
        {
            setSize(wantW + padW, wantH + padH);
            validate();
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
     * Since we can't designate as default visually through the standard AWT API,
     * try to bold the button text or set its color to white.
     *
     * @param b  Button to style visually as default.  Please add button to
     *           dialog layout before calling, so we can query the font.
     */
    public static void styleAsDefault(Button b)
    {
        try
        {
            Font bf = b.getFont();            
            if (bf == null)
                bf = new Font("Dialog", Font.BOLD, 12);
            else
                bf = bf.deriveFont(Font.BOLD);
            b.setFont(bf);
        }
        catch (Throwable th)
        {
            // If we can't do that, try to mark via
            // background color change instead
            try
            {
                b.setBackground(Color.WHITE);
            }
            catch (Throwable th2)
            {}
        }
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
     * Dialog close requested by user. Dispose and call windowCloseChosen.
     */
    public void windowClosing(WindowEvent e)
    {
        dispose();
        windowCloseChosen();  // <--- Callback for close/ESC ---
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
            }
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            dispose();
            e.consume();
            windowCloseChosen();  // <--- Callback for close/ESC ---
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

}
