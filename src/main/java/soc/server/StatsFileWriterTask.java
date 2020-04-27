/**
 * JSettlers stats summary file writer.
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
package soc.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Writes a daily {@code *STATS*} summary, creating or appending to the file
 * named in {@link SOCServer#PROP_JSETTLERS_STATS_FILE_NAME}.
 *<P>
 * Constructor checks directory/file writability, warns if any issues, then
 * schedules the first summary 1 hour from now ({@link #INITIAL_RUN_DELAY_MINUTES}).
 * After that, daily summaries are appended every 24 hours at 00:01 local time
 * (from server's timezone via {@link Calendar#getInstance()}).
 *<P>
 * The actual {@link TimerTask}s here are {@link FirstRun} and {@link DailyRun}.
 *
 * @since 2.3.00
 */
class StatsFileWriterTask
{
    /** Wait 1 hour (60 minutes) first, so that a quick test-run won't write to the file. */
    public static final int INITIAL_RUN_DELAY_MINUTES = 60;

    /** Date format for timestamp: {@code "2020-04-02 00:01 EDT"} */
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm z");

    private final SOCServerMessageHandler ssmh;

    /** Timer, for scheduling daily run after initial run */
    private final Timer timer;

    /** File to create or append to; assumed to be absolute */
    private final File statsFile;

    /** {@link #statsFile}'s original path from property, for warning prints */
    private final String statsFilename;

    /**
     * Creates a new {@link StatsFileWriterTask} and schedule its delayed initial run
     * in {@link #INITIAL_RUN_DELAY_MINUTES}. Calls {@link #checkIfWritable()} now
     * to print a warning message if not writable.
     *
     * @param ssmh  Server message handler, for {@link SOCServerMessageHandler#getSettingsFormatted(soc.util.SOCStringManager)}
     * @param statsFile  File to create or append to; assumes is from caller using {@link File#getAbsoluteFile()}
     * @param filename  for printing warning
     * @param timer  Timer on which to schedule
     */
    public StatsFileWriterTask
        (SOCServerMessageHandler ssmh, File statsFile, String filename, Timer timer)
    {
        this.ssmh = ssmh;
        this.statsFile = statsFile;
        statsFilename = filename;
        this.timer = timer;

        checkIfWritable();

        timer.schedule(new FirstRun(), INITIAL_RUN_DELAY_MINUTES * 60 * 1000);
    }

    /**
     * Checks if statsFile is writable if exists, otherwise check if its directory allows creating new files.
     * If not, prints warning message to {@link System#err}.
     * @return True if file is writable or can be created;
     *     false if not (a warning message was printed)
     */
    public boolean checkIfWritable()
    {
        boolean allOK = false;

        try
        {
            final File statsDir = statsFile.getParentFile();
            final String statsDirName = statsDir.getPath();
            if (! statsDir.exists())
            {
                System.err.println("Warning: Directory not found for stats.file: " + statsFilename);
            } else if (! statsDir.isDirectory()) {
                System.err.println
                    ("Warning: stats.file parent exists but isn't a directory: " + statsDirName);
            } else {
                if (statsFile.exists())
                {
                    if (statsFile.isDirectory())
                    {
                        System.err.println
                            ("Warning: stats.file exists but is a directory: " + statsFilename);
                        return false;  // <---- Early return: file is dir ----
                    } else if (statsFile.canWrite()) {
                        allOK = true;
                    } else {
                        System.err.println
                            ("Warning: stats.file exists but is read-only: " + statsFilename);
                    }
                }

                boolean canWriteDir = false;
                try
                {
                    canWriteDir = Files.isWritable(statsDir.toPath());
                    if (! canWriteDir)
                        System.err.println("Warning: Directory for stats.file is read-only: " + statsDirName);
                } catch (SecurityException e) {
                    System.err.println("Warning: Can't access stats.file's directory " + statsDirName + ": " + e);
                }

                if (! allOK)
                    allOK = canWriteDir;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            System.err.println("Warning: Can't access stats.file " + statsFilename + ": " + e);
        }

        return allOK;
    }

    /**
     * Runs a report now and append it to {@link #statsFile},
     * if {@link #checkIfWritable()} returns true.
     * Prints {@code "Stats file: Updated <full path>"} to {@link System#err}.
     * @return calendar set to current local time at start of report,
     *     for reuse if needed to schedule daily runs.
     */
    private Calendar runReportIfWritable()
    {
        final Calendar localCal = Calendar.getInstance();

        if (checkIfWritable())
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Server stats at ");
            sb.append(SDF.format(localCal.getTime()));
            sb.append("\n\n");
            Iterator<String> it = ssmh.getSettingsFormatted(null).iterator();
            while (it.hasNext())
                sb.append(it.next() + ": " + it.next() + "\n");
            sb.append("\n\n");

            try(OutputStreamWriter writer = new OutputStreamWriter
                (new FileOutputStream(statsFile, true), "UTF-8"))
            {
                writer.append(sb);  // to append all at once if possible
                writer.flush();
                System.err.println("\nStats file: Updated " + statsFile.getPath() + "\n");
            } catch (IOException | SecurityException e) {
                System.err.println("\n* Can't write to stats.file " + statsFilename + ": " + e + "\n");
            }
        }

        return localCal;
    }

    /**
     * Writes stats report or warn if can't do so,
     * by calling {@link StatsFileWriterTask#runReportIfWritable()}.
     * Schedules future daily runs ({@link DailyRun}).
     * See {@link StatsFileWriterTask} class javadoc for details.
     */
    private class FirstRun extends TimerTask
    {
        public void run()
        {
            final Calendar localCal = runReportIfWritable();

            // Schedule daily just after midnight: 00:01 local time
            final long nowUTC = localCal.getTimeInMillis();
            localCal.add(Calendar.DAY_OF_MONTH, 1);  // tomorrow
            localCal.set(Calendar.HOUR_OF_DAY, 0);
            localCal.set(Calendar.MINUTE, 1);
            localCal.set(Calendar.SECOND, 30);  // remain 00:01 in case of slight drift, not 00:00 or 00:02
            timer.scheduleAtFixedRate
                (new DailyRun(), localCal.getTimeInMillis() - nowUTC, 24 * 60 * 60 * 1000);
        }
    }

    /**
     * This task is scheduled once per day in {@link StatsFileWriterTask#timer}.
     * Writes stats report or warn if can't do so,
     * by calling {@link StatsFileWriterTask#runReportIfWritable()}.
     */
    private class DailyRun extends TimerTask
    {
        public void run()
        {
            runReportIfWritable();
        }
    }

}
