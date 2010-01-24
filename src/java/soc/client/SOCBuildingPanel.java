/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2010 Jeremy D. Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
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


/**
 * This class is a panel that shows how much it costs
 * to build things, and it allows the player to build.
 * Sits within a game's {@link SOCPlayerInterface} frame.
 */
public class SOCBuildingPanel extends Panel implements ActionListener
{
    static final String ROAD = "road";
    static final String STLMT = "stlmt";
    static final String CITY = "city";
    static final String CARD = "card";
    private static final String SBP = "sbp";  // Special Building Phase button; @since 1.1.08
    Label title;
    Button roadBut;
    Button settlementBut;
    Button cityBut;
    Button cardBut;
    Button optsBut;  // show SOCGameOptions; @since 1.1.07
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
    ColorSquare cardWheat;
    ColorSquare cardSheep;
    ColorSquare cardOre;
    ColorSquare cardCount;
    // For 6-player board: request Special Building Phase: @since 1.1.08
    private Panel sbPanel;
    private Button sbBut;
    private Label sbLab;
    private boolean sbIsHilight;  // Yellow, not grey, when true

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
        setFont(new Font("Helvetica", Font.PLAIN, 10));

        /*
           title = new Label("Building Costs:");
           title.setAlignment(Label.CENTER);
           add(title);
         */
        roadT = new Label("Road: ");
        add(roadT);
        new AWTToolTip ("0 VP  (longest road = 2 VP) ", roadT);
        roadC = new Label("Cost: ");
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

        settlementT = new Label("Settlement: ");
        add(settlementT);
        new AWTToolTip ("1 VP ", settlementT);
        settlementC = new Label("Cost: ");
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

        cityT = new Label("City Upgrade: ");
        add(cityT);
        new AWTToolTip ("2 VP  (receives 2x rsrc.) ", cityT);
        cityC = new Label("Cost: ");
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

        optsBut = new Button("Game Options...");
        add(optsBut);
        optsBut.addActionListener(this);

        cardT = new Label("Card: ");
        add(cardT);
        new AWTToolTip ("? VP  (largest army = 2 VP) ", cardT);
        cardC = new Label("Cost: ");
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
        cardCountLab = new Label("available");
        cardCountLab.setAlignment(Label.LEFT);
        add(cardCountLab);
        cardCount = new ColorSquare(ColorSquare.GREY, 0);        
        cardCount.setTooltipText("Development cards available to buy");
        cardCount.setTooltipLowWarningLevel("Almost out of development cards to buy", 3);
        cardCount.setTooltipZeroText("No more development cards available to buy");
        add(cardCount);

