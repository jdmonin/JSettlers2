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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import soc.game.*;

/**
 * Load a game and board's current state from a JSON file into a {@link SavedGameModel}.
 * Once loaded, game's {@link SOCGame#getGameState()} will temporarily have state {@link SOCGame#LOADING};
 * its actual gameState will be in game's {@code oldGameState} field.
 * Once the debug user has connected necessary bots or otherwise satisfied possible constraints,
 * must call {@link SavedGameModel#resumePlay(boolean)} to check constraints and resume game play.
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
     *
     * @param loadFrom File to load from; filename should end with {@link GameSaverJSON#FILENAME_EXTENSION}
     * @return  loaded game model
     * @throws IllegalStateException if required static game list field {@link SavedGameModel#glas} is null
     * @throws IOException  if a problem occurs while loading
     */
    public static SavedGameModel loadGame(final File loadFrom)
        throws IllegalStateException, IOException
    {
        if (SavedGameModel.glas == null)
            throw new IllegalStateException("SavedGameModel.glas is null");

        initGson();

        InputStreamReader reader = new InputStreamReader
            (new FileInputStream(loadFrom), "UTF-8");
        SavedGameModel sgm = gsonb.create().fromJson(reader, SavedGameModel.class);
        reader.close();

        sgm.createLoadedGame();

        return sgm;
    }

    /** Initialize {@link #gsonb} once when needed, including registering a deserializer. */
    private static void initGson()
    {
        GsonBuilder gb = gsonb;
        if (gb != null)
            return;

        gb = new GsonBuilder();
        gb.registerTypeAdapter(SOCPlayingPiece.class, new PPieceDeserializer());

        gsonb = gb;
    }

    /**
     * Deserialize abstract {@link SOCPlayingPiece} as {@link SOCRoad}, {@link SOCSettlement}, etc
     * based on {@code pieceType} field. Unknown pieceTypes throw {@link JsonParseException}.
     */
    private static class PPieceDeserializer implements JsonDeserializer<SOCPlayingPiece>
    {
        public SOCPlayingPiece deserialize
            (final JsonElement elem, final Type t, final JsonDeserializationContext ctx)
            throws JsonParseException
        {
            final JsonObject elemo = elem.getAsJsonObject();
            final int ptype = elemo.get("pieceType").getAsInt(),
                      coord = elemo.get("coord").getAsInt();

            final SOCPlayingPiece pp;

            switch (ptype)
            {
            case SOCPlayingPiece.ROAD:
                pp = new SOCRoad(dummyPlayer, coord, null);
                break;

            case SOCPlayingPiece.SETTLEMENT:
                pp = new SOCSettlement(dummyPlayer, coord, null);
                break;

            case SOCPlayingPiece.CITY:
                pp = new SOCCity(dummyPlayer, coord, null);
                break;

            case SOCPlayingPiece.SHIP:
                pp = new SOCShip(dummyPlayer, coord, null);
                if (elemo.has("isClosed") && elemo.get("isClosed").getAsBoolean())
                    ((SOCShip) pp).setClosed();
                break;

            // doesn't need to handle SOCPlayingPiece.FORTRESS,
            // because that's not part of player's SOCPlayingPiece list

            default:
                throw new JsonParseException("unknown pieceType: " + ptype);
            }

            if (elemo.has("specialVP"))
            {
                int n = elemo.get("specialVP").getAsInt();
                if (n != 0)
                    pp.specialVP = n;
            }

            return pp;
        }
    }
}
