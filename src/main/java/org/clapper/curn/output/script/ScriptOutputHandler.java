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


package org.clapper.curn.output.script;

import org.clapper.curn.Constants;
import org.clapper.curn.CurnConfig;
import org.clapper.curn.ConfiguredOutputHandler;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.Version;
import org.clapper.curn.output.FileOutputHandler;
import org.clapper.curn.parser.RSSChannel;

import org.clapper.util.config.ConfigurationException;
import org.clapper.util.config.NoSuchSectionException;
import org.clapper.util.io.FileUtil;
import org.clapper.util.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.clapper.curn.CurnUtil;

/**
 * Provides an output handler calls a script via the Apache Jakarta
 * {@link <a href="http://jakarta.apache.org/bsf/">Bean Scripting Framework</a>}
 * (BSF). This handler supports any scripting language supported by BSF. In
 * addition to the  configuration parameters supported by the
 * {@link FileOutputHandler} base class, this handler supports the
 * following additional configuration variables, which must be specified in
 * the handler's configuration section.
 *
 * <table border="1" align="center">
 *   <tr>
 *     <th>Parameter</th>
 *     <th>Explanation</th>
 *   </tr>
 *
 *   <tr>
 *     <td><tt>Script</tt></td>
 *     <td>Path to the script to be invoked. The script will be called
 *         as if from the command line, except that additional objects will
 *         be available via BSF.
 *     </td>
 *   </tr>
 *
 *   <tr>
 *     <td><tt>Language</tt></td>
 *     <td><p>The scripting language, as recognized by BSF. This handler
 *         supports all the scripting language engines that are registered
 *         with the BSF software. (Those predefined engines are configured in
 *         a properties file within the BSF software.) Some of the
 *         scripting language engines are actually bundled with BSF. Some
 *         are not. Regardless, of
 *         course, the actual the jar files for the scripting
 *         languages themselves must be in the CLASSPATH at runtime, for those
 *         languages to be available.</p>
 *
 *         <p>If you want to use a BSF scripting language engine that isn't
 *         automatically registered with BSF, simply extend this class and
 *         override the {@link #registerAdditionalScriptingEngines} method.
 *         In that method, call <tt>BSFManager.registerScriptingEngine()</tt>
 *         for each additional language you want to support. For example,
 *         to provide a handler that supports
 *         {@link <a href="http://www.judoscript.com/">JudoScript</a>},
 *         you might write an output handler that looks like this:</p>
 * <blockquote><pre>
 * import org.clapper.curn.CurnException;
 * import org.clapper.curn.output.script.ScriptOutputHandler;
 * import org.apache.bsf.BSFManager;
 *
 * public class MyOutputHandler extends ScriptOutputHandler
 * {
 *     public JudoScriptOutputHandler()
 *     {
 *         super();
 *     }
 *
 *     public void registerAdditionalScriptingEngines()
 *         throws CurnException
 *     {
 *         BSFManager.registerScriptingEngine ("mylang",
 *                                             "com.example.BSFMyLangEngine",
 *                                             new String[] {"ml", "myl"});
 *     }
 * }
 * </pre></blockquote>
 *
 *         Then, simply use your class instead of <tt>ScriptOutputHandler</tt>
 *         in your configuration file.
 *     </td>
 *   </tr>
 * </table>
 *
 * <p>This handler's {@link #displayChannel displayChannel()} method does
 * not invoke the script; instead, it buffers up all the channels so that
 * the {@link #flush} method can invoke the script. That way, the overhead
 * of invoking the script only occurs once. Via the BSF engine, this
 * handler makes available an iterator of special objects that wrap both
 * the {@link RSSChannel} and {@link FeedInfo} objects for a given channel.
 * See below for a more complete description.</p>
 *
 * <p>The complete list of objects bound into the BSF beanspace follows.</p>
 *
 * <table border="0">
 *   <tr valign="top">
 *     <th>Bound name</th>
 *     <th>Java type</th>
 *     <th>Explanation</th>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>channels</td>
 *     <td><tt>java.util.Collection</tt></td>
 *     <td>An <tt>Collection</tt> of special internal objects that wrap
 *         both {@link RSSChannel} and {@link FeedInfo} objects. The
 *         wrapper objects provide two methods:</td>
 *
 *         <ul>
 *           <li><tt>getChannel()</tt> gets the <tt>RSSChannel</tt> object
 *           <li><tt>getFeedInfo()</tt> gets the <tt>FeedInfo</tt> object
 *         </ul>
 *    </tr>
 *
 *   <tr valign="top">
 *     <td>outputPath</td>
 *     <td><tt>java.lang.String</tt></td>
 *     <td>The path to an output file. The script should write its output
 *         to that file. Overwriting the file is fine. If the script generates
 *         no output, then it can ignore the file.</td>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>config</td>
 *     <td><tt>{@link CurnConfig}</tt></td>
 *     <td>The <tt>org.clapper.curn.CurnConfig</tt> object that represents
 *         the parsed configuration data. Useful in conjunction with the
 *         "configSection" object, to parse additional parameters from
 *         the configuration.</td>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>configSection</td>
 *     <td><tt>java.lang.String</tt></td>
 *     <td>The name of the configuration file section in which the output
 *         handler was defined. Useful if the script wants to access
 *         additional script-specific configuration data.</td>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>mimeType</td>
 *     <td><tt>java.io.PrintWriter</tt></td>
 *     <td>A <tt>PrintWriter</tt> object to which the script should print
 *         the MIME type that corresponds to the generated output.
 *         If the script generates no output, then it can ignore this
 *         object.</td>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>logger</td>
 *     <td>{@link Logger org.clapper.util.logging.Logger}</td>
 *     <td>A <tt>Logger</tt> object, useful for logging messages to
 *         the <i>curn</i> log file.</td>
 *   </tr>
 *
 *   <tr valign="top">
 *     <td>version</td>
 *     <td><tt>java.lang.String</tt></td>
 *     <td>Full <i>curn</i> version string, in case the script wants to
 *         include it in the generated output
 *   </tr>
 * </table>
 *
 * <p>For example, the following Jython script can be used as a template
 * for a Jython output handler.</p>
 *
 * <blockquote>
 * <pre>
 * import sys
 *
 * def __init__ (self):
 *     """
 *     Initialize a new TextOutputHandler object.
 *     """
 *     self.__channels    = bsf.lookupBean ("channels")
 *     self.__outputPath  = bsf.lookupBean ("outputPath")
 *     self.__mimeTypeOut = bsf.lookupBean ("mimeType")
 *     self.__config      = bsf.lookupBean ("config")
 *     self.__sectionName = bsf.lookupBean ("configSection")
 *     self.__logger      = bsf.lookupBean ("logger");
 *     self.__version     = bsf.lookupBean ("version")
 *     self.__message     = None
 *
 * def processChannels (self):
 *     """
 *     Process the channels passed in through the Bean Scripting Framework.
 *     """
 *
 *     out = open (self.__outputPath, "w")
 *     msg = self.__config.getOptionalStringValue (self.__sectionName,
 *                                                 "Message",
 *                                                 None)
 *
 *     totalNew = 0
 *
 *     # First, count the total number of new items
 *
 *     iterator = self.__channels.iterator()
 *     while iterator.hasNext():
 *         channel_wrapper = iterator.next()
 *         channel = channel_wrapper.getChannel()
 *         totalNew = totalNew + channel.getItems().size()
 *
 *     if totalNew > 0:
 *         # If the config file specifies a message for this handler,
 *         # display it.
 *
 *         if msg != None:
 *             out.println (msg)
 *             out.println ()
 *
 *         # Now, process the items
 *
 *         iterator = self.__channels.iterator()
 *         while iterator.hasNext():
 *             channel_wrapper = iterator.next()
 *             channel = channel_wrapper.getChannel()
 *             feed_info = channel_wrapper.getFeedInfo()
 *             self.__process_channel (out, channel, feed_info, indentation)
 *
 *         self.__mimeTypeBuf.print ("text/plain")
 *
 *         # Output a footer
 *
 *         self.__indent (out, indentation)
 *         out.write ("\n")
 *         out.write (self.__version + "\n")
 *         out.close ()
 *
 * def process_channel (channel, feed_info):
 *     item_iterator = channel.getItems().iterator()
 *     while item_iterator.hasNext():
 *         # Do output for item
 *         ...
 *
 * main()
 * </pre>
 * </blockquote>
 *
 * @see org.clapper.curn.OutputHandler
 * @see FileOutputHandler
 * @see org.clapper.curn.Curn
 * @see org.clapper.curn.parser.RSSChannel
 *
 * @version <tt>$Revision$</tt>
 */
