/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2014 Réal Gagnon <real@rgagnon.com>
 * (genTone method, which has a BSD-like license: "There is no restriction to use
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
 *<H3>Usage:</H3>
 * Either generate and play a tone using {@link #chime(int, int, double)}
 * or {@link #tone(int, int, double)}, or generate one using
 * {@link #genChime(int, int, double)} or {@link #genTone(int, int, double)}
 * to be played later with {@link #playPCMBytes(byte[])}.
 *<P>
 * Generating tones ahead of time can help with latency, instead of
 * allocating a buffer each time a sound is played.
 *
 * @since 1.2.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class Sounds
{
    /** Sampling rate: 22050 Hz */
    public static final float SAMPLE_RATE_HZ = 22050f;

    /** Major-scale "A" at 2 * 880 Hz */
    public static final int CHIME_A_HZ = 2 * 880;

    private static final double PI_X_2 = 2.0 * Math.PI;

    /** Audio format for PCM-encoded signed 8-bit mono at {@link #SAMPLE_RATE_HZ} */
    private static final AudioFormat AFMT_PCM_8_AT_SAMPLE_RATE = new AudioFormat
        (SAMPLE_RATE_HZ,
         8,           // sampleSizeInBits
         1,           // channels
         true,        // signed
         false);      // bigEndian

    /**
     * Generate a chime, with volume fading out to 0.
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @throws IllegalArgumentException if {@code msec} > 1000
     */
    public static byte[] genChime(int hz, int msec, double vol)
        throws IllegalArgumentException
    {
        if (msec > 1000)
            throw new IllegalArgumentException("msec");

        final int imax = (int) ((msec * SAMPLE_RATE_HZ) / 1000);
        byte[] buf = new byte[imax];

        // 2 parts if >= 40ms: attack for first 10msec (amplitude 0.8 * vol to vol),
        // then release for rest of msec (fading amplitude: vol to 0)

        final int amax;
        if (msec >= 40)
        {
            amax = (int) ((10 * SAMPLE_RATE_HZ) / 1000);
            final double vol0 = 0.8 * vol,
                         dVol = vol - vol0;
            for (int i = 0; i < amax; ++i)
            {
                double angle = i / (SAMPLE_RATE_HZ / hz) * PI_X_2;
                buf[i] = (byte)(Math.sin(angle) * 127.0 * (vol0 + ((dVol * i) / amax)));
            }
        } else {
            amax = 0;
        }

        final int rmax = imax - amax;
        for (int i = amax, j = rmax; j > 0; ++i, --j)
        {
            double angle = i / (SAMPLE_RATE_HZ / hz) * PI_X_2;
            buf[i] = (byte)(Math.sin(angle) * ((127.0 * vol * j) / rmax));
        }

        return buf;
    }

    /**
     * Generate and play a chime, with volume fading out to 0.
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @throws IllegalArgumentException if {@code msec} > 1000
     * @throws LineUnavailableException if the line resource can't be opened
     */
    public static void chime(int hz, int msec, double vol)
        throws IllegalArgumentException, LineUnavailableException
    {
        playPCMBytes(genChime(hz, msec, vol));
    }

    /**
     * Generate a constant tone.
     *<P>
     * Based on https://stackoverflow.com/questions/23096533/how-to-play-a-sound-with-a-given-sample-rate-in-java
     * from Réal Gagnon's code at http://www.rgagnon.com/javadetails/java-0499.html:
     * optimized, decoupled from 8000Hz fixed sampling rate, separated generation from playback.
     *
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @return  A sound byte buffer, suitable for {@link #playPCMBytes(byte[])}
     * @throws IllegalArgumentException if {@code msec} > 1000
     */
    public static byte[] genTone(int hz, int msec, double vol)
        throws IllegalArgumentException
    {
        if (msec > 1000)
            throw new IllegalArgumentException("msec");

        final double vol_x_127 = 127.0 * vol;

        final int imax = (int) ((msec * SAMPLE_RATE_HZ) / 1000);
        byte[] buf = new byte[imax];
        for (int i=0; i < imax; i++)
        {
            double angle = i / (SAMPLE_RATE_HZ / hz) * PI_X_2;
            buf[i] = (byte)(Math.sin(angle) * vol_x_127);
        }

        return buf;
    }

    /**
     * Generate and play a constant tone.
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @throws IllegalArgumentException if {@code msec} > 1000
     * @throws LineUnavailableException if the line resource can't be opened
     */
    public static void tone(int hz, int msec, double vol)
        throws IllegalArgumentException, LineUnavailableException
    {
        playPCMBytes(genTone(hz, msec, vol));
    }

    /**
     * Play a sound byte buffer, such as that generated by
     * {@link #genTone(int, int, double)} or {@link #genChime(int, int, double)}.
     * @param buf  Buffer to play; PCM mono 8-bit signed, at {@link #SAMPLE_RATE_HZ}
     * @throws LineUnavailableException if the line resource can't be opened
     */
    public static final void playPCMBytes(final byte[] buf)
        throws LineUnavailableException
    {
        SourceDataLine sdl = AudioSystem.getSourceDataLine(AFMT_PCM_8_AT_SAMPLE_RATE);
        sdl.open(AFMT_PCM_8_AT_SAMPLE_RATE);
        sdl.start();
        sdl.write(buf, 0, buf.length);
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
            Thread.sleep(60);
            chime(CHIME_A_HZ / 2, 180 + 90, .9);

        } catch (Exception e) {
            // LineUnavailableException, InterruptedException
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

}
