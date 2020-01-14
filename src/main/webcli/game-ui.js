/*
 game window/board javascript functions, for index.html and its inGames[] array

 Since this modifies the dispatcher array, is loaded asynchronously from index-ws-dispatcher.js.
 This script loads konva and board.js.

 This file is part of the Java Settlers Web App (JSWeb).

 This file Copyright (C) 2020 Jeremy D Monin (jeremy@nand.net)

 JSWeb is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 JSWeb is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with JSWeb.  If not, see <https://www.gnu.org/licenses/>.
 */

$.ajax({
    'url': 'inc/konva-4.0.0.min.js',
    'dataType': 'script',
    'cache': true,
    // success: function() { ... }  TODO allow queue up join-game reqs until konva loaded, then do so here
    });
$.ajax({
    'url': 'board.js',
    'dataType': 'script',
    'cache': (window.location.host != 'localhost'),
    });

// Board/game constants

const PTYPE_ROAD = 0, PTYPE_SETTLEMENT = 1, PTYPE_CITY = 2, PTYPE_SHIP = 3, PTYPE_FORTRESS = 4, PTYPE_VILLAGE = 5;
// type names as in proto/json
const HEXTYPE_NAMES = ['WATER_HEX', 'CLAY_HEX', 'ORE_HEX', 'SHEEP_HEX', 'WHEAT_HEX', 'WOOD_HEX', 'DESERT_HEX', 'GOLD_HEX', 'FOG_HEX'];
const PTYPE_NAMES = ['ROAD', 'SETTLEMENT', 'CITY', 'SHIP', 'FORTRESS', 'VILLAGE'];  // also for ID prefix in rsLayer/ppLayer

// Board geometry constants; some are from SOCBoardPanel.java //

const BOARDWIDTH_VISUAL_MIN = 18, BOARDHEIGHT_VISUAL_MIN = 17;  // half-hex units
const BOARD_BORDER = 20;  // px on each side
const DELTA_X = 92, DELTA_Y = 80, HALF_DELTA_X = DELTA_X / 2, HALF_DELTA_Y = DELTA_Y / 2;  // px offsets for each hex
const HEX_RADIUS = 53, DICENUM_RADIUS = 15;
/** Half the vertical offset for A-nodes vs Y-nodes along a road; half height of an angled edge */
const HEXY_ANGLED_HALF_HEIGHT = 12;


/** water, clay, ore, sheep, wheat, wood, desert, gold, fog */
const HEXTYPE_COLORS = ['#33a', '#cc5544', '#999999', '#22dd22', '#cccc33', '#339922', '#ffff99', '#ff0', '#dcdcdc'];
const PLAYER_COLORS = ['#6d7ce7', '#e72323', '#f4eece', '#f9801d', '#619771', '#a658c9']; // TODO improve

// Other styles

const FONT_FAMILY = 'Verdana, Geneva, sans-serif';

// Misc utilities

function coordHex(r, c)  // -> "0b07"
{
	let hexR = Number(r).toString(16), hexC = Number(c).toString(16);
	if (hexR.length == 1)
	    hexR = '0' + hexR;
	if (hexC.length == 1)
	    hexC = '0' + hexC;
	return hexR + hexC;
}
function coordHex4(coord)  // -> "0007"
{
	return ("000" + Number(coord).toString(16)).slice(-4);
}

function hextypeStyle(htype)
{
	return ((htype >= 0) && (htype < HEXTYPE_COLORS.length)) ? HEXTYPE_COLORS[htype] : '#aaa';
}

// Non-gameMessage handlers; scroll down for GameUI object, inGames[] entries, gameMessage handlers //

function handleGameJoin(mData)
{
    let gaObj = inGames[mData.gaName];
    if (gaObj)
    {
	let h = 0, w = 0, vsD = 0, vsR = 0;
	if (mData.boardSizeVshift)
	{
	    v = mData.boardSizeVshift;
	    h = v[0]; w = v[1];
	    if (v.length >= 4) { vsD = v[2]; vsR = v[3]; }
	}
	gaObj.handleJoin(mData.memberName, h, w, vsD, vsR);
    }
}

