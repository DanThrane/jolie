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

package jolie;

import jolie.configuration.ProcessedParameterAssignment;
import jolie.configuration.ProcessedParameterDefinition;
import jolie.lang.parse.SemanticException;
import jolie.lang.parse.context.ParsingContext;
import jolie.runtime.*;
import jolie.runtime.typing.Type;
import jolie.runtime.typing.TypeCheckingException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs "late" checking which involves a fully processed
 * interpretation tree. This checking takes place just
 * before the CommCore is initialized.
 *
 * @author Dan Sebastian Thrane
 */
public class LateChecker
{
	// TODO Come up with a better name with this.
	// This should potentially work into a bigger rework of error checking, see issue #108

	private final Interpreter interpreter;
	private boolean isValid = true;
	private SemanticException semanticException = new SemanticException();

	public LateChecker( Interpreter interpreter )
	{
		this.interpreter = interpreter;
	}

	public void validate() throws SemanticException
	{
		Map< String, ProcessedParameterDefinition > definitions = interpreter.externalParameterDefinitions();
		List< ProcessedParameterAssignment > assignments = interpreter.externalParameterAssignments();
		Value root = interpreter.externalParametersRoot();
		Map< String, ParsingContext > firstAssignment = new HashMap<>();

		for ( ProcessedParameterAssignment assignment : assignments ) {
			VariablePath path = assignment.path();
			String name = path.path()[0].key().evaluate().valueObject().toString();
			if ( !firstAssignment.containsKey( name ) ) {
				firstAssignment.put( name, assignment.context() );
			}
			path.getValue().deepCopy( assignment.expression().evaluate() );
		}

		for ( Map.Entry< String, ValueVector > child : root.children().entrySet() ) {
			String name = child.getKey();
			ValueVector vector = child.getValue();
			ParsingContext assignmentContext = firstAssignment.get( name );

			if ( vector.size() != 1 ) {
				throw new IllegalStateException( "entry should never have more than one child" );
			}

			Value value = vector.first();
			ProcessedParameterDefinition definition = definitions.get( name );

			if ( definition == null ) {
				userError( assignmentContext,
						"attempting to assign value to parameter '" + name + "', but this parameter was " +
								"not defined" );
				continue;
			}

			Type type = definition.type();

			try {
				type.check( value );
			} catch ( TypeCheckingException e ) {
				userError( assignmentContext, String.format(
						"Parameter '%s' [%s:%d] failed type checking. Reason: %s",
						name, definition.context().sourceName(), definition.context().line(),
						e.getMessage()
				) );
			}
		}

		if ( !isValid ) {
			throw semanticException;
		}
	}

	private void userError( ParsingContext context, String message )
	{
		isValid = false;
		semanticException.addSemanticError( context, message );
	}
}
