package jolie;

import jolie.lang.Constants;
import jolie.lang.parse.Scanner;
import jolie.runtime.correlation.CorrelationEngine;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Represents the arguments that an interpreter was started with. This class also provides raw CLI parsing.
 */
public class Arguments
{
	private final static Pattern PATH_SEPARATOR_PATTERN = Pattern.compile( jolie.lang.Constants.pathSeparator );

	private int connectionsLimit = -1;
	private int connectionsCache = 100;
	private CorrelationEngine.Type correlationAlgorithmType = CorrelationEngine.Type.SIMPLE;
	private List< String > optionsList = new ArrayList<>();
	private List< String > programArguments = new ArrayList<>();
	private String charset = null;
	private Map< String, Scanner.Token > constants = new HashMap< String, Scanner.Token >();
	private boolean typeCheck = false;
	private boolean tracer = false;
	private boolean check = false;
	private String programFilePath = null;
	private Level logLevel = Level.INFO;
	private String packageRoot = null;
	private String deploymentProfile = null;
	private String deploymentFile = null;
	private String packageLocation = null;
	private String packageSelf = null;
	private Map< String, String > entryPoints = new HashMap<>();
	private List< String > includeList = new ArrayList<>();
	private List< String > libList = new ArrayList<>();

	private final CommandLineParser cli;
	private final CommandLineParser.ArgumentHandler argHandler;

	public Arguments( CommandLineParser cli, CommandLineParser.ArgumentHandler argHandler )
	{
		this.cli = cli;
		this.argHandler = argHandler;
	}

	public void parse( String[] args )
			throws CommandLineException, IOException
	{
		List< String > argsList = new ArrayList<>( args.length );
		Collections.addAll( argsList, args );

		List< String > optionsList = new ArrayList<>();
		List< String > programArgumentsList = new ArrayList<>();
		int i = addOptions( 0, argsList );
		i++;

		// Now parse the command line programArguments for the Jolie program
		for ( ; i < argsList.size() && programFilePath != null; i++ ) {
			programArgumentsList.add( argsList.get( i ) );
		}
		programArguments.addAll( programArgumentsList );
	}

	public int addOptions( int i, List< String > args ) throws CommandLineException
	{
		// First parse Jolie programArguments with the Jolie program argument
		for ( ; i < args.size(); i++ ) {
			if ( "--help".equals( args.get( i ) ) || "-h".equals( args.get( i ) ) ) {
				throw new CommandLineException( cli.getHelpString() );
			} else if ( "-C".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				try {
					parseCommandLineConstant( args.get( i ) );
				} catch ( IOException e ) {
					throw new CommandLineException( "Invalid constant definition, reason: " + e.getMessage() );
				}
				optionsList.add( args.get( i ) );
			} else if ( "-i".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				Collections.addAll( includeList, PATH_SEPARATOR_PATTERN.split( args.get( i ) ) );
				optionsList.add( args.get( i ) );
			} else if ( "-l".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				Collections.addAll( libList, PATH_SEPARATOR_PATTERN.split( args.get( i ) ) );
				optionsList.add( args.get( i ) );
			} else if ( "--connlimit".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				connectionsLimit = Integer.parseInt( args.get( i ) );
				optionsList.add( args.get( i ) );
			} else if ( "--conncache".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				connectionsCache = Integer.parseInt( args.get( i ) );
				optionsList.add( args.get( i ) );
			} else if ( "--correlationAlgorithm".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;

				String csetAlgorithmName = args.get( i );
				correlationAlgorithmType = CorrelationEngine.Type.fromString( csetAlgorithmName );
				if ( correlationAlgorithmType == null ) {
					throw new CommandLineException( "Unrecognized correlation algorithm: " + csetAlgorithmName );
				}
				optionsList.add( args.get( i ) );
			} else if ( "--typecheck".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				String typeCheckStr = args.get( i );
				optionsList.add( args.get( i ) );
				if ( "false".equals( typeCheckStr ) ) {
					typeCheck = false;
				} else if ( "true".equals( typeCheckStr ) ) {
					typeCheck = true;
				}
			} else if ( "--check".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				check = true;
			} else if ( "--trace".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				tracer = true;
			} else if ( "--log".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				logLevel = getLevel( args.get( i ) );
				optionsList.add( args.get( i ) );
			} else if ( "--charset".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				charset = args.get( i );
				optionsList.add( args.get( i ) );
			} else if ( "--version".equals( args.get( i ) ) ) {
				throw new CommandLineException( getVersionString() );
			} else if ( "--deploy".equals( args.get( i ) ) ) {
				List< String > deployArgs = parseOptionsFromIndex( i, args, 2, "--deploy <profile> <file>" );
				// TODO Use this? Or write something else
				deploymentProfile = deployArgs.get( 0 );
				deploymentFile = deployArgs.get( 1 );
				i += 2;
				break;
			} else if ( "--pkg-root".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				optionsList.add( args.get( i ) );
				packageRoot = args.get( i );
			} else if ( "--pkg-folder".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				optionsList.add( args.get( i ) );
				packageLocation = args.get( i );
			} else if ( "--pkg-self".equals( args.get( i ) ) ) {
				optionsList.add( args.get( i ) );
				i++;
				optionsList.add( args.get( i ) );
				packageSelf = args.get( i );
				break;
			} else if ( args.get( i ).startsWith( "--main." ) ) {
				optionsList.add( args.get( i ) );
				String packageName = args.get( i ).substring( "--main.".length() );
				i++;
				optionsList.add( args.get( i ) );
				String entryPoint = args.get( i );
				entryPoints.put( packageName, entryPoint );
			} else if ( isArgumentJolieProgram( args, i ) ) {
				programFilePath = args.get( i );
				break;
			} else {
				// It's an unrecognized argument
				int newIndex = argHandler.onUnrecognizedArgument( args, i );
				if ( newIndex == i ) {
					// The handler didn't change the index.
					// We abort so to avoid infinite looping.
					throw new CommandLineException( "Unrecognized command line option: " + args.get( i ) );
				}
				i = newIndex;
			}
		}
		return i;
	}

