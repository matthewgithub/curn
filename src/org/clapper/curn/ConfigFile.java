/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.curn;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import java.net.URL;
import java.net.MalformedURLException;

import org.clapper.util.text.TextUtils;

import org.clapper.util.misc.OrderedHashMap;

import org.clapper.util.config.Configuration;
import org.clapper.util.config.NoSuchVariableException;
import org.clapper.util.config.ConfigurationException;

/**
 * <p><tt>ConfigFile</tt> uses the <tt>Configuration</tt> class (part of
 * the <i>clapper.org</i> Java Utility library) to parse and validate the
 * <i>curn</i> configuration file, holding the results in memory for easy
 * access.</p>
 *
 * @version <tt>$Revision$</tt>
 */
public class ConfigFile extends Configuration
{
    /*----------------------------------------------------------------------*\
                                 Constants
    \*----------------------------------------------------------------------*/

    /**
     * Main section name
     */
    private static final String  MAIN_SECTION         = "curn";

    /**
     * Prefix for sections that describing individual feeds.
     */
    private static final String FEED_SECTION_PREFIX   = "Feed";

    /**
     * Prefix for output handler sections.
     */
    private static final String OUTPUT_HANDLER_PREFIX = "OutputHandler";

    /**
     * Variable names
     */
    private static final String VAR_CACHE_FILE        = "CacheFile";
    private static final String VAR_NO_CACHE_UPDATE   = "NoCacheUpdate";
    private static final String VAR_QUIET             = "Quiet";
    private static final String VAR_SUMMARY_ONLY      = "SummaryOnly";
    private static final String VAR_SMTPHOST          = "SMTPHost";
    private static final String VAR_DEFAULT_MAIL_FROM = "DefaultMailFrom";
    private static final String VAR_MAIL_SUBJECT      = "Subject";
    private static final String VAR_DAYS_TO_CACHE     = "DaysToCache";
    private static final String VAR_PARSER_CLASS_NAME = "ParserClass";
    private static final String VAR_PRUNE_URLS        = "PruneURLs";
    private static final String VAR_SHOW_RSS_VERSION  = "ShowRSSVersion";
    private static final String VAR_SMTP_HOST         = "SMTPHost";
    private static final String VAR_DEF_EMAIL_SENDER  = "MailFrom";
    private static final String VAR_EMAIL_SUBJECT     = "MailSubject";
    private static final String VAR_SHOW_DATES        = "ShowDates";
    private static final String VAR_TITLE_OVERRIDE    = "TitleOverride";
    private static final String VAR_EDIT_ITEM_URL     = "EditItemURL";
    private static final String VAR_DISABLED          = "Disabled";
    private static final String VAR_SHOW_AUTHORS      = "ShowAuthors";
    private static final String VAR_SAVE_FEED_AS      = "SaveAs";
    private static final String VAR_FEED_URL          = "URL";
    private static final String VAR_CLASS             = "Class";

    /**
     * Default values
     */
    private static final int     DEF_DAYS_TO_CACHE     = 30;
    private static final boolean DEF_PRUNE_URLS        = false;
    private static final boolean DEF_QUIET             = false;
    private static final boolean DEF_NO_CACHE_UPDATE   = false;
    private static final boolean DEF_SUMMARY_ONLY      = false;
    private static final boolean DEF_SHOW_RSS_VERSION  = false;
    private static final boolean DEF_SHOW_DATES        = false;
    private static final boolean DEF_SHOW_AUTHORS      = false;
    private static final String  DEF_SMTP_HOST         = "localhost";
    private static final String  DEF_EMAIL_SUBJECT     = "RSS Feeds";
    private static final String  DEF_PARSER_CLASS_NAME =
                             "org.clapper.curn.parser.minirss.MiniRSSParser";
    private static final String  DEF_OUTPUT_CLASS =
                             "org.clapper.curn.TextOutputHandler";

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    private File            cacheFile             = null;
    private int             defaultCacheDays      = DEF_DAYS_TO_CACHE;
    private boolean         updateCache           = true;
    private boolean         quiet                 = false;
    private boolean         summaryOnly           = false;
    private boolean         showRSSFormat         = false;
    private boolean         showDates             = false;
    private Collection      feeds                 = new ArrayList();
    private Map             feedMap               = new HashMap();
    private String          parserClassName       = DEF_PARSER_CLASS_NAME;
    private OrderedHashMap  outputHandlers        = new OrderedHashMap();
    private Map             outputHandlerSections = new HashMap();
    private String          smtpHost              = DEF_SMTP_HOST;
    private String          emailSender           = null;
    private String          emailSubject          = DEF_EMAIL_SUBJECT;
    private boolean         showAuthors           = false;

