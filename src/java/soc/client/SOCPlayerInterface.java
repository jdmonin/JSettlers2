/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D. Monin <jeremy@nand.net>
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

import soc.debug.D;  // JM

import soc.game.SOCGame;
import soc.game.SOCPlayer;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import java.io.PrintWriter;  // For chatPrintStackTrace
import java.io.StringWriter;

/**
 * Window with interface for a player in one game of Settlers of Catan.
 * Contains {@link SOCBoardPanel board}, client's and other players' {@link SOCHandPanel hands},
 * chat interface, game message window, and the {@link SOCBuildingPanel building/buying panel}.
 *<P>
 * A separate {@link SOCPlayerClient} window holds the list of current games and channels.
 *
 * @author Robert S. Thomas
 */
public class SOCPlayerInterface extends Frame implements ActionListener
{
    /**
     * the board display
     */
    protected SOCBoardPanel boardPanel;

    /**
     * Is the boardpanel stretched beyond normal size in {@link #doLayout()}?
     * @see SOCBoardPanel#isScaled()
     */
    protected boolean boardIsScaled;

    /**
     * For perf/display-bugs during component layout (OSX firefox),
     * show only background color in {@link #update(Graphics)} when true.
     * @since 1.1.06
     */
    private boolean layoutNotReadyYet;

    /**
     * where the player types in text
     */
    protected TextField textInput;

    /**
     * Not yet typed-in; display prompt message.
     *
     * @see #textInput
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected boolean textInputIsInitial;

    /**
     * At least one text chat line has been sent by the player.
     * Don't show the initial prompt message if the text field
     * becomes blank again.
     *
     * @see #textInput
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected boolean textInputHasSent;

    /**
     * Number of change-of-turns during game, after which
     * the initial prompt message fades to light grey.
     *
     * @see #textInput
     * @see #textInputGreyCountFrom
     */
    protected int textInputGreyCountdown;
    
    /**
     * Initial value (20 turns) for textInputGreyCountdown
     *
     * @see #textInputGreyCountdown
     */
    protected static int textInputGreyCountFrom = 20;

    /**
     * Not yet typed-in; display prompt message.
     *
     * @see #textInput
     */
    public static final String TEXTINPUT_INITIAL_PROMPT_MSG
        = "Type here to chat.";

    /** Titlebar text for game in progress */
    public static final String TITLEBAR_GAME
        = "Settlers of Catan Game: ";

    /** Titlebar text for game when over */
    public static final String TITLEBAR_GAME_OVER
        = "Settlers of Catan Game Over: ";

    /**
     * Used for responding to textfield changes by setting/clearing prompt message.
     *
     * @see #textInput
     */
    protected SOCPITextfieldListener textInputListener;

    /**
     * where text is displayed
     */
    protected SnippingTextArea textDisplay;

    /**
     * where chat text is displayed
     */
    protected SnippingTextArea chatDisplay;

    /**
     * interface for building pieces
     */
    protected SOCBuildingPanel buildingPanel;

    /**
     * the display for the players' hands
     */
    protected SOCHandPanel[] hands;
    
    /** 
     * Tracks our own hand within hands[], if we are
     * active in a game.  Null otherwise.
     * Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     */
    protected SOCHandPanel clientHand;

    /**
     * Player ID of clientHand, or -1.
     * Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     */
    private int clientHandPlayerNum;

    /**
     * the player colors. Indexes from 0 to {@link SOCGame#MAXPLAYERS} - 1.
     */
    protected Color[] playerColors, playerColorsGhost;

    /**
     * the client that spawned us
     */
    protected SOCPlayerClient client;

    /**
     * the game associated with this interface
     */
    protected SOCGame game;

    /**
     * Flag to ensure interface is updated, when the first actual
     * turn begins (state changes from {@link SOCGame#START2B}
     * to {@link SOCGame#PLAY}).
     * Initially set in {@link #startGame()}.
     * Checked/cleared in {@link #updateAtGameState()};
     */
    protected boolean gameIsStarting;

    /**
     * this other player has requested a board reset; voting is under way.
     * Null if no board reset vote is under way.
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     */
    protected SOCHandPanel boardResetRequester;

    /**
     * Board reset voting: If voting is active and we haven't yet voted,
     * track our dialog; this lets us dispose of it if voting is cancelled.
     */
    protected ResetBoardVoteDialog boardResetVoteDia;

    /** Is one or more handpanel (of other players) showing a "Discarding..." message? */
    private boolean showingPlayerDiscards;

    /**
     * Synchronize access to {@link #showingPlayerDiscards}
     * and {@link #showingPlayerDiscardsTask}
     */
    private Object showingPlayerDiscards_lock;

    /** May be null if not active. @see #showingPlayerDiscards */
    private SOCPIDiscardMsgTask showingPlayerDiscardsTask;

    /**
     * number of columns in the text output area
     */
    protected int ncols;

    /**
     * width of text output area in pixels
     */
    protected int npix;

    /**
     * To reduce text clutter: server has just sent a dice result message.
     * If the next text message from server is the roll,
     *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
     *   with:    * It's Player's turn to roll. Rolled a 9.
     *<P>
     * Set to 0 at most times.
     * Set to the roll result when roll text is expected.
     * Will be cleared to 0 in {@link #print(String)}.
     * Whenever this field is nonzero, textmessages from the server
     * will be scanned for " rolled a ".
     */
    protected int textDisplayRollExpected;
    
    /**
     * the dialog for getting what resources the player wants to discard
     */
    protected SOCDiscardDialog discardDialog;

    /**
     * the dialog for choosing a player from which to steal
     */
    protected SOCChoosePlayerDialog choosePlayerDialog;

    /**
     * the dialog for choosing 2 resources to discover
     */
    protected SOCDiscoveryDialog discoveryDialog;

    /**
     * the dialog for choosing a resource to monopolize
     */
    protected SOCMonopolyDialog monopolyDialog;

