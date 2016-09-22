/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
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


package jolie.runtime.embedding;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import jolie.CommandLineException;
import jolie.Interpreter;
import jolie.lang.parse.context.ParsingContext;
import jolie.runtime.expression.Expression;


public class JolieServiceLoader extends EmbeddedServiceLoader
{
	private final static Pattern servicePathSplitPattern = Pattern.compile( " " );
	public static final String FOLDER_NAME_JPM_PACKAGES = "jpm_packages";
	private final Interpreter interpreter;

	public JolieServiceLoader ( Expression channelDest, Interpreter currInterpreter, String servicePath,
								ParsingContext parsingContext )
			throws IOException, CommandLineException
	{
		super( channelDest );
		final String[] ss = servicePathSplitPattern.split( servicePath );
		final String[] options = currInterpreter.optionArgs();

		List< String > newArgs = new ArrayList<>();
		newArgs.add( "-i" );
		newArgs.add( currInterpreter.programDirectory().getAbsolutePath() );

		List< Path > directoryComponents = new ArrayList<>();
		Path contextPath = new File( parsingContext.source() ).toPath();
		contextPath.forEach( directoryComponents::add );
		if ( parsingContext.source().getScheme().equals( "file" ) &&
				directoryComponents.stream().anyMatch( it -> it.toString().equals( FOLDER_NAME_JPM_PACKAGES ) ) ) {
			newArgs.addAll( getPackageIncludePaths( contextPath, directoryComponents ) );
		}

		newArgs.addAll( Arrays.asList( options ) );
		newArgs.addAll( Arrays.asList( ss ) );

		interpreter = new Interpreter(
				newArgs.toArray( new String[0] ),
				currInterpreter.getClassLoader(),
				currInterpreter.programDirectory()
		);
	}

	/**
	 * We have special handling of packages. We add the following entries to the include path:
	 * <p>
	 * <ul>
	 * <li>The root of the package</li>
	 * <li>The include folder at the root of the package</li>
	 * <li>The folder which contains the embedding statement (to allow for relative includes)</li>
	 * </ul>
	 *
	 * @param contextPath         The complete context path
	 * @param directoryComponents The path components of the parsing context
	 * @return A list of include paths
	 */
	private List< String > getPackageIncludePaths ( Path contextPath, List< Path > directoryComponents )
	{
		List< String > result = new ArrayList<>();

		File directory = contextPath.toFile().getParentFile();
		result.add( "-i" );
		result.add( directory.getAbsolutePath() );

		for ( int i = directoryComponents.size() - 1; i >= 0 ; i-- ) {
			Path component = directoryComponents.get( i );
			if ( component.toString().equals( FOLDER_NAME_JPM_PACKAGES ) ) {
				if ( i + 1 >= directoryComponents.size() ) {
					throw new IllegalStateException( "Cannot find package include path (invalid path)" );
				}

				// For some reason the root is stripped when we do subpath. Not sure this is the right way to do things
				// TODO Test on other operating systems
				File packageRoot = contextPath.getRoot()
						.resolve( contextPath.subpath( 0, i + 2 ) ).toAbsolutePath().toFile();

				result.add( "-i" );
				result.add( packageRoot.getAbsolutePath() );

				File packageIncludeDirectory = new File( packageRoot, "include" );
				if ( packageIncludeDirectory.exists() ) {
					result.add( "-i" );
					result.add( packageIncludeDirectory.getAbsolutePath() );
				}

				return result;
			}
		}

		throw new IllegalStateException( "Cannot find package include path (invalid path)" );
	}

	public void load ()
			throws EmbeddedServiceLoadingException
	{
		Future< Exception > f = interpreter.start();
		try {
			Exception e = f.get();
			if ( e == null ) {
				setChannel( interpreter.commCore().getLocalCommChannel() );
			} else {
				throw new EmbeddedServiceLoadingException( e );
			}
		} catch ( Exception e ) {
			throw new EmbeddedServiceLoadingException( e );
		}
	}

	public Interpreter interpreter ()
	{
		return interpreter;
	}
}