    /**
     * For log messages
     */
    private static Logger log = new Logger (ConfigFile.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct an <tt>ConfigFile</tt> object that parses data
     * from the specified file.
     *
     * @param f  The <tt>File</tt> to open and parse
     *
     * @throws IOException             can't open or read file
     * @throws ConfigurationException  error in configuration data
     */
    ConfigFile (File f)
        throws IOException,
               ConfigurationException
    {
        super (f);
        validate();
    }

    /**
     * Construct an <tt>ConfigFile</tt> object that parses data
     * from the specified file.
     *
     * @param path  the path to the file to parse
     *
     * @throws FileNotFoundException   specified file doesn't exist
     * @throws IOException             can't open or read file
     * @throws ConfigurationException  error in configuration data
     */
    ConfigFile (String path)
        throws FileNotFoundException,
               IOException,
               ConfigurationException
    {
        super (path);
        validate();
    }

    /**
     * Construct an <tt>ConfigFile</tt> object that parses data
     * from the specified URL.
     *
     * @param url  the URL to open and parse
     *
     * @throws IOException             can't open or read URL
     * @throws ConfigurationException  error in configuration data
     */
    ConfigFile (URL url)
        throws IOException,
               ConfigurationException
    {
        super (url);
        validate();
    }

    /**
     * Construct an <tt>ConfigFile</tt> object that parses data
     * from the specified <tt>InputStream</tt>.
     *
     * @param iStream  the <tt>InputStream</tt>
     *
     * @throws IOException             can't open or read URL
     * @throws ConfigurationException  error in configuration data
     */
    ConfigFile (InputStream iStream)
        throws IOException,
               ConfigurationException
    {
        super (iStream);
        validate();
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Get the name of the RSS parser class to use. The caller is responsible
     * for loading the returned class name and verifying that it implements
     * the appropriate interface(s).
     *
     * @return the full class name
     */
    public String getRSSParserClassName()
    {
        return parserClassName;
    }

    /**
     * Get the names of the classes that handle output. The caller is
     * responsible for loading the returned class names and verifying that
     * they implements the appropriate interface(s).
     *
     * @return a <tt>Collection</tt> of strings containing full class names
     */
    public Collection getOutputHandlerClassNames()
    {
        return outputHandlers.keysInInsertionOrder();
    }

    /**
     * Get the output handler-specific parameters for an output handler.
     *
     * @param className  the output handler class name
     *
     * @return a <tt>Map</tt>, where each key is a variable name and each
     *         value is the string value for the key. The map will be empty,
     *         but not null, if there are no additional handler-specific
     *         variables.
     *
     * @see #getOutputHandlerClassNames()
     * @see #getOutputHandlerSpecificVariables(Class)
     * @see #getOutputHandlerSectionName(String)
     * @see #getOutputHandlerSectionName(Class)
     */
    public Map getOutputHandlerSpecificVariables (String className)
    {
        return (Map) outputHandlers.get (className);
    }

    /**
     * Get the output handler-specific parameters for an output handler.
     *
     * @param cls  the output handler class
     *
     * @return a <tt>Map</tt>, where each key is a variable name and each
     *         value is the string value for the key. The map will be empty,
     *         but not null, if there are no additional handler-specific
     *         variables.
     *
     * @see #getOutputHandlerClassNames()
     * @see #getOutputHandlerSpecificVariables(String)
     * @see #getOutputHandlerSectionName(String)
     * @see #getOutputHandlerSectionName(Class)
     */
    public Map getOutputHandlerSpecificVariables (Class cls)
    {
        return (Map) outputHandlers.get (cls.getName());
    }

    /**
     * Gets the configuration section name for a specific output handler.
     * This makes it easier for the output handler to ask for its variables.
     *
     * @param className  the output handler class name
     *
     * @return the name of the configuration section where the output handler
     *         was defined
     *
     * @see #getOutputHandlerClassNames()
     * @see #getOutputHandlerSpecificVariables(String)
     * @see #getOutputHandlerSpecificVariables(Class)
     * @see #getOutputHandlerSectionName(Class)
     */
    public String getOutputHandlerSectionName (String className)
    {
        return (String) outputHandlerSections.get (className);
    }

    /**
     * Gets the configuration section name for a specific output handler.
     * This makes it easier for the output handler to ask for its variables.
     *
     * @param cls  the output handler class
     *
     * @return the name of the configuration section where the output handler
     *         was defined
     *
     * @see #getOutputHandlerClassNames()
     * @see #getOutputHandlerSpecificVariables(String)
     * @see #getOutputHandlerSpecificVariables(Class)
     * @see #getOutputHandlerSectionName(String)
     */
    public String getOutputHandlerSectionName (Class cls)
    {
        return getOutputHandlerSectionName (cls.getName());
    }

    /**
     * Get the configured cache file.
     *
     * @return the cache file
     *
     * @see #mustUpdateCache
     * @see #setMustUpdateCacheFlag
     */
    public File getCacheFile()
    {
        return cacheFile;
    }

    /**
     * Determine whether the cache should be updated.
     *
     * @return <tt>true</tt> if the cache should be updated, <tt>false</tt>
     *         if it should not.
     *
     * @see #getCacheFile
     * @see #setMustUpdateCacheFlag
     */
    public boolean mustUpdateCache()
    {
        return updateCache;
    }

    /**
     * Change the "update cache" flag.
     *
     * @param val <tt>true</tt> if the cache should be updated, <tt>false</tt>
     *            if it should not
     *
     * @see #mustUpdateCache
     * @see #getCacheFile
     */
    public void setMustUpdateCacheFlag (boolean val)
    {
        updateCache = val;
    }

    /**
     * Return the value of the "quiet" flag.
     *
     * @return <tt>true</tt> if "quiet" flag is set, <tt>false</tt>
     *         otherwise
     *
     * @see #setQuietFlag
     */
    public boolean beQuiet()
    {
        return quiet;
    }

    /**
     * Set the value of the "quiet" flag.
     *
     * @param val <tt>true</tt> to set the "quiet" flag, <tt>false</tt>
     *            to clear it
     *
     * @see #beQuiet
     */
    public void setQuietFlag (boolean val)
    {
        this.quiet = val;
    }

    /**
     * Return the value of the "show authors" flag. This flag controls whether
     * to display the authors associated with each item, if available.
     *
     * @return <tt>true</tt> if "show authors" flag is set, <tt>false</tt>
     *         otherwise
     *
     * @see #setShowAuthorsFlag
     */
    public boolean showAuthors()
    {
        return showAuthors;
    }

    /**
     * Set the value of the "show authors" flag. This flag controls whether
     * to display the authors associated with each item, if available.
     *
     * @param val <tt>true</tt> to set the "show authors" flag, <tt>false</tt>
     *            to clear it
     *
     * @see #showAuthors
     */
    public void setShowAuthorsFlag (boolean val)
    {
        this.showAuthors = val;
    }

    /**
     * Return the value of the "show dates" flag. This flag controls whether
     * to display the dates associated with each feed and item, if available.
     *
     * @return <tt>true</tt> if "show dates" flag is set, <tt>false</tt>
     *         otherwise
     *
     * @see #setShowDatesFlag
     */
    public boolean showDates()
    {
        return showDates;
    }

    /**
     * Set the value of the "show dates" flag. This flag controls whether
     * to display the dates associated with each feed and item, if available.
     *
     * @param val <tt>true</tt> to set the "show dates" flag, <tt>false</tt>
     *            to clear it
     *
     * @see #showDates
     */
    public void setShowDatesFlag (boolean val)
    {
        this.showDates = val;
    }

    /**
     * Return the value of "show RSS version" flag.
     *
     * @return <tt>true</tt> if flag is set, <tt>false</tt> if it isn't
     *
     * @see #setShowRSSVersionFlag
     */
    public boolean showRSSVersion()
    {
        return showRSSFormat;
    }

    /**
     * Set the value of the "show RSS version" flag.
     *
     * @param val <tt>true</tt> to set the flag,
     *            <tt>false</tt> to clear it
     *
     * @see #showRSSVersion
     */
    public void setShowRSSVersionFlag (boolean val)
    {
        this.showRSSFormat = val;
    }

    /**
     * Get the SMTP host to use when sending email.
     *
     * @return the SMTP host. Never null.
     *
     * @see #setSMTPHost
     */
    public String getSMTPHost()
    {
        return smtpHost;
    }

    /**
     * Set the SMTP host to use when sending email.
     *
     * @param host the SMTP host, or null to revert to the default value
     *
     * @see #getSMTPHost
     */
    public void setSMTPHost (String host)
    {
        smtpHost = (host == null) ? DEF_SMTP_HOST : host;
    }

    /**
     * Get the email address to use as the sender for email messages.
     *
     * @return the email address, or null if not specified
     *
     * @see #setEmailSender
     */
    public String getEmailSender()
    {
        return emailSender;
    }

    /**
     * Set the email address to use as the sender for email messages.
     *
     * @param address the new address, or null to clear the field
     *
     * @see #getEmailSender
     */
    public void setEmailSender (String address)
    {
        this.emailSender = address;
    }

    /**
     * Get the subject to use in email messages, if email is being sent.
     *
     * @return the subject. Never null.
     *
     * @see #setEmailSubject
     */
    public String getEmailSubject()
    {
        return emailSubject;
    }

    /**
     * Set the subject to use in email messages, if email is being sent.
     *
     * @param subject the subject, or null to reset to the default
     *
     * @see #getEmailSubject
     */
    public void setEmailSubject (String subject)
    {
        this.emailSubject = (subject == null) ? DEF_EMAIL_SUBJECT : subject;
    }

    /**
     * Get the configured RSS feeds.
     *
     * @return a <tt>Collection</tt> of <tt>FeedInfo</tt> objects.
     *
     * @see #hasFeed
     * @see #getFeedInfoFor(String)
     * @see #getFeedInfoFor(URL)
     */
    public Collection getFeeds()
    {
        return Collections.unmodifiableCollection (feeds);
    }

    /**
     * Determine whether the specified URL is one of the configured RSS
     * feeds.
     *
     * @param url  the URL
     *
     * @return <tt>true</tt> if it's there, <tt>false</tt> if not
     *
     * @see #getFeeds
     * @see #getFeedInfoFor(String)
     * @see #getFeedInfoFor(URL)
     */
    public boolean hasFeed (URL url)
    {
        return feedMap.containsKey (url.toString());
    }

    /**
     * Get the feed data for a given URL.
     *
     * @param url   the URL
     *
     * @return the corresponding <tt>RSSFeedInfo</tt> object, or null
     *         if not found
     *
     * @see #getFeeds
     * @see #hasFeed
     * @see #getFeedInfoFor(String)
     * @see FeedInfo
     */
    public FeedInfo getFeedInfoFor (URL url)
    {
        return (FeedInfo) feedMap.get (url.toString());
    }

    /**
     * Get the feed data for a given URL.
     *
     * @param urlString   the URL, as a string
     *
     * @return the corresponding <tt>FeedInfo</tt> object, or null
     *         if not found
     *
     * @see #getFeeds
     * @see #hasFeed
     * @see #getFeedInfoFor(URL)
     * @see FeedInfo
     */
    public FeedInfo getFeedInfoFor (String urlString)
    {
        return (FeedInfo) feedMap.get (urlString);
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Validate the loaded configuration.
     *
     * @throws ConfigurationException on error
     */
    private void validate()
        throws ConfigurationException
    {
        // First, verify that the main section is there and process it.

        processMainSection();

        // Process the remaining sections. Skip ones we don't recognize.

        for (Iterator it = getSectionNames (new ArrayList()).iterator();
             it.hasNext(); )
        {
            String sectionName = (String) it.next();

            if (sectionName.startsWith (FEED_SECTION_PREFIX))
                processSiteURLSection (sectionName);

            else if (sectionName.startsWith (OUTPUT_HANDLER_PREFIX))
                processOutputHandlerSection (sectionName);
        }
    }

    /**
     * Verify existence of main section and process it.
     *
     * @throws ConfigurationException on error
     */
    private void processMainSection()
        throws ConfigurationException
    {
        if (! this.containsSection (MAIN_SECTION))
        {
            throw new ConfigurationException ("Configuration file is missing "
                                            + "required \""
                                            + MAIN_SECTION
                                            + "\" section.");
        }

        try
        {
            String s;

            cacheFile = new File (getVariableValue (MAIN_SECTION,
                                                    VAR_CACHE_FILE));
            if (cacheFile.isDirectory())
            {
                throw new ConfigurationException ("Specified cache file \""
                                                + cacheFile.getPath()
                                                + "\" is a directory.");
            }

            defaultCacheDays = getOptionalIntegerValue (MAIN_SECTION,
                                                        VAR_DAYS_TO_CACHE,
                                                        DEF_DAYS_TO_CACHE);
            updateCache = (!getOptionalBooleanValue (MAIN_SECTION,
                                                     VAR_NO_CACHE_UPDATE,
                                                     DEF_NO_CACHE_UPDATE));
            quiet = getOptionalBooleanValue (MAIN_SECTION,
                                             VAR_QUIET,
                                             DEF_QUIET);
            summaryOnly = getOptionalBooleanValue (MAIN_SECTION,
                                                   VAR_SUMMARY_ONLY,
                                                   DEF_SUMMARY_ONLY);
            showRSSFormat = getOptionalBooleanValue (MAIN_SECTION,
                                                     VAR_SHOW_RSS_VERSION,
                                                     DEF_SHOW_RSS_VERSION);
            showDates = getOptionalBooleanValue (MAIN_SECTION,
                                                 VAR_SHOW_DATES,
                                                 DEF_SHOW_DATES);
            parserClassName = getOptionalStringValue (MAIN_SECTION,
                                                      VAR_PARSER_CLASS_NAME,
                                                      DEF_PARSER_CLASS_NAME);
            smtpHost = getOptionalStringValue (MAIN_SECTION,
                                               VAR_SMTP_HOST,
                                               DEF_SMTP_HOST);
            emailSender = getOptionalStringValue (MAIN_SECTION,
                                                  VAR_DEF_EMAIL_SENDER,
                                                  null);
            emailSubject = getOptionalStringValue (MAIN_SECTION,
                                                   VAR_EMAIL_SUBJECT,
                                                   DEF_EMAIL_SUBJECT);
            showAuthors = getOptionalBooleanValue (MAIN_SECTION,
                                                   VAR_SHOW_AUTHORS,
                                                   DEF_SHOW_AUTHORS);
        }

        catch (NoSuchVariableException ex)
        {
            throw new ConfigurationException ("Missing required variable \""
                                            + ex.getVariableName()
                                            + "\" in section \""
                                            + ex.getSectionName()
                                            + "\".");
        
        }
    }

    /**
     * Process a section that identifies a site's RSS URL.
     *
     * @param sectionName  the section name
     *
     * @throws ConfigurationException  configuration error
     */
    private void processSiteURLSection (String sectionName)
        throws ConfigurationException
    {
        FeedInfo  feedInfo = null;
        String    feedURLString = getVariableValue (sectionName, VAR_FEED_URL);
        String    s;
        URL       url = null;

        try
        {
            url = Util.normalizeURL (feedURLString);
            log.debug ("Configured feed: URL=\"" + url.toString() + "\"");
            feedInfo = new FeedInfo (url);
        }

        catch (MalformedURLException ex)
        {
            throw new ConfigurationException ("Bad RSS site URL: \""
                                            + feedURLString
                                            + "\"");
        }

        feedInfo.setDaysToCache (getOptionalIntegerValue (sectionName,
                                                          VAR_DAYS_TO_CACHE,
                                                          defaultCacheDays));
        feedInfo.setPruneURLsFlag (getOptionalBooleanValue (sectionName,
                                                            VAR_PRUNE_URLS,
                                                            DEF_PRUNE_URLS));
        feedInfo.setSummarizeOnlyFlag
                              (getOptionalBooleanValue (sectionName,
                                                        VAR_SUMMARY_ONLY,
                                                        summaryOnly));
        feedInfo.setEnabledFlag
                              (! getOptionalBooleanValue (sectionName,
                                                          VAR_DISABLED,
                                                          false));

        s = getOptionalStringValue (sectionName, VAR_TITLE_OVERRIDE, null);
        if (s != null)
            feedInfo.setTitleOverride (s);

        s = getOptionalStringValue (sectionName, VAR_EDIT_ITEM_URL, null);
        if (s != null)
            feedInfo.setItemURLEditCommand (s);

        s = getOptionalStringValue (sectionName, VAR_SAVE_FEED_AS, null);
        if (s != null)
            feedInfo.setSaveAsFile (new File (s));

        feeds.add (feedInfo);
        feedMap.put (url.toString(), feedInfo);
    }

    /**
     * Process a section that identifies an output handler.
     *
     * @param sectionName  the section name
     *
     * @throws ConfigurationException  configuration error
     */
    private void processOutputHandlerSection (String sectionName)
        throws ConfigurationException
    {
        // Get the required class name.

        String className = getVariableValue (sectionName, VAR_CLASS);

        // Only process the rest if it's not disabled.

        if (! getOptionalBooleanValue (sectionName, VAR_DISABLED, false))
        {
            Iterator it = getVariableNames (sectionName, new ArrayList())
                        .iterator();
            Map extraVariables = new HashMap();

            while (it.hasNext())
            {
                String variableName = (String) it.next();

                // Skip the ones we've already processed.

                if (variableName.equals (VAR_DISABLED))
                    continue;

                if (variableName.equals (VAR_CLASS))
                    continue;

                extraVariables.put (variableName,
                                    getVariableValue (sectionName,
                                                      variableName));
            }

            outputHandlers.put (className, extraVariables);
            outputHandlerSections.put (className, sectionName);
        }
    }
}
