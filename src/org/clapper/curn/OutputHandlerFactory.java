/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.curn;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.clapper.util.config.ConfigurationException;

/**
 * This class provides a factory for retrieving a specific
 * <tt>OutputHandler</tt> implementation.
 *
 * @see OutputHandler
 *
 * @version <tt>$Revision$</tt>
 */
public class OutputHandlerFactory
{
    /*----------------------------------------------------------------------*\
                            Instance Data Items
    \*----------------------------------------------------------------------*/

    /**
     * For log messages
     */
    private static Logger log = new Logger (OutputHandlerFactory.class);

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Get an instance of the named <tt>OutputHandler</tt> class. This
     * method loads the specified class, verifies that it conforms to the
     * {@link OutputHandler} interface, instantiates it (via its default
     * constructor), and returns the resulting <tt>OutputHandler</tt> object.
     *
     * @param cls  the class for the output handler
     *
     * @return an <tt>OutputHandler</tt> object
     *
     * @throws ConfigurationException Error instantiating class. The
     *                                exception will contain (i.e., nest)
     *                                the real underlying exception.
     *                                (<tt>ConfigurationException</tt> is
     *                                thrown because it's unlikely to get
     *                                here unless an incorrect class name
     *                                was placed in the config file.)
     */
    public static OutputHandler getOutputHandler (Class cls)
        throws ConfigurationException
    {
        try
        {
            log.debug ("Instantiating output handler: " + cls.getName());

            Constructor constructor = cls.getConstructor (null);
            return (OutputHandler) constructor.newInstance (null);
        }

        catch (NoSuchMethodException ex)
        {
            throw new ConfigurationException (ex);
        }

        catch (InvocationTargetException ex)
        {
            throw new ConfigurationException (ex);
        }

        catch (InstantiationException ex)
        {
            throw new ConfigurationException (ex);
        }

        catch (IllegalAccessException ex)
        {
            throw new ConfigurationException (ex);
        }
    }

    /**
     * Get an instance of the named <tt>OutputHandler</tt> class. This
     * method loads the specified class, verifies that it conforms to the
     * {@link OutputHandler} interface, instantiates it (via its default
     * constructor), and returns the resulting <tt>OutputHandler</tt> object.
     *
     * @param className  the class name
     *
     * @return an <tt>OutputHandler</tt> object
     *
     * @throws ConfigurationException Error instantiating class. The
     *                                exception will contain (i.e., nest)
     *                                the real underlying exception.
     *                                (<tt>ConfigurationException</tt> is
     *                                thrown because it's unlikely to get
     *                                here unless an incorrect class name
     *                                was placed in the config file.)
     */
    public static OutputHandler getOutputHandler (String className)
        throws ConfigurationException
    {
        try
        {
            return getOutputHandler (Class.forName (className));
        }

        catch (ClassNotFoundException ex)
        {
            throw new ConfigurationException (ex);
        }
    }
}
