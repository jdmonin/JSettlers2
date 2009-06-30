/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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

// TODO consider callback option; ActionListener

/**
 * This is a generic dialog to popup a message to the player, with one button.
 * Asynchronously returns, and then the dialog sticks around not affecting
 * anything, until the user dismisses it.  At that point it only disappears.
 * A callback can be added later if needed.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.06
 */
class NotifyDialog extends AskDialog
{
    /**
     * Creates and shows a new NotifyDialog.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface, or null
     * @param promptText  Prompt text appearing above button
     * @param btnText  Button text
     * @param hasDefault  Button is default (responds to Enter)
     * @throws IllegalArgumentException If cli, promptText, or btnText is null
     */
    public static void createAndShow(SOCPlayerClient cli, SOCPlayerInterface gamePI, String promptText, String btnText, boolean hasDefault)
        throws IllegalArgumentException
    {
        NotifyDialog nd = new NotifyDialog
	    (cli, gamePI, promptText, btnText, hasDefault);
        nd.show();      
    }

    /**
     * Creates a new NotifyDialog.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface, or null
     * @param promptText  Prompt text appearing above button
     * @param btnText  Button text
     * @param hasDefault  Button is default (responds to Enter)
     * @throws IllegalArgumentException If cli or btnText is null
     */
    protected NotifyDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI, String promptText, String btnText, boolean hasDefault)
    {
        super(cli,
       	     ((gamePI != null)
       		      ? getParentFrame(gamePI)
       		      : getParentFrame(cli)),
        	promptText, promptText,
            btnText, hasDefault);
    }

    /**
     * React to the button. (AskDialog will dismiss the dialog)
     */
    public void button1Chosen()
    {
        // Nothing to do (AskDialog will dismiss it)
    }

    /**
     * Required stub; there is no button 2 in this dialog.
     */
    public void button2Chosen() { }

    /**
     * React to the dialog window closed by user. (Nothing to do)
     */
    public void windowCloseChosen() { }

}
