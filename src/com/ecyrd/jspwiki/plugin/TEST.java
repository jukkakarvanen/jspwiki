/*     JSPWiki - a JSP-based WikiWiki clone.    Copyright (C) 2004 Janne Jalkanen (Janne.Jalkanen@iki.fi)    This program is free software; you can redistribute it and/or modify    it under the terms of the GNU Lesser General Public License as published by    the Free Software Foundation; either version 2.1 of the License, or    (at your option) any later version.    This program is distributed in the hope that it will be useful,    but WITHOUT ANY WARRANTY; without even the implied warranty of    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the    GNU Lesser General Public License for more details.    You should have received a copy of the GNU Lesser General Public License    along with this program; if not, write to the Free Software    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA */package com.ecyrd.jspwiki.plugin;import org.apache.log4j.Logger;import com.ecyrd.jspwiki.*;import com.ecyrd.jspwiki.parser.*;import com.ecyrd.jspwiki.plugin.*;import org.apache.oro.text.*;import org.apache.oro.text.regex.*;import java.util.*;/** *  TestPlugin * *  Includes the body of the plugin based on a condition. *  Valid conditions are the presence of a page or a variable *  or the value of a page or a variable * *  <P>Parameters</P> *  <UL> *    <LI>page: name of the wiki page; default is current page *    <LI>name: name of a wiki variable (optional) *    <LI>match: regexp; when matching page content or variable value, yields true *    <LI>except: regexp; when matching page content or variable value, yields false *  </UL> * *  @author Dirk Frederickx */public class TEST       implements WikiPlugin{    //private static Logger log = Logger.getLogger( ReferredPagesPlugin.class );    private WikiEngine     m_engine;    private StringBuffer   m_result  = new StringBuffer();    private PatternMatcher m_matcher = new Perl5Matcher();    private Pattern        m_matchPattern;    private Pattern        m_exceptPattern;        public static final String PARAM_BODY   = "_body";    public static final String PARAM_PAGE   = "page";    public static final String PARAM_NAME   = "name";    public static final String PARAM_MATCH  = "match";    public static final String PARAM_EXCEPT = "except";    public static final String RETURN_OK    = "true";    public String execute( WikiContext context, Map params )        throws PluginException    {            m_engine = context.getEngine();                WikiPage page = context.getPage();        String testpagename = (String)params.get( PARAM_PAGE );        if( testpagename != null )         {          testpagename = MarkupParser.cleanLink( testpagename );          if( !m_engine.pageExists( testpagename ) )  return ""; //page not present          page = m_engine.getPage( testpagename );        }        if( page == null ) return ""; // page not present         String content = null;                String testvar = (String)params.get( PARAM_NAME );        if( testvar != null )        {          content = m_engine.getVariable( context, testvar );          if( content == null ) return ""; //variable not present        }        else        {          content = m_engine.getPureText( page );        }        String matchPattern = (String) params.get( PARAM_MATCH );        if( matchPattern == null ) matchPattern = ".*";        String exceptPattern = (String) params.get( PARAM_EXCEPT );        if( exceptPattern == null ) exceptPattern = "^$";                // pre compile all needed patterns        // glob compiler :  * is 0..n instance of any char  -- more convenient as input         // perl5 compiler : .* is 0..n instances of any char -- more powerful        //PatternCompiler g_compiler = new GlobCompiler();        PatternCompiler compiler = new Perl5Compiler();        try        {          m_matchPattern  = compiler.compile( matchPattern, Perl5Compiler.SINGLELINE_MASK );                  m_exceptPattern = compiler.compile( exceptPattern, Perl5Compiler.SINGLELINE_MASK );        }        catch( MalformedPatternException e )        {          if( m_matchPattern == null )             throw new PluginException("Illegal match pattern detected.");                     if( m_exceptPattern == null )             throw new PluginException("Illegal except pattern detected.");        }                          if(  m_matcher.matches( content , m_exceptPattern ) ) return ""; //matched except pattern                 if( !m_matcher.matches( content , m_matchPattern  ) ) return ""; //no match with match pattern        m_result.append( "<div class=\"conditional-plugin\">" );                String body = (String)params.get( PARAM_BODY );        if( body == null )         {          m_result.append( RETURN_OK );        }        else        {          m_result.append( m_engine.textToHTML( context, body ) );        }                m_result.append ( "</div>" ) ;                  return m_result.toString() ;    }    }