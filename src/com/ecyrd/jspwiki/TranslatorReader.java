/* 
    JSPWiki - a JSP-based WikiWiki clone.

    Copyright (C) 2001 Janne Jalkanen (Janne.Jalkanen@iki.fi)

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.ecyrd.jspwiki;

import java.io.*;
import java.util.*;
import java.text.*;

import org.apache.log4j.Category;
import org.apache.oro.text.*;
import org.apache.oro.text.regex.*;

/**
 *  Handles conversion from Wiki format into fully featured HTML.
 *  This is where all the magic happens.  It is CRITICAL that this
 *  class is tested, or all Wikis might die horribly.
 */
// FIXME: Class still has problems with {{{: all conversion on that line where the {{{
//        appears is done, but after that, conversion is not done.  The only real solution
//        is to move away from a line-based system into a pure stream-based system.
public class TranslatorReader extends Reader
{
    public  static final int              READ  = 0;
    public  static final int              EDIT  = 1;    
    private static final int              EMPTY = 2;  // Empty message
    private static final int              LOCAL = 3;
    private static final int              LOCALREF = 4;
    private static final int              IMAGE = 5;
    private static final int              EXTERNAL = 6;
    private static final int              INTERWIKI = 7;

    private BufferedReader m_in;

    private StringReader   m_data = new StringReader("");

    private static Category log = Category.getInstance( TranslatorReader.class );

    private boolean        m_iscode       = false;
    private boolean        m_isbold       = false;
    private boolean        m_isitalic     = false;
    private int            m_listlevel    = 0;
    private int            m_numlistlevel = 0;

    private WikiEngine     m_engine;

    /** Keeps image regexp Patterns */
    private ArrayList      m_inlineImagePatterns;

    private PatternMatcher m_inlineMatcher = new Perl5Matcher();

    /**
     *  This property defines the inline image pattern.  It's current value
     *  is jspwiki.translatorReader.inlinePattern
     */
    public static final String     PROP_INLINEIMAGEPTRN  = "jspwiki.translatorReader.inlinePattern";

    /**
     *  The default inlining pattern.  Currently "*.png"
     */
    public static final String     DEFAULT_INLINEPATTERN = "*.png";

    /**
     *  @param engine The WikiEngine this reader is attached to.  Is
     * used to figure out of a page exits.
     */
    public TranslatorReader( WikiEngine engine, Reader in )
    {
        PatternCompiler compiler         = new GlobCompiler();
        ArrayList       compiledpatterns = new ArrayList();

        m_in     = new BufferedReader( in );
        m_engine = engine;

        Collection ptrns = getImagePatterns( engine );

        //
        //  Make them into Regexp Patterns.  Unknown patterns
        //  are ignored.
        //
        for( Iterator i = ptrns.iterator(); i.hasNext(); )
        {
            try
            {       
                compiledpatterns.add( compiler.compile( (String)i.next() ) );
            }
            catch( MalformedPatternException e )
            {
                log.error("Malformed pattern in properties: ", e );
            }
        }

        m_inlineImagePatterns = compiledpatterns;
    }

    /**
     *  Figure out which image suffixes should be inlined.
     *  @return Collection of Strings with patterns.
     */

    protected static Collection getImagePatterns( WikiEngine engine )
    {
        Properties props    = engine.getWikiProperties();
        ArrayList  ptrnlist = new ArrayList();

        for( Enumeration e = props.propertyNames(); e.hasMoreElements(); )
        {
            String name = (String) e.nextElement();

            if( name.startsWith( PROP_INLINEIMAGEPTRN ) )
            {
                String ptrn = props.getProperty( name );

                ptrnlist.add( ptrn );
            }
        }

        if( ptrnlist.size() == 0 )
        {
            ptrnlist.add( DEFAULT_INLINEPATTERN );
        }

        return ptrnlist;
    }

    private boolean linkExists( String link )
    {
        return m_engine.pageExists( link );
    }

