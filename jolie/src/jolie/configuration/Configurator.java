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

package jolie.configuration;

import jolie.Interpreter;
import jolie.InterpreterException;
import jolie.lang.Constants;
import jolie.lang.JoliePackage;
import jolie.lang.parse.COLParser;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.lang.parse.ast.*;
import jolie.lang.parse.ast.expression.ConstantStringExpression;
import jolie.lang.parse.context.ParsingContext;
import jolie.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Processes a {@link Program} to add external configuration.
 *
 * @author Dan Sebastian Thrane <dthrane@gmail.com>
 */
public class Configurator
{
	private final Interpreter parent;
	private final JoliePackage thisPackage;
	private final Program inputProgram;
	private ConfigurationTree.Region mergedRegion;
	private ConfigurationTree inputTree;

	public Configurator( Interpreter parent, Program program )
	{
		this.parent = parent;
		this.inputProgram = program;
		thisPackage = parent.knownPackages().get( parent.thisPackage() );
	}

	public Program process() throws InterpreterException, ParserException, IOException
	{
		if ( parent.thisPackage() != null ) {
			ConfigurationTree.Region region = getRegionFromArguments();
			ConfigurationTree.Region defaultRegion = getDefaultRegion();

			if ( defaultRegion != null ) {
				region = ConfigurationTree.Region.merge( region, defaultRegion );
			}
			mergedRegion = region;
			try {
				return doProcess( inputProgram );
			} catch ( ConfigurationException e ) {
				// We need an unchecked exception to allow throwing from
				// visitor. We use this exception internally, and just rethrow
				// it as an interpreter exception (which the upper layer
				// expects us to do).
				throw new InterpreterException( e );
			}
		}
		return inputProgram;
	}

	private ConfigurationTree.Region getDefaultRegion() throws IOException, ParserException, InterpreterException
	{
		File root = new File( thisPackage.getRoot() );
		File file = new File( root, "default.col" );
		if ( !file.exists() ) return null;

		COLParser parser = new COLParser(
				new Scanner( new FileInputStream( file ), file.toURI(), "US-ASCII" ),
				root
		);
		ConfigurationTree tree = parser.parse();

		if ( tree.getRegions().size() != 1 ) {
			throw new InterpreterException( "Default configuration unit (" + file.getAbsolutePath() + ") contains " +
					"more than one unit or no units. Only one unit named 'default' is allowed" );
		}

		if ( !tree.getRegions().get( 0 ).getProfileName().equals( "default" ) ) {
			throw new InterpreterException( "Default configuration unit (" + file.getAbsolutePath() + ") is not " +
					"named correctly. Only one unit named 'default' is allowed" );
		}

		return tree.getRegions().get( 0 );
	}

	private ConfigurationTree.Region getRegionFromArguments() throws IOException, ParserException, InterpreterException
	{
		if ( parent.configurationFile() != null ) {
			File configFile = new File( parent.configurationFile() );
			COLParser parser = new COLParser(
					new Scanner( new FileInputStream( configFile ), configFile.toURI(), "US-ASCII" ),
					configFile.getParentFile()
			);

			inputTree = parser.parse();
			return inputTree.getRegions().stream()
					.filter( it -> it.getProfileName().equals( parent.configurationProfile() ) )
					.findAny()
					.orElseThrow( () -> new InterpreterException(
							"Could not find requested configuration region." +
									" Was requested to find '" + parent.configurationProfile() + "' from '" +
									parent.configurationFile() + "'. This configuration should match package '" +
									thisPackage.getName()
					) );
		} else {
			ConfigurationTree.Region region = new ConfigurationTree.Region();
			region.setProfileName( "empty-unit-" + thisPackage.getName() );
			region.setPackageName( thisPackage.getName() );
			return region;
		}
	}

	private OLSyntaxNode oldInitBody;

