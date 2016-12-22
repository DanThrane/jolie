package jolie;

import jolie.configuration.Configuration;
import jolie.configuration.ExternalConfigurationProcessor;
import jolie.lang.Constants;
import jolie.lang.parse.COLParser;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.lang.parse.ast.ConfigurationTree;
import jolie.util.Helpers;
import jolie.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Responsible for dealing with configuration based on the interpreter's arguments
 */
public class Configurator
{
	private final static Pattern OPTION_SEPARATOR_PATTERN = Pattern.compile( " " );
	public static final String DEFAULT_COL = "default.col";
	private final Arguments arguments;

	private String mainName;
	private Map< String, Configuration > externalConfiguration = new HashMap<>();

	private static Map< String, Map< String, Configuration > > configurationCache = new HashMap<>();

	public Configurator( Arguments arguments )
	{
		this.arguments = arguments;
	}

	public void configure() throws IOException, CommandLineException, ParserException
	{
		// TODO General verification

		String programFilePath = arguments.getProgramFilePath();
		mainName = programFilePath; // TODO Should we be setting this on the arguments instance instead?

		if ( programFilePath != null && programFilePath.endsWith( ".jap" ) ) {
			JarFile japFile = new JarFile( programFilePath );
			Manifest manifest = japFile.getManifest();
			String japMain = parseJapManifestForMainProgram( manifest, japFile );
			if ( Helpers.getOperatingSystemType() == Helpers.OSType.Windows ) {
				japMain = japMain.replace( "\\", "/" );
			}
			mainName = japMain;

			List< String > japOptions = parseJapManifestForOptions( manifest );
			arguments.addOptions( 0, japOptions );
		}

		if ( hasInternalDeployment() ) {
			externalConfiguration = arguments.getInternalConfiguration();
		} else if ( hasDeploymentFromFile() ) {
			// Parsing ext config will set packageSelf if we're deploying from a file without --pkg-self
			parseExtConfigurationFromFile();
		}

		if ( hasDeployment() ) {
			validateConfiguration();
			parsePackageMainEntry();
		}
		validateState();
	}

	private boolean hasDeployment()
	{
		return hasDeploymentFromFile() || hasInternalDeployment();
	}

	private boolean hasInternalDeployment()
	{
		return arguments.getInternalConfiguration() != null;
	}

	private boolean hasDeploymentFromFile()
	{
		return arguments.getPackageSelf() != null || arguments.getDeploymentFile() != null;
	}

	private void parseExtConfigurationFromFile() throws IOException, CommandLineException
	{
		File deploymentFile = null;
		if ( arguments.getDeploymentFile() == null ) {
			File defaultConfigFile = new File( getPackageRoot( arguments.getPackageSelf() ), DEFAULT_COL );
			if ( defaultConfigFile.exists() ) {
				deploymentFile = defaultConfigFile;
			}
			// Needed if we make a copy of the arguments. We should still read configuration from the same tree.
			arguments.setDeploymentFile( defaultConfigFile.getAbsolutePath() );
		} else {
			deploymentFile = new File( arguments.getDeploymentFile() );
		}

		if ( deploymentFile != null ) {
			if ( deploymentFile.exists() ) {
				externalConfiguration = parseAndProcessConfigurationFile( deploymentFile );
			} else if ( arguments.getDeploymentFile() != null ) {
				throw new CommandLineException( "Could not find deployment file '" + deploymentFile + "'" );
			}
		}
	}

	private void validateConfiguration()
	{
		if ( externalConfiguration == null ) return;

		String deploymentProfile = arguments.getDeploymentProfile();
		Configuration deployedConfigUnit = externalConfiguration.get( deploymentProfile );

		if ( deployedConfigUnit == null ) {
			throw new IllegalStateException( "Unable to find configuration profile '" +
					deploymentProfile + "'" );
		}

		String actualSelf = deployedConfigUnit.getPackageName();
		String expectedSelf = arguments.getPackageSelf();
		if ( expectedSelf != null ) {
			if ( !expectedSelf.equals( actualSelf ) ) {
				throw new IllegalStateException( "--pkg-self does not match the package name of the " +
						"configuration unit. From --pkg-self I got '" + expectedSelf + "'. But the used " +
						"configuration unit has the package '" + actualSelf + "'" );
			}
		} else {
			arguments.setPackageSelf( actualSelf );
		}
	}

