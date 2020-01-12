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

// Board geometry constants; some are from SOCBoardPanel.java //

const BOARDWIDTH_VISUAL_MIN = 18, BOARDHEIGHT_VISUAL_MIN = 17;  // half-hex units
const BOARD_BORDER = 20;  // px on each side
const DELTA_X = 92, DELTA_Y = 80, HALF_DELTA_X = DELTA_X / 2, HALF_DELTA_Y = DELTA_Y / 2;  // px offsets for each hex
const HEX_RADIUS = 53;

// Colors: water, clay, ore, sheep, wheat, wood, desert, gold, fog
const HEXTYPE_COLORS = ['#33a', '#cc5544', '#999999', '#22dd22', '#cccc33', '#339922', '#ffff99', '#ff0', '#dcdcdc'];

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

// Non-gameMessage handlers; see bottom of file for GameUI object, inGames[] entries, gameMessage handlers //

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
    // initBoard sets this.board (Board obj) and this.klayer (Konva.Layer)
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
	let klayer = new Konva.Layer();
	this.klayer = klayer;
	kstage.add(klayer);
	for (let r = 1, y = this.hexOffsY; r < h; r += 2, y += DELTA_Y)
	{
	    let c = ((r % 4 == 3) ? 1 : 0);
	    for (let x = this.hexOffsX + c * HALF_DELTA_X; c < w; c += 2, x += DELTA_X)
		this.drawHex(r, c, 0, 0);
	}
	klayer.draw();
    }
    this.drawHex = function(r, c, htype, hdice)
    {
	let x = this.hexOffsX + (c+1) * HALF_DELTA_X, y = this.hexOffsY + (r+1) * HALF_DELTA_Y;
	let fillStyle = hextypeStyle(htype);
	let klayer = this.klayer;

	let hID = 'hex_' + coordHex(r, c);
	let coll = klayer.find("#" + hID);
	if (coll.length != 0)
		coll[0].fill(fillStyle);
	else
		klayer.add(new Konva.RegularPolygon
		    ({ x: x, y: y, sides: 6, radius: HEX_RADIUS, id: hID, fill: fillStyle, stroke: '#666', strokeWidth: 1.5}));

	if (hdice == 0)
		return;

	let dStr = "" + hdice;
	let diceID = 'hdice_' + coordHex(r, c);
	let ktxt;
	coll = klayer.find("#" + diceID);
	if (coll.length != 0)
	{
		ktxt = coll[0];
		ktxt.text(dStr);
	} else {
		klayer.add(new Konva.Circle
		    ({ x: x, y: y, radius: 15, fill: '#ddd'}));
		ktxt = new Konva.Text
		    ({ x: x, y: y, text: dStr, id: diceID, fontFamily: FONT_FAMILY, fontSize: 18, fill: 'black'});
		klayer.add(ktxt);
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
	let landHex = this.board.getAdjacentHexToEdge(edge, facing),
		r = (landHex >> 8) - DR_FACING[facing],
		c = (landHex & 0xFF) - DC_FACING[facing];
	let x = this.hexOffsX + (c+1) * HALF_DELTA_X, y = this.hexOffsY + (r+1) * HALF_DELTA_Y;
	let fillStyle = (ptype > 0) ? hextypeStyle(ptype) : '#ddd';
	let klayer = this.klayer;
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
	this.klayer.add(pgroup);
    }
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
	gaWin.addEventListener("beforeunload", unloadGameDocEvent);
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
    delete inGames[gaName];  // remove before close(); undefined is OK since we aren't iterating all.
    if (! isFromUnloadEvt)
	gameObj.gaWindow.close();
    if (gameObj.cliJoined && ! gameObj.sentLeaveMsg)
    {
	let leaveMsg = { "gaLeave": { "gaName": gaName } };
	sendToServer(leaveMsg);
	gameObj.sentLeaveMsg = true;
    }
}

// Message dispatch //

dispatchTo['gaJoin'] = handleGameJoin;
dispatchTo['gaMembers'] = handleGameMembers;
dispatchTo['gaPlayerText'] = handleGameText;
dispatchTo['gaLeave'] = handleLeaveGame;

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
	// TODO try-except in case LH or PL not found
	const LH = mdata.parts.LH.iArr.arr;
	for (let i = 0; i < LH.length; i += 3)
	{
	    let hcoord = LH[i];
	    gameui.drawHex((hcoord >> 8) & 0xFF, hcoord & 0xFF, LH[i+1], LH[i+2]);
	}
	// port positions (all ports' types, then edge coords, then facings)
	const PL = mdata.parts.PL.iArr.arr, nPort = PL.length / 3, n2 = 2 * nPort;
	for (let i = 0; i < nPort; ++i)
		gameui.addPort(PL[i], PL[i+nPort], PL[i+n2]);
	gameui.klayer.draw();
    }
};

dispatchTo['gameMessage'] = function(mdata)
    {
	let gaName = mdata.gameName, playerNumber = mdata.playerNumber;  // might be undefined
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