    /**
     *  Write a HTMLized link depending on its type.
     */
    private String makeLink( int type, String link, String text )
    {
        String result;

        if( text == null ) text = link;

        // Make sure we make a link name that can be accepted
        // as a valid URL.

        String encodedlink = m_engine.encodeName( link );

        if( encodedlink.length() == 0 )
        {
            type = EMPTY;
        }

        switch(type)
        {
          case READ:
            result = "<A CLASS=\"wikipage\" HREF=\"Wiki.jsp?page="+encodedlink+"\">"+text+"</A>";
            break;

          case EDIT:
            result = "<U>"+text+"</U><A HREF=\"Edit.jsp?page="+encodedlink+"\">?</A>";
            break;

          case EMPTY:
            result = "<U>"+text+"</U>";
            break;

          case LOCALREF:
            result = "<A CLASS=\"footnoteref\" HREF=\"#ref"+
                     link+"\">[["+text+"]</A>";
            break;

          case LOCAL:
            result = "<A CLASS=\"footnote\" NAME=\"ref"+
                     link.substring(1)+"\">[["+text+"]</A>";
            break;

          //
          //  With the image, external and interwiki types we need to
          //  make sure nobody can put in Javascript or something else
          //  annoying into the links themselves.  We do this by preventing
          //  a haxor from stopping the link name short with quotes in 
          //  fillBuffer().
          //
          case IMAGE:
            result = "<IMG CLASS=\"inline\" SRC=\""+link+"\" ALT=\""+text+"\">";
            break;

          case EXTERNAL:
            result = "<A CLASS=\"external\" HREF=\""+link+"\">"+text+"</A>";
            break;

          case INTERWIKI:
            result = "<A CLASS=\"interwiki\" HREF=\""+link+"\">"+text+"</A>";
            break;

          default:
            result = "";
            break;
        }

        return result;
    }

    /**
     *  @param orig Original string.  Null is safe.
     */

    public static String replaceString( String orig, String src, String dest )
    {
        if( orig == null ) return null;

        StringBuffer res = new StringBuffer();
        int start, end = 0, last = 0;

        while( (start = orig.indexOf(src,end)) != -1 )
        {
            res.append( orig.substring( last, start ) );
            res.append( dest );
            end = start+src.length();
            last = start+1;
        }

        res.append( orig.substring( end ) );

        return res.toString();
    }

    /**
     *  @param orig Original string.  Null is safe.
     */
    public static String replaceString( String orig, int start, int end, String text )
    {
        if( orig == null ) return null;

        StringBuffer buf = new StringBuffer(orig);

        buf.replace( start, end, text );

        return buf.toString();
    }

    /**
     *  [ This is a link ] -> ThisIsALink
     */
    private String cleanLink( String link )
    {
        StringBuffer clean = new StringBuffer();

        //
        //  Compress away all whitespace and capitalize
        //  all words in between.
        //

        StringTokenizer st = new StringTokenizer( link, " -" );

        while( st.hasMoreTokens() )
        {
            StringBuffer component = new StringBuffer(st.nextToken());

            component.setCharAt(0, Character.toUpperCase( component.charAt(0) ) );

            clean.append( component );
        }

        //
        //  Remove non-alphanumeric characters that should not
        //  be put inside WikiNames.  Note that all valid
        //  Unicode letters are considered okay for WikiNames.
        //  It is the problem of the WikiPageProvider to take
        //  care of actually storing that information.
        //

        for( int i = 0; i < clean.length(); i++ )
        {
            if( !(Character.isLetterOrDigit(clean.charAt(i)) ||
                  clean.charAt(i) == '_' ||
                  clean.charAt(i) == '.') )
            {
                clean.deleteCharAt(i);
                --i; // We just shortened this buffer.
            }
        }

        return clean.toString();
    }

    /**
     *  Figures out if a link is an off-site link.
     */
    private boolean isExternalLink( String link )
    {
        return link.startsWith("http:") || link.startsWith("ftp:") ||
               link.startsWith("https:") || link.startsWith("mailto:") ||
               link.startsWith("news:");
    }

