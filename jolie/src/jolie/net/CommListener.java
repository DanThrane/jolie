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


package jolie.net;

import java.util.Collection;
import java.util.Map;

import jolie.Interpreter;
import jolie.JolieThread;
import jolie.runtime.InputOperation;

/** Base class for a communication input listener.
 * @author Fabrizio Montesi
 */
abstract public class CommListener extends JolieThread
{
	private static int index = 0;

	private CommProtocol protocol;
	private Collection< InputPort > inputPorts;
	private Map< String, OutputPort > redirectionMap;
	
	public CommListener(
				Interpreter interpreter,
				CommProtocol protocol,
				Collection< InputPort > inputPorts,
				Map< String, OutputPort > redirectionMap
			)
	{
		super( interpreter, interpreter.commCore().threadGroup(), "CommListener-" + index++ );
		this.protocol = protocol;
		this.inputPorts = inputPorts;
		this.redirectionMap = redirectionMap;
	}
	
	public CommProtocol createProtocol()
	{
		return protocol.clone();
	}
	
	public Map< String, OutputPort > redirectionMap()
	{
		return redirectionMap;
	}
	
	public boolean canHandleInputOperation( InputOperation operation )
	{
		for( InputPort port : inputPorts ) {
			if ( port.operations().contains( operation ) )
				return true;
		}
		return false;
	}

	abstract public void run();
}