public class ScriptOutputHandler extends FileOutputHandler
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                               Inner Classes
    \*----------------------------------------------------------------------*/

    /**
     * Wraps an RSSChannel object and its FeedInfo object.
     */
    public class ChannelWrapper
    {
        private RSSChannel channel;
        private FeedInfo   feedInfo;

        ChannelWrapper(RSSChannel channel, FeedInfo feedInfo)
        {
            this.channel  = channel;
            this.feedInfo = feedInfo;
        }

        public RSSChannel getChannel()
        {
            return this.channel;
        }

        public FeedInfo getFeedInfo()
        {
            return this.feedInfo;
        }
    }

    /**
     * Type alias
     */
    private class ChannelList extends ArrayList<ChannelWrapper>
    {
        ChannelList()
        {
            super();
        }
    }

    /**
     * Container for the objects exported to the script.
     */
    public class CurnScriptObjects
    {
        public Collection channels      = null;
        public String     outputPath    = null;
        public CurnConfig config        = null;
        public String     configSection = null;
        public Logger     logger        = null;                       // NOPMD

        String mimeType = null;

        CurnScriptObjects()
        {
            // Nothing to do
        }

        public void setMIMEType(String mimeType)
        {
            this.mimeType = mimeType;
        }

        public String getVersion()
        {
            return Version.getInstance().getFullVersion();
        }
    }

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    private ScriptEngineManager        scriptManager      = null;
    private ScriptEngine               scriptEngine       = null;
    private Collection<ChannelWrapper> channels           = new ChannelList();
    private String                     scriptPath         = null;
    private String                     scriptString       = null;
    private String                     mimeType           = null;
    private String                     language           = null;
    private Logger                     scriptLogger       = null; // NOPMD
    private CurnScriptObjects          scriptObjects      = null;
    private boolean                    allowEmbeddedHTML  = false;

    /**
     * For logging
     */
    private static final Logger log = new Logger(ScriptOutputHandler.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct a new <tt>ScriptOutputHandler</tt>.
     */
    public ScriptOutputHandler()
    {
        // Nothing to do.
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Initializes the output handler for another set of RSS channels.
     *
     * @param config     the parsed <i>curn</i> configuration data
     * @param cfgHandler the <tt>ConfiguredOutputHandler</tt> wrapper
     *                   containing this object; the wrapper has some useful
     *                   metadata, such as the object's configuration section
     *                   name and extra variables.
     *
     * @throws ConfigurationException  configuration error
     * @throws CurnException           some other initialization error
     */
    public final void initOutputHandler(CurnConfig              config,
                                        ConfiguredOutputHandler cfgHandler)
        throws ConfigurationException,
               CurnException
    {
        // Parse handler-specific configuration variables

        String section = cfgHandler.getSectionName();

        try
        {
            if (section != null)
            {
                scriptPath = config.getConfigurationValue(section, "Script");
                language  = config.getConfigurationValue(section, "Language");
                allowEmbeddedHTML =
                    config.getOptionalBooleanValue
                        (section,
                         CurnConfig.CFG_ALLOW_EMBEDDED_HTML,
                         false);
            }
        }

        catch (NoSuchSectionException ex)
        {
            throw new ConfigurationException (ex);
        }

        // Verify that the script exists.

        File scriptFile = CurnUtil.mapConfiguredPathName (scriptPath);
        if (! scriptFile.exists())
        {
            scriptPath = null;
            throw new ConfigurationException(section,
                                             "Script file \"" +
                                             scriptFile.getPath() +
                                             "\" does not exist.");
        }

        if (! scriptFile.isFile())
        {
            scriptPath = null;
            throw new ConfigurationException(section,
                                             "Script file \"" +
                                             scriptFile.getPath() +
                                             "\" is not a regular file.");
        }

        // Allocate the script engine manager.

        try
        {
            scriptManager = new ScriptEngineManager();
        }

        catch (Throwable ex)
        {
            throw new CurnException(ex);
        }

        // Next, get the scripting engine itself.

        try
        {
            scriptEngine = scriptManager.getEngineByName(language);
        }

        catch (Throwable ex)
        {
            throw new CurnException("Unable to load scripting engine for \"" +
                                    language + "\" language",
                                    ex);
        }

        // Set up a logger for the script. The logger name can't have dots
        // in it, because the underlying logging API strips them out,
        // thinking they're class/package delimiters. That means we have to
        // strip the extension or change it to something else. Since the
        // extension conveys information (i.e., the language), we just
        // convert it to an underscore.

        StringBuilder scriptLoggerName = new StringBuilder(128);
        String scriptName = scriptFile.getName();
        scriptLoggerName.append(FileUtil.getFileNameNoExtension(scriptName));
        scriptLoggerName.append('_');
        scriptLoggerName.append(FileUtil.getFileNameExtension(scriptName));
        scriptLogger = new Logger(scriptLoggerName.toString());

        // Declare the script object. We'll fill it partially now; the rest
        // will be filled later. Also, for backward compatibility, register
        // individual BSF beans.

        this.scriptObjects = new CurnScriptObjects();
        try
        {
            scriptEngine.put("curn", scriptObjects);
        }

        catch (Throwable ex)
        {
            throw new CurnException ("Can't register script 'curn' object",
                                     ex);
        }

        scriptObjects.config = config;
        scriptObjects.configSection = section;
        scriptObjects.logger = scriptLogger;

        // Load the contents of the script into an in-memory buffer.

        scriptString = loadScript(scriptFile);

        channels.clear();
    }

    /**
     * Display the list of <tt>RSSItem</tt> news items to whatever output
     * is defined for the underlying class. This handler simply buffers up
     * the channel, so that {@link #flush} can pass all the channels to the
     * script.
     *
     * @param channel  The channel containing the items to emit. The method
     *                 should emit all the items in the channel; the caller
     *                 is responsible for clearing out any items that should
     *                 not be seen.
     * @param feedInfo Information about the feed, from the configuration
     *
     * @throws CurnException  unable to write output
     */
    public final void displayChannel(RSSChannel  channel,
                                     FeedInfo    feedInfo)
        throws CurnException
    {
        // Save the channel.

        if (! allowEmbeddedHTML)
            channel.stripHTML();

        channels.add (new ChannelWrapper (channel, feedInfo));
    }

    /**
     * Flush any buffered-up output.
     *
     * @throws CurnException  unable to write output
     */
    public final void flush() throws CurnException
    {
        try
        {
            // Put the channels and output path in the global object.

            scriptObjects.channels = channels;
            scriptObjects.outputPath = getOutputFile().getPath();

            // Run the script

            log.debug ("Invoking " + scriptPath);
            scriptEngine.eval(scriptString);

            // Handle the MIME type.

            mimeType = scriptObjects.mimeType;
        }

        catch (ScriptException ex)
        {
            Throwable realException = ex.getCause();
            if (ex == null)
                realException = ex;
            log.error ("Error interacting with scripting framework",
                       realException);
            throw new CurnException (Constants.BUNDLE_NAME,
                                     "ScriptOutputHandler.bsfError",
                                     "Error interacting with scripting " +
                                     "framework: {0}",
                                     new Object[] {ex.getMessage()},
                                     realException);
        }
    }

    /**
     * Get the content (i.e., MIME) type for output produced by this output
     * handler.
     *
     * @return the content type
     */
    public final String getContentType()
    {
        return mimeType;
    }

    /**
     * Register additional scripting language engines that are not
     * supported by this class. By default, this method does nothing.
     * Subclasses that wish to register additional BSF scripting engine
     * bindings should override this method and use
     * <tt>BSFManager.registerScriptingEngine()</tt> to register the
     * engined. See the class documentation, above, for additional details.
     *
     * @throws CurnException on error
     *
     * @deprecated as of <i>curn</i> 3.1.
     */
    public void registerAdditionalScriptingEngines()
        throws CurnException
    {
        // Nothing to do.
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Load the contents of the external script (any file, really) into an
     * in-memory buffer.
     *
     * @param scriptFile    the script file
     *
     * @return the string representing the loaded script
     *
     * @throws CurnException on error
     */
    private String loadScript (File scriptFile)
        throws CurnException
    {
        try
        {
            Reader       r = new BufferedReader (new FileReader (scriptFile));
            StringWriter w = new StringWriter();
            int          c;

            while ((c = r.read()) != -1)
                w.write (c);

            r.close();

            return w.toString();
        }

        catch (IOException ex)
        {
            throw new CurnException (Constants.BUNDLE_NAME,
                                     "ScriptOutputHandler.cantLoadScript",
                                     "Failed to load script \"{0}\" into " +
                                     "memory.",
                                     new Object[] {scriptFile.getPath()},
                                     ex);
        }
    }
}