    /**
     *  Matches the given link to the list of image name patterns
     *  to determine whether it should be treated as an inline image
     *  or not.
     */
    private boolean isImageLink( String link )
    {
        for( Iterator i = m_inlineImagePatterns.iterator(); i.hasNext(); )
        {
            if( m_inlineMatcher.matches( link, (Pattern) i.next() ) )
                return true;
        }

        return false;
    }

    // FIXME: Non-optimal.
    private boolean isNumber( String s )
    {
        try
        {
            int i = Integer.parseInt( s );
        }
        catch( Exception e )
        {
            return false;
        }
        return true;
    }

    private String setHyperLinks( String line )
    {
        int start, end = 0;
        
        while( ( start = line.indexOf('[', end) ) != -1 )
        {
            // Words starting with multiple [[s are not hyperlinks.
            if( line.charAt( start+1 ) == '[' )
            {
                for( end = start; end < line.length() && line.charAt(end) == '['; end++ );
                line = replaceString( line, start, start+1, "" );
                continue;
            }

            end = line.indexOf( ']', start );

            if( end != -1 )
            {
                // Everything between these two is a link

                String link = line.substring( start+1, end );
                String reallink;
                int cutpoint;

                if( (cutpoint = link.indexOf('|')) != -1 )
                {                    
                    reallink = link.substring( cutpoint+1 ).trim();
                    link = link.substring( 0, cutpoint );
                }
                else
                {
                    reallink = link.trim();
                }

                int interwikipoint = -1;

                if( isExternalLink( reallink ) )
                {
                    // It's an external link, out of this Wiki
                    if( isImageLink( reallink ) )
                    {
                        // Image links are 
                        line = replaceString( line, start, end+1,
                                              makeLink( IMAGE, reallink, link ) );
                    }
                    else
                    {
                        line = replaceString( line, start, end+1, 
                                              makeLink( EXTERNAL, reallink, link ) );
                    }
                }
                else if( (interwikipoint = reallink.indexOf(":")) != -1 )
                {
                    // It's an interwiki link

                    String extWiki = reallink.substring( 0, interwikipoint );
                    String wikiPage = cleanLink(reallink.substring( interwikipoint+1 ));

                    String urlReference = m_engine.getInterWikiURL( extWiki );

                    if( urlReference != null )
                    {
                        urlReference = replaceString( urlReference, "%s", wikiPage );
                        line = replaceString( line, start, end+1,
                                              makeLink( INTERWIKI, urlReference, link ) );
                    }
                    else
                    {
                        line = replaceString( line, start, end+1, 
                                              link+" <FONT COLOR=\"#FF0000\">(No InterWiki reference defined in properties for Wiki called '"+extWiki+"'!)</FONT>");
                    }
                }
                else if( reallink.startsWith("#") )
                {
                    // It defines a local footnote
                    line = replaceString( line, start, end+1, 
                                          makeLink( LOCAL, reallink, link ) );
                }
                else if( isNumber( reallink ) )
                {
                    // It defines a reference to a local footnote
                    line = replaceString( line, start, end+1, 
                                          makeLink( LOCALREF, reallink, link ) );
                }
                else
                {
                    // It's an internal Wiki link
                    reallink = cleanLink( reallink );

                    if( linkExists( reallink ) )
                    {
                        line = replaceString( line, start, end+1, 
                                              makeLink( READ, reallink, link ) );
                    }
                    else
                    {
                        line = replaceString( line, start, end+1, makeLink( EDIT, reallink, link ) );
                    }
                }
            }
            else
            {
                log.error("Unterminated link");
                break;
            }
        }

        return line;
    }

    /**
     *  Checks if this line is a heading line.
     */
    private String setHeadings( String line )
    {
        if( line.startsWith("!!!") )
        {
            line = replaceString( line, 0, 3, "<H2>" ) + "</H2>";
        }
        else if( line.startsWith("!!") )
        {
            line = replaceString( line, 0, 2, "<H3>" ) + "</H3>";
        }
        else if( line.startsWith("!") )
        {
            line = replaceString( line, 0, 1, "<H4>" ) + "</H4>";
        }
        
        return line;
    }

