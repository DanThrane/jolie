/***************************************************************************
 *   Copyright (C) 2008-2010 by Fabrizio Montesi <famontesi@gmail.com>     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import jolie.configuration.Configuration;
import jolie.lang.Constants;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.runtime.correlation.CorrelationEngine;
import jolie.util.Pair;

/**
 * A parser for JOLIE's command line arguments,
 * providing methods for accessing them.
 * @author Fabrizio Montesi
 */
public class CommandLineParser implements Closeable
{
	private final Arguments arguments;
	private final Configurator configurator;
	private final ProgramPaths paths;
	private final ClassLoader parentClassLoader;
	private URI programURI;
	private InputStream programStream;

	/**
	 * Constructor
	 * @param args the command line arguments
	 * @param parentClassLoader the ClassLoader to use for finding resources
	 * @throws jolie.CommandLineException if the command line is not valid or asks for simple information. (like --help and --version)
	 * @throws java.io.IOException
	 */
	public CommandLineParser( String[] args, ClassLoader parentClassLoader )
			throws CommandLineException, IOException
	{
		this( args, parentClassLoader, ArgumentHandler.DEFAULT_ARGUMENT_HANDLER );
	}

	/**
	 * Constructor
	 * @param args the command line arguments
	 * @param parentClassLoader the ClassLoader to use for finding resources
	 * @param argHandler
	 * @throws CommandLineException
	 * @throws IOException
	 */
	public CommandLineParser( String[] args, ClassLoader parentClassLoader, ArgumentHandler argHandler )
			throws CommandLineException, IOException
	{
		this(args, parentClassLoader, argHandler, false);
	}

	/**
	 * Constructor
	 * @param args the command line arguments
	 * @param parentClassLoader the ClassLoader to use for finding resources
	 * @param ignoreFile do not open file that is given as parameter (used for internal services)
	 * @throws CommandLineException
	 * @throws IOException
	 */
	public CommandLineParser( String[] args, ClassLoader parentClassLoader, boolean ignoreFile )
			throws CommandLineException, IOException
	{
		this( args, parentClassLoader, ArgumentHandler.DEFAULT_ARGUMENT_HANDLER, ignoreFile );
	}

	/**
	 * Constructor
	 * @param args the command line arguments
	 * @param parentClassLoader the ClassLoader to use for finding resources
	 * @param argHandler
	 * @param ignoreFile do not open file that is given as parameter (used for internal services)
	 * @throws CommandLineException
	 * @throws IOException
	 */
	public CommandLineParser( String[] args, ClassLoader parentClassLoader, ArgumentHandler argHandler,
							  boolean ignoreFile )
			throws CommandLineException, IOException
	{
		this.parentClassLoader = parentClassLoader;
		arguments = new Arguments();
		arguments.init( this, argHandler );
		arguments.parse( args );
		configurator = new Configurator( arguments );
		try {
			configurator.configure();
		} catch ( ParserException e ) {
			throw new CommandLineException( e.getMessage() );
		}
		paths = new ProgramPaths( arguments, configurator, parentClassLoader );
		paths.configure();

		if ( !ignoreFile ) {
			Pair< URI, InputStream > program = paths.openProgram();
			programURI = program.key();
			programStream = new BufferedInputStream( program.value() );
		} else {
			// I'm really not sure what the correct values would be here
			programURI = null;
			programStream = new ByteArrayInputStream( new byte[]{} );
		}
	}

	/**
	 * Constructor used for cloning an existing CommandLineParser instance with new argument.s
	 *
	 * @param arguments       The arguments instance
	 * @param configurator    The configurator instance
	 * @param paths           The paths instance
	 * @throws IOException
	 */
	public CommandLineParser( Arguments arguments, Configurator configurator, ProgramPaths paths,
							  ClassLoader parentClassLoader ) throws IOException
	{
		this.arguments = arguments;
		this.configurator = configurator;
		this.paths = paths;
		this.parentClassLoader = parentClassLoader;
	}

	public CommandLineParser makeCopy( Consumer< Arguments > argumentsConsumer )
			throws IOException, CommandLineException
	{
		ClassLoader classLoader = parentClassLoader;

		Arguments arguments = this.arguments.makeCopy();
		Configurator configurator = new Configurator( arguments );
		ProgramPaths paths = new ProgramPaths( arguments, configurator, classLoader );
		CommandLineParser result = new CommandLineParser( arguments, configurator, paths, classLoader );

		arguments.init( result, ArgumentHandler.DEFAULT_ARGUMENT_HANDLER );
		argumentsConsumer.accept( arguments );

		try {
			configurator.configure();
		} catch ( ParserException e ) {
			throw new CommandLineException( e.getMessage() );
		}
		paths.configure();

		Pair< URI, InputStream > program = paths.openProgram();
		result.programURI = program.key();
		result.programStream = program.value();
		return result;
	}

