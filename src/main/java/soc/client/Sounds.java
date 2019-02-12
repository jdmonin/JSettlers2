/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2018 Jeremy D Monin <jeremy@nand.net>
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
 * To concatenate sequentially generated tones or chimes in a single buffer,
 * make a buffer of length {@link #bufferLen(int)} and then call
 * {@link #genChime(int, int, double, byte[], int, boolean)} and/or
 * {@link #genTone(int, int, double, byte[], int)} to fill it.
 *<P>
 * Generating tones ahead of time can help with latency, instead of
 * allocating a buffer each time a sound is played.
 *<P>
 * Some Java versions on some platforms may have trouble reliably playing
 * a sample longer than 1000ms.
 *
 * @since 1.2.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
/*package*/ class Sounds
{
    /** Sampling rate: 44100 Hz. Can also use 22050, but that adds more white noise. */
    public static final float SAMPLE_RATE_HZ = 44100f;

    /** Musical note C4, 262 Hz */
    public static final int NOTE_C4_HZ = 262;

    /** Musical note E4, 330 Hz */
    public static final int NOTE_E4_HZ = 330;

    /** Musical note A5, 880 Hz */
    public static final int NOTE_A5_HZ = 880;

    /**
     * Musical note B5, 988 Hz (987.77)
     * @since 1.2.01
     */
    public static final int NOTE_B5_HZ = 988;

    private static final double PI_X_2 = 2.0 * Math.PI;

    /**
     * Audio format for PCM-encoded little-endian signed 16-bit mono at {@link #SAMPLE_RATE_HZ}.
     * Much nicer quality than 8-bit. On OSX, Java 1.7 has lots of extra static for 8-bit playback
     * but 16-bit is fine.
     */
    private static final AudioFormat AFMT_PCM_16_AT_SAMPLE_RATE = new AudioFormat
        (SAMPLE_RATE_HZ,
         16,          // sampleSizeInBits
         1,           // channels
         true,        // signed
         false);      // not bigEndian

    /**
     * To reduce latency in {@link #playPCMBytes(byte[])}, a cached SourceDataLine to try to reopen.
     */
    private static SourceDataLine playPCM_sdl;

    /**
     * Calculate the length of a mono 16-bit PCM byte buffer,
     * at {@link #SAMPLE_RATE_HZ}, to store {@code msec} milliseconds.
     * @param msec  Duration in milliseconds
     * @return  Buffer length required to store {@code msec} milliseconds; is also the number of samples
     */
    public static final int bufferLen(final int msec)
    {
        return (2 * msec * (int) SAMPLE_RATE_HZ) / 1000;
    }

    /**
     * Generate a chime, with volume fading out to 0, into an existing buffer.
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds
     * @param vol  Volume (max is 1.0)
     * @param buf An existing little-endian mono 16-bit PCM buffer into which to generate the chime.
     *    Use {@link #bufferLen(int)} to calculate the required length.
     * @param i0  Starting position (index) to use within {@code buf}
     * @param overlay  If true, combine amplitude of the new chime with what's in the buffer.
     *     Total volume is additive. Does not clip or check for overflow.
     * @return  1 past the ending position (index) used within {@code buf};
     *     the next generate call can use this value for its {@code i0}
     * @throws IllegalArgumentException if {@code buf} isn't long enough,
     *     given {@code msec} and {@code i0}
     * @throws NullPointerException if {@code buf} is null
     * @see #genChime(int, int, double)
     */
    public static int genChime
        (int hz, int msec, double vol, final byte[] buf, final int i0, final boolean overlay)
        throws IllegalArgumentException, NullPointerException
    {
        final int imax = bufferLen(msec) / 2;  // max number of 16-bit samples
        if (buf.length < i0 + (2 * imax))
            throw new IllegalArgumentException("buf too short");

        // 2 parts if >= 40ms: attack for first 10msec (amplitude 0.8 * vol to vol),
        // then release for rest of msec (fading amplitude: vol to 0)

        int ib = i0;  // byte position
        int iSa = ib / 2;  // for angle calc: samples, not bytes
        final int amax;
        if (msec >= 40)
        {
            amax = (10 * (int) SAMPLE_RATE_HZ) / 1000;
            final double vol0 = 0.8 * vol,
                         dVol = vol - vol0;
            for (int i = 0; i < amax; ++i, ++iSa)
            {
                double angle = (iSa / (SAMPLE_RATE_HZ / hz)) * PI_X_2;
                short val = (short) (Math.sin(angle) * 32767.0 * (vol0 + ((dVol * i) / amax)));
                if (overlay)
                {
                    int vWith = (short) ( (buf[ib] & 0xFF) | (buf[ib + 1] << 8) );
                    val += vWith;  // reminder: java shorts are always signed
                }
                // little endian
                buf[ib] = (byte) (val & 0xFF);
                ++ib;
                buf[ib] = (byte) ((val >> 8) & 0xFF);
                ++ib;
            }
        } else {
            amax = 0;
        }

        final int rmax = imax - amax;
        for (int i = rmax; i > 0; --i, ++iSa)
        {
            double angle = (iSa / (SAMPLE_RATE_HZ / hz)) * PI_X_2;
            short val = (short) (Math.sin(angle) * ((32767.0 * vol * i) / rmax));
            if (overlay)
            {
                int vWith = (short) ( (buf[ib] & 0xFF) | (buf[ib + 1] << 8) );
                val += vWith;  // reminder: java shorts are always signed
            }
            buf[ib] = (byte) (val & 0xFF);
            ++ib;
            buf[ib] = (byte) ((val >> 8) & 0xFF);
            ++ib;
        }

        return ib;
    }

    /**
     * Generate a chime, with volume fading out to 0.
     * For buffer format details see {@link #genChime(int, int, double, byte[], int, boolean)}.
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @return a PCM buffer containing the generated chime, suitable for {@link #playPCMBytes(byte[])}
     * @throws IllegalArgumentException if {@code msec} > 1000
     * @see #genChime(int, int, double, byte[], int, boolean)
     */
    public static byte[] genChime(int hz, int msec, double vol)
        throws IllegalArgumentException
    {
        if (msec > 1000)
            throw new IllegalArgumentException("msec");

        byte[] buf = new byte[bufferLen(msec)];
        genChime(hz, msec, vol, buf, 0, false);
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
     * Generate a constant tone into an existing buffer.
     *<P>
     * Based on https://stackoverflow.com/questions/23096533/how-to-play-a-sound-with-a-given-sample-rate-in-java
     * from Réal Gagnon's code at http://www.rgagnon.com/javadetails/java-0499.html:
     * optimized, decoupled from 8000Hz fixed sampling rate, separated generation from playback,
     * used 16 bits, implemented generation into existing buffer.
     *
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds
     * @param vol  Volume (max is 1.0)
     * @param buf An existing little-endian mono 16-bit PCM buffer into which to generate the tone.
     *    Use {@link #bufferLen(int)} to calculate the required length.
     * @param i0  Starting position (index) to use within {@code buf}
     * @return  1 past the ending position (index) used within {@code buf};
     *     the next generate call can use this value for its {@code i0}
     * @throws IllegalArgumentException if {@code buf} isn't long enough,
     *     given {@code msec} and {@code i0}
     * @throws NullPointerException if {@code buf} is null
     * @see #genTone(int, int, double)
     */
    public static int genTone(int hz, int msec, double vol, final byte[] buf, final int i0)
        throws IllegalArgumentException, NullPointerException
    {
        final int imax = bufferLen(msec) / 2;  // max number of 16-bit samples
        if (buf.length < i0 + (2 * imax))
            throw new IllegalArgumentException("buf too short");

        final double vol_x_32767 = 32767.0 * vol;
        int ib = i0;  // byte position
        int iSa = ib / 2;  // for angle calc: samples, not bytes
        for (int i = 0; i < imax; ++i, ++iSa)
        {
            double angle = (iSa / (SAMPLE_RATE_HZ / hz)) * PI_X_2;
            short val = (short) (Math.sin(angle) * vol_x_32767);
            buf[ib] = (byte) (val & 0xFF);
            ++ib;
            buf[ib] = (byte) ((val >> 8) & 0xFF);
            ++ib;
        }

        return ib;
    }

    /**
     * Generate a constant tone into a new PCM buffer.
     * For buffer format details see {@link #genTone(int, int, double, byte[], int)}.
     *
     * @param hz  Tone in Hertz (recommended max is half of {@link #SAMPLE_RATE_HZ})
     * @param msec  Duration in milliseconds (max is 1000)
     * @param vol  Volume (max is 1.0)
     * @return  A sound byte buffer, suitable for {@link #playPCMBytes(byte[])}
     * @throws IllegalArgumentException if {@code msec} > 1000
     * @see #genTone(int, int, double, byte[], int)
     */
    public static byte[] genTone(int hz, int msec, double vol)
        throws IllegalArgumentException
    {
        if (msec > 1000)
            throw new IllegalArgumentException("msec");

        byte[] buf = new byte[bufferLen(msec)];
        genTone(hz, msec, vol, buf, 0);
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
     * @param buf  Buffer to play; PCM little-endian mono 16-bit signed, at {@link #SAMPLE_RATE_HZ}
     * @throws LineUnavailableException if the line resource can't be opened
     */
    public static final void playPCMBytes(final byte[] buf)
        throws LineUnavailableException
    {
        SourceDataLine sdl = playPCM_sdl;
        if (sdl != null)
        {
            try
            {
                sdl.open(AFMT_PCM_16_AT_SAMPLE_RATE);
            } catch (Exception e) {
                // LineUnavailableException, IllegalStateException, etc
                sdl = null;
            }
        }
        if (sdl == null)
        {
            sdl = AudioSystem.getSourceDataLine(AFMT_PCM_16_AT_SAMPLE_RATE);
            playPCM_sdl = sdl;
            sdl.open(AFMT_PCM_16_AT_SAMPLE_RATE);
        }
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
            tone(NOTE_A5_HZ, 180, .9);
            Thread.sleep(60);
            chime(NOTE_A5_HZ, 180, .9);
            Thread.sleep(60);
            chime(NOTE_A5_HZ / 2, 180 + 90, .9);
            Thread.sleep(60);

            byte[] buf = new byte[bufferLen(600)];
            genChime(NOTE_A5_HZ, 600, .5, buf, 0, false);
            genChime(NOTE_E4_HZ, 600, .5, buf, 0, true);
            playPCMBytes(buf);
            Thread.sleep(90);

            buf = new byte[bufferLen(120 + 90)];
            int i = genChime(NOTE_E4_HZ, 120, .9, buf, 0, false);
            genChime(NOTE_C4_HZ, 90, .9, buf, i, false);
            playPCMBytes(buf);

        } catch (Exception e) {
            // LineUnavailableException, InterruptedException
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

}
