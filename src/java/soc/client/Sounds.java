/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
package soc.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Utility class for basic sounds.
 *
 * @since 1.2.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class Sounds
{
    // Based on https://stackoverflow.com/questions/23096533/how-to-play-a-sound-with-a-given-sample-rate-in-java

    public static float SAMPLE_RATE = 8000f;

    public static void tone(int hz, int msecs, double vol)
        throws LineUnavailableException
    {
        byte[] buf = new byte[1];
        AudioFormat af =
            new AudioFormat(
                SAMPLE_RATE, // sampleRate
                8,           // sampleSizeInBits
                1,           // channels
                true,        // signed
                false);      // bigEndian
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
        sdl.open(af);
        sdl.start();
        for (int i=0; i < msecs*8; i++) {
          double angle = i / (SAMPLE_RATE / hz) * 2.0 * Math.PI;
          buf[0] = (byte)(Math.sin(angle) * 127.0 * vol);
          sdl.write(buf,0,1);
        }
        sdl.drain();
        sdl.stop();
        sdl.close();
    }

    /** Main, for testing */
    public static final void main(final String[] args)
        throws Exception
    {
        tone(1000,100, 1.0);
        Thread.sleep(1000);
        tone(100,1000, 1.0);
        Thread.sleep(1000);
        tone(5000,100, 1.0);
        Thread.sleep(1000);
        tone(400,500, 1.0);
        Thread.sleep(1000);
        tone(400,500, 0.2);
    }

}