	private Map< String, Configuration > parseAndProcessConfigurationFile( File configFile )
	{
		String path = configFile.getAbsolutePath();
		if ( configurationCache.containsKey( path ) ) {
			return configurationCache.get( path );
		} else {
			try ( FileInputStream fileInputStream = new FileInputStream( configFile ) ) {
				COLParser parser = new COLParser( new Scanner( fileInputStream, configFile.toURI(),
						arguments.getCharset() ), configFile.getParentFile() );
				try {
					ConfigurationTree parsedTree = parser.parse();
					ExternalConfigurationProcessor tree = new ExternalConfigurationProcessor( parsedTree );
					Map< String, Configuration > config = tree.process();

					if ( configFile.getName().equals( DEFAULT_COL ) ) {
						validateDefaultConfigUnit( config );
					}

					// TODO Should always validate default unit
					Map< String, Configuration > mergedConfiguration = mergeConfigurationUnitWithDefaults( configFile,
							config );
					configurationCache.put( path, mergedConfiguration );
					return mergedConfiguration;
				} catch ( ParserException ex ) {
					throw new IllegalStateException( "Unable to parse external configuration.", ex );
				}
			} catch ( IOException e ) {
				throw new RuntimeException( e );
			}
		}
	}

	private Map< String, Configuration > mergeConfigurationUnitWithDefaults( File configFile,
																			 Map< String, Configuration > config )
	{
		return config.values().stream().map( ( configUnit ) -> {
			File file = new File( getPackageRoot( configUnit.getPackageName() ), DEFAULT_COL );
			if ( !file.exists() || file.equals( configFile ) ) {
				return new Pair<>( configUnit.getProfileName(), configUnit );
			}

			Map< String, Configuration > defaults = parseAndProcessConfigurationFile( file );
			validateDefaultConfigUnit( defaults );

			Configuration mergedUnit = Configuration.merge( configUnit, defaults.get( "default" ) );
			return new Pair<>( configUnit.getProfileName(), mergedUnit );
		} ).collect( Collectors.toMap( Pair::key, Pair::value ) );
	}

	private void validateDefaultConfigUnit( Map< String, Configuration > defaults )
	{
		if ( defaults.size() != 1 || !defaults.containsKey( "default" )) {
			throw new IllegalStateException( "Default configuration files only allow a single " +
					"configuration unit named 'default'" );
		}
	}

	private void parsePackageMainEntry() throws CommandLineException
	{
		String entryPoint = arguments.getEntryPoints().get( arguments.getPackageSelf() );
		if ( entryPoint == null ) {
			throw new CommandLineException( "Missing entry point for package. Was told that this package is: '" +
					arguments.getPackageSelf() + "'. Found no such entry point." );
		}
		mainName = entryPoint;
	}

	private File getPackageRoot( String packageSelf )
	{
		if ( packageSelf == null ) { // Old behavior
			return new File( "." );
		}

		String packageRoot = arguments.getPackageRoot();
		if ( packageSelf.equals( packageRoot ) ) {
			return new File( "." ); // We're launching the service at the working directory
		} else {
			// Launching a dependency (from the dependency folder)
			return new File( arguments.getPackageLocation(), packageSelf );
		}
	}

	private void validateState() throws CommandLineException
	{
		if ( mainName == null ) {
			throw new CommandLineException( "Missing main file" );
		}
	}

	private String parseJapManifestForMainProgram( Manifest manifest, JarFile japFile )
	{
		String filepath = null;
		if ( manifest != null ) { // See if a main program is defined through a Manifest attribute
			Attributes attrs = manifest.getMainAttributes();
			filepath = attrs.getValue( Constants.Manifest.MAIN_PROGRAM );
		}

		if ( filepath == null ) { // Main program not defined, we make <japName>.ol and <japName>.olc guesses
			String name = new File( japFile.getName() ).getName();
			filepath = new StringBuilder()
					.append( name.subSequence( 0, name.lastIndexOf( ".jap" ) ) )
					.append( ".ol" )
					.toString();
			if ( japFile.getEntry( filepath ) == null ) {
				filepath = null;
				filepath = filepath + 'c';
				if ( japFile.getEntry( filepath ) == null ) {
					filepath = null;
				}
			}
		}

		if ( filepath != null ) {
			filepath = new StringBuilder()
					.append( "jap:file:" )
					.append( japFile.getName() )
					.append( "!/" )
					.append( filepath )
					.toString();
		}
		return filepath;
	}

	private List< String > parseJapManifestForOptions( Manifest manifest )
			throws IOException
	{
		List< String > optionList = new ArrayList<>();
		if ( manifest != null ) {
			Attributes attrs = manifest.getMainAttributes();
			String options = attrs.getValue( Constants.Manifest.OPTIONS );
			if ( options != null ) {
				String[] tmp = OPTION_SEPARATOR_PATTERN.split( options );
				Collections.addAll( optionList, tmp );
			}
		}
		return optionList;
	}

	public String getMainName()
	{
		return mainName;
	}

	public Map< String, Configuration > getExternalConfiguration()
	{
		return externalConfiguration;
	}
}
