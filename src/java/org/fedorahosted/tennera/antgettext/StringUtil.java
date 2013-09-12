/*
 * JBoss, the OpenSource J2EE webOS
 * 
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * Portions of this file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
 */
package org.fedorahosted.tennera.antgettext;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * String utilities and i18n pseudolocalization from the JBoss Ant-Gettext utilities.
 * <P>
 * For the JSettlers project, retrieved 2013-09-10 from
 *   <A href="https://github.com/zanata/tennera/blob/master/ant-gettext/src/main/java/org/fedorahosted/tennera/antgettext/StringUtil.java"
 *   >https://github.com/zanata/tennera/blob/master/ant-gettext/src/main/java/org/fedorahosted/tennera/antgettext/StringUtil.java</A><BR>
 *  Retrieved version was last modified 2009-05-05, LGPL license.
 * <P>
 *  Changes for JSettlers:
 * <UL>
 * <LI> 2013-09-12  jeremy  Make class and removeFileExtension public;
 *                          pseudolocalise: shorten prefix, suffix to {@code "[-- ", " --]"},
 *                              don't pseudo any text within {curly brackets}
 * </UL>
 */
public class StringUtil {

    private static final String alphabetEnglish = "abcdefghijklmnopqrstuvwxyz";
    /* decoding thanks to http://rishida.net/scripts/uniview/uniview.php
      	00E5:   å  LATIN SMALL LETTER A WITH RING ABOVE
        042C:   Ь  CYRILLIC CAPITAL LETTER SOFT SIGN
        00E7:   ç  LATIN SMALL LETTER C WITH CEDILLA
        0111:   đ  LATIN SMALL LETTER D WITH STROKE
        00E9:   é  LATIN SMALL LETTER E WITH ACUTE
        03DD:   ϝ  GREEK SMALL LETTER DIGAMMA
        0581:   ց  ARMENIAN SMALL LETTER CO
        2C68:   ⱨ  LATIN SMALL LETTER H WITH DESCENDER
        00EE:   î  LATIN SMALL LETTER I WITH CIRCUMFLEX
        FEA9:   ﺩ  ARABIC LETTER DAL ISOLATED FORM
        2C6A:   ⱪ  LATIN SMALL LETTER K WITH DESCENDER
        0140:   ŀ  LATIN SMALL LETTER L WITH MIDDLE DOT
        10DD:   ო  GEORGIAN LETTER ON
        0148:   ň  LATIN SMALL LETTER N WITH CARON
        00F8:   ø  LATIN SMALL LETTER O WITH STROKE
        00DE:   Þ  LATIN CAPITAL LETTER THORN
        1574:   ᕴ  CANADIAN SYLLABICS NUNAVIK HE
        044F:   я  CYRILLIC SMALL LETTER YA
        0161:   š  LATIN SMALL LETTER S WITH CARON
        0167:   ŧ  LATIN SMALL LETTER T WITH STROKE
        0574:   մ  ARMENIAN SMALL LETTER MEN
        2C71:   ⱱ  LATIN SMALL LETTER V WITH RIGHT HOOK
        05E9:   ש  HEBREW LETTER SHIN
        1E8B:   ẋ  LATIN SMALL LETTER X WITH DOT ABOVE
        0177:   ŷ  LATIN SMALL LETTER Y WITH CIRCUMFLEX
        017C:   ż  LATIN SMALL LETTER Z WITH DOT ABOVE
     */
    // 16 bit characters from BMP
//    private static final String alphabetMunged = "åЬçđéϝցⱨîﺩⱪŀოňøÞᕴяšŧմⱱשẋŷż";
    /*
        00E5:   å  LATIN SMALL LETTER A WITH RING ABOVE
        042C:   Ь  CYRILLIC CAPITAL LETTER SOFT SIGN
        00E7:   ç  LATIN SMALL LETTER C WITH CEDILLA
        0111:   đ  LATIN SMALL LETTER D WITH STROKE
        1D5BE:   𝖾  MATHEMATICAL SANS-SERIF SMALL E
        03DD:   ϝ  GREEK SMALL LETTER DIGAMMA
        0581:   ց  ARMENIAN SMALL LETTER CO
        2C68:   ⱨ  LATIN SMALL LETTER H WITH DESCENDER
        00EE:   î  LATIN SMALL LETTER I WITH CIRCUMFLEX
        1D693:   𝚓  MATHEMATICAL MONOSPACE SMALL J
        2C6A:   ⱪ  LATIN SMALL LETTER K WITH DESCENDER
        0140:   ŀ  LATIN SMALL LETTER L WITH MIDDLE DOT
        10DD:   ო  GEORGIAN LETTER ON
        0148:   ň  LATIN SMALL LETTER N WITH CARON
        00F8:   ø  LATIN SMALL LETTER O WITH STROKE
        00DE:   Þ  LATIN CAPITAL LETTER THORN
        1574:   ᕴ  CANADIAN SYLLABICS NUNAVIK HE
        044F:   я  CYRILLIC SMALL LETTER YA
        0161:   š  LATIN SMALL LETTER S WITH CARON
        0167:   ŧ  LATIN SMALL LETTER T WITH STROKE
        0574:   մ  ARMENIAN SMALL LETTER MEN
        2C71:   ⱱ  LATIN SMALL LETTER V WITH RIGHT HOOK
        05E9:   ש  HEBREW LETTER SHIN
        1E8B:   ẋ  LATIN SMALL LETTER X WITH DOT ABOVE
     */
    // BMP and SMP chars (mathematical alphanumeric)
//    private static final String alphabetMunged = "åЬçđ𝖾ϝցⱨî𝚓ⱪŀოňøÞᕴяšŧմⱱשẋŷż";
    /*
        00E5:   å  LATIN SMALL LETTER A WITH RING ABOVE
        042C:   Ь  CYRILLIC CAPITAL LETTER SOFT SIGN
        00E7:   ç  LATIN SMALL LETTER C WITH CEDILLA
        0111:   đ  LATIN SMALL LETTER D WITH STROKE
        00E9:   é  LATIN SMALL LETTER E WITH ACUTE
        03DD:   ϝ  GREEK SMALL LETTER DIGAMMA
        0581:   ց  ARMENIAN SMALL LETTER CO
        2C68:   ⱨ  LATIN SMALL LETTER H WITH DESCENDER
        00EE:   î  LATIN SMALL LETTER I WITH CIRCUMFLEX
        029D:   ʝ  LATIN SMALL LETTER J WITH CROSSED-TAIL
        2C6A:   ⱪ  LATIN SMALL LETTER K WITH DESCENDER
        0140:   ŀ  LATIN SMALL LETTER L WITH MIDDLE DOT
        10DD:   ო  GEORGIAN LETTER ON
        0148:   ň  LATIN SMALL LETTER N WITH CARON
        00F8:   ø  LATIN SMALL LETTER O WITH STROKE
        FF50:   ｐ  FULLWIDTH LATIN SMALL LETTER P
        1574:   ᕴ  CANADIAN SYLLABICS NUNAVIK HE
        044F:   я  CYRILLIC SMALL LETTER YA
        0161:   š  LATIN SMALL LETTER S WITH CARON
        0167:   ŧ  LATIN SMALL LETTER T WITH STROKE
        0574:   մ  ARMENIAN SMALL LETTER MEN
        2C71:   ⱱ  LATIN SMALL LETTER V WITH RIGHT HOOK
        1D355:   𝍕  TETRAGRAM FOR LABOURING
        1E8B:   ẋ  LATIN SMALL LETTER X WITH DOT ABOVE
        0177:   ŷ  LATIN SMALL LETTER Y WITH CIRCUMFLEX
        017C:   ż  LATIN SMALL LETTER Z WITH DOT ABOVE
     */
    // BMP and supplementary characters (all left-to-right)
    private static final String alphabetMunged = "åЬçđéϝցⱨîʝⱪŀოňøｐᕴяšŧմⱱ𝍕ẋŷż"; //$NON-NLS-1$
    // http://whatsmyip.org/upsidedowntext/
//    private static final String alphabetFlipped = "zʎxʍʌnʇsɹbdouɯןʞɾıɥƃɟǝpɔqɐ";
    private static final int[] codepointsMunged;
    
