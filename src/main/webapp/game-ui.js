/*
 game window/board javascript functions, for index.html and its inGames[] array

 Since this modifies the dispatcher array, is loaded asynchronously from index-ws-dispatcher.js.

 This file is part of the Java Settlers Web App.

 This file Copyright (C) 2020 Jeremy D Monin (jeremy@nand.net)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

// Board geometry constants; some are from SOCBoardPanel.java //

const BOARDWIDTH_VISUAL_MIN = 18, BOARDHEIGHT_VISUAL_MIN = 17;  // half-hex units
const BOARD_BORDER = 20;  // px on each side
const DELTA_X = 54, DELTA_Y = 46, HALF_DELTA_X = DELTA_X / 2, HALF_DELTA_Y = DELTA_Y / 2;  // px offsets for each hex

// Colors: water, clay, ore, sheep, wheat, wood, desert, gold, fog
const HEXTYPE_COLORS = ['#33a', '#cc6666', '#999999', '#33cc33', '#cccc33', '#cc9966', '#ffff99', '#ff0', '#dcdcdc'];

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
    // initBoard sets this.gameCanvas
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
		+ '<div id="main" style="flex: 1 1 auto; overflow: auto;">'
		+ '<canvas id="board" width=' + canW + ' height=' + canH + '></canvas></div>'
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
    this.initBoard = function(h,w)
    {
	let doc = this.gaWindow.document;
	const can = doc.getElementById("board");
	this.gameCanvas = can;
	let ctx = can.getContext('2d');
	ctx.fillStyle = "#119";  // dk blue
	ctx.fillRect(0, 0, can.width, can.height);
	for (let r = 1, y = this.hexOffsY; r < h; r += 2, y += DELTA_Y)
	{
	    let c = ((r % 4 == 3) ? 1 : 0);
	    for (let x = this.hexOffsX + c * HALF_DELTA_X; c < w; c += 2, x += DELTA_X)
		this.drawHex(r, c, 0);
        }
    }
    this.drawHex = function(r, c, htype)
    {
	let x = this.hexOffsX + c * HALF_DELTA_X, y = this.hexOffsY + r * HALF_DELTA_Y;
	let ctx = this.gameCanvas.getContext('2d');
	ctx.fillStyle = ((htype >= 0) && (htype < HEXTYPE_COLORS.length)) ? HEXTYPE_COLORS[htype] : 'gray';
	ctx.fillRect(x+4, y+4, DELTA_X-8, DELTA_Y-8);  // TODO an actual hex, w/ id hex_RRCC
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
	// TODO try-except in case LH not found
	const LH = mdata.parts.LH.iArr.arr;
	for (let i = 0; i < LH.length; i += 3)
	{
	    let hcoord = LH[i], htype = LH[i+1];
	    gameui.drawHex((hcoord >> 8) & 0xFF, hcoord & 0xFF, htype);
	}
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

