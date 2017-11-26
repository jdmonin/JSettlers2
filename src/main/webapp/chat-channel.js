/*
 chat channel window/tab javascript functions, for index.html and its inChannels[] array

 Since this modifies the dispatcher array, is loaded asynchronously from index-ws-dispatcher.js.

 This file is part of the Java Settlers Web App.

 This file Copyright (C) 2017 Jeremy D Monin (jeremy@nand.net)

 Open-source license: TBD
 */

// Message handlers; see bottom of file for inChannels[] entries //

function handleChannelJoin(mData)
{
    var chObj = inChannels[mData.chName];
    if (chObj)
	chObj.handleJoin(mData.memberName);
}

function handleChannelMembers(mData)
{
    var chObj = inChannels[mData.chName];
    if (chObj)
	chObj.handleMembers(mData.members);
}

function handleChannelText(mData)
{
    var chObj = inChannels[mData.chName];
    if (chObj)
	chObj.handleText(mData.memberName, mData.text);
}

function handleLeaveChannel(mData)
{
    var chObj = inChannels[mData.chName];
    if (chObj)
	chObj.handleLeave(mData.memberName);
}

// Top-level UI and ChatChannel object //

function ChatChannel(chName)
{
    this.chName = chName;
    // newChannelWindow sets this.chWindow, and sets chWindow.document.soc_chat_obj to this
    this.cliJoined = false;  // obj & new window must be created from dblclick handler, not server's chJoin
    this.sentLeaveMsg = false;  // flag for closeChannelWindow
    this.handleJoin = function(memberName)
    {
	// if we're in that channel, add person to member list there
	// otherwise if mData.memberName is us, open a chat channel window
	// and expect channelmembers message soon
	console.log("chch handleJoin: got here, cliJoined is " + this.cliJoined);
	if ((memberName == nickname) && ! this.cliJoined)
	{
	    var chBody = this.chWindow.document.body;
	    chBody.innerHTML = 'Chat Channel: ' + chName + '<HR noshade><div id="ch">Joined</div>';
	    // TODO try further DOM adjustment? maybe set up msgs area & channel members area
	    this.cliJoined = true;
	}
	if (! this.cliJoined)
	    return;
    };
    this.handleMembers = function(memberNames)
    {
	var doc = this.chWindow.document;
	var chDiv = doc.getElementById("ch");
	if (chDiv != null)
	{
	    chDiv.appendChild(doc.createElement("br"));
	    chDiv.appendChild(doc.createTextNode("Members: " + memberNames.toString()));
	}
    };
    this.handleLeave = function(memberName) { };  // TODO
    this.handleText = function(memberName, text) { };  // TODO
}

function unloadChannelDocEvent(evt)
{
    var chObj = evt.target.soc_chat_obj;
    if (chObj)
	closeChannelWindow(chObj.chName, true);
}

/** New channel name should be html-safe, based on JSettlers allowed names */
function newChannelWindow(chName)
{
    var chObj = new ChatChannel(chName);
    var chWin = window.open("", "soc_chat_" + chName);
    if (chWin)
    {
	chObj.chWindow = chWin;
	chWin.addEventListener("beforeunload", unloadChannelDocEvent);
	chWin.document.write('Chat Channel: ' + chName + '<HR noshade>Joining...');
	chWin.document.close();
	chWin.document.soc_chat_obj = chObj;
	inChannels[chName] = chObj;
    } else {
	alert("Could not open a new tab to join chat channel.");
    }
}

/* Cleanup when leaving the chat: Called from message dispatcher & from user closing chat window */
function closeChannelWindow(chName, isFromUnloadEvt)
{
    var channelObj = inChannels[chName];
    if (! channelObj)
	return;
    channelObj.chWindow.document.soc_chat_obj = null;
    delete inChannels[chName];  // remove before close(); undefined is OK since we aren't iterating all.
    if (! isFromUnloadEvt)
	channelObj.chWindow.close();
    if (channelObj.cliJoined && ! channelObj.sentLeaveMsg)
    {
	var leaveMsg = { "chLeave": { "chName": chName } };
	sendToServer(leaveMsg);
	channelObj.sentLeaveMsg = true;
    }
}

// Message dispatch //

dispatchTo['chJoin'] = handleChannelJoin;
dispatchTo['chMembers'] = handleChannelMembers;
dispatchTo['chText'] = handleChannelText;
dispatchTo['chLeave'] = handleLeaveChannel;
