/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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


/////////////////////////////////////////////////////// SNIPPING TEXT AREA 
// 
// SnippingTextArea.java 
// Brian Davies 
// Written 1/21/99 
// 
///////////
/////////////////////////////////////////////////////// PACKAGES
import java.awt.TextArea;


/////////////////////////////////////////////////////// CLASS DEFINITION
public class SnippingTextArea extends TextArea
{
    int maximumLines = 100;
    int lines = 0;

    /////////////////////////////////////////////////// CONSTRUCTORS
    public SnippingTextArea(int height, int width, int maxLines)
    {
        super(height, width);
        maximumLines = maxLines;
    }

    /**
     * Creates a new SnippingTextArea object.
     *
     * @param contents DOCUMENT ME!
     * @param maxLines DOCUMENT ME!
     */
    public SnippingTextArea(String contents, int maxLines)
    {
        super(contents);
        maximumLines = maxLines;
        lines = countLines(contents);

        // snipText();
    }

    /**
     * Creates a new SnippingTextArea object.
     *
     * @param contents DOCUMENT ME!
     * @param height DOCUMENT ME!
     * @param width DOCUMENT ME!
     * @param maxLines DOCUMENT ME!
     */
    public SnippingTextArea(String contents, int height, int width, int maxLines)
    {
        super(contents, height, width);
        maximumLines = maxLines;
        lines = countLines(contents);

        // snipText();
    }

    /**
     * Creates a new SnippingTextArea object.
     *
     * @param contents DOCUMENT ME!
     * @param height DOCUMENT ME!
     * @param width DOCUMENT ME!
     * @param scrollType DOCUMENT ME!
     * @param maxLines DOCUMENT ME!
     */
    public SnippingTextArea(String contents, int height, int width, int scrollType, int maxLines)
    {
        super(contents, height, width, scrollType);
        maximumLines = maxLines;
        lines = countLines(contents);

        // snipText();
    }

    /////////////////////////////////////////////////// ACCESSORS
    public int getMaximumLines()
    {
        return maximumLines;
    }

    /**
     * DOCUMENT ME!
     *
     * @param newMax DOCUMENT ME!
     */
    public void setMaximumLines(int newMax)
    {
        maximumLines = newMax;
        snipText();
    }

    /////////////////////////////////////////////////// OVERWRITTEN METHODS
    /**
     * DOCUMENT ME!
     *
     * @param newString DOCUMENT ME!
     */
    public void setText(String newString)
    {
        super.setText(newString);

        // lines += countLines(newString);
        // snipText (); 
    }
    /**
     * DOCUMENT ME!
     *
     * @param newString DOCUMENT ME!
     * @param x DOCUMENT ME!
     * @param y DOCUMENT ME!
     */
    public synchronized void replaceRange(String newString, int x, int y)
    {
        super.replaceRange(newString, x, y);

        // lines += countLines(newString);
        // snipText (); 
    }

    /**
     * DOCUMENT ME!
     *
     * @param newString DOCUMENT ME!
     * @param x DOCUMENT ME!
     */
    public synchronized void insert(String newString, int x)
    {
        super.insert(newString, x);

        // lines += countLines(newString);
        // snipText (); 
    }

    /**
     * DOCUMENT ME!
     *
     * @param newString DOCUMENT ME!
     */
    public synchronized void append(String newString)
    {
        super.append(newString);
        lines += countLines(newString);
        snipText();
    }

    /////////////////////////////////////////////////// SNIPPING
    protected int countLines(String s)
    {
        int lines = 0;
        int end = s.length();

        for (int idx = 0; idx < end; idx++)
        {
            if (s.charAt(idx) == '\n')
            {
                lines++;
            }
        }

        return lines;
    }

    /**
     * DOCUMENT ME!
     */
    public void snipText()
    {
        // //D.ebugPrintln("LINES = "+lines);
        while (lines > maximumLines)
        {
            String s = getText();
            super.replaceRange("", 0, s.indexOf('\n') + 1);
            lines--;
        }

        if (isDisplayable())
            super.setCaretPosition(getText().length());
    }
}


/////////////////////////////////////////////////////// END