	private List< String > parseOptionsFromIndex( int i, List< String > arguments, int requiredOptions, String usage )
			throws CommandLineException
	{
		if ( arguments.size() < i + requiredOptions ) {
			throw new CommandLineException( "Not enough arguments. Usage: " + usage );
		}
		optionsList.add( arguments.get( i ) );
		i++;

		List< String > result = new ArrayList<>();
		for ( int offset = 0; offset < requiredOptions; offset++ ) {
			String argument = arguments.get( i + offset );
			optionsList.add( argument );
			result.add( argument );
		}
		return result;
	}

	private Level getLevel( String argument ) throws CommandLineException
	{
		switch ( argument ) {
			case "severe":
				return Level.SEVERE;
			case "warning":
				return Level.WARNING;
			case "fine":
				return Level.FINE;
			case "info":
				return Level.INFO;
			default:
				throw new CommandLineException( "Unknown log level: '" + argument + "'" );
		}
	}

	private boolean isArgumentJolieProgram( List< String > argsList, int i )
	{
		String s = argsList.get( i );
		return s.endsWith( ".ol" ) || s.endsWith( ".iol" ) || s.endsWith( ".olc" ) || s.endsWith( ".jap" );
	}

	private void parseCommandLineConstant( String input )
			throws IOException
	{
		try {
			// for command line options use the system's default charset (null)
			Scanner scanner = new Scanner( new ByteArrayInputStream( input.getBytes() ), new URI( "urn:CommandLine" ), null );
			Scanner.Token token = scanner.getToken();
			if ( token.is( Scanner.TokenType.ID ) ) {
				String id = token.content();
				token = scanner.getToken();
				if ( token.isNot( Scanner.TokenType.ASSIGN ) ) {
					throw new IOException( "expected = after constant identifier " + id + ", found token type " + token.type() );
				}
				token = scanner.getToken();
				if ( !token.isValidConstant() ) {
					throw new IOException( "expected constant value for constant identifier " + id + ", found token type " + token.type() );
				}
				constants.put( id, token );
			} else {
				throw new IOException( "expected constant identifier, found token type " + token.type() );
			}
		} catch ( URISyntaxException e ) {
			throw new IOException( e );
		}
	}

	private String getVersionString()
	{
		return ( Constants.VERSION + "  " + Constants.COPYRIGHT );
	}

	public int getConnectionsLimit()
	{
		return connectionsLimit;
	}

	public int getConnectionsCache()
	{
		return connectionsCache;
	}

	public CorrelationEngine.Type getCorrelationAlgorithmType()
	{
		return correlationAlgorithmType;
	}

	public List< String > getOptionsList()
	{
		return optionsList;
	}

	public String getCharset()
	{
		return charset;
	}

	public List< String > getProgramArguments()
	{
		return programArguments;
	}

	public Map< String, Scanner.Token > getConstants()
	{
		return constants;
	}

	public boolean isTypeCheck()
	{
		return typeCheck;
	}

	public boolean isTracer()
	{
		return tracer;
	}

	public boolean isCheck()
	{
		return check;
	}

	public Level getLogLevel()
	{
		return logLevel;
	}

	public String getDeploymentProfile()
	{
		return deploymentProfile;
	}

	public String getDeploymentFile()
	{
		return deploymentFile;
	}

	public String getPackageLocation()
	{
		return packageLocation;
	}

	public String getPackageSelf()
	{
		return packageSelf;
	}

	public Map< String, String > getEntryPoints()
	{
		return entryPoints;
	}

	public String getProgramFilePath()
	{
		return programFilePath;
	}

	public List< String > getIncludeList()
	{
		return includeList;
	}

	public List< String > getLibList()
	{
		return libList;
	}

	public String getPackageRoot()
	{
		return packageRoot;
	}
}
