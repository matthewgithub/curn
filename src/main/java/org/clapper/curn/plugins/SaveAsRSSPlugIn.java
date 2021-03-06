/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2004-2012 Brian M. Clapper.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

  * Neither the name "clapper.org", "curn", nor the names of the project's
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
\*---------------------------------------------------------------------------*/


package org.clapper.curn.plugins;

import org.clapper.curn.Constants;
import org.clapper.curn.CurnConfig;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.FeedConfigItemPlugIn;
import org.clapper.curn.PostConfigPlugIn;

import org.clapper.util.classutil.ClassUtil;
import org.clapper.util.config.ConfigurationException;
import org.clapper.util.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.clapper.curn.CurnUtil;
import org.clapper.curn.FeedCache;
import org.clapper.curn.PostFeedProcessPlugIn;
import org.clapper.curn.output.freemarker.FreeMarkerFeedTransformer;
import org.clapper.curn.output.freemarker.TemplateLocation;
import org.clapper.curn.output.freemarker.TemplateType;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.util.cmdline.CommandLineUsageException;
import org.clapper.util.cmdline.ParameterHandler;
import org.clapper.util.cmdline.ParameterParser;
import org.clapper.util.cmdline.UsageInfo;
import org.clapper.util.io.IOExceptionExt;
import org.clapper.util.text.TextUtil;

/**
 * <p>The <tt>SaveAsRSSPlugIn</tt> acts sort of like a single-feed output
 * handler: It takes a feed that's been parsed, converts the parsed data to RSS
 * or Atom format, and writes it to a file. It differs from an output handler in
 * that an output handler must handle multiple feeds, whereas this plug-in
 * handles a single feed at a time.</p>
 *
 * <p>This plug-in intercepts the following per-feed configuration
 * parameters:</p>
 *
 * <table width="80%" class="nested-table" align="center">
 *   <tr valign="top">
 *     <td><tt>SaveAsRSS&nbsp;[options]&nbsp;path</tt></td>
 *     <td><i>path</i> is the path to the file to receive the RSS output.
 *
 *       Options:
 *       <ul>
 *         <li><tt>-t <i>type</i></tt> (or <tt>--type <i>type</i></tt>)
 *             is the type of RSS output to generate. Currently, the legal
 *             values for the <i>type</i> argument are: "rss1", "rss2", "atom".
 *             If not specified, this option defaults to "atom".
 *         <li><tt>-b <i>backups</i></tt> (or <tt>--backups <i>backups</i></tt>)
 *             specifies how many backups of <i>path</i> to retain. Default: 0
 *         <li><tt>-e <i>encoding</i></tt> (or <tt>--encoding <i>encoding</i></tt>
 *             is encoding to use for the file. It defaults to "utf-8".
 *       </ul>
 *       Examples:
 * <pre>
 *    SaveAsRSS: -t rss1 -b 3 -e iso-8859-1 ${user.home:.curn}/rss/foo.xml
 *    SaveAsRSS: --type atom --encoding utf16 C:/temp/foo.xml</pre>
 *     </td>
 *   </tr>
 *   <tr>
 *     <td><tt>SaveRSSOnly</tt></td>
 *     <td>If set to "true", this parameter indicates that the RSS file should
 *         generated, but that all further processing on the feed should be
 *         skip. In particular, the feed won't be passed to any other plug-ins,
 *         and it won't be passed to any output handlers. This parameter cannot
 *         be specified unless <tt>SaveAsRSS</tt> is also specified.</td>
 *   </tr>
 * </table>
 *
 * <p>Note: If this plug-in is used in conjunction with the
 * {@link RawFeedSaveAsPlugIn} class, and the {@link RawFeedSaveAsPlugIn}
 * class's <tt>SaveOnly</tt> parameter is specified, this plug-in will
 * <i>not</i> be invoked.</p>
 *
 * @version <tt>$Revision$</tt>
 */
