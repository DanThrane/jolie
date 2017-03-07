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

import jolie.CommandLineException;
import jolie.CommandLineParser;
import jolie.Interpreter;
import jolie.runtime.expression.Expression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class JolieServiceLoader extends EmbeddedServiceLoader
{
	private final static Pattern servicePathSplitPattern = Pattern.compile( " " );
	private final Interpreter interpreter;

	public JolieServiceLoader( Expression channelDest, Interpreter currInterpreter, String servicePath )
			throws IOException, CommandLineException
	{
		super( channelDest );

		final ArrayList< String > serviceOptions = new ArrayList<>(
				Arrays.asList( servicePathSplitPattern.split( servicePath ) )
		);

		// We need to remove deployment such that we can pass a normal file here.
		CommandLineParser newArguments = currInterpreter.cmdParser().makeCopy( it -> {
			it.setDeploymentFile( null );
			it.setDeploymentProfile( null );
			it.setInternalConfiguration( null );
			it.setPackageSelf( null );
			it.getIncludeList().add( currInterpreter.programDirectory().getAbsolutePath() );
			try {
				it.addOptions( 0, serviceOptions );
			} catch ( CommandLineException e ) {
				throw new RuntimeException( e );
			}
		} );
		// TODO Program directory probably isn't right.
		interpreter = new Interpreter( newArguments, currInterpreter.programDirectory() );
	}

	public JolieServiceLoader( Expression channelDest, CommandLineParser cli ) throws IOException
	{
		super( channelDest );
		interpreter = new Interpreter( cli, null );
	}

	public void load()
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

	public Interpreter interpreter()
	{
		return interpreter;
	}
}
