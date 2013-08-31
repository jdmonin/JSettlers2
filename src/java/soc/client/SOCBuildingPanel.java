/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net> - GameStatisticsFrame
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

import soc.client.stats.GameStatisticsFrame;
import soc.game.SOCBoardLarge;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;   // for GameStatisticsFrame


/**
 * This class is a panel that shows how much it costs
 * to build things, and it allows the player to build.
 * Sits within a game's {@link SOCPlayerInterface} frame.
 */
public class SOCBuildingPanel extends Panel
    implements ActionListener, WindowListener
{
    static final String ROAD = /*I*/"road"/*18N*/;
    static final String STLMT = /*I*/"stlmt"/*18N*/;
    static final String CITY = /*I*/"city"/*18N*/;
    static final String CARD = /*I*/"card"/*18N*/;
    static final String SHIP = /*I*/"ship"/*18N*/;  // Ship for large sea board; @since 2.0.00
    private static final String SBP = "sbp";  // Special Building Phase button; @since 1.1.08
    Label title;
    Button roadBut;
    Button settlementBut;
    Button cityBut;
    Button cardBut;
    Button gameInfoBut;  // show SOCGameOptions; @since 1.1.07; 2.0.00 renamed from optsBut
    Button statsBut;
    JFrame statsFrame;
    Label roadT;  // text
    Label roadC;  // cost
    ColorSquare roadWood;
    ColorSquare roadClay;
    Label settlementT;  // text
    Label settlementC;  // cost
    ColorSquare settlementWood;
    ColorSquare settlementClay;
    ColorSquare settlementWheat;
    ColorSquare settlementSheep;
    Label cityT;
    Label cityC;
    ColorSquare cityWheat;
    ColorSquare cityOre;
    Label cardT;
    Label cardC;
    Label cardCountLab;
    private Label vpToWinLab;  // null unless hasSeaBoard or vp != 10; @since 1.1.14
    ColorSquare cardWheat;
    ColorSquare cardSheep;
    ColorSquare cardOre;
    ColorSquare cardCount;
    private ColorSquare vpToWin;  // null unless hasSeaBoard or vp != 10; @since 1.1.14

    /** For game scenario {@link SOCGameOption#K_SC_CLVI _SC_CLVI}, the
     *  amount of cloth left in the board's "general supply". Null otherwise.
     *  @since 2.0.00
     */
    private ColorSquare cloth;
    private Label clothLab;

    // Large Sea Board Ship button; @since 2.0.00
    private Label shipT;  // text
    private Label shipC;  // cost
    private ColorSquare shipWood;
    private ColorSquare shipSheep;

    /**
     * For large sea board ({@link SOCGame#hasSeaBoard}), button to buy a ship.
     * Null if this game doesn't have that board.
     * @since 2.0.00
     */
    private Button shipBut;

    // For 6-player board: request Special Building Phase: @since 1.1.08
    private Panel sbPanel;
    private Button sbBut;
    private Label sbLab;
    private boolean sbIsHilight;  // Yellow, not grey, when true

    /**
     * "Game Info" window, from {@link #gameInfoBut} click, or null.
     * Tracked to prevent showing more than 1 at a time.
     * @since 1.1.18
     */
    private NewGameOptionsFrame ngof;

    /** Our parent window */
    SOCPlayerInterface pi;

    /**
     * Minimum required size of this panel, as laid out in {@link #doLayout()}.
     * @since 1.1.08
     */
    public static final int MINHEIGHT =  2 + (4*ColorSquare.HEIGHT) + (3*ColorSquare.HEIGHT / 2);

    /**
     * Client's player data.  Initially null; call setPlayer once seat is chosen.
     *
     * @see #setPlayer()
     */
    SOCPlayer player;

    /**
     * make a new building panel
     *
     * @param pi  the player interface that this panel is in
     */
    public SOCBuildingPanel(SOCPlayerInterface pi)
    {
        super();
        setLayout(null);

        this.player = null;
        this.pi = pi;

        setBackground(new Color(156, 179, 94));
        setForeground(Color.black);
        setFont(new Font("Dialog", Font.PLAIN, 10));

        /*
           title = new Label("Building Costs:");
           title.setAlignment(Label.CENTER);
           add(title);
         */
        roadT = new Label(/*I*/"Road: "/*18N*/);
        add(roadT);
        new AWTToolTip (/*I*/"0 VP  (longest road = 2 VP) "/*18N*/, roadT);
        roadC = new Label(/*I*/"Cost: "/*18N*/);
        add(roadC);
        roadWood = new ColorSquare(ColorSquare.WOOD, 1);
        add(roadWood);
        roadClay = new ColorSquare(ColorSquare.CLAY, 1);
        add(roadClay);
        roadBut = new Button("---");
        roadBut.setEnabled(false);
        add(roadBut);
        roadBut.setActionCommand(ROAD);
        roadBut.addActionListener(this);

        settlementT = new Label(/*I*/"Settlement: "/*18N*/);
        add(settlementT);
        new AWTToolTip (/*I*/"1 VP "/*18N*/, settlementT);
        settlementC = new Label(/*I*/"Cost: "/*18N*/);
        add(settlementC);
        settlementWood = new ColorSquare(ColorSquare.WOOD, 1);
        add(settlementWood);
        settlementClay = new ColorSquare(ColorSquare.CLAY, 1);
        add(settlementClay);
        settlementWheat = new ColorSquare(ColorSquare.WHEAT, 1);
        add(settlementWheat);
        settlementSheep = new ColorSquare(ColorSquare.SHEEP, 1);
        add(settlementSheep);
        settlementBut = new Button("---");
        settlementBut.setEnabled(false);
        add(settlementBut);
        settlementBut.setActionCommand(STLMT);
        settlementBut.addActionListener(this);

        cityT = new Label(/*I*/"City Upgrade: "/*18N*/);
        add(cityT);
        new AWTToolTip (/*I*/"2 VP  (receives 2x rsrc.) "/*18N*/, cityT);
        cityC = new Label(/*I*/"Cost: "/*18N*/);
        add(cityC);
        cityWheat = new ColorSquare(ColorSquare.WHEAT, 2);
        add(cityWheat);
        cityOre = new ColorSquare(ColorSquare.ORE, 3);
        add(cityOre);
        cityBut = new Button("---");
        cityBut.setEnabled(false);
        add(cityBut);
        cityBut.setActionCommand(CITY);
        cityBut.addActionListener(this);

        gameInfoBut = new Button(/*I*/"Game Info..."/*18N*/);  // show game options
        add(gameInfoBut);
        gameInfoBut.addActionListener(this);

        //TODO: disable until the game initialization is complete and the first roll is made
        statsBut = new Button(/*I*/"Game Statistics..."/*18N*/);
        add(statsBut);
        statsBut.addActionListener(this);

        cardT = new Label(/*I*/"Dev Card: "/*18N*/);
        add(cardT);
        new AWTToolTip (/*I*/"? VP  (largest army = 2 VP) "/*18N*/, cardT);
        cardC = new Label(/*I*/"Cost: "/*18N*/);
        add(cardC);
        cardWheat = new ColorSquare(ColorSquare.WHEAT, 1);
        add(cardWheat);
        cardSheep = new ColorSquare(ColorSquare.SHEEP, 1);
        add(cardSheep);
        cardOre = new ColorSquare(ColorSquare.ORE, 1);
        add(cardOre);
        cardBut = new Button("---");
        cardBut.setEnabled(false);
        add(cardBut);
        cardBut.setActionCommand(CARD);
        cardBut.addActionListener(this);
        // Development Card count. Initial amount will be sent from server soon.
        //TODO Is 'Available X' better than 'X available' in some languages'?
        cardCountLab = new Label(/*I*/"available"/*18N*/);
        cardCountLab.setAlignment(Label.LEFT);
        add(cardCountLab);
        cardCount = new ColorSquare(ColorSquare.GREY, 0);        
        cardCount.setTooltipText(/*I*/"Development cards available to buy"/*18N*/);
        cardCount.setTooltipLowWarningLevel(/*I*/"Almost out of development cards to buy"/*18N*/, 3);
        cardCount.setTooltipZeroText(/*I*/"No more development cards available to buy"/*18N*/);
        add(cardCount);

        final SOCGame ga = pi.getGame();

        if (ga.hasSeaBoard)
        {
            shipT = new Label(/*I*/"Ship: "/*18N*/);
            shipT.setAlignment(Label.LEFT);
            add(shipT);
            new AWTToolTip (/*I*/"0 VP  (longest route = 2 VP) "/*18N*/, shipT);
            shipC = new Label(/*I*/"Cost: "/*18N*/);
            add(shipC);
            shipWood = new ColorSquare(ColorSquare.WOOD, 1);
            add(shipWood);
            shipSheep = new ColorSquare(ColorSquare.SHEEP, 1);
            add(shipSheep);
            shipBut = new Button("---");
            shipBut.setEnabled(false);
            add(shipBut);
            shipBut.setActionCommand(SHIP);
            shipBut.addActionListener(this);

            if (ga.isGameOptionSet(SOCGameOption.K_SC_CLVI))
            {
                // General Supply cloth count. Initial amount will be sent from server soon.
                // (joingame if already started, or startgame as part of board layout)
                final String TTIP_CLOTH_TEXT =
                    /*I*/"General Supply of cloth for any villages shared by multiple players";/*18N*/

                clothLab = new Label(/*I*/"Cloth:"/*18N*/);
                add(clothLab);
                new AWTToolTip(TTIP_CLOTH_TEXT, clothLab);
                cloth = new ColorSquare(ColorSquare.GREY, 0);
                add(cloth);
                cloth.setTooltipText(TTIP_CLOTH_TEXT);
            }
        } else {
            // shipBut, cloth already null
        }

        if (ga.hasSeaBoard || (ga.vp_winner != 10))  // 10, not SOCGame.VP_WINNER_STANDARD, in case someone changes that
        {
            final String TTIP_VP_TEXT = /*I*/"Victory Points total needed to win the game"/*18N*/;

            vpToWinLab = new Label(/*I*/"VP to win:"/*18N*/);
            vpToWinLab.setAlignment(Label.RIGHT);
            add(vpToWinLab);
            new AWTToolTip(TTIP_VP_TEXT, vpToWinLab);

            vpToWin = new ColorSquare(ColorSquare.GREY, ga.vp_winner);
            vpToWin.setTooltipText(TTIP_VP_TEXT);
            add(vpToWin);
        } else {
            vpToWinLab = null;
            vpToWin = null;
        }

        if (ga.maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            sbIsHilight = false;
            sbPanel = new Panel();  // with default FlowLayout, alignment FlowLayout.CENTER.
            sbPanel.setBackground(ColorSquare.GREY);
            sbLab = new Label(/*I*/"Special Building Phase"/*18N*/);
            sbBut = new Button(/*I*/"Buy/Build"/*18N*/);
            sbBut.setEnabled(false);
            sbBut.setActionCommand(SBP);
            sbBut.addActionListener(this);
            sbPanel.add(sbLab);
            sbPanel.add(sbBut);
            add(sbPanel);
            new AWTToolTip(/*I*/"This phase allows building between player turns."/*18N*/, sbPanel);
            new AWTToolTip(/*I*/"This phase allows building between player turns."/*18N*/, sbLab);
        }

    }

    /**
     * custom layout for this panel.
     * If you change the line spacing or total height laid out here,
     * please update {@link #MINHEIGHT}.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();
        final int maxPlayers = pi.getGame().maxPlayers;
        int curY = 1;
        int curX;
        FontMetrics fm = this.getFontMetrics(this.getFont());
        final int lineH = ColorSquare.HEIGHT;
        final int rowSpaceH = lineH / 2;
        //TODO get 'cost: ' i18n'd, then replace ' ' for '_' 
        final int costW = fm.stringWidth(/*I*/"Cost:_"/*18N*/);    //Bug in stringWidth does not give correct size for ' ' so use '_'
        final int butW = 50;
        final int margin = 2;

        final int settlementTW = fm.stringWidth(settlementT.getText());
        final int cityTW = fm.stringWidth(cityT.getText());
        final int buttonMargin = 2 * margin + ((settlementTW > cityTW) ? settlementTW : cityTW);

        /*
           title.setSize(dim.width, lineH);
           title.setLocation(0, 0);
           curY += lineH;
         */
        roadT.setSize(fm.stringWidth(roadT.getText()), lineH);
        roadT.setLocation(margin, curY);
        roadBut.setSize(butW, lineH);
        roadBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        roadC.setSize(costW, lineH);
        roadC.setLocation(curX, curY);
        curX += 1 + costW + 3;
        roadWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        roadWood.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        roadClay.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        roadClay.setLocation(curX, curY);

        if (shipBut != null)
        {
            // Ship buying button is top-center of panel
            // (3 squares over from Road)
            final int shipTW = fm.stringWidth(shipT.getText());
            curX += 3 * (ColorSquare.WIDTH + 3);
            shipT.setSize(shipTW, lineH);
            shipT.setLocation(curX, curY);
            curX += shipTW + margin;
            shipBut.setSize(butW, lineH);
            shipBut.setLocation(curX, curY);
            curX += butW + margin;
            shipC.setSize(costW, lineH);
            shipC.setLocation(curX, curY);
            curX += 1 + costW + 3;
            shipWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
            shipWood.setLocation(curX, curY);
            curX += (ColorSquare.WIDTH + 3);
            shipSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
            shipSheep.setLocation(curX, curY);
        }

        if (cloth != null)
        {
            // Cloth General Supply count is top-right of panel
            //    TODO: position for 6-player (vs Special Building Phase button, etc)
            final int clothTW = fm.stringWidth(clothLab.getText());
            curX = dim.width - (3 * margin) - clothTW - ColorSquare.WIDTH;
            clothLab.setLocation(curX, curY);
            curX += clothTW + (2 * margin);
            cloth.setLocation(curX, curY);
        }

        curY += (rowSpaceH + lineH);

        settlementT.setSize(settlementTW, lineH);
        settlementT.setLocation(margin, curY);
        settlementBut.setSize(butW, lineH);
        settlementBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        settlementC.setSize(costW, lineH);
        settlementC.setLocation(curX, curY);
        curX += 1 + costW + 3;
        settlementWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementWood.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        settlementClay.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementClay.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        settlementWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        settlementSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementSheep.setLocation(curX, curY);

        if (maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            curX += (ColorSquare.WIDTH + 3);
            sbPanel.setSize(dim.width - curX - margin, rowSpaceH + 2 * lineH);
            sbPanel.setLocation(curX, curY);
            // sbBut.setSize(dim.width - curX - margin - 2 * buttonMargin, lineH);
            // (can't set size, FlowLayout will override it)
        }

        curY += (rowSpaceH + lineH);

        cityT.setSize(cityTW, lineH);
        cityT.setLocation(margin, curY);
        cityBut.setSize(butW, lineH);
        cityBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        cityC.setSize(costW, lineH);
        cityC.setLocation(curX, curY);
        curX += 1 + costW + 3;
        cityWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cityWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        cityOre.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cityOre.setLocation(curX, curY);
        curY += (rowSpaceH + lineH);

        cardT.setSize(fm.stringWidth(cardT.getText()), lineH);
        cardT.setLocation(margin, curY);
        cardBut.setSize(butW, lineH);
        cardBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        cardC.setSize(costW, lineH);
        cardC.setLocation(curX, curY);
        curX += 1 + costW + 3;
        cardWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        cardSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardSheep.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 3);
        cardOre.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardOre.setLocation(curX, curY);

        curX += 2 * (ColorSquare.WIDTH + 3);
        // cardCount.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardCount.setLocation(curX, curY);
        final int cardCLabW = fm.stringWidth(cardCountLab.getText());
        curX += (ColorSquare.WIDTH + 3);
        cardCountLab.setLocation(curX, curY);
        cardCountLab.setSize(cardCLabW + 2, lineH);

        // Game Info button is bottom-right of panel
        // Game Statistics button is just above it for 4-player games,
        //     top-right for 6-player games (to make room for Special Building button)
        // On 4-player classic board, both are moved up to make room for the dev card count.
        if ((maxPlayers <= 4) && ! pi.getGame().hasSeaBoard)
            curY -= (lineH + 5);
        curX = dim.width - (2 * butW) - margin;
        gameInfoBut.setSize(butW * 2, lineH);
        gameInfoBut.setLocation(curX, curY);
        statsBut.setSize(butW * 2, lineH);
        if (maxPlayers <= 4)
            statsBut.setLocation(curX, curY - lineH - 5);
        else
            statsBut.setLocation(curX, 1);
        if ((maxPlayers <= 4) && ! pi.getGame().hasSeaBoard)
            curY += (lineH + 5);

        // VP to Win label moves to make room for various buttons.
        if (vpToWin != null)
        {
            // #VP total to Win
            if (pi.getGame().hasSeaBoard)
            {
                // bottom-right corner of panel, left of Game Info
                curX -= (1.5f * ColorSquare.WIDTH + margin);
                vpToWin.setLocation(curX, curY);
    
                final int vpLabW = fm.stringWidth(vpToWinLab.getText());
                curX -= (vpLabW + (2*margin));
                vpToWinLab.setLocation(curX, curY);
                vpToWinLab.setSize(vpLabW + margin, lineH);
            } else {
                // upper-right corner of panel
                //    If 6-player, shift left to make room for Game Stats button
                //    (which is moved up to make room for Special Building button)
                curY = 1;
                final int vpLabW = fm.stringWidth(vpToWinLab.getText());
                if (maxPlayers <= 4)
                {
                    // 4-player: align from right
                    curX = dim.width - ColorSquare.WIDTH - margin;
                    vpToWin.setLocation(curX, curY);
        
                    curX -= (vpLabW + (2*margin));
                    vpToWinLab.setLocation(curX, curY);
                } else {
                    // 6-player: align from left, from width of piece-buying buttons/colorsquares
                    curX = buttonMargin + butW + margin + (1 + costW + 3) + (4 * (ColorSquare.WIDTH + 3));
                    vpToWinLab.setLocation(curX, curY);

                    curX += (vpLabW + (2*margin));
                    vpToWin.setLocation(curX, curY);
                }
                vpToWinLab.setSize(vpLabW + margin, lineH);
            }
        }

    }

    /**
     * Handle button clicks in this panel.
     *
     * @param e button click event
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
        String target = e.getActionCommand();
        SOCGame game = pi.getGame();

        if (e.getSource() == gameInfoBut)
        {
            if ((ngof != null) && ngof.isVisible())
            {
                ngof.setVisible(true);  // method override also requests topmost/focus
            } else {
                ngof = NewGameOptionsFrame.createAndShow
                    (pi.getGameDisplay(), game.getName(), game.getGameOptions(), false, true);
                ngof.addWindowListener(this);  // drop ngof reference when window is closed
            }

            return;
        }
        if (e.getSource() == statsBut)
        {
            if (statsFrame != null)
                statsFrame.dispose();
            GameStatisticsFrame f = new GameStatisticsFrame(pi);
            f.register(pi.getGameStats());
            f.setVisible(true);
            f.setLocation(this.getLocationOnScreen());
            statsFrame = f;

            return;
        }

        if (player != null)
        {
            clickBuildingButton(game, target, false);
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }
    
    /**
     * React to our parent game window being closed.
     * If the {@link GameStatisticsFrame} is showing, dispose it.
     * @since 2.0.00
     */
    public void gameWindowClosed()
    {
        if ((statsFrame != null) && statsFrame.isVisible())
            statsFrame.dispose();
    }

    /** Handle a click (Buy or Cancel) on a building-panel button.
     * Assumes client is currently allowed to build, and sends request to server.
     * {@link SOCBoardPanel.BoardPopupMenu} also calls this method.
     *
     * @param game   The game, for status
     * @param target Button clicked, as returned by ActionEvent.getActionCommand
     * @param doNotClearPopup Do not call {@link SOCBoardPanel#popupClearBuildRequest()}
     */
    public void clickBuildingButton(SOCGame game, String target, boolean doNotClearPopup)
    {
        SOCPlayerClient client = pi.getClient();

        if (! doNotClearPopup)
            pi.getBoardPanel().popupClearBuildRequest();  // Just in case

        final boolean isCurrent = pi.clientIsCurrentPlayer();
        final int gstate = game.getGameState();
        final boolean canAskSBP =
            game.canAskSpecialBuild(player.getPlayerNumber(), false)
            && ! sbIsHilight;
        final boolean stateBuyOK =        // same as in updateButtonStatus.
            (isCurrent)
            ? ((gstate == SOCGame.PLAY1) || (gstate == SOCGame.SPECIAL_BUILDING))
            : canAskSBP;

        int sendBuildRequest = -9;  // send client.buildRequest if this changes

        if (target == ROAD)
        {
            if (roadBut.getLabel().equals(/*I*/"Buy"/*18N*/))
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.ROAD;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (roadBut.getLabel().equals(/*I*/"Cancel"/*18N*/))
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.ROAD);
            }
        }
        else if (target == STLMT)
        {
            if (settlementBut.getLabel().equals(/*I*/"Buy"/*18N*/))  // && statebuyOK
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.SETTLEMENT;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (settlementBut.getLabel().equals(/*I*/"Cancel"/*18N*/))
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.SETTLEMENT);
            }
        }
        else if (target == CITY)
        {
            if (cityBut.getLabel().equals(/*I*/"Buy"/*18N*/))
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.CITY;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (cityBut.getLabel().equals(/*I*/"Cancel"/*18N*/))
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.CITY);
            }
        }
        else if (target == CARD)
        {
            if (cardBut.getLabel().equals(/*I*/"Buy"/*18N*/))
            {
                if (stateBuyOK || canAskSBP)
                {
                    client.getGameManager().buyDevCard(game);
                    pi.getClientHand().disableBankUndoButton();
                }
            }
        }
        else if (target == SHIP)
        {
            if (shipBut.getLabel().equals(/*I*/"Buy"/*18N*/))
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.SHIP;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (shipBut.getLabel().equals(/*I*/"Cancel"/*18N*/))
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.SHIP);
            }
        }
        else if (target == SBP)
        {
            if (canAskSBP)
                sendBuildRequest = -1;
        }

        if (sendBuildRequest != -9)
        {
            final SOCHandPanel chp = pi.getClientHand();
            if (isCurrent && (sendBuildRequest == -1))
                chp.setRollPrompt(null, true);  // clear the auto-roll countdown

            client.getGameManager().buildRequest(game, sendBuildRequest);
            chp.disableBankUndoButton();
        }
    }

    /**
     * update the status of the buttons
     */
    public void updateButtonStatus()
    {
        SOCGame game = pi.getGame();

        if (player != null)
        {
            final int pnum = player.getPlayerNumber();
            final boolean isDebugFreePlacement = game.isDebugFreePlacement();
            final boolean isCurrent = (! isDebugFreePlacement)
                && (game.getCurrentPlayerNumber() == pnum);
            final int gstate = game.getGameState();
            boolean currentCanBuy = (! isDebugFreePlacement)
                && game.canBuyOrAskSpecialBuild(pnum);

            if (isCurrent && ((gstate == SOCGame.PLACING_ROAD)
                    || ((gstate == SOCGame.PLACING_FREE_ROAD2)
                        && (game.isPractice
                            || pi.getClient().sVersion >= SOCGame.VERSION_FOR_CANCEL_FREE_ROAD2))))
            {
                roadBut.setEnabled(true);
                roadBut.setLabel(/*I*/"Cancel"/*18N*/);
            }
            else if (game.couldBuildRoad(pnum))
            {
                roadBut.setEnabled(currentCanBuy);
                roadBut.setLabel(/*I*/"Buy"/*18N*/);
            }
            else
            {
                roadBut.setEnabled(false);
                roadBut.setLabel("---");
            }

            if (isCurrent &&
                ((gstate == SOCGame.PLACING_SETTLEMENT) || (gstate == SOCGame.START1B)
                 || (gstate == SOCGame.START2B) || (gstate == SOCGame.START3B)) )
            {
                settlementBut.setEnabled(true);
                settlementBut.setLabel(/*I*/"Cancel"/*18N*/);
            }
            else if (game.couldBuildSettlement(pnum))
            {
                settlementBut.setEnabled(currentCanBuy);
                settlementBut.setLabel(/*I*/"Buy"/*18N*/);
            }
            else
            {
                settlementBut.setEnabled(false);
                settlementBut.setLabel("---");
            }

            if (isCurrent && (gstate == SOCGame.PLACING_CITY))
            {
                cityBut.setEnabled(true);
                cityBut.setLabel(/*I*/"Cancel"/*18N*/);
            }
            else if (game.couldBuildCity(pnum))
            {
                cityBut.setEnabled(currentCanBuy);
                cityBut.setLabel(/*I*/"Buy"/*18N*/);
            }
            else
            {
                cityBut.setEnabled(false);
                cityBut.setLabel("---");
            }

            if (game.couldBuyDevCard(pnum))
            {
                cardBut.setEnabled(currentCanBuy);
                cardBut.setLabel(/*I*/"Buy"/*18N*/);
            }
            else
            {
                cardBut.setEnabled(false);
                cardBut.setLabel("---");
            }

            if (shipBut != null)
            {
                if (isCurrent && ((gstate == SOCGame.PLACING_SHIP) || (gstate == SOCGame.PLACING_FREE_ROAD2)))
                {
                    shipBut.setEnabled(true);
                    shipBut.setLabel(/*I*/"Cancel"/*18N*/);
                    // ships were added after VERSION_FOR_CANCEL_FREE_ROAD2, so no need to check server version
                }
                else if (game.couldBuildShip(pnum))
                {
                    shipBut.setEnabled(currentCanBuy);
                    shipBut.setLabel(/*I*/"Buy"/*18N*/);
                }
                else
                {
                    shipBut.setEnabled(false);
                    shipBut.setLabel("---");
                }
            }

            if ((sbBut != null) && (player != null))
            {
                final boolean askedSB = player.hasAskedSpecialBuild();
                if (askedSB != sbIsHilight)
                {
                    final Color want =
                        (askedSB)
                        ? ColorSquare.WARN_LEVEL_COLOR_BG_FROMGREY
                        : ColorSquare.GREY;
                    sbPanel.setBackground(want);
                    sbLab.setBackground(want);
                    sbIsHilight = askedSB;
                }
                sbBut.setEnabled(game.canAskSpecialBuild(pnum, false) && ! askedSB);
            }
        }
    }

    /**
     * The game's count of development cards remaining has changed.
     * Update the display.
     */
    public void updateDevCardCount()
    {
        int newCount = pi.getGame().getNumDevCards();
        cardCount.setIntValue(newCount);
    }

    /**
     * The board's general supply of cloth remaining has changed.
     * Update the display.  Used for scenario {@link SOCGameOption#K_SC_CLVI}.
     * @since 2.0.00
     */
    public void updateClothCount()
    {
        if (cloth == null)
            return;
        cloth.setIntValue( ((SOCBoardLarge) pi.getGame().getBoard()).getCloth() );
    }

    /**
     * Set our game and player data based on client's nickname,
     * via game.getPlayer(client.getNickname()).
     *
     * @throws IllegalStateException If the player data has already been set,
     *    and this isn't a new game (a board reset).
     */
    public void setPlayer()
        throws IllegalStateException
    {
        SOCGame game = pi.getGame();
        if ((player != null) && ! game.isBoardReset())
            throw new IllegalStateException("Player data is already set");

        player = game.getPlayer(pi.getClient().getNickname());
    }

    /**
     * If our "Game Info" window ({@link NewGameOptionsFrame}) is closed,
     * drop our reference to it so it can be gc'd.
     * @since 1.1.18
     */
    public void windowClosed(WindowEvent e)
    {
        if (e.getWindow() == ngof)
            ngof = null;
    }

    /** Required stub for {@link WindowListener} */
    public void windowClosing(WindowEvent e) {}

    /** Required stub for {@link WindowListener} */
    public void windowOpened(WindowEvent e) {}

    /** Required stub for {@link WindowListener} */
    public void windowIconified(WindowEvent e) {}

    /** Required stub for {@link WindowListener} */
    public void windowDeiconified(WindowEvent e) {}

    /** Required stub for {@link WindowListener} */
    public void windowActivated(WindowEvent e) {}

    /** Required stub for {@link WindowListener} */
    public void windowDeactivated(WindowEvent e) {}

}