public class SaveAsRSSPlugIn
    implements FeedConfigItemPlugIn,
               PostConfigPlugIn,
               PostFeedProcessPlugIn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String VAR_SAVE_AS_RSS   = "SaveAsRSS";
    private static final String VAR_SAVE_RSS_ONLY = "SaveRSSOnly";
    private static final String RSS1_TEMPLATE_PATH =
        "org/clapper/curn/output/freemarker/RSS1.ftl";
    private static final String RSS2_TEMPLATE_PATH =
        "org/clapper/curn/output/freemarker/RSS2.ftl";
    private static final String ATOM_TEMPLATE_PATH =
        "org/clapper/curn/output/freemarker/Atom.ftl";

    /*----------------------------------------------------------------------*\
                              Private Classes
    \*----------------------------------------------------------------------*/

    /**
     * Feed save info
     */
    class FeedSaveInfo
    {
        String  sectionName;
        File    saveAsFile;
        boolean saveOnly;
        String  saveAsEncoding = "utf-8";
        TemplateLocation templateLocation = null;
        int backups = 0;

        FeedSaveInfo()
        {
            // Nothing to do
        }
    }

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * Feed save data, by feed
     */
    private Map<FeedInfo,FeedSaveInfo> perFeedSaveAsMap =
        new HashMap<FeedInfo,FeedSaveInfo>();

    /**
     * Saved reference to the configuration
     */
    private CurnConfig config = null;

    /**
     * For log messages
     */
    private static final Logger log = new Logger(SaveAsRSSPlugIn.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    public SaveAsRSSPlugIn()
    {
        // Nothing to do
    }

    /*----------------------------------------------------------------------*\
               Public Methods Required by *PlugIn Interfaces
    \*----------------------------------------------------------------------*/

    /**
     * Get a displayable name for the plug-in.
     *
     * @return the name
     */
    public String getPlugInName()
    {
        return "Save As RSS";
    }

    /**
     * Get the sort key for this plug-in.
     *
     * @return the sort key string.
     */
    public String getPlugInSortKey()
    {
        return ClassUtil.getShortClassName (getClass().getName());
    }

    /**
     * Initialize the plug-in. This method is called before any of the
     * plug-in methods are called.
     *
     * @throws CurnException on error
     */
    public void initPlugIn()
        throws CurnException
    {
    }

    /**
     * Called immediately after <i>curn</i> has read and processed a
     * configuration item in a "feed" configuration section. All
     * configuration items are passed, one by one, to each loaded plug-in.
     * If a plug-in class is not interested in a particular configuration
     * item, this method should simply return without doing anything. Note
     * that some configuration items may simply be variable assignment;
     * there's no real way to distinguish a variable assignment from a
     * blessed configuration item.
     *
     * @param sectionName  the name of the configuration section where
     *                     the item was found
     * @param paramName    the name of the parameter
     * @param config       the active configuration
     * @param feedInfo     partially complete <tt>FeedInfo</tt> object
     *                     for the feed. The URL is guaranteed to be
     *                     present, but no other fields are.
     *
     * @return <tt>true</tt> to continue processing the feed,
     *         <tt>false</tt> to skip it
     *
     * @throws CurnException on error
     *
     * @see CurnConfig
     * @see FeedInfo
     * @see FeedInfo#getURL
     */
    public boolean runFeedConfigItemPlugIn(String     sectionName,
                                           String     paramName,
                                           CurnConfig config,
                                           FeedInfo   feedInfo)
        throws CurnException
    {
        try
        {
            if (paramName.equals (VAR_SAVE_AS_RSS))
            {
                handleSaveAsConfigParam(sectionName,
                                        paramName,
                                        config,
                                        feedInfo);
            }

            else if (paramName.equals (VAR_SAVE_RSS_ONLY))
            {
                FeedSaveInfo saveInfo = getOrMakeFeedSaveInfo (feedInfo);
                saveInfo.saveOnly =
                    config.getOptionalBooleanValue (sectionName,
                                                    paramName,
                                                    false);
                saveInfo.sectionName = sectionName;
                log.debug ("[" + sectionName + "]: SaveRSSOnly=" +
                           saveInfo.saveOnly);
            }

            return true;
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException (ex);
        }
    }

    /**
     * Called after the entire configuration has been read and parsed, but
     * before any feeds are processed. Intercepting this event is useful
     * for plug-ins that want to adjust the configuration. For instance,
     * the <i>curn</i> command-line wrapper intercepts this plug-in event
     * so it can adjust the configuration to account for command line
     * options.
     *
     * @param config  the parsed {@link CurnConfig} object
     *
     * @throws CurnException on error
     *
     * @see CurnConfig
     */
    public void runPostConfigPlugIn(CurnConfig config)
        throws CurnException
    {
        this.config = config;

        for (FeedInfo feedInfo : perFeedSaveAsMap.keySet())
        {
            FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);

            if (saveInfo.saveOnly && (saveInfo.saveAsFile == null))
            {
                throw new CurnException
                    (Constants.BUNDLE_NAME,
                     "CurnConfig.saveOnlyButNoSaveAs",
                     "Configuration section \"{0}\": " +
                     "\"[1}\" may only be specified if \"{2}\" is set.",
                     new Object[]
                     {
                         saveInfo.sectionName,
                         VAR_SAVE_RSS_ONLY,
                         VAR_SAVE_AS_RSS
                     });
            }
        }
    }

    /**
     * <p>Called just after the feed has been parsed, but before it is
     * otherwise processed.
     *
     * @param feedInfo  the {@link FeedInfo} object for the feed
     * @param feedCache the feed cache
     * @param channel   the parsed feed data
     *
     * @return <tt>true</tt> if <i>curn</i> should continue to process the
     *         feed, <tt>false</tt> to skip the feed
     *
     * @throws CurnException on error
     *
     * @see FeedInfo
     * @see RSSChannel
     */
    public boolean runPostFeedProcessPlugIn(final FeedInfo   feedInfo,
                                          final FeedCache  feedCache,
                                          final RSSChannel channel)
        throws CurnException
    {
        boolean keepGoing = true;
        FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);

        if ((saveInfo != null) && (saveInfo.saveAsFile != null))
        {
            // Create a feed transformer and set the invariant stuff.

            FreeMarkerFeedTransformer feedTransformer =
                new FreeMarkerFeedTransformer(config, true);
            feedTransformer.setEncoding(saveInfo.saveAsEncoding);
            feedTransformer.setTemplate(saveInfo.templateLocation, "text/xml");

            // Now, add the channel.

            feedTransformer.addChannel(channel, feedInfo, true);

            // Now, transform the feed.

            try
            {
                log.debug("Generating RSS output file \"" +
                          saveInfo.saveAsFile + "\" (encoding " +
                          saveInfo.saveAsEncoding + ")");

                Writer out =
                    CurnUtil.openOutputFile(saveInfo.saveAsFile,
                                            saveInfo.saveAsEncoding,
                                            CurnUtil.IndexMarker.BEFORE_EXTENSION,
                                            saveInfo.backups);

                    new OutputStreamWriter
                        (new FileOutputStream(saveInfo.saveAsFile),
                         saveInfo.saveAsEncoding);
                feedTransformer.transform(out);
                out.close();
            }

            catch (IOExceptionExt ex)
            {
                throw new CurnException ("Can't write RSS output to \"" +
                                         saveInfo.saveAsFile + "\": ",
                                         ex);
            }

            catch (IOException ex)
            {
                throw new CurnException ("Can't write RSS output to \"" +
                                         saveInfo.saveAsFile + "\": ",
                                         ex);
            }

            keepGoing = ! saveInfo.saveOnly;
        }

        return keepGoing;
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    private FeedSaveInfo getOrMakeFeedSaveInfo (FeedInfo feedInfo)
    {
        FeedSaveInfo saveInfo = perFeedSaveAsMap.get(feedInfo);
        if (saveInfo == null)
        {
            saveInfo = new FeedSaveInfo();
            perFeedSaveAsMap.put(feedInfo, saveInfo);
        }

        return saveInfo;
    }

    private void handleSaveAsConfigParam(final String     sectionName,
                                         final String     paramName,
                                         final CurnConfig config,
                                         final FeedInfo   feedInfo)
        throws CurnException,
               ConfigurationException
    {
        final FeedSaveInfo saveInfo = getOrMakeFeedSaveInfo(feedInfo);

        // Parse the value as a command line.

        UsageInfo usageInfo = new UsageInfo();
        usageInfo.addOption('b', "backups", "<n>",
                            "Number of backups to keep");
        usageInfo.addOption('t', "type", "<rss1|rss2|atom>",
                            "RSS type for output.");
        usageInfo.addOption('e', "encoding", "<encoding>",
                            "Desired output encoding");
        usageInfo.addParameter("<path>", "Path to RSS output file", true);

        // Inner class for handling command-line syntax of the value.

        class ConfigParameterHandler implements ParameterHandler
        {
            String templatePath = ATOM_TEMPLATE_PATH;
            private String rawValue;

            ConfigParameterHandler(String rawValue)
            {
                this.rawValue = rawValue;
            }

            public void parseOption(char             shortOption,
                                    String           longOption,
                                    Iterator<String> it)
                throws CommandLineUsageException,
                       NoSuchElementException
            {
                String value;
                switch (shortOption)
                {
                    case 'b':
                        value = it.next();
                        try
                        {
                            saveInfo.backups = Integer.parseInt(value);
                        }

                        catch (NumberFormatException ex)
                        {
                            throw new CommandLineUsageException
                                ("Section [" + sectionName +
                                 "], parameter \"" + paramName + "\": " +
                                 "Unexpected non-numeric value \"" + value +
                                 "\" for \"" +
                                  UsageInfo.SHORT_OPTION_PREFIX + shortOption +
                                  "\" option.");
                        }
                        break;

                    case 't':
                        value = it.next();
                        if (value.equalsIgnoreCase("rss1"))
                            templatePath = RSS1_TEMPLATE_PATH;
                        else if (value.equalsIgnoreCase("rss2"))
                            templatePath = RSS2_TEMPLATE_PATH;
                        else if (value.equalsIgnoreCase("atom"))
                            templatePath = ATOM_TEMPLATE_PATH;
                        else
                        {
                            throw new CommandLineUsageException
                                ("Section \"" + sectionName +
                                 "\": Parameter \"" + paramName +
                                 "\" has unknown RSS type \"" +
                                 value + "\"");
                        }

                        break;

                    case 'e':
                        saveInfo.saveAsEncoding = it.next();
                        break;

                    default:
                        throw new CommandLineUsageException
                            ("Section [" + sectionName +
                             "], parameter \"" + paramName + "\": " +
                             "Unknown option \"" +
                             UsageInfo.SHORT_OPTION_PREFIX + shortOption +
                            "\" in value \"" + rawValue + "\"");
                }
            }

            public void parsePostOptionParameters(Iterator<String> it)
                throws CommandLineUsageException,
                       NoSuchElementException
            {
                saveInfo.saveAsFile = CurnUtil.mapConfiguredPathName(it.next());
            }
        };

        // Parse the parameters.

        ParameterParser paramParser = new ParameterParser(usageInfo);
        String rawValue = config.getConfigurationValue(sectionName, paramName);
        try
        {
            String[] valueTokens = config.getConfigurationTokens(sectionName,
                                                                 paramName);
            if (log.isDebugEnabled())
            {
                log.debug("[" + sectionName + "]: SaveAsRSS: value=\"" +
                          rawValue + "\", tokens=" +
                          TextUtil.join(valueTokens, '|'));
            }

            ConfigParameterHandler handler = new ConfigParameterHandler(rawValue);
            log.debug("Parsing value \"" + rawValue + "\"");
            paramParser.parse(valueTokens, handler);

            // Save values the parser could not.

            saveInfo.templateLocation =
                new TemplateLocation(TemplateType.CLASSPATH,
                                     handler.templatePath);
        }

        catch (CommandLineUsageException ex)
        {
            throw new CurnException("Section [" + sectionName +
                                    "], parameter \"" + paramName +
                                    "\": Error parsing value \"" + rawValue +
                                    "\"",
                                    ex);
        }
    }
}