    /**
     *  Translates horizontal rulers.
     */
    private String setHR( String line )
    {
        StringBuffer buf = new StringBuffer();
        int start = line.indexOf("----");

        if( start != -1 )
        {
            int i;
            buf.append( line.substring( 0, start ) );
            for( i = start; i<line.length() && line.charAt(i) == '-'; i++ )
            {
            }
            buf.append("<HR>");
            buf.append( line.substring( i ) );

            return buf.toString();
        }

        return line;
    }

    private String closeAll()
    {
        StringBuffer buf = new StringBuffer();

        if( m_isbold )
        {
            buf.append("</B>");
            m_isbold = false;
        }

        if( m_isitalic )
        {
            buf.append("</I>");
            m_isitalic = false;
        }

        for( ; m_listlevel > 0; m_listlevel-- )
        {
            buf.append( "</UL>\n" );
        }

        for( ; m_numlistlevel > 0; m_numlistlevel-- )
        {
            buf.append( "</OL>\n" );
        }

        if( m_iscode ) 
        {
            buf.append("</PRE>\n");
            m_iscode = false;
        }

        return buf.toString();
    }

    /**
     *  Sets bold text.
     */
    private String setBold( String line )
    {
        StringBuffer buf = new StringBuffer();

        for( int i = 0; i < line.length(); i++ )
        {
            if( line.charAt(i) == '_' && i < line.length()-1 )
            {
                if( line.charAt(i+1) == '_' )
                {
                    buf.append( m_isbold ? "</B>" : "<B>" );
                    m_isbold = !m_isbold;
                    i++;
                }
                else buf.append( "_" );
            }
            else buf.append( line.charAt(i) );
        }

        return buf.toString();
    }

    /**
     *  Counts how many consecutive characters of a certain type exists on the line.
     *  @param line String of chars to check.
     *  @param startPos Position to start reading from.
     *  @param char Character to check for.
     */
    private int countChar( String line, int startPos, char c )
    {
        int count;

        for( count = 0; (startPos+count < line.length()) && (line.charAt(count+startPos) == c); count++ );

        return count;
    }

    /**
     *  Returns a new String that has char c n times.
     */
    private String repeatChar( char c, int n )
    {
        StringBuffer sb = new StringBuffer();
        for( int i = 0; i < n; i++ ) sb.append(c);

        return sb.toString();
    }

    /**
     *  {{text}} = <TT>text</TT>
     */
    private String setTT( String line )
    {
        StringBuffer buf = new StringBuffer();
        boolean      ison = false;

        for( int i = 0; i < line.length(); i++ )
        {
            if( line.charAt(i) == '{' && !ison )
            {
                int count = countChar( line, i, '{' );

                if( count == 2 )
                {
                    buf.append( "<TT>" );
                    ison = true;
                }
                else 
                {
                    buf.append( repeatChar( '{', count ) );
                }
                i += count-1;
            }
            else if( line.charAt(i) == '}' && ison )
            {
                int count = countChar( line, i, '}' );

                if( count == 2 )
                {
                    buf.append( "</TT>" );
                    ison = false;
                }
                else 
                {
                    buf.append( repeatChar( '}', count ) );
                }
                i += count-1;
            }
            else
            { 
                buf.append( line.charAt(i) );
            }
        }

        // Make sure we don't forget it open.
        if( ison )
        {
            buf.append("</TT>");
        }

        return buf.toString();
    }

    private String setItalic( String line )
    {
        StringBuffer buf = new StringBuffer();

        for( int i = 0; i < line.length(); i++ )
        {
            if( line.charAt(i) == '\'' && i < line.length()-1 )
            {
                if( line.charAt(i+1) == '\'' )
                {
                    buf.append( m_isitalic ? "</I>" : "<I>" );
                    m_isitalic = !m_isitalic;
                    i++;
                }
                else buf.append( "'" );
            }
            else buf.append( line.charAt(i) );
        }

        return buf.toString();
    }