	private Program doProcess( Program n )
	{
		// We only ever touch top-level nodes. For this reason we don't really
		// need to go for a full-blown OLVisitor. If this code is ever
		// expanded to handle most nodes, then this code should be
		// refactored to use a visitor.
		Program output = new Program( n.context() );
		for ( OLSyntaxNode node : n.children() ) {
			List< OLSyntaxNode > nodes;
			if ( node instanceof OutputPortInfo ) {
				nodes = processOutputPort( ( OutputPortInfo ) node );
			} else if ( node instanceof InputPortInfo ) {
				nodes = processInputPort( ( InputPortInfo ) node );
			} else if ( node instanceof DefinitionNode && ( ( DefinitionNode ) node ).id().equals( "init" ) ) {
				// Save old init body. We'll insert stuff before it in a new
				// init process.
				oldInitBody = ( ( DefinitionNode ) node ).body();
				nodes = Collections.emptyList();
			} else {
				nodes = Collections.singletonList( node );
			}

			nodes.forEach( output::addChild );
		}

		if ( oldInitBody != null || !mergedRegion.getConstants().isEmpty() ) {
			SequenceStatement initBody = new SequenceStatement( n.context() );
			output.addChild( new DefinitionNode( n.context(), "init", initBody ) );

			// Add parameters first, this way "init" can also use parameters.
			createParameterInitialization().forEach( initBody::addChild );
			if ( oldInitBody != null ) {
				initBody.addChild( oldInitBody );
			}
		}
		return output;
	}

	private List< OLSyntaxNode > createParameterInitialization()
	{
		List< OLSyntaxNode > result = new ArrayList<>();
		for ( ConfigurationTree.ExternalConstantNode node : mergedRegion.getConstants() ) {
			result.add( new DeepCopyStatement(
					node.context(),
					buildParamsPath( node.context(), node.name() ),
					node.expressionNode()
			) );
		}
		return result;
	}

	private VariablePathNode buildParamsPath( ParsingContext context, String destination )
	{
		VariablePathNode node = new VariablePathNode( context, VariablePathNode.Type.GLOBAL );
		node.append( new Pair<>( new ConstantStringExpression( context, "params" ), null ) );
		node.append( new Pair<>( new ConstantStringExpression( context, destination ), null ) );
		return node;
	}

	private List< OLSyntaxNode > processOutputPort( OutputPortInfo n )
	{
		List< OLSyntaxNode > result = new ArrayList<>();
		ConfigurationTree.ExternalPort port = mergedRegion.getOutputPort( n.id() );
		if ( port != null ) {
			if ( port.getLocation() != null ) {
				n.setLocation( safeParse( port.getLocation() ) );
			}

			if ( port.getProtocol() != null ) {
				n.setProtocolId( port.getProtocol().getType() );

				if ( port.getProtocol().getProperties() != null ) {
					n.setProtocolConfiguration( port.getProtocol().getProperties() );
				}
			}
		}

		result.add( n );

		// Embedding needs to go after the port in the AST
		if ( port != null && port.isEmbedding() ) {
			ConfigurationTree.Region region = inputTree.getRegions().stream()
					.filter( it -> it.getProfileName().equals( port.getEmbeds() ) )
					.findAny()
					.orElseThrow( () ->
							new ConfigurationException( "Attempting to embed profile " +
									port.getEmbeds() + ", but could not find profile" ) );

			result.add( new EmbeddedServiceNode(
					n.context(),
					Constants.EmbeddedServiceType.JOLIE,
					String.format( "--conf %s %s %s.pkg",
							port.getEmbeds(),
							parent.configurationFile(),
							region.getPackageName()
					),
					n.id()
			) );
		}
		return result;
	}

	private List< OLSyntaxNode > processInputPort( InputPortInfo n )
	{
		List< OLSyntaxNode > result = new ArrayList<>();

		ConfigurationTree.ExternalPort port = mergedRegion.getInputPort( n.id() );
		// Right now, let's assume that if we have values, it means we need to
		// override existing info.
		if ( port != null ) {
			URI location = n.location();
			String protocolId = n.protocolId();
			OLSyntaxNode properties = n.protocolConfiguration();
			if ( port.getLocation() != null ) {
				location = safeParse( port.getLocation() );
			}

			if ( port.getProtocol() != null ) {
				protocolId = port.getProtocol().getType();

				if ( port.getProtocol().getProperties() != null ) {
					properties = port.getProtocol().getProperties();
				}
			}

			result.add( new InputPortInfo( n.context(), n.id(), location, protocolId, properties,
					n.aggregationList(), n.redirectionMap() ) );
		} else {
			result.add( n );
		}
		return result;
	}

	private URI safeParse( String input )
	{
		try {
			return new URI( input );
		} catch ( URISyntaxException e ) {
			throw new ConfigurationException( e.getMessage() );
		}
	}

	private static class ConfigurationException extends RuntimeException
	{
		ConfigurationException( String message )
		{
			super( message );
		}
	}
}
