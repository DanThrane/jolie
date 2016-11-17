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
import jolie.CommandLineParser;
import jolie.Interpreter;
import jolie.runtime.expression.Expression;


public class JolieServiceLoader extends EmbeddedServiceLoader
{
	private final static Pattern servicePathSplitPattern = Pattern.compile( " " );
	private final Interpreter interpreter;

	public JolieServiceLoader( Expression channelDest, Interpreter currInterpreter, String servicePath )
			throws IOException, CommandLineException
	{
		super( channelDest );
		final ArrayList< String > serviceOptions = new ArrayList<>( Arrays.asList( servicePathSplitPattern.split( servicePath ) ) );
		final ArrayList< String > options = new ArrayList<>( Arrays.asList( currInterpreter.optionArgs() ) );
		List< String > newArgs = new ArrayList<>();

		newArgs.add( "-i" );
		newArgs.add( currInterpreter.programDirectory().getAbsolutePath() );

		newArgs.addAll( options );
		newArgs.addAll( serviceOptions );

		interpreter = new Interpreter(
				newArgs.toArray( new String[0] ),
				currInterpreter.getClassLoader(),
				currInterpreter.programDirectory()
		);
	}

	public JolieServiceLoader( Expression channelDest, CommandLineParser cli ) throws IOException
	{
		super( channelDest );
		interpreter = new Interpreter( cli, null );
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
