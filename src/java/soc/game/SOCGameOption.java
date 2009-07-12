/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009 Jeremy Monin <jeremy@nand.net>
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
package soc.game;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import soc.message.SOCMessage;

/**
 * Game-specific options, configurable at game creation.
 * This class has two purposes:
 *<UL>
 * <LI> Static dictionary of known options
 * <LI> Per-game values of options
 *</UL>
 * If a new option changes previously expected behavior, it should default here to
 * the old behavior; its default value on your server can be changed at runtime.
 *<P>
 * Introduced in 1.1.07; check server, client versions against
 * {@link soc.message.SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}.
 * Each option has version information, because options can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about game options:  If the client is newer, it must defer to the
 * server's older set of known options.  At client connect, the client compares its
 * version to server's, and asks for any changes to options if their versions differ.
 *<P>
 * @author Jeremy D. Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOption implements Cloneable
{
    /**
     * Set of "known options".
     * allOptions must never be null, because other places assume it is filled.
     */
    private static Hashtable allOptions = initAllOptions();

    /**
     * If you add an option (TODO) descr here...
     *   possibly update {@link soc.util.SOCRobotParameters#copyIfOptionChanged(Hashtable)}.
     *<P>
     * This method creates a Hashtable but does not set {@link #allOptions} field.
     * @return the default options
     */
    private static Hashtable initAllOptions()
    {
        Hashtable opt = new Hashtable();
        opt.put("PL", new SOCGameOption
                ("PL", -1, 1107, 4, 2, 4, true, "Maximum # players"));
        opt.put("RD", new SOCGameOption
                ("RD", -1, 1107, false, false, "Robber can't return to the desert"));
        opt.put("E7", new SOCGameOption
                ("E7", -1, 1107, false, 7, 1, 999, false, "Roll no 7s during first # rounds"));
        opt.put("BC", new SOCGameOption
                ("BC", -1, 1107, true, 3, 3, 9, false, "Break up clumps of # or more same-type hexes/ports"));
        opt.put("NT", new SOCGameOption
                ("NT", 1107, 1107, false, true, "No Trading allowed"));

        /*
        opt.put("DEBUG_ENUM", new SOCGameOption
                ("DEBUG_ENUM", 1107, 1107, 
                 3, new String[]{ "First", "Second", "Third", "Fourth"}, false, "Test option enum"));
        opt.put("DEBUG_STR", new SOCGameOption
                ("DEBUG_STR", 1107, 1107, 20, false, true, "Test option str"));
        opt.put("DEBUG_STRHIDE", new SOCGameOption
                ("DEBUG_STRHIDE", 1107, 1107, 20, true, true, "Test option strhide"));
        */

        return opt;
    }

    /** Lowest OTYPE value known at this version */
    public static final int OTYPE_MIN = 0;

    /** Option type: unknown (probably due to version mismatch)  */
    public static final int OTYPE_UNKNOWN = 0;

    /** Option type: boolean  */
    public static final int OTYPE_BOOL = 1;

    /** Option type: integer  */
    public static final int OTYPE_INT = 2;

    /** Option type: integer + boolean */
    public static final int OTYPE_INTBOOL = 3;

    /** Option type: enumeration (1 of several possible int values, described with text strings) */
    public static final int OTYPE_ENUM = 4;

    /** Option type: text string (max string length is {@link #maxIntValue}, default value is "") */
    public static final int OTYPE_STR = 5;

    /** Option type: text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted,
     *  but contents show up as "*" when typed into a text field.
     */
    public static final int OTYPE_STRHIDE = 6;

    /** Highest OTYPE value known at this version */
    public static final int OTYPE_MAX = OTYPE_STRHIDE;  // OTYPE_* - adj OTYPE_MAX if adding new type

    // If you create a new option type,
    // please update parseOptionsToHash(), packOptionsToString(),
    // adjustOptionsToKnown(), and soc.message.SOCGameOptionGetInfo,
    // and other places.
    // (Search *.java for "// OTYPE_*" to find all locations)

    /** Option type.
     *<UL>
     * <LI> {@link #OTYPE_BOOL} Boolean
     * <LI> {@link #OTYPE_INT}  Integer, with min/max value
     * <LI> {@link #OTYPE_INTBOOL} Int plus bool (Ex. [x] no robber rolls in first _5_ turns)
     * <LI> {@link #OTYPE_ENUM} Enumerated-choice (Ex. Standard vs Seafarers):
     *        Stored like integer {@link #OTYPE_INT} in range 1-n, described to user with text strings.
     * <LI> {@link #OTYPE_STR} short text string: max string length is {@link #maxIntValue}; default value is the empty string.
     * <LI> {@link #OTYPE_STRHIDE} text string (like {@link #OTYPE_STR}) but hidden from view; is NOT encrypted.
     *</UL>
     */
    public final int optType;  // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * Option key/name: Short alphanumeric name (2 characters)
     */
    public final String optKey;

    /**
     * Minimum game version supporting this option, or -1;
     * same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * Public direct usage of this is discouraged; use {@link #optionsMinimumVersion(Hashtable)} instead,
     * because the current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s minVersion is -1, to allow 2- or 3-player games with any client.
     */
    public final int minVersion;  // or -1

    /**
     * Most recent game version in which this option changed, or if not modified, the version which added it.
     * changes would include different min/max values, new options for {@link #OTYPE_ENUM}, etc.
     * Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     */
    public final int lastModVersion;

    /**
     * If this option's value is the default, should we not add it to game options
     * or send over the network (to reduce overhead)?
     * Only recommended if game behavior without the option is well-established
     * (for example, maxplayers == 4 unless option "PL" is present).
     * This is done in {@link #adjustOptionsToKnown(Hashtable, Hashtable)}.
     */
    public final boolean skipIfDefault;

    /**
     * Default value for boolean part of this option, if any
     */
    public final boolean defaultBoolValue;

    /**
     * Default value for integer part of this option, if any
     */
    public final int defaultIntValue;

    /**
     * Minumum and maximum permitted values for integer part of this option, if any,
     * or maximum length of a string value. (There is no minimum length)
     */
    public final int minIntValue, maxIntValue;

    /**
     * Descriptive text for the option. If {@link #OTYPE_INTBOOL},
     * may contain a placeholder '#' where the value is typed onscreen.
     */
    public final String optDesc;   // OTYPE_* - if a new type is added, update this field's javadoc.

    /**
     * For type {@link #OTYPE_ENUM}, descriptive text for each possible value,
     * otherwise null.  If a value is added or changed in a later version, the option's
     * {@link #lastModVersion} field must be updated, so server/client will know
     * to ask for the proper version with all available options.
     */
    public final String[] enumVals;

    private boolean boolValue;
    private int     intValue;
    private String  strValue;  // no default value: is "", stored as null

    /**
     * Create a new game option of unknown type ({@link #OTYPE_UNKNOWN}).
     * Minimum version will be {@link Integer#MAX_VALUE}.
     * Value will be false/0. optDesc will be an empty string.
     * @param key   Alphanumeric 2-character code for this option
     */
    public SOCGameOption(String key)
    {
        this(OTYPE_UNKNOWN, key, Integer.MAX_VALUE, Integer.MAX_VALUE, false, 0, 0, 0, false, null, "");
    }

    /**
     * Create a new boolean game option. Type is {@link #OTYPE_BOOL}.
     *
     * @param key     Alphanumeric 2-character code for this option
     * @param minVers Minimum client version if this option is set (is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default value (true if set, false if not set)
     * @param skipIfDefault If this option's value is the default, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, maxplayers == 4 unless option PL is present).
     * @param desc    Descriptive brief text, to appear in the options dialog
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        boolean defaultValue, boolean skipIfDefault, String desc)
    {
	this(OTYPE_BOOL, key, minVers, lastModVers, defaultValue, 0, 0, 0, skipIfDefault, null, desc);
    }

    /**
     * Create a new integer game option.  Type is {@link #OTYPE_INT}.
     * @param key     Alphanumeric 2-character code for this option
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param skipIfDefault If this option's value is the default, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, maxplayers == 4 unless option PL is present).
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the int value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        int defaultValue, int minValue, int maxValue, boolean skipIfDefault, String desc)
        throws IllegalArgumentException 
    {
	this(OTYPE_INT, key, minVers, lastModVers, false, defaultValue,
	     minValue, maxValue, skipIfDefault, null, desc);
    }

    /**
     * Create a new int+boolean game option. Type is {@link #OTYPE_INTBOOL}.
     * @param key     Alphanumeric 2-character code for this option
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param skipIfDefault If this option's value is the default, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, maxplayers == 4 unless option PL is present).
     *           For type OTYPE_INTBOOL, the integer value is checkd, not the boolean value.
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue
     */
    public SOCGameOption(String key, int minVers, int lastModVers, boolean defaultBoolValue, int defaultIntValue,
        int minValue, int maxValue, boolean skipIfDefault, String desc)
        throws IllegalArgumentException
    {
	this(OTYPE_INTBOOL, key, minVers, lastModVers, defaultBoolValue, defaultIntValue,
	     minValue, maxValue, skipIfDefault, null, desc);
    }

    /**
     * Create a new enumerated game option.  Type is {@link #OTYPE_ENUM}.
     * The {@link #minIntValue} will be 1, {@link #maxIntValue} will be enumVals.length.
     * @param key     Alphanumeric 2-character code for this option
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultValue Default int value, in range 1 - n (n == number of possible values)
     * @param enumVals text to display for each possible value
     * @param skipIfDefault If this option's value is the default, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, maxplayers == 4 unless option PL is present).
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the enum value goes.
     *             If no placeholder is found, the value field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if defaultValue < minValue or is > maxValue
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
        int defaultValue, String[] enumVals, boolean skipIfDefault, String desc)
        throws IllegalArgumentException 
    {
	this(OTYPE_ENUM, key, minVers, lastModVers, false, defaultValue,
	     1, enumVals.length, skipIfDefault, enumVals, desc);
    }

    /**
     * Create a new text game option.  Type is {@link #OTYPE_STR} or {@link #OTYPE_STRHIDE}.
     * The {@link #maxIntValue} will be maxLength.
     * @param key     Alphanumeric 2-character code for this option
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param maxLength   Maximum length, between 1 and 255 (for network bandwidth conservation)
     * @param hideTyping  Should type be {@link #OTYPE_STRHIDE} instead of {@link #OTYPE_STR}?
     * @param skipIfDefault If this option's value is the default, should we not add it to game options
     *           or send over the network (to reduce overhead)?
     *           Only recommended if game behavior without the option is well-established
     *           (for example, maxplayers == 4 unless option PL is present).
     * @param desc Descriptive brief text, to appear in the options dialog; may
     *             contain a placeholder character '#' where the text value goes.
     *             If no placeholder is found, the value text field appears at left,
     *             like boolean options.
     * @throws IllegalArgumentException if maxLength > 255
     */
    public SOCGameOption(String key, int minVers, int lastModVers,
	int maxLength, boolean hideTyping, boolean skipIfDefault, String desc)
        throws IllegalArgumentException 
    {
	this( (hideTyping ? OTYPE_STRHIDE : OTYPE_STR ),
	     key, minVers, lastModVers, false, 0,
	     0, maxLength, skipIfDefault, null, desc);
	if ((maxLength < 1) || (maxLength > 255))
	    throw new IllegalArgumentException("maxLength");
    }

    /**
     * Create a new game option - common constructor.
     * @param key     Alphanumeric 2-character code for this option
     * @param otype   Option type; use caution, as this is unvalidated against
     *                {@link #OTYPE_MIN} or {@link #OTYPE_MAX}.
     * @param minVers Minimum client version if this option is set (boolean is true), or -1
     * @param lastModVers Last-modified version for this option, or version which added it
     * @param defaultBoolValue Default value (true if set, false if not set)
     * @param defaultIntValue Default int value, to use if option is set
     * @param minValue Minimum permissible value
     * @param maxValue Maximum permissible value; the width of the options-dialog
     *                 value field is based on the number of digits in maxValue.
     * @param skipIfDefault If this option's value is the default, should we not add it to game options?
     * @param enumVals Possible choices for enum text, or null
     * @param desc Descriptive brief text, to appear in the options dialog; should
     *             contain a placeholder character '#' where the int value goes.
     * @throws IllegalArgumentException if defaultIntValue < minValue or is > maxValue
     */
    protected SOCGameOption(int otype, String key, int minVers, int lastModVers,
        boolean defaultBoolValue, int defaultIntValue,
        int minValue, int maxValue, boolean skipIfDefault,
        String[] enumVals, String desc)
        throws IllegalArgumentException
    {
	// properties
	optKey = key;  // TODO validate length and characters
	optType = otype;
	minVersion = minVers;  // TODO -1 or >= 1000
	lastModVersion = lastModVers;  // TODO -1 or >= 1000
	this.defaultBoolValue = defaultBoolValue;
	this.defaultIntValue = defaultIntValue;
	minIntValue = minValue;
	maxIntValue = maxValue;
	this.skipIfDefault = skipIfDefault;
        this.enumVals = enumVals;
	optDesc = desc;   // TODO validate/javadoc: no sep/sep2 chars

	// starting values (= defaults)
	boolValue = defaultBoolValue;
	intValue = defaultIntValue;
	strValue = null;
	if ((intValue < minIntValue) || (intValue > maxIntValue))
	    throw new IllegalArgumentException("defaultIntValue");
    }

    /**
     * Is this option set, if this option's type has a boolean component?
     * @return current boolean value of this option
     */
    public boolean getBoolValue() { return boolValue; }

    public void setBoolValue(boolean v) { boolValue = v; }

    /**
     * This option's integer value, if this option's type has an integer component.
     * @return current integer value of this option
     */
    public int getIntValue() { return intValue; }

    /**
     * Set this option's integer value to new value v, or clip to min/max allowed values.
     * @param v set to this value, if it's within min/max for this option
     */
    public void setIntValue(int v)
    {
        if (v < minIntValue)
            intValue = minIntValue;
        else if (v > maxIntValue)
            intValue = maxIntValue;
        else
	    intValue = v;
    }

    /**
     * @return current string value of this option, or "" (empty string) if not set
     */
    public String getStringValue()
    {
	if (strValue != null)
	    return strValue;
	else
	    return "";
    }

    /**
     * Set this option's string value to new value v
     * @param v set to this value, or "" (empty string) if null; should
     *          be a simple string (no newlines or control characters).
     *          if v.length > {@link #maxIntValue}, length will be truncated.
     * @throws IllegalArgumentException if v contains characters reserved for
     *          message handling: {@link SOCMessage#sep} or 
     *          {@link SOCMessage#sep2} ('|' or ',')
     */
    public void setStringValue(String v)
	throws IllegalArgumentException
    {
        if (v != null)
        {
            final int vl = v.length();
            if (vl == 0)
            {
                v = null;
            } else {
                if (vl > maxIntValue)
                    v = v.substring(0, maxIntValue);
                if ((-1 != v.indexOf(SOCMessage.sep_char))
                      || (-1 != v.indexOf(SOCMessage.sep2_char)))
                    throw new IllegalArgumentException("new value contains msg separator char");
            }
        }
        strValue = v;
    }

    /**
     * @return a deep copy of all known option objects
     * @see #addKnownOption(SOCGameOption)
     */
    public static Hashtable getAllKnownOptions()
    {
	return cloneOptions(allOptions);
    }

    /**
     * Add a new known option (presumably received from a server of newer or older version),
     * or update the option's information.
     * @param onew New option, or a changed version of an option we already know.
     *             If onew.optType == {@link #OTYPE_UNKNOWN}, will remove from the known table.
     * @return true if it's new, false if we already had that key and it was updated
     * @see #getAllKnownOptions()
     */
    public static boolean addKnownOption(SOCGameOption onew)
    {
	final String oKey = onew.optKey;
	final boolean hadIt = allOptions.containsKey(oKey);
	if (hadIt)
	    allOptions.remove(oKey);
	if (onew.optType != OTYPE_UNKNOWN)
	    allOptions.put(oKey, onew);
	return ! hadIt;
    }

    /**
     * Set the current value of a known option, based on the current value of
     * another object with the same {@link #optKey}.
     * If there is no known option with oCurr.{@link #optKey}, it is ignored and nothing is set.
     * @param ocurr Option with the requested current value
     * @throws  IllegalArgumentException if value is not permitted; note that
     *            intValues outside of range are silently clipped, and will not
     *            throw this exception.
     */
    public static void setKnownOptionCurrentValue(SOCGameOption ocurr)
        throws IllegalArgumentException
    {
        final String oKey = ocurr.optKey;
        SOCGameOption oKnown = (SOCGameOption) allOptions.get(oKey);
        if (oKnown == null)
            return;
        switch (oKnown.optType)  // OTYPE_*
        {
        case OTYPE_BOOL:
            oKnown.boolValue = ocurr.boolValue;
            break;

        case OTYPE_INT:
        case OTYPE_ENUM:
            oKnown.setIntValue(ocurr.intValue);
            break;

        case OTYPE_INTBOOL:
            oKnown.boolValue = ocurr.boolValue;
            oKnown.setIntValue(ocurr.intValue);
            break;

        case OTYPE_STR:
        case OTYPE_STRHIDE:
            oKnown.setStringValue(ocurr.strValue);
            break;
        }
    }

    /**
     * @param opts  a hashtable of SOCGameOptions, or null
     * @return a deep copy of all option objects within opts, or null if opts is null
     */
    public static Hashtable cloneOptions(Hashtable opts)
    {
	if (opts == null)
	    return null;

	Hashtable opts2 = new Hashtable();
	for (Enumeration e = allOptions.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) allOptions.get(e.nextElement());
	    try
	    {
	        opts2.put(op.optKey, (SOCGameOption) op.clone());
	    } catch (CloneNotSupportedException ce) {}
	}
	return opts2;
    }

    /**
     * @return information about a known option, or null if none with that key
     */
    public static SOCGameOption getOption(String key)
    {
	return (SOCGameOption) allOptions.get(key);  // null is ok
    }

    /**
     * Search these options and find any unknown ones (type {@link #OTYPE_UNKNOWN})
     * @param opts hashtable of SOCGameOption
     * @return vector(SOCGameOption) of unknown options, or null if all are known
     */
    public static Vector findUnknowns(Hashtable opts)
    {
        Vector unknowns = null;
        for (Enumeration e = opts.keys(); e.hasMoreElements(); )
        {
            SOCGameOption op = (SOCGameOption) opts.get((String) e.nextElement());
            if (op.optType == SOCGameOption.OTYPE_UNKNOWN)
            {
                if (unknowns == null)
                    unknowns = new Vector();
                unknowns.addElement(op.optKey);
            }
        }
        return unknowns;
    }

    /**
     * Utility - build a string of option name-value pairs from the
     *           {@link #getAllKnownOptions() known options}' current values.
     *
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @return string of name-value pairs;
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string.
     * @see #parseOptionsToHash(String)
     */
    public static String packKnownOptionsToString(boolean hideEmptyStringOpts)
    {
	return packOptionsToString(allOptions, hideEmptyStringOpts);
    }

    /**
     * Utility - build a string of option name-value pairs.
     * This can be unpacked with {@link #parseOptionsToHash(String)}.
     *
     * @param ohash Hashtable of SOCGameOptions, or null
     * @param hideEmptyStringOpts omit string-valued options which are empty?
     *            Suitable only for sending defaults.
     * @return string of name-value pairs, or "-" for an empty or null ohash;
     *         any gameoptions of {@link #OTYPE_UNKNOWN} will not be
     *         part of the string. Format: k1=t,k2=f,k3=10,k4=t7,k5=f7.
     * The format for each value depends on its type:
     *<UL>
     *<LI>OTYPE_BOOL: t or f
     *<LI>OTYPE_ENUM: int in range 1-n
     *<LI>OTYPE_INTBOOL: t or f followed immediately by int value, as in: t7 or f9
     *<LI>All other optTypes: int value or string value, as appropriate
     *</UL>
     *
     * @throws ClassCastException if hashtable contains anything other
     *         than SOCGameOptions
     * @see #parseOptionNameValue(String, boolean)
     * @see #packValue(StringBuffer)
     */
    public static String packOptionsToString(Hashtable ohash, boolean hideEmptyStringOpts)
	throws ClassCastException
    {
	if ((ohash == null) || ohash.size() == 0)
	    return "-";

	StringBuffer sb = new StringBuffer();
	boolean hadAny = false;
	for (Enumeration e = ohash.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) ohash.get(e.nextElement());
	    if (op.optType == OTYPE_UNKNOWN)
		continue;  // <-- Skip this one --
	    if (hideEmptyStringOpts
	        && ((op.optType == OTYPE_STR) || (op.optType == OTYPE_STRHIDE))  // OTYPE_* - add here if string-valued
	        && op.getStringValue().length() == 0)
                continue;  // <-- Skip this one --       

	    if (hadAny)
		sb.append(SOCMessage.sep2_char);
	    else
		hadAny = true;
	    sb.append(op.optKey);
	    sb.append('=');
            op.packValue(sb);
	}
	return sb.toString();
    }

    /**
     * Pack current value of this option into a string.
     * This is used in {@link #packOptionsToString(Hashtable, boolean)} and
     * read in {@link #parseOptionNameValue(String, boolean)} and {@link #parseOptionsToHash(String)}.
     * See {@link #packOptionsToString(Hashtable, boolean)} for the string's format.
     *
     * @param sb Pack into (append to) this buffer
     */
    public void packValue(StringBuffer sb)
    {
        switch (optType)  // OTYPE_* - update this switch, and javadoc of packOptionsToString and of parseOptionNameValue.
        {                 //           The format produced must match that expected in parseOptionNameValue.
        case OTYPE_BOOL:
            sb.append(boolValue ? 't' : 'f');
            break;

        case OTYPE_INT:
        case OTYPE_ENUM:
            sb.append(intValue);
            break;

        case OTYPE_INTBOOL:
            sb.append(boolValue ? 't' : 'f');
            sb.append(intValue);
            break;

        case OTYPE_STR:
        case OTYPE_STRHIDE:
            if (strValue != null)
                sb.append(strValue);  // value is checked in setter vs SEP, SEP2
            break;

        default:
            sb.append ('?');  // Shouldn't happen
        }
    }

    /**
     * Utility - build a hashtable by parsing a list of option name-value pairs.
     *
     * @param ostr string of name-value pairs, as created by
     *             {@link #packOptionsToString(Hashtable, boolean)}.
     *             A leading comma is OK (possible artifact of StringTokenizer
     *             coming from over the network).
     *             If ostr=="-", hashtable will be null.
     * @return hashtable of SOCGameOptions, or null if ostr==null or empty ("-")
     *         or if ostr is malformed.  Any unrecognized options
     *         will be in the hashtable as type {@link #OTYPE_UNKNOWN}.
     * @see #parseOptionNameValue(String, boolean)
     */
    public static Hashtable parseOptionsToHash(String ostr)
    {
	if ((ostr == null) || ostr.equals("-"))
	    return null;

	Hashtable ohash = new Hashtable();

	StringTokenizer st = new StringTokenizer(ostr, SOCMessage.sep2);
	String nvpair;
	while (st.hasMoreTokens())
	{
	    nvpair = st.nextToken();  // skips any leading commas or doubled commas
            SOCGameOption copyOpt = parseOptionNameValue(nvpair, false);
            if (copyOpt == null)
                return null;  // parse error
            ohash.put(copyOpt.optKey, copyOpt);
	}  // while (moreTokens)

	return ohash;
    }

    /**
     * Utility - parse a single name-value pair produced by packOptionsToString.
     * Expected format of nvpair: "optname=optvalue".
     * Expected format of optvalue depends on its type.
     * See {@link #packOptionsToString(Hashtable, boolean)} for the format.
     *
     * @param nvpair Name-value pair string, as created by
     *               {@link #packOptionsToString(Hashtable, boolean)}.
     *               'T' or 't' is always allowed for bool value, regardless of forceNameUpcase.
     * @param forceNameUpcase Call {@link String#toUpperCase()} on keyname within nvpair?
     *               For friendlier parsing of manually entered (command-line) nvpair strings.
     * @return Parsed option, or null if parse error;
     *         if nvpair's option keyname is not a known option, returned optType will be {@link #OTYPE_UNKNOWN}.
     * @see #parseOptionsToHash(String)
     * @see #packValue(StringBuffer)
     */
    public static SOCGameOption parseOptionNameValue(final String nvpair, final boolean forceNameUpcase)
    {
        int i = nvpair.indexOf('=');  // don't just tokenize for this (efficiency, and param value may contain a "=")
        if (i < 1)
            return null;  // malformed

        String optkey = nvpair.substring(0, i);
        String optval = nvpair.substring(i+1);
        if (forceNameUpcase)
            optkey = optkey.toUpperCase();
        SOCGameOption knownOpt = (SOCGameOption) allOptions.get(optkey);
        SOCGameOption copyOpt;
        if (knownOpt == null)
        {
            copyOpt = new SOCGameOption(optkey);  // OTYPE_UNKNOWN
        }
        else
        {
            if ((optval.length() == 0)
                    && (knownOpt.optType != OTYPE_STR)  // OTYPE_* - if string-type, add here
                    && (knownOpt.optType != OTYPE_STRHIDE))
            {
                return null;  // malformed: no value
            }
            try
            {
                copyOpt = (SOCGameOption) knownOpt.clone();
            } catch (CloneNotSupportedException ce)
            {
                return null;
            }

            switch (copyOpt.optType)  // OTYPE_* - update this switch, must match format produced
            {                         //           in packValue / packOptionsToString
            case OTYPE_BOOL:
                copyOpt.setBoolValue(optval.equals("t") || optval.equals("T"));
                break;

            case OTYPE_INT:
            case OTYPE_ENUM:
                try
                {
                    copyOpt.setIntValue(Integer.parseInt(optval));
                } catch (NumberFormatException e)
                { 
                    return null;  // malformed
                }
                break;

            case OTYPE_INTBOOL:
                try
                {
                    final char ch0 = optval.charAt(0);
                    copyOpt.setBoolValue((ch0 == 't') || (ch0 == 'T'));
                    copyOpt.setIntValue(Integer.parseInt(optval.substring(1)));
                } catch (NumberFormatException e)
                { 
                    return null;  // malformed
                }
                break;

            case OTYPE_STR:
            case OTYPE_STRHIDE:
                copyOpt.setStringValue(optval);
                break;

            default:
                copyOpt = new SOCGameOption(optkey);  // OTYPE_UNKNOWN
            }
        }
        return copyOpt;
    }

    /**
     * Examine this set of options, finding the minimum required version to support
     * a game with these options.  The current value of an option can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but option "PL"'s minVersion is -1, to allow 2- or 3-player games with any client.
     *<P>
     * This calculation is done at the server when creating a new game.  Although the client's
     * version and options (and thus its copy of optionsMinimumVersion) may be newer or older,
     * and would give a different result if called, the server is authoritative for game options.
     * Calls at the client to optionsMinimumVersion should keep this in mind, especially if
     * a client's game option's {@link #lastModVersion} is newer than the server.
     *
     * @return the highest 'minimum version' among these options, or -1
     * @throws ClassCastException if values contain a non-SOCGameOption
     */
    public static int optionsMinimumVersion(Hashtable opts)
	throws ClassCastException
    {
	int minVers = -1;
	for (Enumeration e = opts.keys(); e.hasMoreElements(); )
	{
	    SOCGameOption op = (SOCGameOption) opts.get(e.nextElement());
	    if (op.minVersion > minVers)
		minVers = op.minVersion;

	    // Any option value checking for minVers is done here.
	    // None of the current options change minVers based on their value.
	}
	return minVers;
    }

    /**
     * Compare all known options against the specified version.
     * Make a list of all which are new or changed since that version
     * (using field {@link #lastModVersion}).
     *
     * @param vers Version to compare known options against
     * @return Vector of the unknown {@link SOCGameOption}s, or null
     *     if all are known and unchanged since vers
     */
    public static Vector optionsNewerThanVersion(int vers)
    {
	Vector uopt = null;
	for (Enumeration e = allOptions.elements(); e.hasMoreElements(); )
	{
	    SOCGameOption opt = (SOCGameOption) e.nextElement();
	    if (opt.lastModVersion > vers)
	    {
		if (uopt == null)
		    uopt = new Vector();
		uopt.addElement(opt);
	    }
	}
	return uopt;
    }

    /**
     * Compare a set of options with known-good values.
     * If any are above/below maximum/minimum, clip to the max/min value in knownOpts.
     * If any are unknown, return false. Will still check (and clip) the known ones.
     * If any are default, and their {@link #skipIfDefault} flag is set, remove them from
     * newOpts.
     *
     * @param newOpts Set of SOCGameOptions to check against knownOpts;
     *            an option's current value will be changed if it's outside of
     *            the min/max for that option in knownOpts.
     * @param knownOpts Set of known SOCGameOptions to check against, or null to use
     *            the server's static copy
     * @return true if all are known; false if any of newOpts are unknown
     *            or if an opt's type differs from that in knownOpts,
     *            or if opt's {@link #lastModVersion} differs from in knownOpts.
     * @throws IllegalArgumentException if newOpts contains a non-SOCGameOption
     */
    public static boolean adjustOptionsToKnown (Hashtable newOpts, Hashtable knownOpts)
	throws IllegalArgumentException
    {
	if (knownOpts == null)
	    knownOpts = allOptions;

	// use Iterator so we can remove from the hash if needed
        boolean allKnown = true;
	for (Iterator ikv = newOpts.entrySet().iterator();
	     ikv.hasNext(); )
	{
	    Map.Entry okv = (Map.Entry) ikv.next();

	    SOCGameOption op;
	    try {
	        op = (SOCGameOption) okv.getValue();
	    }
	    catch (ClassCastException ce)
	    {
                throw new IllegalArgumentException("wrong class, expected gameoption");	        
	    }
	    SOCGameOption knownOp = (SOCGameOption) knownOpts.get(op.optKey);
	    if ((knownOp == null) || (knownOp.optType != op.optType))
	    {
		allKnown = false;
	    } else {
	        // Clip int values, check default values

		if (knownOp.lastModVersion != op.lastModVersion)
		    allKnown = false;

		switch (op.optType)  // OTYPE_*
		{
		case OTYPE_INT:
		case OTYPE_INTBOOL:
		case OTYPE_ENUM:
		    {
			int iv = op.intValue;
			if (iv < knownOp.minIntValue)
			{
			    iv = knownOp.minIntValue;
			    op.setIntValue(iv);
			}
			else if (iv > knownOp.maxIntValue)
			{
			    iv = knownOp.maxIntValue;
			    op.setIntValue(iv);
			}

			if (knownOp.skipIfDefault && (iv == knownOp.defaultIntValue))
			    ikv.remove();
		    }
		    break;

		case OTYPE_BOOL:
                    if (knownOp.skipIfDefault && (op.boolValue == knownOp.defaultBoolValue))
                        ikv.remove();
		    break;

		// default: other types don't have default values (OTYPE_STR, OTYPE_STRHIDE)

		}  // endsw
	    }
	}
	return allKnown;
    }

    /**
     * For user output, the string name of the option type's constant.
     * The prefix "OTYPE_" is omitted.
     * @param optType An option's {@link #optType} value
     * @return String for this otype constant, such as "INTBOOL" or "UNKNOWN",
     *         or null if optType is outside the known type value range.
     */
    public static String optionTypeName(final int optType)
    {
        String otname;
        switch (optType)  // OTYPE_*
        {
        case OTYPE_UNKNOWN:
            otname = "UNKNOWN";  break;

        case OTYPE_BOOL:
            otname = "BOOL";  break; 

        case OTYPE_INT:
            otname = "INT";  break;

        case OTYPE_INTBOOL:
            otname = "INTBOOL";  break;

        case OTYPE_ENUM:
            otname = "ENUM";  break;

        case OTYPE_STR:
            otname = "STR";  break;

        case OTYPE_STRHIDE:
            otname = "STRHIDE"; break;

        default:
            otname = null;
        }
        return otname;
    }
}
