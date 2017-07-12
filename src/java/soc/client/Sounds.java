/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2014 Réal Gagnon <real@rgagnon.com>
 * (tone method, which has a BSD-like license: "There is no restriction to use
 *  individual How-To in a development (compiled/source) but a mention is appreciated.")
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
 * Utility class for basic sounds, using {@code javax.sound.sampled}.
 *
 * @since 1.2.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class Sounds
{
    /** Sampling rate */
    private static final float SAMPLE_RATE_HZ = 22050f;

    /** Major-scale "A" */
    private static final int CHIME_A_HZ = 2 * 880;

    private static final double PI_X_2 = 2.0 * Math.PI;

    // plan: generate CHIME_A_HZ, amplitude 0.8-0.9 for .01sec, then .9 to 0 for .18sec.

    /**
     * Generate and play a chime, with volume fading out to 0.
     * @param hz  Tone in Hertz
     * @param msec  Duration in milliseconds
     * @param vol  Volume (max is 1.0)
     * @throws LineUnavailableException
     */
    public static void chime(int hz, int msec, double vol)
        throws LineUnavailableException
    {
        // TODO 2 parts: attack for first 10msec, then release for rest of msec

        byte[] buf = new byte[1];
        AudioFormat af = new AudioFormat
            (SAMPLE_RATE_HZ, // sampleRate
             8,           // sampleSizeInBits
             1,           // channels
             true,        // signed
             false);      // bigEndian
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
        sdl.open(af);
        sdl.start();
        final int imax = (int) ((msec * SAMPLE_RATE_HZ) / 1000);
        for (int i = imax; i > 0; --i)
        {
            double angle = i / (SAMPLE_RATE_HZ / hz) * PI_X_2;
            buf[0] = (byte)(Math.sin(angle) * ((127.0 * vol * i) / imax));
            sdl.write(buf,0,1);
        }
        sdl.drain();
        sdl.stop();
        sdl.close();

    }

    /**
     * Generate and play a constant tone.
     *<P>
     * Based on https://stackoverflow.com/questions/23096533/how-to-play-a-sound-with-a-given-sample-rate-in-java
     * from Réal Gagnon's code at http://www.rgagnon.com/javadetails/java-0499.html:
     * optimized, decoupled from 8000Hz fixed sampling rate.
     * @param hz  Tone in Hertz
     * @param msec  Duration in milliseconds
     * @param vol  Volume (max is 1.0)
     * @throws LineUnavailableException
     */
    public static void tone(int hz, int msec, double vol)
        throws LineUnavailableException
    {
        final double vol_x_127 = 127.0 * vol;

        byte[] buf = new byte[1];
        AudioFormat af = new AudioFormat
            (SAMPLE_RATE_HZ, // sampleRate
             8,           // sampleSizeInBits
             1,           // channels
             true,        // signed
             false);      // bigEndian
        SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
        sdl.open(af);
        sdl.start();
        final int imax = (int) ((msec * SAMPLE_RATE_HZ) / 1000);
        for (int i=0; i < imax; i++)
        {
            double angle = i / (SAMPLE_RATE_HZ / hz) * PI_X_2;
            buf[0] = (byte)(Math.sin(angle) * vol_x_127);
            sdl.write(buf,0,1);
        }
        sdl.drain();
        sdl.stop();
        sdl.close();
    }

    /** Main, for testing */
    public static final void main(final String[] args)
    {
        try
        {
            tone(CHIME_A_HZ, 180, .9);
            Thread.sleep(60);
            chime(CHIME_A_HZ, 180, .9);

        } catch (Exception e) {
            // LineUnavailableException, InterruptedException
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

}