function handleGameMembers(mData)
{
    let gaObj = inGames[mData.gaName];
    if (gaObj)
	gaObj.handleMembers(mData.members);
}

function handleGameText(mData)
{
    let gaObj = inGames[mData.gaName];
    if (gaObj)
	gaObj.handleText(mData.memberName, mData.text);
}

function handleLeaveGame(mData)
{
    let gaObj = inGames[mData.gaName];
    if (gaObj)
	gaObj.handleLeave(mData.memberName);
}

function sendLine(gaDoc)
{
    if (! gaDoc)
	return;
    if (wsConn == null)
    {
	alert("Not connected.");
	return;
    }

    let gaObj = gaDoc.soc_gameui_obj;
    if (! gaObj)
	return;
    let txfield = gaDoc.forms.send.txt;
    let txt = txfield.value.trim();
    if (0 == txt.length)
	return;

    txfield.value = "";
    wsConn.send(JSON.stringify({gaPlayerText:{gaName: gaObj.gaName, text: txt}}));
}

// Top-level UI and GameUI object //

function GameUI(gaName)
{
    this.gaName = gaName;
    this.gaMembers = new Set();
    // newGameWindow sets this.gaWindow, and sets gaWindow.document.soc_gameui_obj to this
    // handleMembers sets this.membersJQ and .messagesJQ: jquery objs for member list, chat message divs
    this.cliJoined = false;  // obj & new window must be created from dblclick handler, not server's gaJoin
    this.sentLeaveMsg = false;  // flag for closeGameWindow
    this.hexOffsX = 0;  // px; includes BOARD_BORDER
    this.hexOffsY = 0;
    // initBoard sets this.board (Board obj), .bLayer (board itself's Konva.Layer), .rsLayer (roads/ships), .ppLayer (other playing pieces, pirate)
    this.handleJoin = function(memberName,bh,bw,vsD,vsR)
    {
	// if we're in that game, add person to member list there
	// otherwise if mData doesn't have memberName, it's auth to join game:
	// open a game-UI window and expect gameMembers message soon
	if ((memberName === undefined) && ! this.cliJoined)
	{
	    if (bh < BOARDHEIGHT_VISUAL_MIN)
		bh = BOARDHEIGHT_VISUAL_MIN;
	    if (bw < BOARDWIDTH_VISUAL_MIN)
		bw = BOARDWIDTH_VISUAL_MIN;
	    let canW = bw * HALF_DELTA_X + 2 * BOARD_BORDER, canH = bh * HALF_DELTA_Y + 2 * BOARD_BORDER;
	    this.hexOffsX = vsR * HALF_DELTA_X / 2;
	    this.hexOffsY = vsD * HALF_DELTA_Y / 2;
	    if (vsD > 0)
		canH += this.hexOffsY;
	    if (vsR > 0)
		canW += this.hexOffsX;
	    this.hexOffsX += BOARD_BORDER;  this.hexOffsY += BOARD_BORDER;

	    let uiBody = this.gaWindow.document.body;
	    // inline styles because separate css for js newDiv.className was being ignored
	    uiBody.style.height = '100%'; uiBody.style.margin = '3px';
	    uiBody.innerHTML = '<div style="float:right; min-width: 130px; padding: 3px;"><div id="members" style="position: fixed; margin: 3px; width: max-content;"></div></div>'
		+ '<div style="display: flex; flex-flow: column; height: 100%;">'
		+ '<div id="header" style="flex: 0 1 auto;">' // = flex-grow:0,flex-shrink:1,flex-basis:auto
		+ 'Game: ' + gaName + '<HR noshade></div>'
		+ '<div id="main" style="flex: 1 1 auto; overflow: auto;"><div id="board" style="background-color: #119; height:' + canH + 'px; width:' + canW + 'px;"></div></div>'
		+ '<div id="send" style="flex: 0 1 auto; margin: 3px;">'
		+ '<form name="send" action="javascript:window.opener.sendLine(window.document);void(0);" autocomplete="off">'
		+ '<input name="txt" size=80 autocomplete="off" /> &nbsp; <button type="submit">Send</button>'
		+ '</form></div>'
		+ '</div>'
	    // handleMembers sets up membersJQ, messagesJQ
	    this.cliJoined = true;
	    this.initBoard(bh, bw);
	}
	if (! (this.cliJoined && this.membersJQ))
	    return;
	if (! this.gaMembers.has(memberName))
	{
	    this.gaMembers.add(memberName);
	    let mJQ = this.membersJQ;
	    listAddJq(mJQ, memberName, 100);
	    setTimeout(function(){
		let par = mJQ.parent();
		if (mJQ.outerWidth() > par.width())
		    par.width(mJQ.outerWidth());
	      }, 110);
	}
    };
    /* set up empty board; see boardLayout dispatch function for further work */
    this.initBoard = function(h,w)  // assumes konva.js done loading
    {
	this.board = new Board(h, w);
	let doc = this.gaWindow.document, div = doc.getElementById("board");
	let kstage = new Konva.Stage({container: div, width: div.offsetWidth, height: div.offsetHeight});
	let bLayer = new Konva.Layer();
	this.bLayer = bLayer; this.rsLayer = new Konva.Layer(); this.ppLayer = new Konva.Layer();
	kstage.add(bLayer);
	kstage.add(this.rsLayer);
	kstage.add(this.ppLayer);
	for (let r = 1, y = this.hexOffsY; r < h; r += 2, y += DELTA_Y)
	{
	    let c = ((r % 4 == 3) ? 1 : 0);
	    for (let x = this.hexOffsX + c * HALF_DELTA_X; c < w; c += 2, x += DELTA_X)
		this.drawHex(r, c, 0, 0);
	}
	bLayer.draw();
	// temporary player-color swatches
	let y = div.offsetHeight - 36;
	for (let pn = 0; pn < PLAYER_COLORS.length; ++pn)
	    this.ppLayer.add(new Konva.Rect({x: pn*30+5, y: y, width: 26, height: 26, fill: PLAYER_COLORS[pn], cornerRadius: 3}));
	this.ppLayer.draw();
    }

    // geometry:
    this.coordToRC = function(coord)
    {
	return [coord >> 8, coord & 0xFF];
    }
    this.rcToXY = function(r, c) { return this._rcToXY(r,c,false); }
    this.rcEdgeToXY = function(r, c) { return this._rcToXY(r,c,true); }
    this._rcToXY = function(r, c, midEdge)
    {
	let [x,y] = [this.hexOffsX + (c+1) * HALF_DELTA_X, this.hexOffsY + (r+1) * HALF_DELTA_Y];
	if (r % 2 == 0)  // along top/bottom of hex
	{
	    if (midEdge)
		x += HALF_DELTA_X / 2;
	    else
		if (this.isEdgeAngledUp(r, c))
		    y += HEXY_ANGLED_HALF_HEIGHT;
		else
		    y -= HEXY_ANGLED_HALF_HEIGHT;
	}
	return [x,y];
    }
    /** [x,y] for a hex center, node coord, center of vertical edge, left side of angled edge coord */
    this.coordToXY = function(coord)
    {
	let [r,c] = this.coordToRC(coord);
	return this.rcToXY(r, c);
    }
    /** If true, edge angles up and right; if false, angles down and right. Do not call if r%2 == 1 (row is center of hex) */
    this.isEdgeAngledUp = function(r, c)
    {
	return (((r % 4 == 0) && (c % 2 == 1)) || ((r % 4 == 2) && (c % 2 == 0)))
    }

    // parts and pieces of board:
    this.drawHex = function(r, c, htype, hdice)
    {
	let [x, y] = this.rcToXY(r, c);
	let fillStyle = hextypeStyle(htype);
	let bLayer = this.bLayer;

	let hID = 'hex_' + coordHex(r, c);
	let coll = bLayer.find("#" + hID);
	if (coll.length)
		coll[0].fill(fillStyle);
	else
		bLayer.add(new Konva.RegularPolygon
		    ({ x: x, y: y, sides: 6, radius: HEX_RADIUS, id: hID, fill: fillStyle, stroke: '#666', strokeWidth: 1.5}));

	if (hdice == 0)
		return;

	let dStr = "" + hdice;
	let diceID = 'hdice_' + coordHex(r, c);
	let ktxt;
	coll = bLayer.find("#" + diceID);
	if (coll.length)
	{
		ktxt = coll[0];
		ktxt.text(dStr);
	} else {
		bLayer.add(new Konva.Circle
		    ({ x: x, y: y, radius: DICENUM_RADIUS, fill: '#ddd'}));
		ktxt = new Konva.Text
		    ({ x: x, y: y, text: dStr, id: diceID, fontFamily: FONT_FAMILY, fontSize: 18, fill: 'black'});
		bLayer.add(ktxt);
		ktxt.offsetY(ktxt.height() / 2);
	}
	ktxt.offsetX(ktxt.width() / 2); // center
    }
    this.addPort = function(ptype, edge, facing)
    {
	/** r-offset, c-offset to move over 1 hex, for each port facing direction (1-6); 0 unused.
	 * Position calc needs these because the some of the hexes overlaid by a port
	 * have invalid coordinates (r=0 or c=0).
	 */
	const DR_FACING = [0, -2, 0, 2, 2, 0, -2], DC_FACING = [0, 1, 2, 1, -1, -2, -1];
	let landHex = this.board.getAdjacentHexToEdge(edge, facing);
	let [r, c] = this.coordToRC(landHex);
	r -= DR_FACING[facing],
	c -= DC_FACING[facing];
	let [x, y] = this.rcToXY(r, c);
	let fillStyle = (ptype > 0) ? hextypeStyle(ptype) : '#ddd';
	let bLayer = this.bLayer;
	let pgroup = new Konva.Group({x: 0, y: 0, id: 'port_' + coordHex4(edge)});
	pgroup.add(new Konva.Circle
	    ({ x: 0, y: 0, radius: 30, fill: fillStyle, stroke: '#fff', strokeWidth: 1.5}));
	if (ptype == 0)
	{
	    let ktxt = new Konva.Text
		({ x: 0, y: 0, text: '3:1', FontFamily: FONT_FAMILY, fontSize: 18, fill: 'black'});
	    ktxt.offsetX(ktxt.width() / 2);  ktxt.offsetY(ktxt.height() / 2);  // center
	    pgroup.add(ktxt);
	}
	// port facings: 1 is NE, 2 is E, etc: so if arrow #s are rotated to (facing-1) and facing, then facing=0 is top-center 45deg triangle
	pgroup.add(new Konva.Line({
	    points: [ 0, -8, -8, 0, 8, 0 ], fill: '#fff', closed: true,
	    offset: { x: 0, y: 40 },  rotation: facing*60 }));
	--facing;
	pgroup.add(new Konva.Line({
	    points: [ 0, -8, -8, 0, 8, 0 ], fill: '#fff', closed: true,
	    offset: { x: 0, y: 40 },  rotation: facing*60 }));
	pgroup.x(x);
	pgroup.y(y);
	this.bLayer.add(pgroup);
    }
    this.placeRobber = function(r, c)
    {
	let robb;
	let rID = 'robber';
	let coll = this.ppLayer.find("#" + rID);
	if (coll.length)
		robb = coll[0];
	else
	{
		robb = new Konva.Line({
			points: [-4, -4, -8, -8, -8, -12, -4, -16, 4, -16, 8, -12, 8, -8, 4, -4, 8, 0, 8, 16, -8, 16, -8, 0, -4, -4, 4, -4], // X -8 to +8; Y -16 to +16
			id: rID, stroke: '#222', strokeWidth: 1.5, fill: '#999', closed: true });
		this.ppLayer.add(robb);
	}
	let [x, y] = this.rcToXY(r, c);
	robb.x(x + DICENUM_RADIUS + 11);
	robb.y(y - DICENUM_RADIUS / 3);
    }
    this._SHIP_PTS = [-6, -16, 4, -14, 11, -6, 11, 0, 8, 8, 20, 8, 17, 16, -19, 16, -19, 8, -4, 8, -1, 3, -1, -3, -4, -11, -6, -16];
    this.placePirate = function(r, c)
    {
	let pir;
	let pID = 'pirate';
	let coll = this.ppLayer.find("#" + pID);
	if (coll.length)
		pir = coll[0];
	else
	{
		pir = new Konva.Line({
			points: this._SHIP_PTS, id: pID, stroke: '#888', strokeWidth: 1.5, fill: '#111', closed: true });
		this.ppLayer.add(pir);
	}
	let [x, y] = this.rcToXY(r, c);
	pir.x(x);
	pir.y(y);
    }
    this.buildPiece = function(r, c, ptype, pn)
    {
	let layer = this.ppLayer;
	let midEdge = false;
	let pts;
	switch(ptype)
	{
	case PTYPE_ROAD:
	    pts = [-4, -26, 4, -26, 4, 26, -4, 26, -4, -26];
	    layer = this.rsLayer;
	    break;
	case PTYPE_SETTLEMENT:
	    pts = [-11, -8, 0, -19, 11, -8, 11, 9, -11, 9, -11, -8];
	    break;
	case PTYPE_CITY:
	    pts = [-16, -12, -6, -22, 3, -12, 3, -6, 16, -6, 16, 9, -16, 9, -16, -12];
	    let coll = layer.find("#" + PTYPE_NAMES[PTYPE_SETTLEMENT] + "_" + coordHex(r, c));
	    if (coll.length)
		coll[0].destroy(); // replaced by city
	    break;
	case PTYPE_SHIP:
	    pts = this._SHIP_PTS;
	    layer = this.rsLayer;
	    midEdge = true;
	    break;
	// TODO PTYPE_FORTRESS, PTYPE_VILLAGE need special contents
	default:
	    throw new RangeError("ptype: " + ptype);
	}
	let piece = new Konva.Line({
		points: pts, id: PTYPE_NAMES[ptype] + '_' + coordHex(r, c),
		stroke: '#222', strokeWidth: 1.5, fill: PLAYER_COLORS[pn], closed: true });
	piece.socPN = pn;
	if ((ptype == PTYPE_ROAD) && (r % 2 == 0))
	{
	    // angled road
	    piece.offsetY(26);
	    piece.rotation((this.isEdgeAngledUp(r, c)) ? 60 : 120);
	}

	let [x, y] = (midEdge) ? this.rcEdgeToXY(r, c) : this.rcToXY(r, c);
	layer.add(piece);
	piece.x(x);
	piece.y(y);
	layer.draw();
    }
    this.moveShip = function(fr, fc, tr, tc)
    {
	const pfix = PTYPE_NAMES[PTYPE_SHIP] + '_';
	let coll = this.rsLayer.find('#' + pfix + coordHex(fr, fc));
	if (! (coll.length))
	    return;
	let ship = coll[0];
	ship.id(pfix + coordHex(tr, tc));
	let [x, y] = this.rcEdgeToXY(tr, tc);
	ship.x(x);
	ship.y(y);
	this.rsLayer.draw();
    }

    // helpers called by gameMessage handlers
    this.handleMembers = function(memberNames)
    {
	let doc = this.gaWindow.document;
	let membersDiv = doc.getElementById("members");
	this.membersJQ = $(membersDiv);
	membersDiv.appendChild(doc.createTextNode("Members:"));
	membersDiv.appendChild(doc.createElement("br"));
	let messagesDiv = doc.getElementById("messages");
	this.messagesJQ = $(messagesDiv);
	let mJQ = this.membersJQ;
	for (let mName of memberNames)
	    if (! this.gaMembers.has(mName))
	    {
		this.gaMembers.add(mName);
		listAddJq(mJQ, mName, 1);
	    }
	setTimeout(function(){
	    let par = mJQ.parent();
	    if (mJQ.outerWidth() > par.width())
		par.width(mJQ.outerWidth());
	  }, 100);  // wait until fade-ins
	doc.forms.send.txt.focus();
    };
    this.handleLeave = function(memberName)
    {
	if (this.gaMembers.has(memberName))
	{
	    this.gaMembers.delete(memberName);
	    let mJQ = this.membersJQ;
	    if (mJQ)
	    {
		listRemoveJq(mJQ, memberName, 100);
		setTimeout(function(){
		    let par = mJQ.parent();
		    if (mJQ.outerWidth() < par.width())
			par.width(mJQ.outerWidth());
		}, 110);
	    }
	}
    };
    this.handleText = function(memberName, txt) {
	let mJQ = this.messagesJQ;
	if (! mJQ)
	    return;
	mJQ.append($("<br />"));
	mJQ.append(document.createTextNode(memberName + ": " + txt));
	mJQ.scrollTop(mJQ[0].scrollHeight);
    };
}

