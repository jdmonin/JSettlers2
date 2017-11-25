/*
 websocket dispatcher for index.html

 This file is part of the Java Settlers Web App.

 This file Copyright (C) 2017 Jeremy D Monin (jeremy@nand.net)

 Open-source license: TBD
 */

dispatchTo =
{
    channels: function(mData)
    {
	if (mData.names)
	    listPopulate('chat_channel_list', mData.names);
    },
    games: function(mData)
    {
	if (mData.game)
	{
	    var gaNames = [];
	    mData.game.forEach(function(gaObj, i) { gaNames[i] = gaObj.gaName; } );
	    listPopulate('game_list', gaNames);
	}
    },
    chNew: function(mData)
    {
	listAdd('chat_channel_list', mData.chName);
    },
    chDelete: function(mData)
    {
	listRemove('chat_channel_list', mData.chName);
    },
    gaNew: function(mData)
    {
	listAdd('game_list', mData.game.gaName);
	    // TODO note minVersion (may be absent) in case it's higher than our version
    },
    gaDelete: function(mData)
    {
	listRemove('game_list', mData.gaName);
    },
};

function msgDispatch(mType, mData)
{
    console.log("type for dispatch: " + mType);
    console.log("    mData is: " + JSON.stringify(mData));
    var dfunc = dispatchTo[mType];
    if (dfunc)
	dfunc(mData);
}

// Done loading and defining: Enable the Connect button.
$('#connect_btn').prop('disabled', false);