	/**
	 * Returns the arguments passed to the JOLIE program.
	 * @return the arguments passed to the JOLIE program.
	 */
	public String[] arguments()
	{
		return arguments.getProgramArguments().toArray( new String[0] );
	}
	
	/**
	 * Returns the {@link Level} of the logger of this interpreter.
	 * @return the {@link Level} of the logger of this interpreter.
	 */
	public Level logLevel()
	{
		return arguments.getLogLevel();
	}
	
	/**
	 * Returns
	 * <code>true</code> if the tracer option has been specified, false
	 * otherwise.
	 *
	 * @return <code>true</code> if the verbose option has been specified, false
	 * otherwise
	 */
	public boolean tracer()
	{
		return arguments.isTracer();
	}

	/**
	 * Returns
	 * <code>true</code> if the check option has been specified, false
	 * otherwise.
	 *
	 * @return <code>true</code> if the verbose option has been specified, false
	 * otherwise
	 */
	public boolean check()
	{
		return arguments.isCheck();
	}

	/**
	 * Returns {@code true} if the program is compiled, {@code false} otherwise.
	 * @return {@code true} if the program is compiled, {@code false} otherwise.
	 */
	public boolean isProgramCompiled()
	{
		return configurator.getMainName().endsWith( ".olc" );
	}
    
	/**
	 * Returns the file path of the JOLIE program to execute.
	 * @return the file path of the JOLIE program to execute
	 * @deprecated Use {@link CommandLineParser#programFileURI()} instead
	 */
	@Deprecated
	public File programFilepath()
	{
		if (programURI == null) {
			return null;
		}
		return new File( programURI );
	}

	public String getProgramName()
	{
		if ( programURI == null ) {
			return "internal";
		} else {
			return programURI.getPath();
		}
	}

	public URI programFileURI() {
		return programURI;
	}

	/**
	 * Returns an InputStream for the program code to execute.
	 * @return an InputStream for the program code to execute
	 */
	public InputStream programStream()
	{
		return programStream;
	}

	/**
	 * Returns the program's character encoding
	 * @return the program's character encoding
	 */
	public String charset()
	{
		return arguments.getCharset();
	}
	
	/**
	 * Closes the underlying {@link InputStream} to the target Jolie program.
	 */
	public void close()
		throws IOException
	{
		programStream.close();
	}

	/**
	 * Returns the library URLs passed by command line with the -l option.
	 * @return the library URLs passed by command line
	 */
	public URL[] libURLs()
	{
		return paths.getLibURLs();
	}

	/**
	 * Returns the include paths passed by command line with the -i option.
	 * @return the include paths passed by command line
	 */
	public String[] includePaths()
	{
		return paths.getIncludePaths().stream()
				.map( File::getAbsolutePath )
				.collect( Collectors.toList() )
				.toArray( new String[0] );
	}

	/**
	 * Returns the connection limit parameter
	 * passed by command line with the -c option.
	 * @return the connection limit parameter passed by command line
	 */
	public int connectionsLimit()
	{
		return arguments.getConnectionsLimit();
	}

	/**
	 * Returns the connection cache parameter
	 * passed by command line with the --conncache option.
	 * @return the connection cache parameter passed by command line
	 */
	public int connectionsCache()
	{
		return arguments.getConnectionsCache();
	}
	
	private static String getOptionString( String option, String description )
	{
		return( '\t' + option + "\t\t" + description + '\n' );
	}
	
	private String getVersionString()
	{
		return( Constants.VERSION + "  " + Constants.COPYRIGHT );
	}

	/**
	 * Returns a map containing the constants defined by command line.
	 * @return a map containing the constants defined by command line
	 */
	public Map< String, Object > definedConstants()
	{
		return arguments.getConstants();
	}

