package soc.util;

// Version.java - mchenryc@acm.org Chadwick A. McHenry
// Portions copyright (C) 2008,2010,2011,2013-2015 Jeremy D Monin <jeremy@nand.net>

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;


/**
 * Package level version file used to keep packaging and codebase in sync. The
 * file Version.java.in is filtered to create Version.java when Callisto is
 * built using <a href="http://ant.apache.org">ant</a>.  If you are not using
 * ant to build Callisto you can do this manually by copying Version.java.in
 * to Version.java, replacing "@ VERSION @" with the "version" property value
 * in the file build.xml.
 *
 * @author <a href="mailto:mchenryc@acm.org">Chadwick A. McHenry</a>
 */
public class Version {

  public static String VERSION   = "project.version";
  public static String VERSNUM   = "project.versionnum";

  /**
   * For minimum version required; warn in NewGameOptionsFrame only
   * if game options require newer than this version number.
   * Property name <tt>versionnumMaxNoWarn</tt> in <tt>build.xml</tt>.
   * @since 1.1.13
   */
  public static final String VERSNUM_NOWARN_MAXIMUM = "project.versionnumMaxNoWarn";

  public static String COPYRIGHT = "project.copyright";
  public static String BUILDNUM  = "project.buildnum";
  
  public static String JRE_MIN_VERSION = "project.jre.min.version";
  public static String JRE_MIN_MAJOR   = "project.jre.min.major";
  public static String JRE_MIN_MINOR   = "project.jre.min.minor";
  public static String JRE_MIN_EDIT    = "project.jre.min.edit";
  
  /** Current version info */
  private static Properties versionInfo = null;

  /** ints for comparisons, concatentated and stored as JRE_MIN_VERSION */
  private static int jreMinMajor = 1;
  private static int jreMinMinor = 4;
  private static int jreMinEdit = 0;

  static {
    versionInfo = new Properties();

    // defaults in case build failed to produce version.info
    versionInfo.put(VERSION, "-error-");
    versionInfo.put(VERSNUM, "-error-");
    versionInfo.put(VERSNUM_NOWARN_MAXIMUM, "-1");
    versionInfo.put(COPYRIGHT, "-error-");
    versionInfo.put(BUILDNUM, "-unknown-");
    // JRE_MIN_VERSION default is built later
    try {
      String resource = "/resources/version.info";
      InputStream in = Version.class.getResourceAsStream (resource);
      versionInfo.load (in);
      in.close ();

    } catch (Exception io) {
      System.err.println ("Unable to load version information.");
      io.printStackTrace ();
    }

    // initialize concatenated value
    minJREVersion();
  }

  /**
   * Given a version number integer such as 1109, return a human-readable string such as "1.1.09".
   * @param  versionNumber  a version number, as returned by {@link #versionNumber()},
   *           which should be 0 or an integer 1000 or higher
   * @return  version as a human-readable string such as "1.1.09"
   * @since 1.1.09
   * @see #version()
   */
  public static String version(final int versionNumber)
  {
      if (versionNumber < 1000)
          return Integer.toString(versionNumber);
      else if (versionNumber < 1100)
          return "1.0." + (versionNumber - 1000);  // 1006 -> 1.0.6
      else
      {
          StringBuffer sb = new StringBuffer(Integer.toString(versionNumber));
          final int L = sb.length();
          sb.insert(L-2, '.');
          sb.insert(L-3, '.');
          return sb.toString();  // 1.1.09 or 12.3.08
      }
  }

  /** Return the current version string. @see #versionNumber() */
  public static String version() {
    return versionInfo.getProperty(VERSION);
  }

  /**
   * Return the current version number.
   * @return Version integer; 1100 is version 1.1.00.
   *         If the version number cannot be read, 0 is returned.
   * @see #version()
   * @see #version(int)
   */
  public static int versionNumber() {
    int vnum;
    try {
        vnum = Integer.parseInt(versionInfo.getProperty(VERSNUM));
    }
    catch (Throwable e) {
        vnum = 0;
    }
    return vnum;
  }

  /**
   * For new game creation, return the minimum recent version number to
   * not warn during new game creation;
   * should be a version released more than a year ago.
   * If game options require a newer version, warn about that
   * in the <tt>NewGameOptionsFrame</tt> options dialog.
   *<P>
   * To view or set this version, see {@link #VERSNUM_NOWARN_MAXIMUM}.
   *
   * @return Version integer; 1108 is version 1.1.08.
   *         If the version number cannot be read, -1 is returned.
   * @see #versionNumber()
   * @since 1.1.13
   */
  public static int versionNumberMaximumNoWarn() {
    int vnum;
    try {
        vnum = Integer.parseInt(versionInfo.getProperty(VERSNUM_NOWARN_MAXIMUM));
    }
    catch (Throwable e) {
        vnum = -1;
    }
    return vnum;
  }

  /** Return the copyright string. */
  public static String copyright() {
    return versionInfo.getProperty(COPYRIGHT);
  }

  /** Return the build-number string. */
  public static String buildnum() {
    return versionInfo.getProperty(BUILDNUM);
  }

  /** Return the minimum required jre. */
  public static String minJREVersion() {
    String jreMinVersion = versionInfo.getProperty(JRE_MIN_VERSION);
    if (jreMinVersion == null) {
      try {
        String major = versionInfo.getProperty(JRE_MIN_MAJOR, ""+jreMinMajor);
        String minor = versionInfo.getProperty(JRE_MIN_MINOR, ""+jreMinMinor);
        String edit  = versionInfo.getProperty(JRE_MIN_EDIT,  ""+jreMinEdit);
        jreMinMajor = Integer.parseInt(major);
        jreMinMinor = Integer.parseInt(minor);
        jreMinEdit  = Integer.parseInt(edit);
        
      } catch(Exception x) { // NPE or NumberFormat uses default values
        System.err.println("Error retrieving Version info: ");
        x.printStackTrace();
      }

      jreMinVersion = jreMinMajor+"."+jreMinMinor+"."+jreMinEdit;
      versionInfo.put(JRE_MIN_VERSION, jreMinVersion);
    }
    return jreMinVersion;
  }

  /** Check for sufficient version of the JRE. */
  static boolean isJREValid () {
    String v = System.getProperty("java.vm.version");
    int major = Integer.parseInt (v.substring (0,1));
    int minor = Integer.parseInt (v.substring (2,3));
    int edit = Integer.parseInt (v.substring (4,5));;
    // String build = v.substring (6);

    if (versionInfo.getProperty(JRE_MIN_VERSION) == null)
      minJREVersion();  
    
    return (major >= jreMinMajor || minor >= jreMinMinor || edit >= jreMinEdit);
  }

  /**
   * Print the JSettlers version banner and attribution text. Formerly inside <tt>SOCPlayerClient, SOCServer</tt>.
   * @param out  {@link System#out} or {@link System#err}
   * @param progname  "Java Settlers Server " or "Java Settlers Client ", including trailing space
   * @since 1.1.18
   */
  public static void printVersionText(PrintStream out, final String progname)
  {
      out.println(progname + version() +
          ", build " + buildnum() + ", (C) " + copyright());
      out.println("Network layer based on code by Cristian Bogdan; Practice Net by Jeremy Monin.");
  }

}
