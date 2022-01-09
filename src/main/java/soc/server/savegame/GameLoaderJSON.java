/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.server.savegame;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import soc.game.*;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCPlayerElement.PEType;
import soc.server.SOCServer;

/**
 * Load a game and board's current state from a JSON file into a {@link SavedGameModel}.
 * Once loaded, game's {@link SOCGame#getGameState()} will temporarily have state {@link SOCGame#LOADING};
 * its actual gameState will be in game's {@code oldGameState} field.
 * Once the debug user has connected necessary bots or otherwise satisfied possible constraints,
 * must call {@link SavedGameModel#resumePlay(boolean)} to check constraints and resume game play.
 *<P>
 * Some fields use custom deserializers, either registered in a private method here or in {@link SavedGameModel},
 * or declared through {@code @JsonAdapter} field annotations in {@link SavedGameModel}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see GameSaverJSON
 * @since 2.3.00
 */
public class GameLoaderJSON
{
    /** Dummy game, for objects which require game != null */
    public final static SOCGame dummyGame = new SOCGame("dummy", false);

    /** Dummy player ({@code pn} == -2), for objects which require player != null */
    public final static SOCPlayer dummyPlayer = new SOCPlayer(-2, dummyGame);

    /** Builder; is set up in {@link #initGson()} */
    private static GsonBuilder gsonb;

    /**
     * Load a game from a JSON file.
     * Loads into a Model and calls {@link SavedGameModel#createLoadedGame(SOCServer)}.
     *<P>
     * Assumes caller has checked that gson jar is on classpath
     * by calling {@code Class.forName("com.google.gson.Gson")} or similar.
     *
     * @param loadFrom File to load from; filename should end with {@link GameSaverJSON#FILENAME_EXTENSION}
     * @param srv  Server reference to check for bot name collisions; not {@code null}.
     *     Any bot players in the loaded game data with same names as those logged into the server
     *     will be renamed to avoid problems during random bot assignment while joining the game.
     * @return  loaded game model
     * @throws NoSuchElementException if file's model schema version is newer than the
     *     current {@link SavedGameModel#MODEL_VERSION}; see {@link SavedGameModel#checkCanLoad(SOCGameOptionSet)}
     *     for details
     * @throws SOCGameOptionVersionException if loaded data's {@link #gameMinVersion} field
     *     is newer than the server's {@link soc.util.Version#versionNumber()};
     *     see {@link SavedGameModel#checkCanLoad(SOCGameOptionSet)} for details
     * @throws SavedGameModel.UnsupportedSGMOperationException if loaded game model has an option or feature
     *     not yet supported by {@link SavedGameModel#createLoadedGame(SOCServer)};
     *     see {@link SavedGameModel#checkCanLoad(SOCGameOptionSet)} for details
     * @throws StringIndexOutOfBoundsException  if a {@link JsonSyntaxException} occurs while loading, this wraps it
     *     so the caller doesn't need to know GSON-specific exception types
     * @throws IOException  if a problem occurs while loading, including a {@link JsonIOException}
     * @throws IllegalArgumentException if there's a problem while creating the loaded game.
     *     {@link Throwable#getCause()} will have the exception thrown by the SOCGame/SOCPlayer method responsible.
     *     Catch subclass {@code SOCGameOptionVersionException} before this one.
     *     Also thrown if {@code srv} is null.
     */
    public static SavedGameModel loadGame(final File loadFrom, final SOCServer srv)
        throws NoSuchElementException, SOCGameOptionVersionException,
            SavedGameModel.UnsupportedSGMOperationException, StringIndexOutOfBoundsException,
            IOException, IllegalArgumentException
    {
        if (srv == null)
            throw new IllegalArgumentException("srv");

        initGson();

        final SavedGameModel sgm;
        try
            (final FileInputStream fis = new FileInputStream(loadFrom);
             final InputStreamReader reader = new InputStreamReader(fis, "UTF-8"); )
        {
            sgm = gsonb.create().fromJson(reader, SavedGameModel.class);
        } catch (JsonIOException e) {
            throw new IOException("JSON: " + e.getMessage(), e);
        } catch (JsonSyntaxException e) {
            StringIndexOutOfBoundsException wrap = new StringIndexOutOfBoundsException("JSON: " + e.getMessage());
            wrap.initCause(e);
            throw wrap;
        }

        sgm.createLoadedGame(srv);

        return sgm;
    }

    /**
     * Initialize {@link #gsonb} once when needed, including registering some deserializers.
     * Assumes gson jar is on classpath, and caller has checked {@link soc.server.SOCServer#savegameInitFailed}.
     * Some other custom deserializers are instead declared through {@code @JsonAdapter} field annotations
     * in {@link SavedGameModel}.
     */
    private static void initGson()
    {
        GsonBuilder gb = gsonb;
        if (gb != null)
            return;

        gb = new GsonBuilder();
        SavedGameModel.initGsonRegisterAdapters(gb);
        gb.registerTypeAdapter
            (new TypeToken<HashMap<GEType, Integer>>(){}.getType(),
             new EnumKeyedMapDeserializer<GEType>(GEType.class));
        gb.registerTypeAdapter
            (new TypeToken<HashMap<PEType, Integer>>(){}.getType(),
             new EnumKeyedMapDeserializer<PEType>(PEType.class));

        gsonb = gb;
    }

    /**
     * Custom deserializer for a {@link HashMap} that has enum keys, to ignore any unknown enum constant names
     * it may possibly contain (for forwards compatibility).
     *<P>
     * GSON's built-in deserializer returns {@code null} for unknown enum constants, so one unknown puts a null
     * key into the map, and a second halts parsing with a "duplicate key" exception for the two nulls.
     * This custom class avoids the problem by not adding null unknown constants to the Map.
     *<P>
     * Used for loading {@link SavedGameModel}'s {@link GEType} and {@link PEType} maps.
     */
    public static class EnumKeyedMapDeserializer<E extends Enum<E>>
        implements JsonDeserializer<HashMap<E, Integer>>
    {
        private final Class<E> enumClassType;

        EnumKeyedMapDeserializer(final Class<E> enumClass)
        {
            enumClassType = enumClass;
        }

        public HashMap<E, Integer> deserialize
            (JsonElement elem, final Type t, final JsonDeserializationContext ctx)
            throws JsonParseException
        {
            HashMap<E, Integer> ret = new HashMap<>();

            for (Map.Entry<String, JsonElement> ent : elem.getAsJsonObject().entrySet())
            {
                final String key = ent.getKey();
                if (key == null)
                    continue;  // unlikely

                final E ev;
                try
                {
                    ev = E.valueOf(enumClassType, key);
                    if (ev == null)
                        continue;
                } catch (IllegalArgumentException e) {
                    continue;  // not found in enum
                }

                final JsonElement val = ent.getValue();
                try
                {
                    ret.put(ev, val.getAsInt());
                } catch (ClassCastException | IllegalStateException e) {
                    throw new JsonParseException("Expected int values in map", e);
                }
            }

            return ret;
        }
    }

}
