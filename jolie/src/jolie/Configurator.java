package jolie;

import jolie.configuration.Configuration;
import jolie.configuration.ExternalConfigurationProcessor;
import jolie.lang.Constants;
import jolie.lang.parse.COLParser;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.lang.parse.ast.ConfigurationTree;
import jolie.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Responsible for dealing with configuration based on the interpreter's arguments
 */
public class Configurator
{
	private final static Pattern OPTION_SEPARATOR_PATTERN = Pattern.compile( " " );
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
		String programFilePath = arguments.getProgramFilePath();
		mainName = programFilePath;

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

		if ( arguments.getPackageSelf() != null ) {
			parsePackageMainEntry();
			parseExtConfiguration();
		}

		validateState();
	}

	private void parseExtConfiguration() throws IOException, CommandLineException
	{
		File deploymentFile = null;
		if ( arguments.getDeploymentFile() == null ) {
			File defaultConfigFile = new File( getPackageRoot(), "default.col" );
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
				if ( configurationCache.containsKey( deploymentFile.getAbsolutePath() ) ) {
					externalConfiguration = configurationCache.get( deploymentFile.getAbsolutePath() );
				} else {
					try ( FileInputStream fileInputStream = new FileInputStream( deploymentFile ) ) {
						COLParser parser = new COLParser( new Scanner( fileInputStream, deploymentFile.toURI(),
								arguments.getCharset() ), deploymentFile.getParentFile() );
						try {
							ConfigurationTree parsedTree = parser.parse();
							ExternalConfigurationProcessor tree = new ExternalConfigurationProcessor( parsedTree );
							externalConfiguration = tree.process();
							configurationCache.put( deploymentFile.getAbsolutePath(), externalConfiguration );
						} catch ( ParserException ex ) {
							throw new IllegalStateException( "Unable to parse external configuration.", ex );
						}
					}
				}
			} else if ( arguments.getDeploymentFile() != null ) {
				throw new CommandLineException( "Could not find deployment file '" + deploymentFile + "'" );
			}
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

	private File getPackageRoot()
	{
		String packageSelf = arguments.getPackageSelf();
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
