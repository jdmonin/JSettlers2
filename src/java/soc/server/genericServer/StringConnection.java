/**
 * Local (StringConnection) network system.  Version 1.2.0.
 * This file Copyright (C) 2007-2009,2013,2015-2017 Jeremy D Monin <jeremy@nand.net>.
 * Portions of this file Copyright (C) 2016 Alessandro D'Ottavio
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
 * The maintainer of this program can be reached at jeremy@nand.net
 **/
package soc.server.genericServer;

import java.io.DataOutputStream;  // strictly for javadocs
import java.text.MessageFormat;
import java.util.Date;
import java.util.MissingResourceException;

import soc.game.SOCGame;  // strictly for passthrough in getLocalizedSpecial, and javadocs; not used otherwise
import soc.util.SOCStringManager;

/**
 * StringConnection allows clients and servers to communicate,
 * with no difference between local and actual networked traffic.
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release, becoming part of jsettlers v1.1.00
 *  1.0.1 - 2008-06-28 - add getConnectTime
 *  1.0.2 - 2008-07-30 - no change in this file
 *  1.0.3 - 2008-08-08 - add disconnectSoft, getVersion, setVersion
 *  1.0.4 - 2008-09-04 - add appData
 *  1.0.5 - 2009-05-31 - add isVersionKnown, setVersion(int,bool),
 *                       setVersionTracking, isInputAvailable,
 *                       wantsHideTimeoutMessage, setHideTimeoutMessage
 *  1.0.5.1- 2009-10-26- javadoc warnings fixed; remove unused import EOFException
 *  1.2.0 - 2017-05-21 - for I18N, add {@link #setI18NStringManager(SOCStringManager, String)} and
 *                       {@link #getLocalized(String)}. StringConnection is now a superclass, not an interface.
 *</PRE>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @version 1.2.0
 */
public abstract class StringConnection
{
    /**
     * Because subclass {@link NetStringConnection}'s connection protocol uses {@link DataOutputStream#writeUTF(String)},
     * its messages must be no longer than 65535 bytes when encoded into {@code UTF-8}
     * (which is not Java's internal string encoding).
     *<P>
     * This limitation is mentioned here for writing code which may send messages over either type of
     * {@code StringConnection}. {@link LocalStringConnection} is limited only by java's {@code String} max length.
     *<P>
     * You can check a string's {@code UTF-8} length with {@link String#getBytes(String) str.getBytes("utf-8")}.length.
     * Because of its cost, that's probably best done within the test cases, not production code.
     * @since 1.2.0
     */
    public final static int MAX_MESSAGE_SIZE_UTF8 = 0xFFFF;

    /**
     * The arbitrary key data (client "name") associated with this connection, or {@code null}.
     */
    protected Object data;

    /**
     * The arbitrary app-specific data associated with this connection, or {@code null}.
     * Not used or referenced by generic server.
     */
    protected Object appData;

    /**
     * The server-side locale for this client connection, for app-specific message formatting, or {@code null}.
     * Not used or referenced by the generic server layer.
     *<P>
     * App-specific connection data ({@link #getAppData()}) can hold a full {@code Locale} object;
     * see {@link soc.server.SOCClientData} for an example.
     *
     * @since 1.2.0
     */
    protected String localeStr;

    /**
     * The server-side string manager for app-specific client message formatting, or {@code null}.
     * Not used or referenced by the generic server layer.
     * @since 1.2.0
     */
    protected SOCStringManager stringMgr;

    protected int remoteVersion;
    protected boolean remoteVersionKnown;
    protected boolean remoteVersionTrack;
    protected boolean hideTimeoutMessage;

    /**
     * Is set if server-side. Notifies at EOF (calls removeConnection).
     * Messages from client will go into ourServer's {@link InboundMessageQueue}.
     */
    protected Server ourServer;

    /** Any error encountered, or {@code null} */
    protected Exception error;

    /** Time of connection to server, or of object creation if that time's not available */
    protected Date connectTime = new Date();

    /**
     * @return Hostname of the remote end of the connection
     */
    public abstract String host();

    /**
     * Send data over the connection.
     *
     * @param str Data to send
     *
     * @throws IllegalStateException if not yet accepted by server
     */
    public abstract void put(String str)
        throws IllegalStateException;

    /** For server-side thread which reads and treats incoming messages */
    public abstract void run();

    /** Are we currently connected and active? */
    public abstract boolean isConnected();

