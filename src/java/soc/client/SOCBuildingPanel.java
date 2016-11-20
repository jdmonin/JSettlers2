/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2014,2016 Jeremy D Monin <jeremy@nand.net>
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
@SuppressWarnings("serial")
public class SOCBuildingPanel extends Panel
    implements ActionListener, WindowListener
{
    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    static final String ROAD = "road";  // I18N: These are internal command labels, not user-visible strings
    static final String STLMT = "stlmt";
    static final String CITY = "city";
    static final String CARD = "card";
    static final String SHIP = "ship";  // Ship for large sea board; @since 2.0.00
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

    /** For game scenario {@link SOCGameOption#K_SC_WOND _SC_WOND}, the
     *  "Wonders" button that brings up a dialog with info and Build buttons. Null otherwise.
     *  @since 2.0.00
     */
    private Button wondersBut;

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

    /** For 6-player board: request Special Building Phase.
     *  Given variable custom layout in v2.0.00 for i18n. Contains {@link #sbLab} and {@link #sbBut}
     *  centered on 1 line or 2 lines based on their text widths.
     *  @since 1.1.08
     */
    private Panel sbPanel;
    private Button sbBut;
    /** "Special Building Phase" label. Not used on Large Board due to space constraints. */
    private Label sbLab;
    private boolean sbIsHilight;  // Yellow, not grey, when true

    /**
     * Piece-purchase button status; either all "Buy", or when placing a piece, 1 type "Cancel" and the rest disabled.
     * When placing the second free road or free ship, this placement can be canceled, and the Road and Ship
     * buttons both say "Cancel".
     *<P>
     * Updated in {@link #updateButtonStatus()}.
     * Value is 0 for "Buy", or when placing a piece, a borrowed SOCGameState constant with the piece type:
     * {@link SOCGame#PLACING_ROAD PLACING_ROAD}, {@link SOCGame#PLACING_SETTLEMENT PLACING_SETTLEMENT},
     * {@link SOCGame#PLACING_CITY PLACING_CITY}, or {@link SOCGame#PLACING_SHIP PLACING_SHIP}.
     * When placing the second free road or ship, {@link SOCGame#PLACING_FREE_ROAD2 PLACING_FREE_ROAD2}.
     *<P>
     * Before v2.0.00 and i18n, button state was checked by comparing the button text to "Buy" or "Cancel".
     * @since 2.0.00
     */
    private int pieceButtonsState;

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
        roadT = new Label(strings.get("build.road"));  // "Road: "
        add(roadT);
        new AWTToolTip(strings.get("build.road.vp"), roadT);  // "0 VP  (longest road = 2 VP)"
        roadC = new Label(strings.get("build.cost"));  // "Cost:"
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

        settlementT = new Label(strings.get("build.settlement"));  // "Settlement: "
        add(settlementT);
        new AWTToolTip(strings.get("build.1.vp"), settlementT);  // "1 VP"
        settlementC = new Label(strings.get("build.cost"));  // "Cost: "
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

        cityT = new Label(strings.get("build.city.upg"));  // "City Upgrade: "
        add(cityT);
        new AWTToolTip(strings.get("build.city.upg.vp"), cityT);  // "2 VP  (receives 2x rsrc.)"
        cityC = new Label(strings.get("build.cost"));  // "Cost: "
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

        gameInfoBut = new Button(strings.get("build.game.info"));  // "Game Info..." -- show game options
        add(gameInfoBut);
        gameInfoBut.addActionListener(this);

        //TODO: disable until the game initialization is complete and the first roll is made
        statsBut = new Button(strings.get("build.game.stats"));  // "Game Statistics..."
        add(statsBut);
        statsBut.addActionListener(this);

        cardT = new Label(strings.get("build.dev.card"));  // "Dev Card: "
        add(cardT);
        new AWTToolTip (/*I*/"? VP  (largest army = 2 VP) "/*18N*/, cardT);
        cardC = new Label(strings.get("build.cost"));  // "Cost: "
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
        //TODO i18n: Is 'Available X' better than 'X available' in some languages?
        cardCountLab = new Label(strings.get("build.available"));  // "available"
        cardCountLab.setAlignment(Label.LEFT);
        add(cardCountLab);
        cardCount = new ColorSquare(ColorSquare.GREY, 0);
        cardCount.setTooltipText(strings.get("build.dev.cards.available"));  // "Development cards available to buy"
        cardCount.setTooltipLowWarningLevel(strings.get("build.dev.cards.low"), 3);  // "Almost out of development cards to buy"
        cardCount.setTooltipZeroText(strings.get("build.dev.cards.none"));  // "No more development cards available to buy"
        add(cardCount);

        final SOCGame ga = pi.getGame();

        if (ga.hasSeaBoard)
        {
            shipT = new Label(strings.get("build.ship"));  // "Ship: "
            shipT.setAlignment(Label.LEFT);
            add(shipT);
            new AWTToolTip (strings.get("build.ship.vp"), shipT);  // "0 VP  (longest route = 2 VP)"
            shipC = new Label(strings.get("build.cost"));  // "Cost: "
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

                final String TTIP_CLOTH_TEXT = strings.get("build.sc_clvi.cloth.tip");
                    // "General Supply of cloth for any villages shared by multiple players"

                clothLab = new Label(strings.get("build.sc_clvi.cloth"));  // "Cloth:"
                add(clothLab);
                new AWTToolTip(TTIP_CLOTH_TEXT, clothLab);
                cloth = new ColorSquare(ColorSquare.GREY, 0);
                add(cloth);
                cloth.setTooltipText(TTIP_CLOTH_TEXT);
            }
            else if (ga.isGameOptionSet(SOCGameOption.K_SC_WOND))
            {
                wondersBut = new Button(strings.get("build.specitem._SC_WOND"));  // "Wonders..."
                add(wondersBut);
                new AWTToolTip(strings.get("build.specitem._SC_WOND.tip"), wondersBut);  // "Build or get info about the Wonders"
                wondersBut.addActionListener(this);
            }
        } else {
            // shipBut, cloth, wondersBut already null
        }

        if (ga.hasSeaBoard || (ga.vp_winner != 10))  // 10, not SOCGame.VP_WINNER_STANDARD, in case someone changes that
        {
            final String TTIP_VP_TEXT = strings.get("build.vp.to.win.tip");  // "Victory Points total needed to win the game"

            // add vpToWin above its label (z-order) in case of slight overlap
            vpToWin = new ColorSquare(ColorSquare.GREY, ga.vp_winner);
            vpToWin.setTooltipText(TTIP_VP_TEXT);
            add(vpToWin);

            vpToWinLab = new Label(strings.get("build.vp.to.win"));  // "VP to win:"
            vpToWinLab.setAlignment(Label.RIGHT);
            add(vpToWinLab);
            new AWTToolTip(TTIP_VP_TEXT, vpToWinLab);
        } else {
            vpToWinLab = null;
            vpToWin = null;
        }

        if (ga.maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            sbIsHilight = false;
            sbPanel = new Panel(null) {
                /** Custom layout for this Panel, with label/button centered on 1 line or 2 lines based on their text widths.
                 *  Line height is {@link ColorSquare#HEIGHT}, the same used in {@link SOCBuildingPanel#doLayout()}.
                 */
                public void doLayout() {
                    final Dimension dim = getSize();
                    final FontMetrics fm = this.getFontMetrics(this.getFont());
                    if ((dim.height == 0) || (sbBut == null) || (fm == null))
                    {
                        invalidate();
                        return;  // <--- not ready for layout yet ---
                    }
                    int lineH = sbBut.getMinimumSize().height;
                    if (lineH == 0)
                        lineH = sbBut.getPreferredSize().height;
                    if (lineH == 0)
                        lineH = ColorSquare.HEIGHT;

                    final int lblW = (sbLab != null) ? fm.stringWidth(sbLab.getText()) : 0;
                    int btnW = sbBut.getPreferredSize().width;
                    final int btnTxtW = 8 + sbBut.getFontMetrics(sbBut.getFont()).stringWidth(sbBut.getLabel());
                    if (btnTxtW > btnW)
                        btnW = btnTxtW;
                    if (btnW > dim.width)
                        btnW = dim.width;

                    final int bothW = (lblW + btnW + 4 + 2 + 2);
                    if ((sbLab == null) || (bothW <= dim.width))
                    {
                        // layout on 1 line
                        int y = (dim.height - lineH) / 2;
                        int x = (dim.width - bothW) / 2;
                        if (sbLab != null)
                        {
                            sbLab.setLocation(x, y);
                            sbLab.setSize(lblW, lineH);
                            x += lblW + 4;
                        } else {
                            x += 2;
                            ++y;
                        }
                        sbBut.setLocation(x, y);
                        sbBut.setSize(btnW, lineH);
                    } else {
                        // layout on 2 lines; y == line height == half of panel height, then adjust for padding
                        int y = (dim.height / 2) - 1;
                        sbLab.setLocation(0, 0);
                        sbLab.setSize(dim.width, y);
                        sbBut.setLocation((dim.width - btnW) / 2, y);
                        sbBut.setSize(btnW, y);
                    }
                }
            };
            sbPanel.setBackground(ColorSquare.GREY);
            if (ga.hasSeaBoard)
            {
                // Large board: 1 line, no label
                sbBut = new Button(strings.get("build.special.build"));  // "Special Build"
            } else {
                // Standard board: 2 lines, label and button
                sbLab = new Label(strings.get("build.special.build.phase"), Label.CENTER);  // "Special Building Phase"
                sbBut = new Button(strings.get("build.buybuild"));  // "Buy/Build"
            }
            sbBut.setEnabled(false);
            sbBut.setActionCommand(SBP);
            sbBut.addActionListener(this);
            if (sbLab != null)
                sbPanel.add(sbLab);
            sbPanel.add(sbBut);
            add(sbPanel);

            final String TTIP_SBP_TEXT = strings.get("build.special.build.tip");
                // "This phase allows building between player turns."
            new AWTToolTip(TTIP_SBP_TEXT, sbPanel);
            if (sbLab != null)
                new AWTToolTip(TTIP_SBP_TEXT, sbLab);
        }

    }

    /**
     * custom layout for this panel.
     * Layout line height is based on {@link ColorSquare#HEIGHT}.
     * If you change the line spacing or total height laid out here,
     * please update {@link #MINHEIGHT}.
     *<P>
     * For 6-player games, {@link #sbPanel} is 2 "layout lines" tall here
     * on the standard board, 1 line tall on the large board,
     * and has its own custom {@code doLayout()} based on whether its label
     * and button will fit on the same line or must be wrapped to 2 lines.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();
        final int maxPlayers = pi.getGame().maxPlayers;
        final boolean hasLargeBoard = pi.getGame().hasSeaBoard;
        int curY = 1;
        int curX;
        FontMetrics fm = this.getFontMetrics(this.getFont());
        final int lineH = ColorSquare.HEIGHT;
        final int rowSpaceH = lineH / 2;
        final int costW = fm.stringWidth(roadC.getText().trim()) + 4;  // "Cost:"
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
        curX += costW + margin;
        roadWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        roadWood.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        roadClay.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        roadClay.setLocation(curX, curY);

        if (shipBut != null)
        {
            // Ship buying button is top-center of panel
            // (3 squares over from Road)
            final int shipTW = fm.stringWidth(shipT.getText());
            curX += 3 * (ColorSquare.WIDTH + 2);
            shipT.setSize(shipTW, lineH);
            shipT.setLocation(curX, curY);
            curX += shipTW + margin;
            shipBut.setSize(butW, lineH);
            shipBut.setLocation(curX, curY);
            curX += butW + margin;
            shipC.setSize(costW, lineH);
            shipC.setLocation(curX, curY);
            curX += costW + margin;
            shipWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
            shipWood.setLocation(curX, curY);
            curX += (ColorSquare.WIDTH + 2);
            shipSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
            shipSheep.setLocation(curX, curY);
        }

        curY += (rowSpaceH + lineH);

        settlementT.setSize(settlementTW, lineH);
        settlementT.setLocation(margin, curY);
        settlementBut.setSize(butW, lineH);
        settlementBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        settlementC.setSize(costW, lineH);
        settlementC.setLocation(curX, curY);
        curX += costW + margin;
        settlementWood.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementWood.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        settlementClay.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementClay.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        settlementWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        settlementSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        settlementSheep.setLocation(curX, curY);

        if (maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            curX += (ColorSquare.WIDTH + 3);
            if (hasLargeBoard)
            {
                // Large Board: 1 line, no label
                sbPanel.setSize(dim.width - curX - margin, rowSpaceH + lineH);
                sbPanel.setLocation(curX, curY - (rowSpaceH / 2));
            } else {
                // Standard board: 2 lines, label and button
                sbPanel.setSize(dim.width - curX - margin, rowSpaceH + 2 * lineH);
                // sbBut.setSize(dim.width - curX - margin - 2 * buttonMargin, lineH);
                // (can't set size, FlowLayout will override it)
                sbPanel.setLocation(curX, curY);
            }
        }

        curY += (rowSpaceH + lineH);

        cityT.setSize(cityTW, lineH);
        cityT.setLocation(margin, curY);
        cityBut.setSize(butW, lineH);
        cityBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        cityC.setSize(costW, lineH);
        cityC.setLocation(curX, curY);
        curX += costW + margin;
        cityWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cityWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        cityOre.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cityOre.setLocation(curX, curY);

        if (cloth != null)
        {
            // Cloth General Supply count is 3rd row, 2 squares to right of City costs
            final int clothTW = fm.stringWidth(clothLab.getText());
            curX += 3 * (ColorSquare.WIDTH + 2);
            clothLab.setSize(clothTW + (2 * margin) - 1, lineH);
            clothLab.setLocation(curX, curY);
            curX += clothTW + (2 * margin);
            cloth.setLocation(curX, curY);
        }

        if (wondersBut != null)
        {
            // Wonders button takes same place that clothLab would: 3rd row, 2 squares to right of City costs
            curX += 3 * (ColorSquare.WIDTH + 2);
            wondersBut.setSize(dim.width - curX - (2 * butW) - (2 * margin), lineH);
            wondersBut.setLocation(curX, curY);
        }

        curY += (rowSpaceH + lineH);

        cardT.setSize(fm.stringWidth(cardT.getText()), lineH);
        cardT.setLocation(margin, curY);
        cardBut.setSize(butW, lineH);
        cardBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + margin;
        cardC.setSize(costW, lineH);
        cardC.setLocation(curX, curY);
        curX += costW + margin;
        cardWheat.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardWheat.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        cardSheep.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardSheep.setLocation(curX, curY);
        curX += (ColorSquare.WIDTH + 2);
        cardOre.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardOre.setLocation(curX, curY);

        curX += 2 * (ColorSquare.WIDTH + 2);
        // cardCount.setSize(ColorSquare.WIDTH, ColorSquare.HEIGHT);
        cardCount.setLocation(curX, curY);
        final int cardCLabW = fm.stringWidth(cardCountLab.getText());
        curX += (ColorSquare.WIDTH + 3);
        cardCountLab.setLocation(curX, curY);
        cardCountLab.setSize(cardCLabW + 2, lineH);

        // Game Info button is bottom-right of panel.
        // Game Statistics button is just above it on the classic board, top-right for large board.
        // On 4-player classic board, Game Info is moved up to make room for the dev card count.
        if ((maxPlayers <= 4) && ! hasLargeBoard)
            curY -= (lineH + 5);

        curX = dim.width - (2 * butW) - margin;
        gameInfoBut.setSize(butW * 2, lineH);
        gameInfoBut.setLocation(curX, curY);
        statsBut.setSize(butW * 2, lineH);
        if (hasLargeBoard)
            statsBut.setLocation(curX, 1 + (2 * (rowSpaceH + lineH)));
        else
            statsBut.setLocation(curX, 1);

        // VP to Win label moves to make room for various buttons.
        if (vpToWin != null)
        {
            // #VP total to Win
            int vpLabW = fm.stringWidth(vpToWinLab.getText());

            if (hasLargeBoard)
            {
                // bottom-right corner of panel, left of Game Info
                curX -= (ColorSquare.WIDTH + (2*margin));
                vpToWin.setLocation(curX, curY);

                curX -= (vpLabW + (2*margin));
                vpToWinLab.setLocation(curX, curY);
                vpToWinLab.setSize(vpLabW + margin, lineH);
            } else {
                // upper-right corner of panel
                //    If 6-player, shift left to make room for Game Stats button
                //    (which is moved up to make room for Special Building button)
                if (maxPlayers <= 4)
                {
                    // 4-player: row 2, align from right; not enough room on row 1 with Game Stats button
                    curY = 1 + (rowSpaceH + lineH);

                    curX = dim.width - ColorSquare.WIDTH - margin;
                    vpToWin.setLocation(curX, curY);

                    curX -= (vpLabW + (2*margin));
                    vpToWinLab.setLocation(curX, curY);
                } else {
                    // 6-player: row 1, align from left if possible, above "special building" button's wide panel
                    curY = 1;
                    curX = buttonMargin + butW + margin + (costW + margin) + (4 * (ColorSquare.WIDTH + 2));
                    final int statsButX = statsBut.getX();
                    if (curX + ColorSquare.WIDTH + vpLabW + (2*margin) > statsButX)
                        curX -= (2 * (ColorSquare.WIDTH + 2));
                    vpToWinLab.setLocation(curX, curY);

                    curX += (vpLabW + (2*margin));
                    final int xmax = statsButX - ColorSquare.WIDTH - margin;
                    if (curX > xmax)
                    {
                        vpLabW = xmax - vpToWinLab.getX() - margin;  // clip to prevent overlap
                        curX = xmax;
                    }
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
        else if (e.getSource() == wondersBut)
        {
            final SOCSpecialItemDialog dia = new SOCSpecialItemDialog(pi, SOCGameOption.K_SC_WOND);
            dia.setNonBlockingDialogDismissListener(pi);
            pi.nbdForEvent = dia;
            dia.pack();
            dia.setVisible(true);  // is modal but other players' gameplay can continue (separate threads)

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

        // TODO i18n: don't rely on label text

        if (target == ROAD)
        {
            if (pieceButtonsState == 0)
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.ROAD;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if ((pieceButtonsState == SOCGame.PLACING_ROAD) || (pieceButtonsState == SOCGame.PLACING_FREE_ROAD2))
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.ROAD);
            }
        }
        else if (target == STLMT)
        {
            if (pieceButtonsState == 0)
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.SETTLEMENT;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (pieceButtonsState == SOCGame.PLACING_SETTLEMENT)
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.SETTLEMENT);
            }
        }
        else if (target == CITY)
        {
            if (pieceButtonsState == 0)
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.CITY;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if (pieceButtonsState == SOCGame.PLACING_CITY)
            {
                client.getGameManager().cancelBuildRequest(game, SOCPlayingPiece.CITY);
            }
        }
        else if (target == CARD)
        {
            if (pieceButtonsState == 0)
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
            if (pieceButtonsState == 0)
            {
                if (stateBuyOK)
                    sendBuildRequest = SOCPlayingPiece.SHIP;
                else if (canAskSBP)
                    sendBuildRequest = -1;
            }
            else if ((pieceButtonsState == SOCGame.PLACING_SHIP) || (pieceButtonsState == SOCGame.PLACING_FREE_ROAD2))
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
     * Update the status of the buttons. Each piece type's button is labeled "Buy" or disabled ("---")
     * depending on game state and resources available, unless we're currently placing a bought piece.
     * In that case the bought piece type's button is labeled "Cancel", and the others are disabled with
     * their current labels until placement is complete.
     */
    public void updateButtonStatus()
    {
        SOCGame game = pi.getGame();

        pieceButtonsState = 0;  // If placing a piece, if-statements here will set the right state

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
                roadBut.setLabel(strings.get("base.cancel"));  // "Cancel"
                pieceButtonsState = (gstate == SOCGame.PLACING_FREE_ROAD2) ? gstate : SOCGame.PLACING_ROAD;
            }
            else if (game.couldBuildRoad(pnum))
            {
                roadBut.setEnabled(currentCanBuy);
                roadBut.setLabel(strings.get("build.buy"));  // "Buy"
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
                settlementBut.setLabel(strings.get("base.cancel"));
                pieceButtonsState = SOCGame.PLACING_SETTLEMENT;
            }
            else if (game.couldBuildSettlement(pnum))
            {
                settlementBut.setEnabled(currentCanBuy);
                settlementBut.setLabel(strings.get("build.buy"));
            }
            else
            {
                settlementBut.setEnabled(false);
                settlementBut.setLabel("---");
            }

            if (isCurrent && (gstate == SOCGame.PLACING_CITY))
            {
                cityBut.setEnabled(true);
                cityBut.setLabel(strings.get("base.cancel"));
                pieceButtonsState = SOCGame.PLACING_CITY;
            }
            else if (game.couldBuildCity(pnum))
            {
                cityBut.setEnabled(currentCanBuy);
                cityBut.setLabel(strings.get("build.buy"));
            }
            else
            {
                cityBut.setEnabled(false);
                cityBut.setLabel("---");
            }

            if (game.couldBuyDevCard(pnum))
            {
                cardBut.setEnabled(currentCanBuy);
                cardBut.setLabel(strings.get("build.buy"));
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
                    shipBut.setLabel(strings.get("base.cancel"));
                    pieceButtonsState = gstate;  // PLACING_SHIP or PLACING_FREE_ROAD2
                    // ships were added after VERSION_FOR_CANCEL_FREE_ROAD2, so no need to check server version
                    // to make sure the server supports canceling.
                }
                else if (game.couldBuildShip(pnum))
                {
                    shipBut.setEnabled(currentCanBuy);
                    shipBut.setLabel(strings.get("build.buy"));
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
                    if (sbLab != null)
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