function unloadGameDocEvent(evt)
{
    let gaObj = evt.target.soc_gameui_obj;
    if (gaObj)
	closeGameWindow(gaObj.gaName, true);
}

/** New game name should be html-safe, based on JSettlers allowed names */
function newGameWindow(gaName)
{
    let gaObj = new GameUI(gaName);
    let gaWin = window.open("", "soc_game_" + gaName);
    if (gaWin)
    {
	gaObj.gaWindow = gaWin;
	gaWin.addEventListener("beforeunload", unloadGameDocEvent);  // not guaranteed to fire
	gaWin.document.write('Game: ' + gaName + '<HR noshade>Joining...');
	gaWin.document.close();
	let cLink = gaWin.document.createElement("link");
	cLink.rel="stylesheet"; cLink.href="game-ui.css"; cLink.type="text/css";
	gaWin.document.head.appendChild(cLink);
	gaWin.document.soc_gameui_obj = gaObj;
	inGames[gaName] = gaObj;
    } else {
	alert("Could not open a new tab to join game.");
    }
}

/* Cleanup when leaving the game: Called from message dispatcher & from user closing game window */
function closeGameWindow(gaName, isFromUnloadEvt)
{
    let gameObj = inGames[gaName];
    if (! gameObj)
	return;

    gameObj.gaWindow.document.soc_gameui_obj = null;
    delete inGames[gaName];  // remove before close(); undefined is OK
    if (gameObj.cliJoined && ! gameObj.sentLeaveMsg)
    {
	sendToServer({ "gaLeave": { "gaName": gaName } });
	gameObj.sentLeaveMsg = true;
    }
    if (! (isFromUnloadEvt || gameObj.gaWindow.closed))
	gameObj.gaWindow.close();
}