        if (pi.getGame().maxPlayers > 4)
        {
            sbIsHilight = false;
            sbPanel = new Panel();  // with default FlowLayout, alignment FlowLayout.CENTER.
            sbPanel.setBackground(ColorSquare.GREY);
            sbLab = new Label("Special Building Phase");
            sbBut = new Button("Buy/Build");
            sbBut.setEnabled(false);
            sbBut.setActionCommand(SBP);
            sbBut.addActionListener(this);
            sbPanel.add(sbLab);
            sbPanel.add(sbBut);
            add(sbPanel);
            new AWTToolTip("This phase allows building between player turns.", sbPanel);
            new AWTToolTip("This phase allows building between player turns.", sbLab);
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
        int curY = 1;
        int curX;
        FontMetrics fm = this.getFontMetrics(this.getFont());
        final int lineH = ColorSquare.HEIGHT;
        final int rowSpaceH = lineH / 2;
        final int costW = fm.stringWidth("Cost:_");    //Bug in stringWidth does not give correct size for ' ' so use '_'
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

        // Game Options button is top-right of panel
        curX = dim.width - (2 * butW) - margin;
        optsBut.setSize(butW * 2, lineH);
        optsBut.setLocation(curX, curY);

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

        if (pi.getGame().maxPlayers > 4)
        {
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

        if (e.getSource() == optsBut)
        {
            NewGameOptionsFrame.createAndShow(pi.getClient(), game.getName(), game.getGameOptions(), false, true);
            return;
        }

        if (player != null)
        {
            clickBuildingButton(game, pi.getClient(), target, false);
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }
    
    /** Handle a click (Buy or Cancel) on a building-panel button.
     * Assumes client is currently allowed to build, and sends request to server.
     *
     * @param game   The game, for status
     * @param client The client, for sending build or cancel request
     * @param target Button clicked, as returned by ActionEvent.getActionCommand
     * @param doNotClearPopup Do not call {@link SOCBoardPanel#popupClearBuildRequest()}
     */
    public void clickBuildingButton(SOCGame game, SOCPlayerClient client, String target, boolean doNotClearPopup)
    {
        if (! doNotClearPopup)
            pi.getBoardPanel().popupClearBuildRequest();  // Just in case

        final boolean isCurrent = (game.getCurrentPlayerNumber() == player.getPlayerNumber());
        final int gstate = game.getGameState();
        final boolean stateBuyOK =        // same as in updateButtonStatus. (TODO) refactor to SOCGame?
            (isCurrent)
            ? ((gstate == SOCGame.PLAY1) || (gstate == SOCGame.SPECIAL_BUILDING))
            : ((game.maxPlayers > 4) && (gstate >= SOCGame.PLAY) && (gstate < SOCGame.OVER));

        if (target == ROAD)
        {
            if (stateBuyOK && (roadBut.getLabel().equals("Buy")))
            {
                client.buildRequest(game, SOCPlayingPiece.ROAD);
            }
            else if (roadBut.getLabel().equals("Cancel"))
            {
                client.cancelBuildRequest(game, SOCPlayingPiece.ROAD);
            }
        }
        else if (target == STLMT)
        {
            if (stateBuyOK && (settlementBut.getLabel().equals("Buy")))
            {
                client.buildRequest(game, SOCPlayingPiece.SETTLEMENT);
            }
            else if (settlementBut.getLabel().equals("Cancel"))
            {
                client.cancelBuildRequest(game, SOCPlayingPiece.SETTLEMENT);
            }
        }
        else if (target == CITY)
        {
            if (stateBuyOK && (cityBut.getLabel().equals("Buy")))
            {
                client.buildRequest(game, SOCPlayingPiece.CITY);
            }
            else if (cityBut.getLabel().equals("Cancel"))
            {
                client.cancelBuildRequest(game, SOCPlayingPiece.CITY);
            }
        }
        else if (target == CARD)
        {
            if (stateBuyOK && (cardBut.getLabel().equals("Buy")))
            {
                client.buyDevCard(game);
            }
        }
        else if (target == SBP)
        {
            if (stateBuyOK && ! sbIsHilight)
                client.buildRequest(game, -1);
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
            final boolean isCurrent = (game.getCurrentPlayerNumber() == pnum);
            final int gstate = game.getGameState();
            boolean currentCanBuy = game.canBuyOrAskSpecialBuild(pnum);

            if (isCurrent && (gstate == SOCGame.PLACING_ROAD))
            {
                roadBut.setEnabled(true);
                roadBut.setLabel("Cancel");
            }
            else if (game.couldBuildRoad(pnum))
            {
                roadBut.setEnabled(currentCanBuy);
                roadBut.setLabel("Buy");
            }
            else
            {
                roadBut.setEnabled(false);
                roadBut.setLabel("---");
            }

            if (isCurrent &&
                ((gstate == SOCGame.PLACING_SETTLEMENT) || (gstate == SOCGame.START1B)
                 || (gstate == SOCGame.START2B)))
            {
                settlementBut.setEnabled(true);
                settlementBut.setLabel("Cancel");
            }
            else if (game.couldBuildSettlement(pnum))
            {
                settlementBut.setEnabled(currentCanBuy);
                settlementBut.setLabel("Buy");
            }
            else
            {
                settlementBut.setEnabled(false);
                settlementBut.setLabel("---");
            }

            if (isCurrent && (gstate == SOCGame.PLACING_CITY))
            {
                cityBut.setEnabled(true);
                cityBut.setLabel("Cancel");
            }
            else if (game.couldBuildCity(pnum))
            {
                cityBut.setEnabled(currentCanBuy);
                cityBut.setLabel("Buy");
            }
            else
            {
                cityBut.setEnabled(false);
                cityBut.setLabel("---");
            }

            if (game.couldBuyDevCard(pnum))
            {
                cardBut.setEnabled(currentCanBuy);
                cardBut.setLabel("Buy");
            }
            else
            {
                cardBut.setEnabled(false);
                cardBut.setLabel("---");
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
                sbBut.setEnabled(currentCanBuy && ! (askedSB || isCurrent));
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

}