    /** Start ability to read from the net; called only by the server.
     * (In a network-based subclass, another thread may be started by this method.)
     *
     * @return true if able to connect, false if an error occurred.
     */
    public abstract boolean connect();

    /** Close the socket, set EOF; called after conn is removed from server structures */
    public abstract void disconnect();

    /**
     * Accept no further input, allow output to drain, don't immediately close the socket.
     * Once called, {@link #isConnected()} will return false, even if output is still being
     * sent to the other side.
     */
    public abstract void disconnectSoft();

    /**
     * The optional key data used to name this connection.
     *
     * @return The key data for this connection, or null.
     * @see #getAppData()
     */
    public Object getData()
    {
        return data;
    }

    /**
     * The optional app-specific changeable data for this connection.
     * Not used anywhere in the generic server, only in your app.
     *
     * @return The app-specific data for this connection.
     * @see #getData()
     */
    public Object getAppData()
    {
        return appData;
    }

    /**
     * Set the optional key data for this connection.
     *<P>
     * This is anything your application wants to associate with the connection.
     * The StringConnection system uses this data to name the connection,
     * so it should not change once set.
     *<P>
     * If you call setData after {@link Server#newConnection1(StringConnection)},
     * please call {@link Server#nameConnection(StringConnection)} afterwards
     * to ensure the name is tracked properly at the server.
     *
     * @param data The new key data, or null
     * @see #setAppData(Object)
     */
    public void setData(Object data)
    {
        this.data = data;
    }

    /**
     * Set the app-specific non-key data for this connection.
     *<P>
     * This is anything your application wants to associate with the connection.
     * The StringConnection system itself does not reference or use this data.
     * You can change it as often as you'd like, or not use it.
     *
     * @param data The new data, or null
     * @see #setData(Object)
     */
    public void setAppData(Object data)
    {
        appData = data;
    }

    /**
     * Get the locale for this connection, as reported to {@link #setI18NStringManager(SOCStringManager, String)}.
     * @return the locale passed to {@code setI18NStringManager}, which may be {@code null}
     * @since 1.2.0
     */
    public String getI18NLocale()
    {
        return localeStr;
    }

    /**
     * Set the I18N string manager and locale name for this connection, for server convenience.
     * Used for {@link #getLocalized(String)}.
     * @param mgr  String manager, or null
     * @param loc  Locale name, used only for {@link #getI18NLocale()}
     * @since 1.2.0
     */
    public void setI18NStringManager(SOCStringManager mgr, final String loc)
    {
        stringMgr = mgr;
        localeStr = loc;
    }

    /**
     * Get a localized string (having no parameters) with the given key.
     * Used for convenience at servers whose clients may have different locales.
     * @param key  Key to use for string retrieval
     * @return the localized string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @since 1.2.0
     * @see #getLocalized(String, Object...)
     * @see #getLocalizedSpecial(SOCGame, String, Object...)
     */
    public String getLocalized(final String key)
        throws MissingResourceException
    {
        SOCStringManager sm = stringMgr;
        if (sm == null)
            sm = SOCStringManager.getFallbackServerManagerForClient();

        return sm.get(key);
    }

    /**
     * Get and format a localized string (with parameters) with the given key.
     * Used for convenience at servers whose clients may have different locales.
     * @param key  Key to use for string retrieval
     * @param arguments  Objects to use with <tt>{0}</tt>, <tt>{1}</tt>, etc in the localized string
     *                   by calling {@link MessageFormat#format(String, Object...)}.
     * @return the localized formatted string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @since 1.2.0
     * @see #getLocalized(String)
     * @see #getLocalizedSpecial(SOCGame, String, Object...)
     */
    public String getLocalized(final String key, final Object ... arguments)
        throws MissingResourceException
    {
        SOCStringManager sm = stringMgr;
        if (sm == null)
            sm = SOCStringManager.getFallbackServerManagerForClient();

        return sm.get(key, arguments);
    }