// Message dispatch //

dispatchTo['gaJoin'] = handleGameJoin;
dispatchTo['gaMembers'] = handleGameMembers;
dispatchTo['gaPlayerText'] = handleGameText;
dispatchTo['gaLeave'] = handleLeaveGame;

// message-parsing utilities

function msgCoord(msgField)
{
    let r=0, c=0;
    if (msgField)
    {
	if (msgField.row || msgField.col)
	{
	    r = msgField.row || 0;
	    c = msgField.column || 0;
	} else {
	    let cfield = (msgField.edgeCoord) ? msgField.edgeCoord : msgField.nodeCoord;
	    if (cfield)
	    {
		r = cfield.row || 0;
		c = cfield.column || 0;
	    }
	}
    }
    return [r, c];
}

// gameMessage handlers: gets gameUI obj for gameName, gameName (or undef), playerNumber (or undef), mdata.
// Handler order here roughly follows that of game_message.proto GameMessageFromServer

var gaDispatchTo =
{
    boardLayout: function(gameui, gn, pn, mdata)
    {
	if (mdata.layoutEncoding != "BOARD_ENCODING_LARGE")
	{
	    this.gaWindow.alert('Cannot draw board: Unknown board encoding');  // TODO gaWindow not found
	    return;
	}
	// the board itself:
	// TODO try-except in case LH or PL not found
	const LH = mdata.parts.LH.iArr.arr;
	for (let i = 0; i < LH.length; i += 3)
	{
	    let [r, c] = gameui.coordToRC(LH[i]);
	    gameui.drawHex(r, c, LH[i+1], LH[i+2]);
	}
	// port positions (all ports' types, then edge coords, then facings)
	const PL = mdata.parts.PL.iArr.arr, nPort = PL.length / 3, n2 = 2 * nPort;
	for (let i = 0; i < nPort; ++i)
	    gameui.addPort(PL[i], PL[i+nPort], PL[i+n2]);
	gameui.bLayer.draw();
	// playing pieces:
	if (mdata.parts.RH)
	{
	    let [r, c] = gameui.coordToRC(mdata.parts.RH.iVal);
	    gameui.placeRobber(r, c);
	}
	if (mdata.parts.PH)
	{
	    let [r, c] = gameui.coordToRC(mdata.parts.PH.iVal);
	    gameui.placePirate(r, c);
	}
	gameui.ppLayer.draw();
    },
    revealFogHex: function(gameui, gn, pn, mdata)
    {
	let [r,c] = msgCoord(mdata.coord);
	gameui.drawHex(r, c, ((mdata.htype) ? HEXTYPE_NAMES.indexOf(mdata.htype) : 0), mdata.diceNum || 0);
	gameui.bLayer.draw();
    },
    buildPiece: function(gameui, gn, pn, mdata)
    {
	let [r,c] = msgCoord(mdata.coordinates);
	gameui.buildPiece(r, c, ((mdata.ptype) ? PTYPE_NAMES.indexOf(mdata.ptype) : 0), pn);
    },
    movePiece: function(gameui, gn, pn, mdata)
    {
	if (mdata.ptype != 'SHIP')
	    return;
	let [fr,fc] = msgCoord(mdata.fromCoordinates);
	let [tr,tc] = msgCoord(mdata.toCoordinates);
	gameui.moveShip(fr,fc, tr, tc);
    },
    moveRobber: function(gameui, gn, pn, mdata)
    {
	if (mdata.isRobber)
	    gameui.placeRobber(mdata.moveTo.row, mdata.moveTo.column);
	else
	    gameui.placePirate(mdata.moveTo.row, mdata.moveTo.column);
	gameui.ppLayer.draw();
    },
};

dispatchTo['gameMessage'] = function(mdata)
    {
	let gaName = mdata.gameName, playerNumber = mdata.playerNumber || 0;
	let mtype;
	for (let kname of Object.keys(mdata))  // message type, and might contain: gameName, playerNumber
	{
	    if ((kname == 'gameName') || (kname == 'playerNumber'))
		continue;
	    if (mtype !== undefined)
	    {
		console.log("malformed: multiple keys: " + mtype + ", " + kname);
		return;
	    } else {
		mtype = kname;
	    }
	}
	let gfunc = gaDispatchTo[mtype];
	if (gfunc)
	    gfunc(inGames[gaName], gaName, playerNumber, mdata[mtype]);
	else
	    console.log("game-ui: no func for msgtype: " + mtype);
    };