    private void fillBuffer()
        throws IOException
    {
        int pre;

        StringBuffer buf = new StringBuffer();

        String line = m_in.readLine();

        if( line == null ) 
        {
            m_data = new StringReader("");
            return;
        }

        String trimmed = line.trim();

        //
        //  Replace the most obvious items that could possibly
        //  break the resulting HTML code.
        //
        line = replaceString( line, "<", "&lt;" );
        line = replaceString( line, ">", "&gt;" );
        line = replaceString( line, "\"", "&quot;" );

        if( !m_iscode )
        {

            // Is this an empty line?
            if( trimmed.length() == 0 )
            {
                buf.append( "<P>" );
            }

            //
            // Make a bulleted list
            //
            if( line.startsWith("*") )
            {
                int numBullets = countChar( line, 0, '*' );
                
                if( numBullets > m_listlevel )
                {
                    for( ; m_listlevel < numBullets; m_listlevel++ )
                        buf.append("<UL>\n");
                }
                else if( numBullets < m_listlevel )
                {
                    for( ; m_listlevel > numBullets; m_listlevel-- )
                        buf.append("</UL>\n");
                }
                
                buf.append("<LI>");
                line = line.substring( numBullets );
            }
            else if( line.startsWith(" ") && m_listlevel > 0 && trimmed.length() != 0 )
            {
                // This is a continuation of a previous line.
            }
            else
            {
                // Close all lists down.
                for( ; m_listlevel > 0; m_listlevel-- )
                {
                    buf.append("</UL>\n");
                }
            }

            //
            //  Ordered list
            //
            if( line.startsWith("#") )
            {
                int numBullets = countChar( line, 0, '#' );
                
                if( numBullets > m_numlistlevel )
                {
                    for( ; m_numlistlevel < numBullets; m_numlistlevel++ )
                        buf.append("<OL>\n");
                }
                else if( numBullets < m_numlistlevel )
                {
                    for( ; m_numlistlevel > numBullets; m_numlistlevel -- )
                        buf.append("</OL>\n");
                }
                
                buf.append("<LI>");
                line = line.substring( numBullets );
            }
            else if( line.startsWith(" ") && m_numlistlevel > 0 && trimmed.length() != 0 )
            {
                // This is a continuation of a previous line.
            }
            else
            {
                // Close all lists down.
                for( ; m_numlistlevel > 0; m_numlistlevel-- )
                {
                    buf.append("</OL>\n");
                }
            }

            // Do the standard settings

            line = setHyperLinks( line );
            line = setHeadings( line );
            line = setHR( line );
            line = setBold( line );
            line = setItalic( line );

            line = setTT( line );
            line = replaceString( line, "\\\\", "<BR>" );

            if( (pre = line.indexOf("{{{")) != -1 )
            {
                line = replaceString( line, pre, pre+3, "<PRE>" );
                m_iscode = true;
            }

        }
            
        if( (pre = line.indexOf("}}}")) != -1 )
        {
            line = replaceString( line, pre, pre+3, "</PRE>" );
            m_iscode = false;
        }

        buf.append( line +"\n");
        
        m_data = new StringReader( buf.toString() );
    }

    public int read()
        throws IOException
    {
        int val = m_data.read();

        if( val == -1 )
        {
            fillBuffer();
            val = m_data.read();

            if( val == -1 )
            {
                m_data = new StringReader( closeAll() );

                val = m_data.read();
            }
        }

        return val;
    }

    public int read( char[] buf, int off, int len )
        throws IOException
    {
        return m_data.read( buf, off, len );
    }

    public boolean ready()
        throws IOException
    {
        log.debug("ready ? "+m_data.ready() );
        if(!m_data.ready())
        {
            fillBuffer();
        }

        return m_data.ready();
    }

    public void close()
    {
    }
}
