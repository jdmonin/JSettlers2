/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2014,2016-2019 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCCity;
import soc.game.SOCDevCard;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * This class is a panel that shows how much it costs
 * to build things, and it allows the player to build.
 * Sits within a game's {@link SOCPlayerInterface} frame,
 * which also sets the building panel's size based on
 * the frame's width and this panel's {@link #MINHEIGHT}.
 * @see NewGameOptionsFrame
 * @see GameStatisticsFrame
 */
@SuppressWarnings("serial")
/*package*/ class SOCBuildingPanel extends JPanel
    implements ActionListener, WindowListener
{
    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    static final String ROAD = "road";  // I18N: These are internal command labels, not user-visible strings
    static final String STLMT = "stlmt";  // Build Settlement
    static final String CITY = "city";
    static final String CARD = "card";
    static final String SHIP = "ship";  // Ship for large sea board; @since 2.0.00
    private static final String SBP = "sbp";  // Special Building Phase button; @since 1.1.08
    JLabel title;
    JButton roadBut;
    JButton settlementBut;
    JButton cityBut;
    JButton cardBut;

    /**
     * "Options..." button; click to show {@link SOCGameOption}s in an {@link #ngof} frame.
     * Before v1.2.00, label was "Game Options...".
     * Before v2.0.00 this button was {@code optsBut}.
     * @since 1.1.07
     */
    JButton gameOptsBut;

    /**
     * Click to show game statistics in {@link #statsFrame}.
     * @since 2.0.00
     */
    JButton statsBut;

    GameStatisticsFrame statsFrame;

    JLabel roadT;  // text
    ArrowheadPanel roadC;  // cost arrow
    final ColorSquare[] roadSq;  // displayed cost
    JLabel settlementT;  // text
    ArrowheadPanel settlementC;  // cost arrow
    final ColorSquare[] settlementSq;  // displayed cost
    JLabel cityT;
    ArrowheadPanel cityC;
    final ColorSquare[] citySq;
    JLabel cardT;
    ArrowheadPanel cardC;
    JLabel cardCountLab;
    private JLabel vpToWinLab;  // null unless hasSeaBoard or vp != 10; @since 1.1.14
    final ColorSquare[] cardSq;
    ColorSquare cardCount;
    private ColorSquare vpToWin;  // null unless hasSeaBoard or vp != 10; @since 1.1.14

    /** For game scenario {@link SOCGameOption#K_SC_CLVI _SC_CLVI}, the
     *  amount of cloth left in the board's "general supply". Null otherwise.
     *  @since 2.0.00
     */
    private ColorSquare cloth;
    private JLabel clothLab;

    /** For game scenario {@link SOCGameOption#K_SC_WOND _SC_WOND}, the
     *  "Wonders" button that brings up a dialog with info and Build buttons. Null otherwise.
     *  @see #clickWondersButton()
     *  @since 2.0.00
     */
    private JButton wondersBut;

    // Large Sea Board Ship button; @since 2.0.00
    private JLabel shipT;  // text
    private ArrowheadPanel shipC;  // cost
    private final ColorSquare[] shipSq;

    /**
     * For large sea board ({@link SOCGame#hasSeaBoard}), button to buy a ship.
     * Null if this game doesn't have that board.
     * @since 2.0.00
     */
    private JButton shipBut;

    /** For 6-player board: request Special Building Phase.
     *  Given variable custom layout in v2.0.00 for i18n. Contains {@link #sbLab} and {@link #sbBut}
     *  centered on 1 line or 2 lines based on their text widths.
     *  (Large Board omits {@code sbLab}.)
     *  @since 1.1.08
     */
    private JPanel sbPanel;
    private JButton sbBut;
    /** "Special Building Phase" label. Not used on Large Board due to space constraints. */
    private JLabel sbLab;
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
     * "Game Options" window, from {@link #gameOptsBut} click, or null.
     * Tracked to prevent showing more than 1 at a time.
     * @since 1.1.18
     */
    private NewGameOptionsFrame ngof;

    /** Our parent window */
    SOCPlayerInterface pi;

    /**
     * Minimum required size of this panel, as laid out in {@link #doLayout()}.
     * Not scaled to {@link SOCPlayerInterface#displayScale}.
     * @since 1.1.08
     */
    public static final int MINHEIGHT = 2 + (4 * ColorSquare.HEIGHT) + (3 * 4) + 2;  // rowSpaceH == 4

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
    public SOCBuildingPanel(final SOCPlayerInterface pi)
    {
        super();
        setLayout(null);

        this.player = null;
        this.pi = pi;

        final Font panelFont = new Font("Dialog", Font.PLAIN, 10 * pi.displayScale);

        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        if (! isOSHighContrast)
        {
            final Color colors[] = SwingMainDisplay.getForegroundBackgroundColors(true, false);
            setBackground(colors[2]);  // SwingMainDisplay.DIALOG_BG_GOLDENROD
            setForeground(colors[0]);  // Color.BLACK
        }
        setFont(panelFont);

        /*
           title = new Label("Building Costs:");
           title.setAlignment(Label.CENTER);
           add(title);
         */

        final int costsH = 10 * pi.displayScale - 1, costsW = 7 * pi.displayScale - 1;
        final String costsTooltip = strings.get("build.cost_to_build");  // "Cost to Build"
        final Component arrowColorsFrom = (isOSHighContrast) ? null : this;

        roadT = new JLabel(strings.get("build.road"));  // "Road: "
        roadT.setToolTipText(strings.get("build.road.vp"));  // "0 VP  (longest road = 2 VP)"
        add(roadT);
        roadC = new ArrowheadPanel(costsW, costsH, costsTooltip, arrowColorsFrom);
        add(roadC);
        roadSq = makeCostSquares(SOCRoad.COST);
        roadBut = new JButton("---");
        roadBut.setEnabled(false);
        // note: will each JButton.setBackground(null) at end of constructor if IS_PLATFORM_WINDOWS
        add(roadBut);
        roadBut.setActionCommand(ROAD);
        roadBut.addActionListener(this);

        settlementT = new JLabel(strings.get("build.settlement"));  // "Settlement: "
        settlementT.setToolTipText(strings.get("build.1.vp"));  // "1 VP"
        add(settlementT);
        settlementC = new ArrowheadPanel(costsW, costsH, costsTooltip, arrowColorsFrom);
        add(settlementC);
        settlementSq = makeCostSquares(SOCSettlement.COST);
        settlementBut = new JButton("---");
        settlementBut.setEnabled(false);
        add(settlementBut);
        settlementBut.setActionCommand(STLMT);
        settlementBut.addActionListener(this);

        cityT = new JLabel(strings.get("build.city"));  // "City: "
        cityT.setToolTipText(strings.get("build.city.vp"));  // "2 VP  (receives 2x rsrc.)"
        add(cityT);
        cityC = new ArrowheadPanel(costsW, costsH, costsTooltip, arrowColorsFrom);
        add(cityC);
        citySq = makeCostSquares(SOCCity.COST);
        cityBut = new JButton("---");
        cityBut.setEnabled(false);
        add(cityBut);
        cityBut.setActionCommand(CITY);
        cityBut.addActionListener(this);

        gameOptsBut = new JButton(strings.get("build.game.options"));  // "Options..." -- show game options
        add(gameOptsBut);
        gameOptsBut.addActionListener(this);

        //TODO: disable until the game initialization is complete and the first roll is made
        statsBut = new JButton(strings.get("build.game.stats"));  // "Statistics..."
        add(statsBut);
        statsBut.addActionListener(this);

        final int sqHeight = ColorSquare.HEIGHT * pi.displayScale;

        cardT = new JLabel(strings.get("build.dev.card"));  // "Dev Card: "
        cardT.setToolTipText(/*I*/"? VP  (largest army = 2 VP) "/*18N*/);
        add(cardT);
        cardC = new ArrowheadPanel(costsW, costsH, costsTooltip, arrowColorsFrom);
        add(cardC);
        cardSq = makeCostSquares(SOCDevCard.COST);
        cardBut = new JButton("---");
        cardBut.setEnabled(false);
        add(cardBut);
        cardBut.setActionCommand(CARD);
        cardBut.addActionListener(this);
        // Development Card count. Initial amount will be sent from server soon.
        //TODO i18n: Is 'Available X' better than 'X available' in some languages?
        cardCountLab = new JLabel(strings.get("build.available"), SwingConstants.LEFT);  // "available"
        add(cardCountLab);
        cardCount = new ColorSquare(ColorSquare.GREY, 0, sqHeight, sqHeight);
        cardCount.setToolTipText(strings.get("build.dev.cards.available"));  // "Development cards available to buy"
        cardCount.setToolTipLowWarningLevel(strings.get("build.dev.cards.low"), 3);  // "Almost out of development cards to buy"
        cardCount.setToolTipZeroText(strings.get("build.dev.cards.none"));  // "No more development cards available to buy"
        add(cardCount);

        final SOCGame ga = pi.getGame();

        if (ga.hasSeaBoard)
        {
            shipT = new JLabel(strings.get("build.ship"), SwingConstants.LEFT);  // "Ship: "
            shipT.setToolTipText(strings.get("build.ship.vp"));  // "0 VP  (longest route = 2 VP)"
            add(shipT);
            shipC = new ArrowheadPanel(costsW, costsH, costsTooltip, arrowColorsFrom);
            add(shipC);
            shipSq = makeCostSquares(SOCShip.COST);
            shipBut = new JButton("---");
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

                clothLab = new JLabel(strings.get("build.sc_clvi.cloth"));  // "Cloth:"
                clothLab.setToolTipText(TTIP_CLOTH_TEXT);
                add(clothLab);
                cloth = new ColorSquare(ColorSquare.GREY, 0, sqHeight, sqHeight);
                add(cloth);
                cloth.setToolTipText(TTIP_CLOTH_TEXT);
            }
            else if (ga.isGameOptionSet(SOCGameOption.K_SC_WOND))
            {
                wondersBut = new JButton(strings.get("build.specitem._SC_WOND"));  // "Wonders..."
                wondersBut.setToolTipText(strings.get("build.specitem._SC_WOND.tip"));  // "Build or get info about the Wonders"
                add(wondersBut);
                wondersBut.addActionListener(this);
            }
        } else {
            // shipBut, cloth, wondersBut already null
            shipSq = null;
        }

        if (ga.hasSeaBoard || (ga.vp_winner != 10))  // 10, not SOCGame.VP_WINNER_STANDARD, in case someone changes that
        {
            final String TTIP_VP_TEXT = strings.get("build.vp.to.win.tip");  // "Victory Points total needed to win the game"

            // add vpToWin above its label (z-order) in case of slight overlap
            vpToWin = new ColorSquare(ColorSquare.GREY, ga.vp_winner, sqHeight, sqHeight);
            vpToWin.setToolTipText(TTIP_VP_TEXT);
            add(vpToWin);

            vpToWinLab = new JLabel(strings.get("build.vp.to.win"), SwingConstants.RIGHT);  // "VP to win:"
            vpToWinLab.setToolTipText(TTIP_VP_TEXT);
            add(vpToWinLab);
        } else {
            vpToWinLab = null;
            vpToWin = null;
        }

        if (ga.maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            sbIsHilight = false;
            sbPanel = new JPanel(null)
            {
                /** Custom layout for this Panel, with button and optional label centered on their own lines.
                 *  Line height is {@link ColorSquare#HEIGHT}, the same used in {@link SOCBuildingPanel#doLayout()}.
                 */
                public void doLayout()
                {
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
                        lineH = sqHeight;

                    int btnW = sbBut.getPreferredSize().width;
                    final int btnTxtW =
                        (8 * pi.displayScale) + sbBut.getFontMetrics(sbBut.getFont()).stringWidth(sbBut.getText());
                    if (btnTxtW > btnW)
                        btnW = btnTxtW;
                    if (btnW > dim.width)
                        btnW = dim.width;

                    if (sbLab == null)
                    {
                        // button only: layout on 1 line
                        int x = (dim.width - btnW) / 2;
                        int y = (dim.height - lineH) / 2 - 1;
                        sbBut.setLocation(x, y);
                        sbBut.setSize(btnW, lineH);
                    } else {
                        // layout on 2 lines; y == line height == half of panel height, then adjust for padding
                        int y = (dim.height / 2) - 1;
                        sbLab.setLocation(0, 0);
                        sbLab.setSize(dim.width, y);  // text is centered within label width
                        sbBut.setLocation((dim.width - btnW) / 2, y);
                        sbBut.setSize(btnW, y);
                    }
                }
            };
            sbPanel.setBackground(ColorSquare.GREY);

            if (ga.hasSeaBoard)
            {
                // Large board: 1 line, no room for sbLab
                sbBut = new JButton(strings.get("build.special.build"));  // "Special Build"
            } else {
                // Classic board: 2 lines, label and button
                sbLab = new JLabel(strings.get("build.special.build.phase"), SwingConstants.CENTER);  // "Special Building Phase"
                sbLab.setFont(panelFont);
                sbBut = new JButton(strings.get("build.buybuild"));  // "Buy/Build"
            }
            if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSHighContrast)
                sbBut.setBackground(null);
            sbBut.setFont(panelFont);
            sbBut.setEnabled(false);
            sbBut.setActionCommand(SBP);
            sbBut.addActionListener(this);

            if (sbLab != null)
                sbPanel.add(sbLab);
            sbPanel.add(sbBut);
            add(sbPanel);

            final String TTIP_SBP_TEXT = strings.get("build.special.build.tip");
                // "This phase allows building between player turns."
            sbPanel.setToolTipText(TTIP_SBP_TEXT);
            if (sbLab != null)
                sbLab.setToolTipText(TTIP_SBP_TEXT);
        }

        // make all labels and buttons use panel's font and background color;
        // to not cut off wide button text, remove button margin since we're using custom layout anyway
        final int pix2 = 2 * pi.displayScale;
        final Insets minMargin = new Insets(pix2, pix2, pix2, pix2);
        final boolean shouldClearButtonBGs = SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSHighContrast;
        for (Component co : getComponents())
        {
            if (! ((co instanceof JLabel) || (co instanceof JButton)))
                continue;

            co.setFont(panelFont);
            if (co instanceof JLabel)
            {
                ((JLabel) co).setVerticalAlignment(JLabel.TOP);
            } else {
                ((JButton) co).setMargin(minMargin);
                if (shouldClearButtonBGs)
                    co.setBackground(null);  // required on win32 to avoid gray corners on JButton
            }
        }
    }

    /**
     * custom layout for this panel.
     * Layout line height is based on {@link ColorSquare#HEIGHT} * {@link SOCPlayerInterface#displayScale}.
     * If you change the line spacing or total height laid out here, please update {@link #MINHEIGHT}.
     *<P>
     * For 6-player games, {@link #sbPanel} is 2 "layout lines" tall here
     * on the classic board, 1 line tall on the large board,
     * and has its own custom {@code doLayout()} based on whether its label
     * and button will fit on the same line or must be wrapped to 2 lines.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();
        final int maxPlayers = pi.getGame().maxPlayers;
        final boolean hasLargeBoard = pi.getGame().hasSeaBoard;
        FontMetrics fm = this.getFontMetrics(this.getFont());
        final int pix1 = pi.displayScale;  // 1 pixel, scaled
        final int lineH = ColorSquare.HEIGHT * pi.displayScale;  // not including rowSpaceH
        final int rowSpaceH = 4 * pi.displayScale;
        final int sqWidth = ColorSquare.WIDTH * pi.displayScale;
        final int margin = 2 * pi.displayScale;
        int curY = margin;
        int curX;
        final int costW = roadC.getWidth() + pix1,  // left arrow for Cost colorsquares
                  costDY = (lineH - roadC.getHeight()) / 2;  // delta-Y to center Costs within lineH
        final int butW = 62 * pi.displayScale;   // all Build buttons

        final int roadTW = fm.stringWidth(roadT.getText()),
                  settlementTW = fm.stringWidth(settlementT.getText()),
                  cityTW = fm.stringWidth(cityT.getText()),
                  cardTW = fm.stringWidth(cardT.getText());
        int buttonMargin = (settlementTW > cityTW) ? settlementTW : cityTW;
        if (roadTW > buttonMargin)
            buttonMargin = roadTW;
        if (cardTW > buttonMargin)
            buttonMargin = cardTW;
        buttonMargin += 2 * margin;

        /*
           title.setSize(dim.width, lineH);
           title.setLocation(0, 0);
           curY += lineH;
         */
        roadT.setSize(roadTW, lineH);
        roadT.setLocation(margin, curY);
        roadBut.setSize(butW, lineH);
        roadBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + pix1;
        roadC.setLocation(curX, curY + costDY);
        curX += costW + margin;
        curX = layoutCostSquares(roadSq, curX, curY);  // 2 squares

        if (shipBut != null)
        {
            // Ship buying button is top-center of panel
            // (2 squares over from Road)
            final int shipTW = fm.stringWidth(shipT.getText());
            curX += 2 * (sqWidth + margin);
            shipT.setSize(shipTW, lineH);
            shipT.setLocation(curX, curY);
            curX += shipTW + margin;
            shipBut.setSize(butW, lineH);
            shipBut.setLocation(curX, curY);
            curX += butW + pix1;
            shipC.setLocation(curX, curY + costDY);
            curX += costW + margin;
            layoutCostSquares(shipSq, curX, curY);  // 2 squares
        }

        curY += (rowSpaceH + lineH);

        settlementT.setSize(settlementTW, lineH);
        settlementT.setLocation(margin, curY);
        settlementBut.setSize(butW, lineH);
        settlementBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + pix1;
        settlementC.setLocation(curX, curY + costDY);
        curX += costW + margin;
        curX = layoutCostSquares(settlementSq, curX, curY);  // 4 squares

        if (maxPlayers > 4)
        {
            // Special Building Phase button for 6-player game
            curX += (sqWidth + margin + 1);
            if (hasLargeBoard)
            {
                // Large Board: 1 line, no label
                // Leaves room for right-hand column of buttons
                sbPanel.setSize(dim.width - curX - (2 * (butW + margin)), rowSpaceH + lineH);
                sbPanel.setLocation(curX, curY - (rowSpaceH / 2));
            } else {
                // Classic board: 2 lines, label and button
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

        curX = buttonMargin + butW + pix1;
        cityC.setLocation(curX, curY + costDY);
        curX += costW + margin;
        curX = layoutCostSquares(citySq, curX, curY);  // 2 squares

        if (cloth != null)
        {
            // Cloth General Supply count is 3rd row, 2 squares to right of City costs
            final int clothTW = fm.stringWidth(clothLab.getText());
            curX += 3 * (sqWidth + margin);
            clothLab.setSize(clothTW + (2 * margin) - 1, lineH);
            clothLab.setLocation(curX, curY);
            curX += clothTW + (2 * margin);
            cloth.setLocation(curX, curY);
        }

        if (wondersBut != null)
        {
            // Wonders button takes same place that clothLab would: 3rd row, 2 squares to right of City costs
            curX += 3 * (sqWidth + margin);
            wondersBut.setSize(dim.width - curX - (2 * butW) - (2 * margin), lineH);
            wondersBut.setLocation(curX, curY);
        }

        curY += (rowSpaceH + lineH);

        cardT.setSize(cardTW, lineH);
        cardT.setLocation(margin, curY);
        cardBut.setSize(butW, lineH);
        cardBut.setLocation(buttonMargin, curY);

        curX = buttonMargin + butW + pix1;
        cardC.setLocation(curX, curY + costDY);
        curX += costW + margin;
        curX = layoutCostSquares(cardSq, curX, curY);  // 3 squares

        curX += 2 * (sqWidth + margin);
        cardCount.setLocation(curX, curY);
        final int cardCLabW = fm.stringWidth(cardCountLab.getText());
        curX += (sqWidth + margin + 1);
        cardCountLab.setLocation(curX, curY);
        cardCountLab.setSize(cardCLabW + margin, lineH);

        // Make sure building-cost squares don't overlap with right-hand buttons; settlementSq is rightmost of them
        int rightButW = 2 * butW;
        curX = settlementSq[3].getX() + settlementSq[3].getWidth() + margin;
        if ((dim.width - rightButW - margin) < curX)
        {
            rightButW = dim.width - curX - margin;
            // keep current curX
        } else {
            curX = dim.width - rightButW - margin;
            // keep current rightButW
        }

        // Options button is bottom-right of panel.
        // Game Statistics button is just above it on the classic board, top-right for large board.
        // On 4-player classic board, Options button is moved up to make room for the dev card count.
        if ((maxPlayers <= 4) && ! hasLargeBoard)
            curY -= (lineH + (5 * pi.displayScale));

        gameOptsBut.setSize(rightButW, lineH);
        if ((maxPlayers <= 4) && ! hasLargeBoard)
            gameOptsBut.setLocation(curX, pix1 + (rowSpaceH + lineH)); // move up to row 2; row 3 will have VP to Win
        else
            gameOptsBut.setLocation(curX, curY);
        statsBut.setSize(rightButW, lineH);
        if (hasLargeBoard)
            statsBut.setLocation(curX, pix1 + (2 * (rowSpaceH + lineH)));
        else
            statsBut.setLocation(curX, pix1);

        // VP to Win label moves to make room for various buttons.
        if (vpToWin != null)
        {
            // #VP total to Win
            int vpLabW = fm.stringWidth(vpToWinLab.getText());

            if (hasLargeBoard)
            {
                // right-hand side of panel, above Game Stats
                curY = rowSpaceH + lineH;
                curX = dim.width - sqWidth - margin;
                vpToWin.setLocation(curX, curY);

                curX -= (vpLabW + (2*margin));
                vpToWinLab.setLocation(curX, curY);
                vpToWinLab.setSize(vpLabW + margin, lineH);
            } else {
                if (maxPlayers <= 4)
                {
                    // 4-player: row 3, align from right, below Options;
                    // not enough room on row 1 with Game Stats button
                    curY = pix1 + (2 * (rowSpaceH + lineH));
                    curX = dim.width - sqWidth - margin;
                    vpToWin.setLocation(curX, curY);

                    curX -= (vpLabW + (2*margin));
                    vpToWinLab.setLocation(curX, curY);
                } else {
                    // 6-player: row 1, upper-right corner of panel; shift left to make room
                    // for Game Stats button (which is moved up to make room for Special Building button).
                    // Align from left if possible, above Special Building button's wide panel
                    curY = pix1;
                    curX = buttonMargin + butW + margin + (costW + margin) + (4 * (sqWidth + margin));
                    final int statsButX = statsBut.getX();
                    if (curX + sqWidth + vpLabW + (2*margin) > statsButX)
                        curX -= (2 * (sqWidth + margin));
                    vpToWinLab.setLocation(curX, curY);

                    curX += (vpLabW + (2*margin));
                    final int xmax = statsButX - sqWidth - margin;
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

    /** For aesthetics use a certain resource-type order, not {@link SOCResourceConstants}' order. */
    private final static int[] makeCostSquares_resMap =
        { SOCResourceConstants.WOOD, SOCResourceConstants.CLAY, SOCResourceConstants.WHEAT,
          SOCResourceConstants.SHEEP, SOCResourceConstants.ORE };

    /**
     * Given an item's resource cost, make an array of {@link ColorSquare}s to show the cost
     * and {@link java.awt.Container#add(Component) add(Component)} them to this panel.
     * Each ColorSquare's tooltip will get a "Cost to Build" prefix, like: "Cost to Build: Sheep"
     * @param cost  Item's cost; not {@code null}
     * @return  ColorSquares for this item's cost
     * @since 2.0.00
     */
    private ColorSquare[] makeCostSquares(final SOCResourceSet cost)
    {
        final String costToBuild = strings.get("build.cost_to_build");  // "Cost to Build"
        final int n = cost.getResourceTypeCount();
        final ColorSquare[] sq = new ColorSquare[n];
        final int sqHeight = ColorSquare.HEIGHT * pi.displayScale;

        for (int i = 0, mapIdx = 0; i < n && mapIdx < 5; ++mapIdx)
        {
            final int res = makeCostSquares_resMap[mapIdx];  // will be in range 1 to 5
            final int itemCost = cost.getAmount(res);
            if (itemCost == 0)
                continue;

            final ColorSquare s = new ColorSquare(ColorSquare.RESOURCE_COLORS[res - 1], itemCost, sqHeight, sqHeight);
            s.setToolTipText(costToBuild + ": " + s.getToolTipText());  // "Cost to Build: Sheep" etc
            sq[i] = s;
            add(s);
            ++i;
        }

        return sq;
    }

    /**
     * Lay out these item-cost {@link ColorSquare}s in a horizontal row starting at {@code (curX, curY)}.
     * Space between them will be 2 pixels.
     * @param sq  Array of ColorSquares
     * @param curX  X-coordinate to use for first square's {@link ColorSquare#setLocation(int, int)}
     * @param curY  Y-coordinate to use for first square's {@link ColorSquare#setLocation(int, int)}
     * @return curX for next component after laying out all squares in {@code sq};
     *    distance from passed-in {@code curX} will be {@code sq.length} * ({@link ColorSquare#WIDTH} + 2).
     * @since 2.0.00
     */
    private int layoutCostSquares(final ColorSquare[] sq, int curX, final int curY)
    {
        final int sqWidth = ColorSquare.WIDTH * pi.displayScale, step = sqWidth + (2 * pi.displayScale);
        for (int i = 0; i < sq.length; ++i, curX += step)
        {
            sq[i].setSize(sqWidth, sqWidth);
            sq[i].setLocation(curX, curY);
        }

        return curX;
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

        if (e.getSource() == gameOptsBut)
        {
            if ((ngof != null) && ngof.isVisible())
            {
                ngof.setVisible(true);  // method override also requests topmost/focus
            } else {
                ngof = NewGameOptionsFrame.createAndShow
                    (pi, pi.getMainDisplay(), game.getName(), game.getGameOptions(), false, true);
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
            f.setLocation(this.getLocationOnScreen());
            f.setVisible(true);
            statsFrame = f;

            return;
        }
        else if (e.getSource() == wondersBut)
        {
            clickWondersButton();

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
     * @since 1.1.00
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
                client.getGameMessageMaker().cancelBuildRequest(game, SOCPlayingPiece.ROAD);
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
                client.getGameMessageMaker().cancelBuildRequest(game, SOCPlayingPiece.SETTLEMENT);
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
                client.getGameMessageMaker().cancelBuildRequest(game, SOCPlayingPiece.CITY);
            }
        }
        else if (target == CARD)
        {
            if (pieceButtonsState == 0)
            {
                if (stateBuyOK || canAskSBP)
                {
                    client.getGameMessageMaker().buyDevCard(game);
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
                client.getGameMessageMaker().cancelBuildRequest(game, SOCPlayingPiece.SHIP);
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

            client.getGameMessageMaker().buildRequest(game, sendBuildRequest);
            chp.disableBankUndoButton();
        }
    }

    /**
     * For game scenario {@link SOCGameOption#K_SC_WOND _SC_WOND},
     * show the Wonders dialog, as is done when the Wonders button is clicked.
     * @throws IllegalStateException if this game doesn't have {@link SOCGameOption#K_SC_WOND}
     *     and so doesn't have the Wonders button
     * @since 2.0.00
     */
    public void clickWondersButton()
        throws IllegalStateException
    {
        if (wondersBut == null)
            throw new IllegalStateException("game not SC_WOND");

        final SOCSpecialItemDialog dia = new SOCSpecialItemDialog(pi, SOCGameOption.K_SC_WOND);
        dia.setNonBlockingDialogDismissListener(pi);
        pi.nbdForEvent = dia;
        dia.pack();
        dia.setVisible(true);  // is modal but other players' gameplay can continue (separate threads)
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
                roadBut.setText(strings.get("base.cancel"));  // "Cancel"
                pieceButtonsState = (gstate == SOCGame.PLACING_FREE_ROAD2) ? gstate : SOCGame.PLACING_ROAD;
            }
            else if (game.couldBuildRoad(pnum))
            {
                roadBut.setEnabled(currentCanBuy);
                roadBut.setText(strings.get("build.buy"));  // "Buy"
            }
            else
            {
                roadBut.setEnabled(false);
                roadBut.setText("---");
            }

            if (isCurrent &&
                ((gstate == SOCGame.PLACING_SETTLEMENT) || (gstate == SOCGame.START1B)
                 || (gstate == SOCGame.START2B) || (gstate == SOCGame.START3B)) )
            {
                settlementBut.setEnabled(true);
                settlementBut.setText(strings.get("base.cancel"));
                pieceButtonsState = SOCGame.PLACING_SETTLEMENT;
            }
            else if (game.couldBuildSettlement(pnum))
            {
                settlementBut.setEnabled(currentCanBuy);
                settlementBut.setText(strings.get("build.buy"));
            }
            else
            {
                settlementBut.setEnabled(false);
                settlementBut.setText("---");
            }

            if (isCurrent && (gstate == SOCGame.PLACING_CITY))
            {
                cityBut.setEnabled(true);
                cityBut.setText(strings.get("base.cancel"));
                pieceButtonsState = SOCGame.PLACING_CITY;
            }
            else if (game.couldBuildCity(pnum))
            {
                cityBut.setEnabled(currentCanBuy);
                cityBut.setText(strings.get("build.buy"));
            }
            else
            {
                cityBut.setEnabled(false);
                cityBut.setText("---");
            }

            if (game.couldBuyDevCard(pnum))
            {
                cardBut.setEnabled(currentCanBuy);
                cardBut.setText(strings.get("build.buy"));
            }
            else
            {
                cardBut.setEnabled(false);
                cardBut.setText("---");
            }

            if (shipBut != null)
            {
                if (isCurrent && ((gstate == SOCGame.PLACING_SHIP) || (gstate == SOCGame.PLACING_FREE_ROAD2)))
                {
                    shipBut.setEnabled(true);
                    shipBut.setText(strings.get("base.cancel"));
                    pieceButtonsState = gstate;  // PLACING_SHIP or PLACING_FREE_ROAD2
                    // ships were added after VERSION_FOR_CANCEL_FREE_ROAD2, so no need to check server version
                    // to make sure the server supports canceling.
                }
                else if (game.couldBuildShip(pnum))
                {
                    shipBut.setEnabled(currentCanBuy);
                    shipBut.setText(strings.get("build.buy"));
                }
                else
                {
                    shipBut.setEnabled(false);
                    shipBut.setText("---");
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
     * If our "Game Options" window ({@link NewGameOptionsFrame}) is closed,
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

    /**
     * Contains a left-pointing arrowhead (a triangle) that fills its height and width.
     * Found to be more reliable than a JLabel with a Unicode arrowhead, espcially with displayScale > 1.
     * @since 2.0.00
     */
    private static class ArrowheadPanel extends JComponent
    {
        /**
         * Create a new ArrowheadPanel, taking another component's background and foreground colors
         * (or system colors if null).
         */
        public ArrowheadPanel
            (final int width, final int height, final String tooltipText, final Component colorsFrom)
        {
            setOpaque(true);
            if (colorsFrom != null)
            {
                setBackground(colorsFrom.getBackground());
                setForeground(colorsFrom.getForeground());
            } else {
                final Color[] sysColors = SwingMainDisplay.getForegroundBackgroundColors(false, true);
                setForeground(sysColors[0]);
                setBackground(sysColors[2]);
            }

            if (tooltipText != null)
                setToolTipText(tooltipText);

            final Dimension initSize = new Dimension(width, height);
            setSize(initSize);
            setMinimumSize(initSize);
            setPreferredSize(initSize);
        }

        @Override
        public void paintComponent(Graphics g)
        {
            Insets ins = getInsets();
            final int x = ins.left,
                      y = ins.top,
                      w = getWidth() - x - ins.right,
                      h = getHeight() - y - ins.bottom;
            g.setColor(getBackground());
            g.fillRect(x, y, w, h);
            if (g instanceof Graphics2D)
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xp = { x, x + w, x + w };
            int[] yp = { h/2 + y, y, y + h };
            g.setColor(getForeground());
            g.fillPolygon(xp, yp, 3);
        }

    }  // nested class ArrowheadPanel

}