    /**
     * create a new player interface
     *
     * @param title  title for this interface - game name
     * @param cl     the player client that spawned us
     * @param ga     the game associated with this interface
     */
    public SOCPlayerInterface(String title, SOCPlayerClient cl, SOCGame ga)
    {
        super(TITLEBAR_GAME + title +
              (ga.isLocal ? "" : " [" + cl.getNickname() + "]"));
        setResizable(true);
        layoutNotReadyYet = true;

        client = cl;
        game = ga;
        gameIsStarting = false;
        clientHand = null;
        clientHandPlayerNum = -1;

        showingPlayerDiscards = false;
        showingPlayerDiscards_lock = new Object();

        /**
         * initialize the player colors
         */
        playerColors = new Color[4];
        playerColorsGhost = new Color[4];
        // FIXME assumes game.MAXPLAYERS==4
        //playerColors[0] = new Color( 10,  63, 172); // blue
        playerColors[0] = new Color(109, 124, 231); // grey-blue
        playerColors[1] = new Color(231,  35,  35); // red
        playerColors[2] = new Color(244, 238, 206); // off-white
        playerColors[3] = new Color(249, 128,  29); // orange
        for (int i = 0; i < SOCGame.MAXPLAYERS; ++i)
        {
            playerColorsGhost[i] = makeGhostColor(playerColors[i]);
        }

        /**
         * initialize the font and the forground, and background colors
         */
        setBackground(Color.black);
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 10));

        /**
         * we're doing our own layout management
         */
        setLayout(null);

        /**
         * setup interface elements.
         * PERF: hide window while doing so (osx firefox)
         */
        final boolean didHideTemp = isShowing();
        if (didHideTemp)
        {
            setVisible(false);
        }
        initInterfaceElements(true);

        /**
         * more initialization stuff
         */
        setLocation(50, 50);
        setSize(840, 730);
        validate();
        
        if (didHideTemp)
        {
            setVisible(true);
        }
        layoutNotReadyYet = false;
        repaint();

        /**
         * complete - reset mouse cursor from hourglass to normal
         * (set in SOCPlayerClient.startPracticeGame or .guardedActionPerform)
         */
        client.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

    }

    /**
     * Setup the interface elements
     *
     * @param firstCall First setup call for this window; do global things
     *   such as windowListeners, not just component-specific things.
     */
    protected void initInterfaceElements(boolean firstCall)
    {
        /**
         * initialize the player hand displays and add them to the interface
         */
        hands = new SOCHandPanel[SOCGame.MAXPLAYERS];

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            SOCHandPanel hp = new SOCHandPanel(this, game.getPlayer(i));
            hands[i] = hp;
            hp.setSize(180, 180);
            add(hp);
            ColorSquare blank = hp.getBlankStandIn();
            blank.setSize(180, 180);
            add(blank);
        }

        /**
         * initialize the building interface and add it to the main interface
         */
        buildingPanel = new SOCBuildingPanel(this);
        buildingPanel.setSize(200, 160);
        add(buildingPanel);

        /**
         * initialize the game board display and add it to the interface
         */
        boardPanel = new SOCBoardPanel(this);
        boardPanel.setBackground(new Color(112, 45, 10));
        boardPanel.setForeground(Color.black);
        boardPanel.setSize(SOCBoardPanel.PANELX, SOCBoardPanel.PANELY);
        boardIsScaled = false;
        add(boardPanel);
        if (game.isGameOptionDefined("PL"))
        {
            updatePlayerLimitDisplay(true);
        }

        /**
         * initialize the text input and display and add them to the interface
         */
        textDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 80);
        textDisplay.setFont(new Font("SansSerif", Font.PLAIN, 10));
        textDisplay.setBackground(new Color(255, 230, 162));
        textDisplay.setForeground(Color.black);
        textDisplay.setEditable(false);
        add(textDisplay);
        textDisplayRollExpected = 0;

        chatDisplay = new SnippingTextArea("", 40, 80, TextArea.SCROLLBARS_VERTICAL_ONLY, 100);
        chatDisplay.setFont(new Font("SansSerif", Font.PLAIN, 10));
        chatDisplay.setBackground(new Color(255, 230, 162));
        chatDisplay.setForeground(Color.black);
        chatDisplay.setEditable(false);
        add(chatDisplay);

        textInput = new TextField();
        textInput.setFont(new Font("SansSerif", Font.PLAIN, 10));
        textInputListener = new SOCPITextfieldListener(this); 
        textInputHasSent = false;
        textInputGreyCountdown = textInputGreyCountFrom;
        textInput.addKeyListener(textInputListener);
        textInput.addTextListener(textInputListener);
        textInput.addFocusListener(textInputListener);

        FontMetrics fm = this.getFontMetrics(textInput.getFont());
        textInput.setSize(SOCBoardPanel.PANELX, fm.getHeight() + 4);
        textInput.setBackground(Color.white);  // new Color(255, 230, 162));
        textInput.setForeground(Color.black);
        textInput.setEditable(false);
        textInputIsInitial = false;  // due to "please wait"
        textInput.setText("Please wait...");
        add(textInput);
        textInput.addActionListener(this);

        /** If player requests window close, ask if they're sure, leave game if so */
        if (firstCall)
        {
            addWindowListener(new MyWindowAdapter(this));
        }
    }

    /**
     * Overriden so the peer isn't painted, which clears background. Don't call
     * this directly, use {@link #repaint()} instead.
     * For performance and display-bug avoidance, checks {@link #layoutNotReadyYet} flag.
     */
    public void update(Graphics g)
    {
        if (! layoutNotReadyYet)
        {
            paint(g);
        } else {
            g.clearRect(0, 0, getWidth(), getHeight());
        }
    }

    /**
     * @return the client that spawned us
     */
    public SOCPlayerClient getClient()
    {
        return client;
    }

    /**
     * @return the game associated with this interface
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * @return the color of a player
     * @param pn  the player number
     */
    public Color getPlayerColor(int pn)
    {
        return getPlayerColor(pn, false);
    }

    /**
     * @return the "ghosted" color of a player
     * @param pn  the player number
     */
    public Color getPlayerColor(int pn, boolean isGhost)
    {
        if (isGhost)
            return playerColorsGhost[pn];
        else
            return playerColors[pn];
    }
    
    /**
     * @return a player's hand panel
     *
     * @param pn  the player's seat number
     * 
     * @see #getClientHand()
     */
    public SOCHandPanel getPlayerHandPanel(int pn)
    {
        return hands[pn];
    }

    /**
     * @return the board panel
     */
    public SOCBoardPanel getBoardPanel()
    {
        return boardPanel;
    }

    /**
     * @return the timer for time-driven events in the interface
     *
     * @see SOCHandPanel#autoRollSetupTimer()
     * @see SOCBoardPanel#popupSetBuildRequest(int, int)
     */
    public Timer getEventTimer()
    {
        return client.getEventTimer();
    }

    /**
     * The game's count of development cards remaining has changed.
     * Update the display.
     */
    public void updateDevCardCount()
    {
       buildingPanel.updateDevCardCount();
    }

    /**
     * The game's longest road or largest army may have changed.
     * Update each player's handpanel (victory points and longest-road/
     * largest-army indicator).  If it changed, print an announcement in
     * the message window.
     *<P>
     * Call this only after updating the SOCGame objects.
     *
     * @param isRoadNotArmy Longest-road, not largest-army, has just changed
     * @param oldp  Previous player with longest/largest, or null if none 
     * @param newp  New player with longest/largest, or null if none
     */
    public void updateLongestLargest
        (boolean isRoadNotArmy, SOCPlayer oldp, SOCPlayer newp)
    {
        // Update handpanels
        final int updateType;
        if (isRoadNotArmy)
            updateType = SOCHandPanel.LONGESTROAD;
        else
            updateType = SOCHandPanel.LARGESTARMY;

        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            hands[i].updateValue(updateType);
            hands[i].updateValue(SOCHandPanel.VICTORYPOINTS);
        }

        // Check for and announce change in largest army, or longest road
        if ((newp != oldp)
            && ((null != oldp) || (null != newp)))
        {
            StringBuffer msgbuf;
            if (isRoadNotArmy)
                msgbuf = new StringBuffer("Longest road was ");
            else
                msgbuf = new StringBuffer("Largest army was ");

            if (newp != null)
            {
                if (oldp != null)
                {
                    msgbuf.append("taken from ");
                    msgbuf.append(oldp.getName());
                    msgbuf.append(" by ");
                } else {
                    msgbuf.append("taken by ");                    
                }
                msgbuf.append(newp.getName());
            } else {
                msgbuf.append("lost by ");
                msgbuf.append(oldp.getName());
            }        

            msgbuf.append('.');
            print(msgbuf.toString());            
        }
    }

    /**
     * Show the maximum and available number of player positions,
     * if game parameter "PL" is less than {@link SOCGame#MAXPLAYERS}.
     * Also, if show, and gamestate is {@link SOCGame#NEW}, check for game-is-full,
     * and hide "sit down" buttons if so.
     * @param show the text, or clear the display (at game start)?
     * @since 1.1.07
     */
    private void updatePlayerLimitDisplay(boolean show)
    {
        if (! show)
        {
            boardPanel.setSuperimposedText(null, null);
            return;           
        }
        final int maxPl = game.getGameOptionIntValue("PL");
        if (maxPl == SOCGame.MAXPLAYERS)
            return;
        int availPl = game.getAvailableSeatCount();
        String availTxt = (availPl == 1) ? "1 seat available" : Integer.toString(availPl) + " seats available";
        boardPanel.setSuperimposedText
            ("Maximum players: " + maxPl, availTxt);
        if (( availPl == 0) && (game.getGameState() == SOCGame.NEW)) 
        {
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                hands[i].removeSitBut();
        }
            
    }

    /**
     * @return the building panel
     */
    public SOCBuildingPanel getBuildingPanel()
    {
        return buildingPanel;
    }
    
    /** The client player's SOCHandPanel interface, if active in a game.
     * 
     * @return our player's hand interface, or null if not in a game.
     */ 
    public SOCHandPanel getClientHand()
    {
        return clientHand; 
    }
    
    /** Update the client player's SOCHandPanel interface, for joining
     *  or leaving a game.
     *  
     *  Set by SOCHandPanel's removePlayer() and addPlayer() methods.
     *  
     * @param h  The SOCHandPanel for us, or null if none (leaving).
     */ 
    public void setClientHand(SOCHandPanel h)
    {
        clientHand = h;
        if (h != null)
            clientHandPlayerNum = h.getPlayer().getPlayerNumber();
        else
            clientHandPlayerNum = -1;
    }
    
    /**
     * Is the client player active in this game, and the current player?
     * @see #getClientPlayerNumber()
     */
    public boolean clientIsCurrentPlayer()
    {
        if (clientHand == null)
            return false;
        else
            return clientHand.isClientAndCurrentPlayer();
    }

    /**
     * If client player is active in game, their player number.
     * 
     * @return client's player ID, or -1.
     * @see #clientIsCurrentPlayer()
     */
    public int getClientPlayerNumber()
    {
        return clientHandPlayerNum;
    }

    /**
     * To reduce text clutter: server has just sent a dice result message.
     * If the next text message from server is the roll,
     *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
     *   with:    * It's Player's turn to roll. Rolled a 9.
     *<P>
     * Set to 0 at most times.
     * Set to the roll result when roll text is expected.
     * Will be cleared to 0 in {@link #print(String)}.
     *<P>
     * Whenever this field is nonzero, textmessages from the server
     * will be scanned for " rolled a ".
     *
     * @param roll The expected roll result, or 0.
     */
    public void setTextDisplayRollExpected(int roll)
    {
        textDisplayRollExpected = roll;
    }

    /**
     * send the message that was just typed in
     */
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == textInput)
        {
            if (textInputIsInitial)
            {
                // Player hit enter while chat prompt is showing (TEXTINPUT_INITIAL_PROMPT_MSG).
                // Just clear the prompt so they can type what they want to say.
                textInputSetToInitialPrompt(false);
                textInput.setText(" ");  // Not completely empty, so TextListener won't re-set prompt.
                return;
            }

            String s = textInput.getText().trim();

            if (s.length() > 100)
            {
                s = s.substring(0, 100);
            }
            else if (s.length() == 0)
            {
                return;
            }

            // Remove listeners for lower overhead on future typing
            if (! textInputHasSent)
            {
                textInputHasSent = true;
                if (textInputListener != null)
                {
                    textInput.removeKeyListener(textInputListener);
                    textInput.removeTextListener(textInputListener);
                    textInputListener = null;
                }
            }

            // Clear and send to game at server
            textInput.setText("");
            client.sendText(game, s + "\n");
        }
    }

    /**
     * leave this game
     */
    public void leaveGame()
    {
        client.leaveGame(game);
        dispose();
    }

    /**
     * Player wants to request to reset the board (same players, new game, new layout).
     * If acceptable, send request to server. If not, say so in text area.
     * Not acceptable if they've already done so this turn, or if voting
     * is active because another player called for a vote.
     *<P>
     * Board reset was added in version 1.1.00.  Older servers won't support it.
     * If this happens, give user a message.
     */
    public void resetBoardRequest()
    {
        if (client.getServerVersion(game) < 1100)
        {
            textDisplay.append("*** This server does not support board reset, server is too old.\n");
            return;
        }
        if (game.getResetVoteActive())
        {
            textDisplay.append("*** Voting is already active. Try again when voting completes.\n");
            return;
        }
        SOCPlayer pl = game.getPlayer(clientHandPlayerNum);        
        if (! pl.hasAskedBoardReset())
            client.resetBoardRequest(game);
        else
            textDisplay.append("*** You may ask only once per turn to reset the board.\n");
    }

    /**
     * Another player has voted on a board reset request.
     * Show the vote.
     */
    public void resetBoardVoted(int pn, boolean vyes)
    {
        String voteMsg;
        if (vyes)
            voteMsg = "Go ahead.";
        else
            voteMsg = "No thanks.";
        textDisplay.append("* " + game.getPlayer(pn).getName() + " has voted: " + voteMsg + "\n");
        game.resetVoteRegister(pn, vyes);
        try { hands[pn].resetBoardSetMessage(voteMsg); }
        catch (IllegalStateException e) { /* ignore; discard message is showing */ }
    }

    /**
     * Voting complete, board reset was rejected.
     * Display text message and clear the offer.
     */
    public void resetBoardRejected()
    {
        textDisplay.append("** The board reset was rejected.\n");
        for (int i = 0; i < SOCGame.MAXPLAYERS; ++i)
        {
            // Clear all displayed votes
            try { hands[i].resetBoardSetMessage(null); }
            catch (IllegalStateException e) { /* ignore; discard message is showing */ }
        }
        boardResetRequester = null;
        if (boardResetVoteDia != null)
        {
            if (boardResetVoteDia.isShowing())
                boardResetVoteDia.disposeQuietly();
            boardResetVoteDia = null;
        }
        // Requester may have already been null, if we're the requester and it was rejected.
    }

    /**
     * Creates and shows a new ResetBoardVoteDialog.
     * If the game is over, the "Reset" button is the default;
     * otherwise, "No" is default.
     * Also announces the vote request (text) and sets boardResetRequester.
     * Dialog is shown in a separate thread, to continue message
     * treating and screen redraws as the other players vote.
     *<P>
     * If we are the requester, we update local game state
     * but don't vote.
     *
     * @param pnRequester Player number of the player requesting the board reset
     */
    public void resetBoardAskVote(int pnRequester)
    {
        boolean gaOver = (game.getGameState() >= SOCGame.OVER);
        try
        {
            game.resetVoteBegin(pnRequester);
        }
        catch (RuntimeException re)
        {
            D.ebugPrintln("resetBoardAskVote: Cannot: " + re);
            return;
        }
        boardResetRequester = hands[pnRequester];
        if (pnRequester != clientHandPlayerNum)
        {
            String pleaseMsg;
            if (gaOver)
                pleaseMsg = "Restart Game?";
            else
                pleaseMsg = "Reset Board?";
            boardResetRequester.resetBoardSetMessage(pleaseMsg);

            String requester = game.getPlayer(pnRequester).getName();
            boardResetVoteDia = new ResetBoardVoteDialog(client, this, requester, gaOver);
            boardResetVoteDia.showInNewThread();
               // Separate thread so ours is not tied up; this allows server
               // messages to be received, and screen to refresh, if other
               // players vote before we do, or if voting is cancelled.
        }
    }

    /** Callback from ResetBoardVoteDialog, to clear our reference when
     *  button is clicked and dialog is going away.
     */
    private void resetBoardClearDia()
    {
        boardResetVoteDia = null;
    }

    /**
     * print text in the text window.
     * For dice-roll message, combine lines to reduce clutter.
     *
     * @param s  the text
     */
    public void print(String s)
    {
        if (textDisplayRollExpected > 0)
        {
            /*
             * Special case: Roll message.  Reduce clutter.
             * Instead of printing this message verbatim,
             * change the textDisplay contents (if matching):
             *   replace: * It's Player's turn to roll the dice. \n * Player rolled a 4 and a 5.
             *   with:    * It's Player's turn to roll. Rolled a 9.
             * 
             * JM 2009-05-21: Don't edit existing text on Mac OS X 10.5; it can lead to a GUI hang/race condition.
             *   Instead just print the total rolled.
             */

            if (s.startsWith("* ") && (s.indexOf(" rolled a ") > 0))
            {
                String currentText = textDisplay.getText();
                int L = currentText.length();
                int i = currentText.lastIndexOf("'s turn to roll the dice.");
                                                // 25 chars: length of match text
                                                //  9 chars: length of " the dice"
                if ((i > 0) && (30 > (L - i)))
                {
                    if (! SnippingTextArea.isJavaOnOSX105)
                    {
                        String rollText = ". Rolled a " + textDisplayRollExpected;
                        currentText = currentText.substring(0, i+15)
                            + rollText + currentText.substring(i+15+9);
                        textDisplay.setText(currentText);
                        //textDisplay.replaceRange(rollText, i+15, i+15+9);
                        //textDisplay.replaceRange(rollText, i+5, i+5+9);                    
                        //textDisplay.insert(rollText, 10); // i+5); // +15);
                    } else {
                        String rollText = "* Rolled a " + textDisplayRollExpected + ".\n";
                        textDisplay.append(rollText);
                    }
                    textDisplayRollExpected = 0;

                    return;  // <--- Early return ---
                }
            }

            textDisplayRollExpected = 0;  // Reset for next call
        }

        StringTokenizer st = new StringTokenizer(s, " \n", true);
        String row = "";

        while (st.hasMoreElements())
        {
            String tk = st.nextToken();

            if (tk.equals("\n"))
            {
                continue;
            }

            if ((row.length() + tk.length()) > ncols)
            {
                textDisplay.append(row + "\n");
                row = tk;

                continue;
            }

            row += tk;
        }

        if (row.trim().length() > 0)
        {
            textDisplay.append(row + "\n");
        }
    }

    /**
     * print text in the chat window
     *
     * @param s  the text
     */
    public void chatPrint(String s)
    {
        StringTokenizer st = new StringTokenizer(s, " \n", true);
        String row = "";

        while (st.hasMoreElements())
        {
            String tk = st.nextToken();

            if (tk.equals("\n"))
            {
                continue;
            }

            if ((row.length() + tk.length()) > ncols)
            {
                chatDisplay.append(row + "\n");
                row = tk;

                continue;
            }

            row += tk;
        }

        if (row.trim().length() > 0)
        {
            chatDisplay.append(row + "\n");
        }
    }

    /**
     * an error occurred, stop playing
     *
     * @param s  an error message
     */
    public void over(String s)
    {
        if (textInputIsInitial)
            textInputSetToInitialPrompt(false);  // Clear, set foreground color
        textInput.setEditable(false);
        textInput.setText(s);
        textDisplay.append("* Sorry, lost connection to the server.\n");
        textDisplay.append("*** Game stopped. ***\n");
        game.setCurrentPlayerNumber(-1);
        boardPanel.repaint();
    }

    /**
     * start the game interface: add "sit" buttons, set chat input (textInput) to initial prompt.
     * This doesn't mean that game play or placement is starting,
     * only that the window is ready for players to choose where to sit.
     */
    public void began()
    {
        textInput.setEditable(true);
        textInput.setText("");
        textInputSetToInitialPrompt(true);
        textInput.requestFocus();

        if ((game.getGameState() == SOCGame.NEW) || (game.getGameState() == SOCGame.READY))
        {
            for (int i = 0; i < 4; i++)
            {
                hands[i].addSitButton();
            }
        }
    }

    /**
     * a player has sat down to play
     *
     * @param n   the name of the player
     * @param pn  the seat number of the player
     */
    public void addPlayer(String n, int pn)
    {
        hands[pn].addPlayer(n);

        if (n.equals(client.getNickname()))
        {
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                D.ebugPrintln("game.getPlayer(" + i + ").isRobot() = " + game.getPlayer(i).isRobot());

                if (game.getPlayer(i).isRobot())
                {
                    hands[i].addSeatLockBut();
                }
            }
        }

        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true);

        if (game.isBoardReset())
        {
            // Retain face after reset
            hands[pn].changeFace(hands[pn].getPlayer().getFaceId());
        }
    }

    /**
     * remove a player from the game
     *
     * @param pn the number of the player
     */
    public void removePlayer(int pn)
    {
        hands[pn].removePlayer();

        if (game.getGameState() <= SOCGame.READY)
        {
            boolean match = false;

            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if ((game.getPlayer(i).getName() != null) && (!game.isSeatVacant(i)) && (game.getPlayer(i).getName().equals(client.getNickname())))
                {
                    match = true;

                    break;
                }
            }

            if (!match)
            {
                hands[pn].addSitButton();
            }
        }

        if (game.isGameOptionDefined("PL"))
            updatePlayerLimitDisplay(true);
    }

    /**
     * Game play is starting (leaving state {@link SOCGame#NEW}).
     * Remove the start buttons and robot-lockout buttons.
     * Next move is for players to make their starting placements.
     */
    public void startGame()
    {
        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            hands[i].removeStartBut();
            // This button has two functions (and two labels).
            // If client joined and then started a game, remove it (as robot lockout).
            // If we're joining a game in progress, keep it (as "sit here").
            hands[i].removeSitLockoutBut();
        }
        updatePlayerLimitDisplay(false);
        gameIsStarting = true;
    }

    /**
     * Game is over; server has sent us the revealed scores
     * for each player.  Refresh the display.
     *
     * @param finalScores Final score for each player position
     */
    public void updateAtOver(int[] finalScores)
    {
        if (game.getGameState() != SOCGame.OVER)
            return;

        for (int i = 0; i < finalScores.length; ++i)
            game.getPlayer(i).forceFinalVP(finalScores[i]);
        if (null == game.getPlayerWithWin())
        {
            game.checkForWinner();  // Assumes "current player" set to winner already, by SETTURN msg
        }
        for (int i = 0; i < finalScores.length; ++i)
            hands[i].updateValue(SOCHandPanel.VICTORYPOINTS);  // Also disables buttons, etc.
        setTitle(TITLEBAR_GAME_OVER + game.getName() +
                 (game.isLocal ? "" : " [" + client.getNickname() + "]"));
        repaint();
    }

    /**
     * Game's current player has changed.  Update displays.
     *
     * @param pnum New current player number; should match game.getCurrentPlayerNumber()
     */
    public void updateAtTurn(int pnum)
    {
        getPlayerHandPanel(pnum).updateDevCards();
        for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
        {
            // hilight current player, update takeover button
            getPlayerHandPanel(i).updateAtTurn();
        }

        boardPanel.updateMode();
        boardPanel.repaint();
        if (textInputGreyCountdown > 0)
        {
            --textInputGreyCountdown;
            if ((textInputGreyCountdown == 0) && textInputIsInitial)
            {
                textInput.setForeground(Color.LIGHT_GRAY);
            }
        }

        // No need for a buildingPanel.updateAtTurn;
        //   its updateButtonStatus is called from client.handleGAMESTATE.
    }

    /**
     * Set or clear the chat text input's initial prompt.
     * Sets its status, foreground color, and the prompt text if true.
     *
     * @param setToInitial If false, clear initial-prompt status, and
     *    clear contents (if they are the initial-prompt message);
     *    If true, set initial-prompt status, and set the prompt
     *    (if contents are blank when trimmed).
     *
     * @throws IllegalStateException if setInitial true, but player
     *    already sent chat text (textInputHasSent).
     *
     * @see #TEXTINPUT_INITIAL_PROMPT_MSG
     */
    protected void textInputSetToInitialPrompt(boolean setToInitial)
        throws IllegalStateException
    {
        if (setToInitial && textInputHasSent)
            throw new IllegalStateException("Already sent text, can't re-initial");

        // Always change text before changing flag,
        // so TextListener doesn't fight this action.

        if (setToInitial)
        {
            if (textInput.getText().trim().length() == 0)
                textInput.setText(TEXTINPUT_INITIAL_PROMPT_MSG);  // Set text before flag
            textInputIsInitial = true;
            textInputGreyCountdown = textInputGreyCountFrom;  // Reset fade countdown
            textInput.setForeground(Color.DARK_GRAY);
        } else {
            if (textInput.getText().equals(TEXTINPUT_INITIAL_PROMPT_MSG))
                textInput.setText("");  // Clear to make room for text being typed
            textInputIsInitial = false;
            textInput.setForeground(Color.BLACK);
        }
    }

    /**
     * show the discard dialog
     *
     * @param nd  the number of discards
     */
    public void showDiscardDialog(int nd)
    {
        discardDialog = new SOCDiscardDialog(this, nd);
        discardDialog.setVisible(true);
    }

    /**
     * show the choose player dialog box
     *
     * @param count   the number of players to choose from
     * @param pnums   the player ids of those players
     */
    public void choosePlayer(int count, int[] pnums)
    {
        choosePlayerDialog = new SOCChoosePlayerDialog(this, count, pnums);
        choosePlayerDialog.setVisible(true);
    }

    /**
     * show the Discovery dialog box
     */
    public void showDiscoveryDialog()
    {
        discoveryDialog = new SOCDiscoveryDialog(this);
        discoveryDialog.setVisible(true);
    }

    /**
     * show the Monopoly dialog box
     */
    public void showMonopolyDialog()
    {
        monopolyDialog = new SOCMonopolyDialog(this);
        monopolyDialog.setVisible(true);
    }

    /** 
     * Client is current player; state changed from PLAY to PLAY1.
     * (Dice has been rolled, or card played.)
     * Update interface accordingly.
     */
    public void updateAtPlay1()
    {
        if (clientHand != null)
            clientHand.updateAtPlay1();
    }

    /**
     * Update interface after game state has changed.
     * Please call after {@link SOCGame#setGameState(int)}.
     * If the game is now starting, please call in this order:
     *<code>
     *   playerInterface.{@link #startGame()};
     *   game.setGameState(newState);
     *   playerInterface.updateAtGameState();
     *</code>
     */
    public void updateAtGameState()
    {
        int gs = game.getGameState();

        getBoardPanel().updateMode();
        getBuildingPanel().updateButtonStatus();
        getBoardPanel().repaint();

        // Check for placement states (board panel popup, build via right-click)
        if ((gs == SOCGame.PLACING_ROAD) || (gs == SOCGame.PLACING_SETTLEMENT)
            || (gs == SOCGame.PLACING_CITY))
        {
            if (getBoardPanel().popupExpectingBuildRequest())
                getBoardPanel().popupFireBuildingRequest();
        }

        // Update our interface at start of first turn;
        // The server won't send a TURN message after the
        // final road is placed (state START2 -> PLAY).
        if (gameIsStarting && (gs >= SOCGame.PLAY))
        {
            gameIsStarting = false;
            if (clientHand != null)
                clientHand.updateAtTurn();
        }

        // React if waiting for players to discard,
        // or if we were waiting, and aren't anymore.
        if (gs == SOCGame.WAITING_FOR_DISCARDS)
        {
            // Set timer.  If still waiting for discards after 2 seconds,
            // show balloons on-screen. (hands[i].setDiscardMsg)
            discardTimerSet();
        } else if ((gs == SOCGame.PLAY1) && showingPlayerDiscards)
        {
            // If not all players' discard status balloons were cleared by
            // PLAYERELEMENT messages, clean up now.
            discardTimerClear();
        }

        // React if we are current player
        if (clientHand != null)
        {            
            SOCPlayer ourPlayerData = clientHand.getPlayer();
            if (ourPlayerData.getPlayerNumber() == game.getCurrentPlayerNumber())
            {
                if (gs == SOCGame.WAITING_FOR_DISCOVERY)
                {
                    showDiscoveryDialog();
                }
                else if (gs == SOCGame.WAITING_FOR_MONOPOLY)
                {
                    showMonopolyDialog();
                }
                else if (gs == SOCGame.PLAY1)
                {
                    updateAtPlay1();
                }
            }
        }
    }

    /**
     * Gamestate just became {@link SOCGame#WAITING_FOR_DISCARDS}.
     * Set up a timer to wait 1 second before showing "Discarding..."
     * balloons in players' handpanels.
     */
    private void discardTimerSet()
    {
        synchronized (showingPlayerDiscards_lock)
        {
            if (showingPlayerDiscardsTask != null)
            {
                showingPlayerDiscardsTask.cancel();  // cancel any previous
            }
            showingPlayerDiscardsTask = new SOCPIDiscardMsgTask(this);

            // Run once, after a brief delay in case only robots must discard.
            client.getEventTimer().schedule(showingPlayerDiscardsTask, 1000 /* ms */ );
        }
    }

    // TODO javadocs
    private void discardTimerClear()
    {
        synchronized (showingPlayerDiscards_lock)
        {
            if (showingPlayerDiscardsTask != null)
            {
                showingPlayerDiscardsTask.cancel();  // cancel any previous
                showingPlayerDiscardsTask = null;
            }
            if (showingPlayerDiscards)
            {
                for (int i = SOCGame.MAXPLAYERS - 1; i >= 0; --i)
                    hands[i].clearDiscardMsg();
                showingPlayerDiscards = false;
            }
        }
    }

    /**
     * Handle board reset (new game with same players, same game name).
     * The reset message will be followed with others which will fill in the game state.
     *
     * @param newGame New game object
     * @param rejoinPlayerNumber Sanity check - must be our correct player number in this game
     * @param requesterNumber Player who requested the board reset  
     * 
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     */
    public void resetBoard(SOCGame newGame, int rejoinPlayerNumber, int requesterNumber)
    {
        if (clientHand == null)
            return;
        if (clientHandPlayerNum != rejoinPlayerNumber)
            return;

        // Feedback: "busy" mouse cursor while clearing and re-laying out the components
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Clear out old state (similar to constructor)
        int oldGameState = game.getResetOldGameState();
        game = newGame;
        for (int i = 0; i < hands.length; ++i)
        {
            hands[i].removePlayer();  // will cancel roll countdown timer, right-click menus, etc
            hands[i].disable();
            hands[i].destroy();
        }
        clientHand = null;
        clientHandPlayerNum = -1;
        removeAll();  // old sub-components
        initInterfaceElements(false);  // new sub-components
        // Clear from possible TITLEBAR_GAME_OVER
        setTitle(TITLEBAR_GAME + game.getName() +
                 (game.isLocal ? "" : " [" + client.getNickname() + "]"));
        validate();
        repaint();
        String requesterName = game.getPlayer(requesterNumber).getName();
        if (requesterName == null)
            requesterName = "player who left";
        String resetMsg;
        if (oldGameState != SOCGame.OVER)
            resetMsg = "** The board was reset by " + requesterName + ".\n";
        else
            resetMsg = "** New game started by " + requesterName + ".\n";
        textDisplay.append(resetMsg);
        chatDisplay.append(resetMsg);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Further messages from server will fill in the rest.
    }

    /**
     * set the face icon for a player
     *
     * @param pn  the number of the player
     * @param id  the id of the face image
     */
    public void changeFace(int pn, int id)
    {
        hands[pn].changeFace(id);
    }

    /**
     * if debug is enabled, print this in the chat display.
     */
    public void chatPrintDebug(String debugMsg)
    {
        if (! D.ebugIsEnabled())
            return;
        chatPrint(debugMsg + "\n");
    }

    /**
     * if debug is enabled, print this exception's stack trace in
     * the chat display.  This eases tracing of exceptions when
     * our code is called in AWT threads (such as EventDispatch).
     */
    public void chatPrintStackTrace(Throwable th)
    {
        chatPrintStackTrace(th, false);
    }
    
    private void chatPrintStackTrace(Throwable th, boolean isNested)
    {
        if (! D.ebugIsEnabled())
            return;
        String excepName = th.getClass().getName();
        if (! isNested)
            chatDisplay.append("** Exception occurred **\n");
        if (th.getMessage() != null)
            chatPrint(excepName + ": " + th.getMessage());
        else
            chatPrint(excepName);
        StringWriter backstack = new StringWriter();
        PrintWriter pw = new PrintWriter(backstack);
        th.printStackTrace(pw);
        pw.flush();
        chatPrint (backstack.getBuffer().toString());
        Throwable cause = th.getCause();
        if ((cause != null) && (cause != th)) // NOTE: getCause is 1.4+
        {
            chatDisplay.append("** --> Nested Cause Exception: **\n");
            chatPrintStackTrace (cause, true);
        }        
        if (! isNested)
            chatDisplay.append("-- Exception ends: " + excepName + " --\n\n");
    }

    /** 
     * Calculate a color towards gray, for a hilight or the robber ghost.
     * If srcColor is light, ghost color is darker. (average with gray)
     * If it's dark or midtone, ghost should be lighter. (average with white)
     * 
     * @param srcColor The color to ghost from
     * @return Ghost color based on srcColor
     */
    public static Color makeGhostColor(Color srcColor)
    {
        int outR, outG, outB;
        outR = srcColor.getRed();
        outG = srcColor.getGreen();
        outB = srcColor.getBlue();
        if ((outR + outG + outB) > (160 * 3))
        {
            // src is light, we should be dark. (average with gray)
            outR = (outR + 140) / 2;
            outG = (outG + 140) / 2;
            outB = (outB + 140) / 2;
        } else {
            // src is dark or midtone, we should be light. (average with white)
            outR = (outR + 255) / 2;
            outG = (outG + 255) / 2;
            outB = (outB + 255) / 2;
        }
        return new Color (outR, outG, outB);
    }
    
    /**
     * do the layout
     */
    public void doLayout()
    {
        Insets i = getInsets();
        Dimension dim = getSize();
        dim.width -= (i.left + i.right);
        dim.height -= (i.top + i.bottom);

        /**
         * Classic Sizing
         * (board size is fixed, cannot scale)
         *
        int bw = SOCBoardPanel.getPanelX();
        int bh = SOCBoardPanel.getPanelY();
        int hw = (dim.width - bw - 16) / 2;
        int hh = (dim.height - 12) / 2;
        int kw = bw;
        int kh = buildingPanel.getSize().height;
        int tfh = textInput.getSize().height;
        int tah = dim.height - bh - kh - tfh - 16;
         */

        /**
         * "Stretch" Scaleable-board Sizing:
         * If board can be at least 15% larger than minimum board width,
         * without violating minimum handpanel width, scale it larger.
         * Otherwise, use minimum board width (widen handpanels instead).
         */
        int bw = (dim.width - 16 - (2*SOCHandPanel.WIDTH_MIN));
        int bh = (int) ((bw * (long) SOCBoardPanel.PANELY) / SOCBoardPanel.PANELX);
        int kh = buildingPanel.getHeight();
        int tfh = textInput.getHeight();
        if (bh > (dim.height - kh - 16 - (int)(5.5f * tfh)))
        {
            // Window is wide: board would become taller than fits in window.
            // Re-calc board max height, then board width.
            bh = dim.height - kh - 16 - (int)(5.5f * tfh);
            bw = (int) ((bh * (long) SOCBoardPanel.PANELX) / SOCBoardPanel.PANELY);
        }
        int hw = (dim.width - bw - 16) / 2;
        int tah = dim.height - bh - kh - tfh - 16;

        boolean canScaleBoard = (bw >= (1.15f * SOCBoardPanel.PANELX));
        if (canScaleBoard)
        {
            try
            {
                boardPanel.setBounds(i.left + hw + 8, i.top + tfh + tah + 8, bw, bh);
            }
            catch (IllegalArgumentException e)
            {
                canScaleBoard = false;
            }
        }
        if (! canScaleBoard)
        {
            bw = SOCBoardPanel.PANELX;
            bh = SOCBoardPanel.PANELY;
            hw = (dim.width - bw - 16) / 2;
            tah = dim.height - bh - kh - tfh - 16;
            try
            {
                boardPanel.setBounds(i.left + hw + 8, i.top + tfh + tah + 8, bw, bh);
            }
            catch (IllegalArgumentException ee)
            {
                bw = boardPanel.getWidth();
                bh = boardPanel.getHeight();
                hw = (dim.width - bw - 16) / 2;
                tah = dim.height - bh - kh - tfh - 16;
            }
        }
        boardIsScaled = canScaleBoard;  // set field, now that we know if it works
        int hh = (dim.height - 12) / 2;
        int kw = bw;

        buildingPanel.setBounds(i.left + hw + 8, i.top + tah + tfh + bh + 12, kw, kh);

        hands[0].setBounds(i.left + 4, i.top + 4, hw, hh);  // hp.setBounds also sets its blankStandIn's bounds

        if (SOCGame.MAXPLAYERS > 1)
        {
            /* FIXME: Assumes MAXPLAYERS == 4 for layout */ 
            hands[1].setBounds(i.left + hw + bw + 12, i.top + 4, hw, hh);
            hands[2].setBounds(i.left + hw + bw + 12, i.top + hh + 8, hw, hh);
            hands[3].setBounds(i.left + 4, i.top + hh + 8, hw, hh);
        }

        int tdh, cdh;
        if (game.isLocal)
        {
            // Game textarea larger than chat textarea
            cdh = (int) (2.2f * tfh);
            tdh = tah - cdh;
        }
        else
        {
            // Equal-sized text, chat textareas
            tdh = tah / 2;
            cdh = tah - tdh;
        }
        textDisplay.setBounds(i.left + hw + 8, i.top + 4, bw, tdh);
        chatDisplay.setBounds(i.left + hw + 8, i.top + 4 + tdh, bw, cdh);
        textInput.setBounds(i.left + hw + 8, i.top + 4 + tah, bw, tfh);

        npix = textDisplay.getPreferredSize().width;
        ncols = (int) ((((float) bw) * 100.0) / ((float) npix)) - 2;

        //FontMetrics fm = this.getFontMetrics(textDisplay.getFont());
        //int nrows = (tdh / fm.getHeight()) - 1;

        //textDisplay.setMaximumLines(nrows);
        //nrows = (cdh / fm.getHeight()) - 1;

        //chatDisplay.setMaximumLines(nrows);
        boardPanel.doLayout();
    }

    /**
     * This is the dialog to vote on another player's board reset request.
     * If game in progress, buttons are Reset and Continue Playing; default Continue.
     * If game is over, buttons are Restart and No thanks; default Restart.
     * Start a new thread to show, so message treating can continue as other players vote.
     *
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    protected static class ResetBoardVoteDialog extends AskDialog implements Runnable
    {
        /** Runs in own thread, to not tie up client's message-treater thread. */
        private Thread rdt;

        /** If true, don't call any methods from callbacks here */
        private boolean askedDisposeQuietly;

        /**
         * Creates a new ResetBoardVoteDialog.
         *
         * @param cli      Player client interface
         * @param gamePI   Current game's player interface
         * @param requester  Name of player requesting the reset
         * @param gameIsOver The game is over - "Reset" button should be default (if not over, "Continue" is default)
         */
        protected ResetBoardVoteDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI, String requester, boolean gameIsOver)
        {
            super(cli, gamePI, "Reset board of game "
                    + gamePI.getGame().getName() + "?",
                (gameIsOver
                    ? (requester + " wants to start a new game.")
                    : (requester + " wants to reset the game being played.")),
                (gameIsOver
                    ? "Restart"
                    : "Reset"),
                (gameIsOver
                    ? "No thanks"
                    : "Continue playing"),
                null,
                (gameIsOver ? 1 : 2));
            rdt = null;
            askedDisposeQuietly = false;
        }

        /**
         * React to the Reset button. (call playerClient.resetBoardVote)
         */
        public void button1Chosen()
        {
            pcli.resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), true);
            pi.resetBoardClearDia();
        }

        /**
         * React to the No button. (call playerClient.resetBoardVote)
         */
        public void button2Chosen()
        {
            pcli.resetBoardVote(pi.getGame(), pi.getClientPlayerNumber(), false);
            pi.resetBoardClearDia();
        }

        /**
         * React to the dialog window closed by user. (Vote No)
         */
        public void windowCloseChosen()
        {
            if (! askedDisposeQuietly)
                button2Chosen();
        }

        /**
         * Make a new thread and show() in that thread.
         * Keep track of the thread, in case we need to dispose of it.
         */
        public void showInNewThread()
        {
            rdt = new Thread(this);
            rdt.setDaemon(true);
            rdt.setName("resetVoteDialog-" + pcli.getNickname());
            rdt.start();  // run method will show the dialog
        }

        public void disposeQuietly()
        {
            askedDisposeQuietly = true;
            rdt.stop();
            dispose();
        }

        /**
         * In new thread, show ourselves. Do not call
         * directly; call {@link #showInNewThread()}.
         */
        public void run()
        {
            try
            {
                show();
            }
            catch (ThreadDeath e) {}
        }

    }  // class ResetBoardVoteDialog
    
    private static class MyWindowAdapter extends WindowAdapter
    {
        private SOCPlayerInterface pi;

        public MyWindowAdapter(SOCPlayerInterface spi)
        {
            pi = spi;
        }

        /**
         * Ask if player is sure - Leave the game when the window closes.
         */
        public void windowClosing(WindowEvent e)
        {
            // leaveGame();
            SOCQuitConfirmDialog.createAndShow(pi.getClient(), pi);
        }
    }  // MyWindowAdapter

    /**
     * Used for chat textfield setting/clearing initial prompt text
     * (TEXTINPUT_INITIAL_PROMPT_MSG).
     * It's expected that after the player sends their first line of chat text,
     * the listeners will be removed so we don't have the overhead of
     * calling these methods.
     */
    private static class SOCPITextfieldListener
        extends KeyAdapter implements TextListener, FocusListener
    {
        private SOCPlayerInterface pi;

        public SOCPITextfieldListener(SOCPlayerInterface spi)
        {
            pi = spi;
        }

        /** If first keypress in initially empty field, clear that prompt message */
        public void keyPressed(KeyEvent e)
        {            
            if (! pi.textInputIsInitial)
            {
                return;
            }
            pi.textInputSetToInitialPrompt(false);
        }

        /**
         * If input text is cleared, and field is again empty, show the
         * prompt message unless player has already sent a line of chat.
         */
        public void textValueChanged(TextEvent e)
        {
            if (pi.textInputIsInitial || pi.textInputHasSent)
            {
                return;
            }
            if (pi.textInput.getText().length() == 0)
            {
                // Former contents were erased,
                // show the prompt message.
                // Do not trim here. (vs focusLost)
                pi.textInputSetToInitialPrompt(true);
            }
        }

        /**
         * If input text is cleared, and player leaves the textfield while it's empty,
         * show the prompt message unless they've already sent a line of chat.
         */
        public void focusLost(FocusEvent e)
        {
            if (pi.textInputIsInitial || pi.textInputHasSent)
            {
                return;
            }
            if (pi.textInput.getText().trim().length() == 0)
            {
                // Former contents were erased,
                // show the prompt message.
                // Trim in case it's " " due to
                // player previously hitting "enter" in an
                // initial field (actionPerformed).

                pi.textInputSetToInitialPrompt(true);
            }
        }

        /** Stub required for FocusListner. */
        public void focusGained(FocusEvent e) {}

    }  // SOCPITextfieldListener


    /**
     * When timer fires, show discard message in any other player
     * (not client player) who must discard.
     * @see SOCPlayerInterface#discardTimerSet()
     */
    private static class SOCPIDiscardMsgTask extends TimerTask
    {
        private SOCPlayerInterface pi;

        public SOCPIDiscardMsgTask (SOCPlayerInterface spi)
        {
            pi = spi;
        }

        /**
         * Called when timer fires. Examine game state and players.
         * Sets "discarding..." to handpanels of discarding players.
         */
        public void run()
        {
            int clientPN = pi.clientHandPlayerNum;
            boolean anyShowing = false;
            SOCHandPanel hp;
            synchronized (pi.showingPlayerDiscards_lock)
            {
                if (pi.game.getGameState() != SOCGame.WAITING_FOR_DISCARDS)
                {
                    return;  // <--- Early return: No longer relevant ---
                }
                for (int i = SOCGame.MAXPLAYERS-1; i >=0; --i)
                {
                    hp = pi.hands[i];
                    if ((i != clientPN) &&
                        (7 < hp.getPlayer().getResources().getTotal()))
                    {
                        if (hp.setDiscardMsg())
                            anyShowing = true;
                    }
                }
                pi.showingPlayerDiscards = anyShowing;
                pi.showingPlayerDiscardsTask = null;  // No longer needed (fires once)
            }
        }

    }  // SOCPIDiscardMsgTask
}  // SOCPlayerInterface