	/**
	 * Returns the usage help message of Jolie.
	 * @return the usage help message of Jolie.
	 */
	protected String getHelpString()
	{
		StringBuilder helpBuilder = new StringBuilder();
		helpBuilder.append( getVersionString() );
		helpBuilder.append( "\n\nUsage: jolie [options] behaviour_file [program arguments]\n\n" );
		helpBuilder.append( "Available options:\n" );
		helpBuilder.append(
				getOptionString( "-h, --help", "Display this help information" ) );
		//TODO include doc for -l and -i
		helpBuilder.append( getOptionString( "-C ConstantIdentifier=ConstantValue", "Sets constant ConstantIdentifier to ConstantValue before starting execution \n"
							+ "-C ConstantIdentifier=ConstantValue".replaceAll("(.)"," ") + "\t\t\t"
							+ "(under Windows use quotes or double-quotes, e.g., -C \"ConstantIdentifier=ConstantValue\" )" ) );
		helpBuilder.append(
				getOptionString( "--connlimit [number]", "Set the maximum number of active connection threads" ) );
		helpBuilder.append(
				getOptionString( "--conncache [number]", "Set the maximum number of cached persistent output connections" ) );
		helpBuilder.append(
				getOptionString( "--correlationAlgorithm [simple|hash]", "Set the algorithm to use for message correlation" ) );
		helpBuilder.append(
				getOptionString( "--log [severe|warning|info|fine]", "Set the logging level (default: info)" ) );
		helpBuilder.append(
				getOptionString( "--typecheck [true|false]", "Check for correlation and other data related typing errors (default: false)" ) );
                helpBuilder.append(
				getOptionString( "--check", "Check for syntactic and semantic errors." ) );
		helpBuilder.append(
				getOptionString( "--trace", "Activate tracer" ) );
		helpBuilder.append(
				getOptionString( "--charset [character encoding, eg. UTF-8]", "Character encoding of the source *.ol/*.iol (default: system-dependent, on GNU/Linux UTF-8)" ) );
		helpBuilder.append(
				getOptionString( "--version", "Display this program version information" ) );
		return helpBuilder.toString();
	}

	/**
	 * Returns the type of correlation algorithm that has been specified.
	 * @return the type of correlation algorithm that has been specified.
	 * @see CorrelationEngine
	 */
	public CorrelationEngine.Type correlationAlgorithmType()
	{
		return arguments.getCorrelationAlgorithmType();
	}

	/**
	 * Returns the directory in which the main program is located.
	 * @return the directory in which the main program is located.
	 */
	public File programDirectory()
	{
		// TODO Need a clear definition of this. How to handle URLs?
		if ( programURI != null && programURI.getScheme().equals( "file" ) ) {
			File file = new File( programURI );
			return file.getParentFile();
		}
		return paths.locateServiceRoot();
	}

	/**
	 * Returns the value of the --typecheck option.
	 * @return the value of the --typecheck option.
	 */
	public boolean typeCheck()
	{
		return arguments.isTypeCheck();
	}

	/**
	 * Returns the classloader to use for the program.
	 * @return the classloader to use for the program.
	 */
	public JolieClassLoader jolieClassLoader()
	{
		return paths.getJolieClassLoader();
	}

	/**
	 * Returns the command line options passed to this command line parser.
	 * This does not include the name of the program.
	 * 
	 * @return the command line options passed to this command line parser.
	 */
	public String[] optionArgs()
	{
		return arguments.getOptionsList().toArray( new String[0] );
	}

	public String deploymentProfile()
	{
		return arguments.getDeploymentProfile();
	}

	public String deploymentFile()
	{
		return arguments.getDeploymentFile();
	}

	public boolean isRunningFromDeploymentConfiguration()
	{
		return deploymentFile() != null && deploymentProfile() != null;
	}

	public String packageLocation()
	{
		return arguments.getPackageLocation();
	}

	public Map< String, Configuration > getExternalConfiguration()
	{
		return configurator.getExternalConfiguration();
	}

	public ClassLoader getParentClassLoader()
	{
		return parentClassLoader;
	}

	/**
	 * A handler for unrecognized arguments, meant to be implemented
	 * by classes that wants to extend the behaviour of {@link jolie.CommandLineParser}.
	 * @author Fabrizio Montesi
	 */
	public interface ArgumentHandler
	{
		/**
		 * Called when {@link CommandLineParser} cannot recognize a command line argument.
		 * @param argumentsList the argument list.
		 * @param index the index at which the unrecognized argument has been found in the list.
		 * @return the new index at which the {@link CommandLineParser} should continue parsing the arguments.
		 * @throws CommandLineException if the argument is invalid or not recognized.
		 */
		int onUnrecognizedArgument( List< String > argumentsList, int index )
			throws CommandLineException;

		/**
		 * Default {@link ArgumentHandler}. It just throws a {@link CommandLineException} when it finds an unrecognised option.
		 */
		ArgumentHandler DEFAULT_ARGUMENT_HANDLER =
				( argumentsList, index ) -> {
					throw new CommandLineException( "Unrecognized command line option: " + argumentsList.get( index ) );
				};
	}
}
