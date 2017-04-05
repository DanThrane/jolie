/*
 * Copyright (C) 2006-2016 Fabrizio Montesi <famontesi@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package jolie.lang;

import jolie.lang.parse.ast.*;

/**
 * Takes a parsed AST and collapses all interfaces into the {@link OperationCollector#operationsMap()} of
 * {@link jolie.lang.parse.ast.PortInfo}s
 *
 * @author Dan Sebastian Thrane
 */
public class InterfaceCollapser
{
	private final Program program;

	public InterfaceCollapser( Program program )
	{
		this.program = program;
	}

	public void collapse()
	{
		for ( OLSyntaxNode node : program.children() ) {
			if ( node instanceof PortInfo ) {
				PortInfo port = ( PortInfo ) node;
				for ( InterfaceDefinition interfaceDefinition : ( ( PortInfo ) node ).getInterfaceList() ) {
					interfaceDefinition.copyTo( port );
				}
			}
		}
		/*
		// Input ports:
		else if ( iface.operationsMap().isEmpty() && redirectionMap.isEmpty() && aggregationList.isEmpty() ) {
			throwException( "expected at least one operation, interface, aggregation or redirection for inputPort " + inputPortName );
		}
		 */
	}
}
