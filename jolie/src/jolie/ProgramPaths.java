package jolie;

import jolie.lang.Constants;
import jolie.util.Pair;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for configuring program paths, this includes:
 * <p>
 * <ul>
 * <li>Class path</li>
 * <li>Include path</li>
 * <li>Opening main file</li>
 * </ul>
 */
public class ProgramPaths
{
	public static final String DIRECTORY_LIB = "lib";
	public static final String DIRECTORY_INCLUDE = "include";
	public static final String DIRECTORY_EXT = "ext";

	private final Arguments arguments;
	private final Configurator configurator;
	private final ClassLoader parentClassLoader;

	private URL[] libURLs;
	private JolieClassLoader jolieClassLoader;
	private Deque< File > includePaths = new LinkedList<>();

	public ProgramPaths( Arguments arguments, Configurator configurator, ClassLoader parentClassLoader )
	{
		this.arguments = arguments;
		this.configurator = configurator;
		this.parentClassLoader = parentClassLoader;
	}

	public void configure() throws IOException
	{
		configureIncludePath();
		configureClassPath();
	}

	private void configureIncludePath()
	{
		File root = locateServiceRoot();
		includePaths.add( root );
		File include = new File( root, DIRECTORY_INCLUDE );
		if ( include.exists() ) {
			includePaths.add( include );
		}

		includePaths.addAll( arguments.getIncludeList().stream().map( File::new ).collect( Collectors.toList() ) );
	}

	public File locateServiceRoot()
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

	private void configureClassPath() throws IOException
	{
		Set< URL > urls = new HashSet<>();
		List< String > liblist = new ArrayList<>();
		liblist.addAll( arguments.getLibList() );
		liblist.addAll(
				getDefaultClassPathIncludes()
						.stream()
						.map( File::getAbsolutePath )
						.collect( Collectors.toList() )
		);

		for ( String path : liblist ) {
			if ( path.contains( "!/" ) && !path.startsWith( "jap:" ) && !path.startsWith( "jar:" ) ) {
				path = "jap:file:" + path;
			}
			if ( path.endsWith( ".jar" ) || path.endsWith( ".jap" ) ) {
				if ( path.startsWith( "jap:" ) ) {
					urls.add( new URL( path + "!/" ) );
				} else {
					urls.add( new URL( "jap:file:" + path + "!/" ) );
				}
			} else if ( new File( path ).isDirectory() ) {
				urls.add( new URL( "file:" + path + "/" ) );
			} else if ( path.endsWith( Constants.fileSeparator + "*" ) ) {
				File dir = new File( path.substring( 0, path.length() - 2 ) );
				extractAndAddJarUrlsFromDirectory( urls, dir );
			} else {
				try {
					urls.add( new URL( path ) );
				} catch ( MalformedURLException ignored ) {
				}
			}
		}

		urls.addAll( searchForPackageLibraries() );
		urls.add( new URL( "file:/" ) );
		libURLs = urls.toArray( new URL[]{} );
		jolieClassLoader = new JolieClassLoader( libURLs, parentClassLoader );
	}

	private void extractAndAddJarUrlsFromDirectory( Set< URL > urls, File dir ) throws IOException
	{
		String jars[] = dir.list( ( File directory, String filename ) -> filename.endsWith( ".jar" ) );
		if ( jars != null ) {
			for ( String jarPath : jars ) {
				urls.add( new URL( "jar:file:" + dir.getCanonicalPath() + '/' + jarPath + "!/" ) );
			}
		}
	}

	private Set< URL > searchForPackageLibraries() throws IOException
	{
		Set< URL > result = new HashSet<>();
		String packageLocation = arguments.getPackageLocation();

		if ( packageLocation != null ) {
			File packagesDirectory = new File( packageLocation );
			if ( packagesDirectory.exists() ) {
				File[] files = packagesDirectory.listFiles();
				if ( files != null ) {
					for ( File packageDirectory : files ) {
						File libDirectory = new File( packageDirectory, DIRECTORY_LIB );
						if ( libDirectory.exists() ) {
							extractAndAddJarUrlsFromDirectory( result, libDirectory );
						}
					}
				}
			}
		}
		return result;
	}

	public Pair< URI, InputStream > openProgram() throws IOException
	{
		Pair< File, InputStream > programAsFile = locateProgramAsFile();
		if ( programAsFile != null ) {
			File parentFile = programAsFile.key().getParentFile();
			if ( parentFile != null ) {
				includePaths.addFirst( parentFile );
			}
			return new Pair<>( programAsFile.key().toURI(), programAsFile.value() );
		}

		Pair< URL, InputStream > urlStream;
		urlStream = locateProgramAsURL();
		if ( urlStream == null ) urlStream = locateProgramFromClassLoader();

		if ( urlStream != null ) {
			// TODO We previously added the "parent" to the include paths
			// However this doesn't make sense. The include paths were only used to construct File instances,
			// which don't understand URLs in the first place.
			try {
				return new Pair<>( urlStream.key().toURI(), urlStream.value() );
			} catch ( URISyntaxException e ) {
				throw new RuntimeException( e );
			}
		}

		throw new IllegalStateException( "Could not locate program. Known program main: " +
				configurator.getMainName() + ". Running from package: " + arguments.getPackageSelf() );
	}

	private Pair< File, InputStream > locateProgramAsFile()
	{
		String fileName = configurator.getMainName();
		for ( File path : includePaths ) {
			File file = new File( path, fileName );
			if ( file.exists() ) {
				return openFileStream( file );
			}
		}

		// Previously we would look for the file without any include path as the first thing, we now postpone this.
		// This is to ensure that relative file paths don't start by checking the working directory. This will cause
		// conflicts if we're starting for example a package.
		File file = new File( fileName );
		if ( file.exists() ) {
			return openFileStream( file );
		}

		return null;
	}

	private Pair< File, InputStream > openFileStream( File file )
	{
		try {
			return new Pair<>( file, new FileInputStream( file ) );
		} catch ( FileNotFoundException e ) {
			throw new RuntimeException( "File no longer present.", e );
		}
	}

	private Pair< URL, InputStream > locateProgramAsURL() throws IOException
	{
		String fileName = configurator.getMainName();
		try {
			URL url = new URL( fileName );
			InputStream inputStream = url.openStream();
			if ( inputStream != null ) {
				return new Pair<>( url, inputStream );
			}
		} catch ( MalformedURLException ignored ) {
		}
		return null;
	}

	private Pair< URL, InputStream > locateProgramFromClassLoader() throws IOException
	{
		String fileName = configurator.getMainName();
		URL resource = jolieClassLoader.getResource( fileName );
		if ( resource != null ) {
			InputStream inputStream = resource.openStream();
			if ( inputStream != null ) {
				return new Pair<>( resource, inputStream );
			}
		}
		return null;
	}

	public List< File > getDefaultClassPathIncludes()
	{
		File root = locateServiceRoot();
		return Arrays.asList( new File( root, DIRECTORY_LIB ), new File( root, DIRECTORY_EXT ) );
	}

	public URL[] getLibURLs()
	{
		return libURLs;
	}

	public Deque< File > getIncludePaths()
	{
		return includePaths;
	}

	public JolieClassLoader getJolieClassLoader()
	{
		return jolieClassLoader;
	}
}