    static 
    {
        codepointsMunged = new int[alphabetMunged.codePointCount(0, alphabetMunged.length())];
        for (int i=0; i < alphabetEnglish.length(); i++)
        {
 	   char ch = alphabetEnglish.charAt(i);
 	   codepointsMunged[ch-'a'] = 
 	       alphabetMunged.codePointAt(alphabetMunged.offsetByCodePoints(0, i));
        }
    }

    public static String pseudolocalise(String text) 
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[-- "); //$NON-NLS-1$
        boolean withinSingleQuote = false;
        int withinCurlyBracket = 0;  // don't pseudo the text like {0,number} within curly brackets
        for (int i = 0; i < text.length(); i++) 
        {
 	   char ch = text.charAt(i);
	   if (ch == '\'')
	   {
	       withinSingleQuote = ! withinSingleQuote;
	       sb.append(ch);
	   } else if ((ch == '{') && ! withinSingleQuote) {
	       ++withinCurlyBracket;
	       sb.append(ch);
	   } else if ((ch == '}') && ! withinSingleQuote) {
	       --withinCurlyBracket;
	       sb.append(ch);
	   } else if (ch < 'a' || ch > 'z' || withinCurlyBracket > 0)
 	   {
 	       sb.append(ch);
 	   } 
 	   else 
 	   {
 	       int mungedCodePoint = codepointsMunged[ch - 'a'];
 	       sb.appendCodePoint(mungedCodePoint);
 	   }
        }
        sb.append(" --]"); //$NON-NLS-1$
        return sb.toString();
    }

    public static void chomp(StringBuilder sb) 
    {
    	chopIfMatch(sb, '\n');
    	chopIfMatch(sb, '\r');
    }
    
    private static void chopIfMatch(StringBuilder sb, char ch)
    {
    	if (sb.length() != 0 && sb.charAt(sb.length()-1) == ch)
    		sb.setLength(sb.length()-1);
    }
    
    public static String chomp(String input) {
        StringBuilder sb = new StringBuilder(input);
        chomp(sb);
        return sb.toString();
    }
    
    public static String removeFileExtension(String filename, String extension)
    {
        if (!filename.endsWith(extension))
        	throw new IllegalArgumentException(
        		"Filename '"+filename+"' should have extension '"+extension+"'");
        String basename = filename.substring(0, 
        			filename.length()-extension.length());
        return basename;
    }
    
    static boolean equals(String a, String b)
    {
    	if (a != null)
    		return a.equals(b);
    	else
    		return b == null;
    }

    public static void main(String[] args) throws Exception 
    {
	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	String line;
	while ((line = reader.readLine()) != null)
	    System.out.println(pseudolocalise(line));
    }

}