    /**
     * Get and format a localized string (with special SoC-specific parameters) with the given key.
     * Used for convenience at servers whose clients may have different locales.
     * See {@link SOCStringManager#getSpecial(SOCGame, String, Object...)} for details.
     * Uses locale/strings from our client connection's {@link SOCStringManager} if set,
     * {@link SOCStringManager#getFallbackServerManagerForClient()} otherwise.
     *
     * @param game  Game object to pass through to {@code SOCStringManager.getSpecial(...)}
     * @param key  Key to use for string retrieval
     * @param arguments  Objects to use with <tt>{0}</tt>, <tt>{1,rsrcs}</tt>, etc in the localized string
     * @return the localized formatted string from the manager's bundle or one of its parents
     * @throws MissingResourceException if no string can be found for {@code key}; this is a RuntimeException
     * @throws IllegalArgumentException if the localized pattern string has a parse error (closing '}' brace without opening '{' brace, etc)
     * @since 1.2.0
     * @see #getLocalized(String)
     * @see #getLocalized(String, Object...)
     */
    public String getLocalizedSpecial(final SOCGame game, final String key, final Object ... arguments)
        throws MissingResourceException, IllegalArgumentException
    {
        SOCStringManager sm = stringMgr;
        if (sm == null)
            sm = SOCStringManager.getFallbackServerManagerForClient();

        return sm.getSpecial(game, key, arguments);
    }

    /**
     * @return Any error encountered, or null
     */
    public Exception getError()
    {
        return error;
    }

    /**
     * @return Time of connection to server, or of object creation if that time's not available
     * @see #connect()
     */
    public Date getConnectTime()
    {
        return connectTime;
    }

    /**
     * Give the version number (if known) of the remote end of this connection.
     * The meaning of this number is application-defined.
     * @return Version number, or 0 if unknown.
     */
    public int getVersion()
    {
        return remoteVersion;
    }

    /**
     * Set the version number of the remote end of this connection.
     * The meaning of this number is application-defined.
     *<P>
     * <b>Locking:</b> If we're on server side, and {@link #setVersionTracking(boolean)} is true,
     *  caller should synchronize on {@link Server#unnamedConns}.
     *
     * @param version Version number, or 0 if unknown.
     *                If version is greater than 0, future calls to {@link #isVersionKnown()}
     *                should return true.
     */
    public void setVersion(final int version)
    {
        setVersion(version, version > 0);
    }

    /**
     * Set the version number of the remote end of this connection.
     * The meaning of this number is application-defined.
     *<P>
     * <b>Locking:</b> If we're on server side, and {@link #setVersionTracking(boolean)} is true,
     *  caller should synchronize on {@link Server#unnamedConns}.
     *
     * @param version Version number, or 0 if unknown.
     * @param isKnown Should this version be considered confirmed/known by {@link #isVersionKnown()}?
     * @since 1.0.5
     */
    public void setVersion(final int version, final boolean isKnown)
    {
        final int prevVers = remoteVersion;
        remoteVersion = version;
        remoteVersionKnown = isKnown;
        if (remoteVersionTrack && (ourServer != null) && (prevVers != version))
        {
            ourServer.clientVersionRem(prevVers);
            ourServer.clientVersionAdd(version);
        }
    }

    /**
     * Is the version known of the remote end of this connection?
     * We may have just assumed it, or taken a default.
     * To confirm the version and set this flag, call {@link #setVersion(int, boolean)}.
     * @return True if we've confirmed the version, false if it's assumed or default.
     * @since 1.0.5
     */
    public boolean isVersionKnown()
    {
        return remoteVersionKnown;
    }

    /**
     * For server-side use, should we notify the server when our version
     * is changed by setVersion calls?
     * @param doTracking true if we should notify server, false otherwise.
     *        If true, please call both setVersion and
     *        {@link Server#clientVersionAdd(int)} before calling setVersionTracking.
     *        If false, please call {@link Server#clientVersionRem(int)} before
     *        calling setVersionTracking.
     * @since 1.0.5
     */
    public void setVersionTracking(final boolean doTracking)
    {
        remoteVersionTrack = doTracking;
    }

    /**
     * Is input available now, without blocking?
     * Same idea as {@link java.io.DataInputStream#available()}.
     * @since 1.0.5
     */
    public abstract boolean isInputAvailable();

    /**
     * If client connection times out at server, should the server not print a message to console?
     * This would be desired, for instance, in automated clients, which would reconnect
     * if they become disconnected.
     * @see #setHideTimeoutMessage(boolean)
     * @since 1.0.5
     */
    public boolean wantsHideTimeoutMessage()
    {
        return hideTimeoutMessage;
    }

    /**
     * If client connection times out at server, should the server not print a message to console?
     * This would be desired, for instance, in automated clients, which would reconnect
     * if they become disconnected.
     * @param wantsHide true to hide, false to print, the log message on idle-disconnect
     * @see #wantsHideTimeoutMessage()
     * @since 1.0.5
     */
    public void setHideTimeoutMessage(final boolean wantsHide)
    {
        hideTimeoutMessage = wantsHide;
    }

}
