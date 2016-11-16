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

		if ( arguments.getDeploymentFile() != null ) {
			externalConfiguration = parseExternalConfiguration();

			String packageSelf = arguments.getPackageSelf();
			mainName = arguments.getEntryPoints().get( packageSelf ); // This won't work for dependencies
		} else if ( arguments.getPackageSelf() != null ) {
			// TODO Parse (default) configuration if --pkg-self is provided.
			// We need to parse default configuration if --pkg-self is passed. This essentially means that we're deploying
			// that package!

			// TODO Set mainName for when --pkg-self is provided
			String entryPoint = arguments.getEntryPoints().get( arguments.getPackageSelf() );
			if ( entryPoint == null ) {
				throw new CommandLineException( "Missing entry point for package. Was told that this package is: '" +
						arguments.getPackageSelf() + "'. Found no such entry point." );
			}
			mainName = entryPoint;
		}

		validateState();
	}

	private void validateState() throws CommandLineException
	{
		if ( mainName == null ) {
			throw new CommandLineException( "Missing main file" );
		}
	}

	private Map< String, Configuration > parseExternalConfiguration() throws IOException, ParserException
	{
		File file = new File( arguments.getDeploymentFile() );
		try ( FileInputStream fileInputStream = new FileInputStream( file ) ) {
			COLParser parser = new COLParser(
					new Scanner( fileInputStream, file.toURI(), arguments.getCharset() ),
					file.getParentFile()
			);
			ConfigurationTree parsedTree = parser.parse();
			System.out.println( "Parsed the appropriate configuration: " );
			System.out.println( parsedTree );
			return new ExternalConfigurationProcessor( parsedTree ).process();
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
}
