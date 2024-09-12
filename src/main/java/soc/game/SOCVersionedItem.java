/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015,2017,2019-2020,2022-2023 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file from SOCGameOption.java Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
package soc.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.message.SOCMessage;

/**
 * Information/objects which can change or have new ones with different versions.
 * Items may have game-specific values, configurable at game creation.
 *<P>
 * A game may have none or some of the items ({@link SOCGameOption}s), or may use none
 * or one of a type of item ({@link SOCScenario}s). See those subclasses for more details.
 *<P>
 * <B>Version negotiation:</B><br>
 * Each item has version information, because items can be added or changed
 * with new versions of JSettlers.  Since games run on the server, the server is
 * authoritative about all items:  If the client is newer, it must defer to the
 * server's older set of known items.  At client connect, the client compares its
 * JSettlers version number to the server's, and asks for any changes to items if
 * their versions differ.
 *<P>
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public abstract class SOCVersionedItem implements Cloneable
{
    /**
     * For reference, the string that compiles into {@link #REGEX_SORT_RANK_PREFIX}.
     * @since 2.6.00
     */
    public static final String REGEX_SORT_RANK_PREFIX_STRING = "^(\\p{Nd}+) -|\\[(\\p{Nd}[^\\]]*)\\]";
        /*
         * "n -" is ^(\p{Nd}+) -
         * "[n]" is ^\[(\p{Nd}[^\]]*)\]
         */

    /**
     * Regex for the optional sort rank prefix recognized by {@link #setDesc(String)}.
     * Matches and groups for the initial {@code "n -"} or {@code "[n]"}.
     *<P>
     * Groups:
     *<OL>
     * <LI> {@code "n"} from dashed non-bracketed version, if present
     * <LI> {@code "n"} from bracketed version, if present
     *</OL>
     * @see #REGEX_SORT_RANK_PREFIX_STRING
     * @since 2.6.00
     */
    public static final Pattern REGEX_SORT_RANK_PREFIX = Pattern.compile(REGEX_SORT_RANK_PREFIX_STRING);

    /**
     * Item key name: Short alphanumeric name (uppercase, starting with a letter, '_' permitted)
     * to identify the item in a compact language-independent way.
     * See {@link #isAlphanumericUpcaseAscii(String)} for format.
     *<P>
     * This abstract parent class doesn't have a max length for {@code key},
     * but subclasses like {@link SOCGameOption} and {@link SOCScenario} do.
     */
    public final String key;

    /**
     * Minimum game version supporting this item, or -1;
     * same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * Public direct usage of this is discouraged;
     * use {@link #itemsMinimumVersion(Map)} or {@link #getMinVersion(Map)} instead,
     * because the current value of an item can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but game option "PL"'s minVersion is -1, to allow 2- or 3-player games with any client.
     *<P>
     * @see #lastModVersion
     */
    public final int minVersion;  // or -1

    /**
     * Most recent game version in which this item changed, or if not modified, the version which added it.
     * changes would include different min/max values, updated description, etc.
     * Same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     * @see #minVersion
     */
    public final int lastModVersion;

    /**
     * Is this item known or unknown at a given client or server version?
     * Used in cross-version compatibility.
     */
    public final boolean isKnown;

    /**
     * Descriptive text for the item. Must not contain the network delimiter
     * characters {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}.
     * See {@link #getDesc()} for more info about this field,
     * including the optional "sort ranking" prefix added in v2.6.00
     * that sets {@link #sortRank}.
     *<P>
     * Is {@code protected} for convenience, but call {@link #setDesc(String)} instead of directly changing its value.
     */
    protected String desc;  // OTYPE_* - if a new SOCGameOption type is added, update this field's javadoc
        // and getDesc() javadoc.

    /**
     * Item's optional sorting rank for localization, or default of {@link Integer#MAX_VALUE};
     * see {@link #getSortRank()} for details.
     * @since 2.6.00
     */
    private int sortRank = Integer.MAX_VALUE;

    /**
     * Create a new unknown item ({@link #isKnown == false}).
     * Minimum version will be {@link Integer#MAX_VALUE}. Desc == key.
     * @param key  Alphanumeric code to identify this item in a compact language-independent way.
     *          See {@link #isAlphanumericUpcaseAscii(String)} for format.
     *          This abstract parent class doesn't have a max length for {@code key},
     *          but subclasses like {@link SOCGameOption} and {@link SOCScenario} do.
     * @throws IllegalArgumentException if key length is > 3 or not alphanumeric,
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    protected SOCVersionedItem(final String key)
        throws IllegalArgumentException
    {
        this(key, Integer.MAX_VALUE, Integer.MAX_VALUE, false, key);
    }

    /**
     * Create a new versioned item.
     *
     * @param key  Alphanumeric code to identify this item in a compact language-independent way.
     *          See {@link #isAlphanumericUpcaseAscii(String)} for format.
     *          This abstract parent class doesn't have a max length for {@code key},
     *          but subclasses like {@link SOCGameOption} and {@link SOCScenario} do.
     * @param minVers Minimum client version where this item is included, or -1
     * @param lastModVers Last-modified version for this item, or version which added it
     * @param isKnown  True if this item is known at the client or server version we're dealing with
     * @param desc Descriptive brief text, to appear in the user interface.
     *             Desc must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     *             See {@link #setDesc(String)} for format of optional sort ranking prefix,
     *             which is parsed and removed in v2.6.00 and newer.
     * @throws IllegalArgumentException if key is not alphanumeric,
     *        or if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *        or if minVers or lastModVers is under 1000 but not -1
     */
    public SOCVersionedItem(final String key, final int minVers, final int lastModVers,
        final boolean isKnown, final String desc)
        throws IllegalArgumentException
    {
        if (! (isAlphanumericUpcaseAscii(key) || key.equals("-")))  // "-" is for server/network use
            throw new IllegalArgumentException("Key not alphanumeric: " + key);
        if ((minVers < 1000) && (minVers != -1))
            throw new IllegalArgumentException("minVers " + minVers + " for key " + key);
        if ((lastModVers < 1000) && (lastModVers != -1))
            throw new IllegalArgumentException("lastModVers " + lastModVers + " for key " + key);
        if (! SOCMessage.isSingleLineAndSafe(desc))
            throw new IllegalArgumentException("desc fails isSingleLineAndSafe");

        this.key = key;
        minVersion = minVers;
        lastModVersion = lastModVers;
        this.isKnown = isKnown;
        setDesc(desc);  // use setter, because might also need to parse sort ranking prefix or reset rank to default
    }

    /**
     * Descriptive text for the item. Must not contain the network delimiter
     * characters {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char}.
     * If ! {@link #isKnown}, will be {@link #key} or an empty string.
     * Exception: Server v2.7.00 and newer may send description strings for
     * specific {@link SOCGameOption}s which can't be used by the client which asked about them.
     *<P>
     * Subclass <b>{@link SOCGameOption}</b>:<BR>
     * If option type is integer-valued ({@link SOCGameOption#OTYPE_ENUM}, {@link SOCGameOption#OTYPE_INTBOOL}, etc),
     * may contain a placeholder '#' where the value is typed onscreen.
     *<P>
     * Before v2.0.00, {@code desc} was a public final field. This gave easy access without allowing changes to the
     * description which might violate the formatting rules mentioned here.  For i18n, v2.0.00 needed to be able to
     * change the field contents, so {@code getDesc()} and {@link #setDesc(String)} were added.
     *
     * @return  the description; never null
     * @see #getSortRank()
     * @since 2.0.00
     */
    public final String getDesc()
    {
        return desc;

        // OTYPE_* - if a new SOCGameOption type is added, update javadoc for getDesc() and desc field.
    }

    /**
     * Update this item's description text and optional sort rank.
     * See {@link #getDesc()} for formatting rules and info.
     *<P>
     * To help with localizations, {@code newDesc} can optionally start with a numeric "sort ranking",
     * to set {@link #getSortRank()}. If found, that prefix is parsed and removed (in v2.6.00 and newer)
     * and won't be returned as part of {@link #getDesc()}'s value. Older clients will keep that desc string prefix
     * visible and use it to help sort alphabetically.
     *<BR>
     * Its format is either {@code "[n] "} or {@code "n - "},
     * where {@code n} is 1 or more digit characters per {@link Character#isDigit(char)}.
     * Uses {@link #REGEX_SORT_RANK_PREFIX}.
     *<P>
     * Before v2.0.00, {@code desc} was a public final field. This gave easy access without allowing changes to the
     * description which might violate the formatting rules. For i18n, v2.0.00 needed to be able to change the
     * field contents, so {@link #getDesc()} and {@code setDesc(String)} were added.
     *
     * @param newDesc Descriptive brief text, to appear in the user interface. Not null.
     *             Must not contain {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *             and must evaluate true from {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws IllegalArgumentException if desc contains {@link SOCMessage#sep_char} or {@link SOCMessage#sep2_char},
     *     or starts but doesn't finish a sort ranking prefix: Missing space after {@code "[n]"} or {@code "n -"}
     *     or non-digit within brackets after {@code "[n"}
     * @since 2.0.00
     */
    public void setDesc(String newDesc)
        throws IllegalArgumentException
    {
        if (! SOCMessage.isSingleLineAndSafe(newDesc))
            throw new IllegalArgumentException("desc fails isSingleLineAndSafe");

        Matcher m = REGEX_SORT_RANK_PREFIX.matcher(newDesc);
        if (m.find())
        {
            String rankValue = m.group(1);  // from "n -"
            if (rankValue == null)
                rankValue = m.group(2);  // from "[n]"

            // check for required space before changing sortRank field, not after
            final int L = newDesc.length();
            int idxAfter = m.end();
            if (idxAfter >= L)
                throw new IllegalArgumentException("failed to parse rank prefix: nothing after prefix: " + newDesc);
            if (newDesc.charAt(idxAfter) != ' ')
                throw new IllegalArgumentException("failed to parse rank prefix: trailing space required: " + newDesc);

            try
            {
                sortRank = Integer.parseInt(rankValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("failed to parse rank prefix: " + newDesc, e);
            }

            newDesc = newDesc.substring(idxAfter + 1);
        } else {
            sortRank = Integer.MAX_VALUE;
        }

        desc = newDesc;
    }

    /**
     * Get this item's sort rank, used by some localizations which want a specific sorting order.
     * Updated by {@link #setDesc(String)} based on optional numeric prefix text.
     * All items without a sort ranking prefix are placed together at the end and sorted alphabetically.
     * @return rank parsed from description (lower values are sorted ahead of higher values),
     *     or default of {@link Integer#MAX_VALUE}
     * @see #getDesc()
     * @since 2.6.00
     */
    public int getSortRank()
    {
        return sortRank;
    }

    /**
     * Minimum game version supporting this item, given {@link #minVersion} and the item's current value.
     * The current value of an item can change its minimum version.
     * For example, a 5- or 6-player game will need a newer client than 4 players,
     * but game option "PL"'s {@link #minVersion} is -1, to allow 2- or 3-player games with any client.
     *<P>
     * Occasionally, an older client version supports a new item, but only by changing
     * the value of some other items it recognizes.
     * This method will calculate the minimum client version at which items are unchanged,
     * if {@code items} != null.
     *<P>
     * Because this calculation is hardcoded here, the version returned may be too low when
     * called at an older-version client.  The server will let the client know if it's too
     * old to join or create a game due to the requested game items.
     *<P>
     * This implementation just returns {@link #minVersion}; override for more complex behavior.
     * See {@link SOCGameOption#getMinVersion(Map)} for calculations done for game options ("PL", "SC",
     * etc) based on their current value.
     *
     * @param  items If null, return the minimum version supporting this item.
     *               Otherwise, the minimum version at which this item's value isn't changed
     *               (for compatibility) by the presence of other items.
     * @return minimum version, or -1;
     *     same format as {@link soc.util.Version#versionNumber() Version.versionNumber()}.
     *     If {@code items} != null, the returned version will either be -1 or >= 1107
     *     (the first version with game options) or >= 2000 (first version with scenarios),
     *     depending on the item subclass.
     * @see #itemsMinimumVersion(Map)
     */
    public int getMinVersion(Map<String, ? extends SOCVersionedItem> items)
    {
        return minVersion;
    }

    /**
     * Search this group of items and find any unknown ones (with ! {@link #isKnown}).
     * @param items  map of SOCVersionedItems
     * @param alreadyKnown  Set of already-known item {@link #key}s to ignore during this check, or null.
     *     This is intended for checking a list of items after server has previously sent info on known/unknown items:
     *     Those items already marked as unknown should be ignored here.
     * @return List of unknown items' {@link #key}s, or null if all are known
     */
    public static <I extends SOCVersionedItem> List<String> findUnknowns
        (final Map<String, I> items, final Set<String> alreadyKnown)
    {
        ArrayList<String> unknowns = null;

        for (Map.Entry<String, I> e : items.entrySet())
        {
            if ((alreadyKnown != null) && alreadyKnown.contains(e.getKey()))
                continue;

            SOCVersionedItem it = e.getValue();
            if (! it.isKnown)
            {
                if (unknowns == null)
                    unknowns = new ArrayList<String>();

                unknowns.add(it.key);
            }
        }

        return unknowns;
    }

    /**
     * Examine this set of items, calculating the minimum required version to support
     * a game with these items. Calls each item's {@link #getMinVersion(Map)}, where
     * {@code Map} is {@code null} unless {@code minCliVersionForUnchangedItems} is true.
     *<P>
     * The current value of an item can change the item's minimum version.
     * For example, game option {@code "PL"}'s minVersion is -1 for 2- to 4-player games with any client version,
     * but a 5- or 6-player game will need client 1.1.08 or newer.
     *<P>
     * This calculation is done at the server when creating a new game.  Although the client's
     * version and classes (and thus its copy of {@code itemsMinimumVersion}) may be newer or older,
     * and would give a different result if called, the server is authoritative for game items.
     * Calls at the client to {@code itemsMinimumVersion} should keep this in mind, especially if
     * a client's game option's {@link #lastModVersion} is newer than the server.
     *<P>
     * Calls {@link #itemsMinimumVersion(Map, boolean, Map) itemsMinimumVersion(items, false, null)}.
     *
     * @param items  a set of items; may be empty or {@code null}
     * @return the highest 'minimum version' among these items, or -1
     * @see #itemsMinimumVersion(Map, boolean, Map)
     * @see #getMinVersion(Map)
     */
    public static int itemsMinimumVersion(final Map<String, ? extends SOCVersionedItem> items)
    {
        return itemsMinimumVersion(items, false, null);
    }

    /**
     * Examine this set of items, calculating the minimum required version to support
     * a game with these items. Calls each item's {@link #getMinVersion(Map)}, where
     * {@code Map} is {@code null} unless {@code minCliVersionForUnchangedItems} is true.
     *<P>
     * The current value of an item can change the item's minimum version.
     * For example, game option {@code "PL"}'s minVersion is -1 for 2- to 4-player games with any client version,
     * but a 5- or 6-player game will need client 1.1.08 or newer.
     *<P>
     * This calculation is done at the server when creating a new game.  Although the client's
     * version and classes (and thus its copy of {@code itemsMinimumVersion}) may be newer or older,
     * and would give a different result if called, the server is authoritative for game items.
     * Calls at the client to {@code itemsMinimumVersion} should keep this in mind, especially if
     * a client's game option's {@link #lastModVersion} is newer than the server.
     *<P>
     * When {@code items} are {@link SOCGameOption}s, will check for {@link SOCGameOption#FLAG_DROP_IF_PARENT_UNUSED}:
     * Items with that flag will be ignored unless their parent is part of {@code items}
     * and parent's {@link SOCGameOption#isSet()} is true.
     *<P>
     * <b>Backwards-compatibility support: {@code calcMinVersionForUnchanged} parameter:</b><br>
     * Occasionally, an older client version supports a new item, but only by changing
     * the value of some other items it recognizes.  If this parameter is true,
     * this method will calculate the minimum client version at which the items are understood
     * without backwards-compatibility changes to their values.
     * (If all connected clients are this version or newer, that new game's item values
     * can be broadcast to all clients without changes.)
     * If {@code calcMinVersionForUnchanged} is true, the returned version may be higher than if false;
     * older clients may support {@code items} only by changing some item values for compatibility.
     *
     * @param items  a set of items; may be empty or {@code null}
     * @param calcMinVersionForUnchanged  If true, return the minimum version at which these
     *         options' values aren't changed (for compatibility) by the presence of new options.
     * @param itemsMins  If not {@code null}, an empty map to which to add any item keys which have a minimum version
     *         in order to return that info
     * @return the highest 'minimum version' among these options, or -1.
     *         If {@code calcMinVersionForUnchanged}, the returned version will either be -1 or >= 1107
     *         (the first version with game options).
     * @throws IllegalArgumentException if {@code itemsMins} != null but isn't empty
     * @see #itemsMinimumVersion(Map)
     * @see #getMinVersion(Map)
     */
    public static int itemsMinimumVersion
        (final Map<String, ? extends SOCVersionedItem> items, final boolean calcMinVersionForUnchanged,
         final Map<String, Integer> itemsMins)
        throws IllegalArgumentException
    {
        if (items == null)
            return -1;
        if ((itemsMins != null) && ! itemsMins.isEmpty())
            throw new IllegalArgumentException("itemsMins");

        int minVers = -1;
        final Map<String, ? extends SOCVersionedItem> itemsChk = calcMinVersionForUnchanged ? items : null;

        for (SOCVersionedItem itm : items.values())
        {
            int itmMin = itm.getMinVersion(itemsChk);  // includes any item-value checking for minVers
            if (itmMin == -1)
                continue;

            if ((itm instanceof SOCGameOption)
                && ((SOCGameOption) itm).hasFlag(SOCGameOption.FLAG_DROP_IF_PARENT_UNUSED))
            {
                final String parKey = SOCGameOption.getGroupParentKey(itm.key);
                if (parKey != null)
                {
                    SOCVersionedItem par = items.get(parKey);
                    if (! ((par instanceof SOCGameOption) && ((SOCGameOption) par).isSet()))
                        continue;
                }
            }

            if (itmMin > minVers)
                minVers = itmMin;
            if (itemsMins != null)
                itemsMins.put(itm.key, itmMin);
        }

        return minVers;
    }

    /**
     * Compare a set of items against the specified version.
     * Make a list of all which are new or changed since that version.
     *<P>
     * This method has 2 modes, because it's called for 2 different purposes:
     *<UL>
     * <LI> Sync client-server known-items info, in general: {@code checkValues} == false
     * <LI> Check if client can create game with a specific set of item values: {@code checkValues} == true
     *</UL>
     * See {@code checkValues} for method's behavior in each mode.
     *<P>
     *
     * @param vers  Version to compare known items against
     * @param checkValues  Which mode: Check items' current values and {@link #minVersion},
     *              not their {@link #lastModVersion}?  An item's minimum version
     *              can increase based on its value; see {@link #getMinVersion(Map)}.
     * @param items Set of items to check versions and current values
     * @return List of the newer (added or changed) items, or null
     *     if all are known and unchanged since {@code vers}.
     *     <BR>
     *     <B>Note:</B> May include items with {@link #minVersion} &gt; {@code vers};
     *     for some item types the client may want to know about those.
     * @see #itemsForVersion(int, Map)
     * @throws NullPointerException if {@code items} is null
     */
    public static <I extends SOCVersionedItem> List<I> itemsNewerThanVersion
        (final int vers, final boolean checkValues, Map<String, I> items)
        throws IllegalArgumentException
    {
        return implItemsVersionCheck(vers, false, checkValues, items);
    }

    /**
     * Get all items valid at version {@code vers}.
     *<P>
     * If {@code vers} from a client is newer than this version of SOCVersionedItem, will return all items known at this
     * version, which may not include all of the newer version's items.  Client game-item negotiation handles this
     * by having the newer client send all its new (added or changed) item keynames to the older server to allow,
     * adjust, or reject.
     *
     * @param vers  Version to compare items against
     * @param items  Set of {@link SOCVersionedItem}s to check versions
     * @return  List of all {@link SOCVersionedItem}s valid at version {@code vers}, or {@code null} if none.
     * @throws NullPointerException if {@code items} is null
     */
    public static <I extends SOCVersionedItem> List<I> itemsForVersion
        (final int vers, Map<String, I> items)
        throws IllegalArgumentException
    {
        return implItemsVersionCheck(vers, true, false, items);
    }

    /**
     * Get all items added or changed since version {@code vers}, or all items valid at {@code vers},
     * to implement {@link #itemsNewerThanVersion(int, boolean, Map)}
     * and {@link #itemsForVersion(int, Map)}.
     * @param vers  Version to compare items against
     * @param getAllForVersion  True to get all valid items ({@code itemsForVersion} mode),
     *              false for newer added or changed items only ({@code itemsNewerThanVersion} modes).
     *              If true and {@code vers} is newer than this version of SOCVersionedItem, will return
     *              all items known at this version.
     * @param checkValues  If not {@code getAllForVersion}, which mode to run in:
     *              Check items' current values and {@link #minVersion}, not their {@link #lastModVersion}?
     *              An item's minimum version can increase based on its value; see {@link #getMinVersion(Map)}.
     * @param items  Set of {@link SOCVersionedItem}s to check versions and current values; not null
     * @return List of the requested {@link SOCVersionedItem}s, or null if none match the conditions, at {@code vers};
     *     see {@code itemsNewerThanVersion} and {@code itemsForVersion} for return details.
     *     <BR>
     *     <B>Note:</B> If not {@code getAllForVersion}, may include items with {@link #minVersion} &gt; {@code vers};
     *     for some item types the client may want to know about those.
     * @throws IllegalArgumentException  if {@code getAllForVersion && checkValues}: Cannot combine these modes
     * @throws NullPointerException if {@code items} is null
     */
    protected static <I extends SOCVersionedItem> List<I> implItemsVersionCheck
        (final int vers, final boolean getAllForVersion, final boolean checkValues,
         Map<String, I> items)
        throws IllegalArgumentException
    {
        if (getAllForVersion && checkValues)
            throw new IllegalArgumentException();

        ArrayList<I> ret = null;  // collect newer items here, or all items if getAllForVersion

        for (I itm : items.values())
        {
            if (getAllForVersion)
            {
                if (itm.minVersion > vers)
                    itm = null;  // too new for vers to use
            } else {
                if (checkValues)
                {
                    if (itm.getMinVersion(null) <= vers)
                        itm = null;  // not too new
                } else {
                    if (itm.lastModVersion <= vers)
                        itm = null;  // not modified since vers
                }
            }

            if (itm == null)
                continue;

            if (ret == null)
                ret = new ArrayList<I>();
            ret.add(itm);
        }

        return ret;
    }

    /**
     * Test whether a string's characters are all within the strict
     * ranges 0-9, A-Z. The first character must be A-Z. Item name keys
     * must start with a letter and contain only ASCII uppercase letters
     * ('A' through 'Z') and digits ('0' through '9'), in order to normalize
     * handling and network message formats.
     *<P>
     * Version 2.0.00 and newer allow '_' in game item and {@link SOCGameOption} names;
     * please check {@link #minVersion} vs '_' outside of this method.
     * Name keys with '_' can't be sent to older clients.
     *<P>
     * Does not check length; see the item's class javadoc ({@link SOCGameOption}, etc) for max length.
     *<P>
     * This method is placed in this class because versioned items (and their keys)
     * are sometimes sent across a network.
     *
     * @param s string to test
     * @return true if all characters are OK, false otherwise
     */
    public static final boolean isAlphanumericUpcaseAscii(String s)
    {
        for (int i = s.length()-1; i>=0; --i)
        {
            final char c = s.charAt(i);
            if (((c < '0') || (c > '9'))
                && ((c < 'A') || (c > 'Z'))
                && (c != '_'))
                return false;
            if ((i == 0) && (c < 'A'))
                return false;

            // We use range checks, and not methods such as
            // Character.isLetterOrDigit(ch), because those
            // methods also permit unicode characters beyond
            // what we'd like to accept here.
        }

        return true;
    }

}
