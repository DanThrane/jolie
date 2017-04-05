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

import jolie.lang.parse.COLParser;
import jolie.lang.parse.OLParser;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.lang.parse.ast.*;
import jolie.lang.parse.ast.expression.ConstantStringExpression;
import jolie.lang.parse.ast.types.*;
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
 * @author Dan Sebastian Thrane
 */
public class Configurator
{
	private final JoliePackage thisPackage;
	private final Program inputProgram;
	private final String configurationFile;
	private final String configurationProfile;
	private final String[] includePaths;
	private final ClassLoader classLoader;
	private final Map< String, JoliePackage > knownPackages;
	private ConfigurationTree.Region mergedRegion;
	private ConfigurationTree inputTree;

	public Configurator( Program program, String thisPackageName, Map< String, JoliePackage > knownPackages,
						 String configurationFile, String configurationProfile, String[] includePaths,
						 ClassLoader classLoader )
	{
		this.inputProgram = program;
		thisPackage = knownPackages.get( thisPackageName );
		this.configurationFile = configurationFile;
		this.configurationProfile = configurationProfile;
		this.knownPackages = knownPackages;
		this.includePaths = includePaths;
		this.classLoader = classLoader;
	}

	public Program process() throws ConfigurationException, ParserException, IOException
	{
		if ( thisPackage != null ) {
			ConfigurationTree.Region region = getRegionFromArguments();
			ConfigurationTree.Region defaultRegion = getDefaultRegion();

			if ( defaultRegion != null ) {
				region = ConfigurationTree.Region.merge( region, defaultRegion );
			}
			mergedRegion = region;
			return doProcess( inputProgram );
		}
		return inputProgram;
	}

	private ConfigurationTree.Region getDefaultRegion() throws IOException, ParserException, ConfigurationException
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
			throw new ConfigurationException( "Default configuration unit (" + file.getAbsolutePath() + ") contains " +
					"more than one unit or no units. Only one unit named 'default' is allowed" );
		}

		if ( !tree.getRegions().get( 0 ).getProfileName().equals( "default" ) ) {
			throw new ConfigurationException( "Default configuration unit (" + file.getAbsolutePath() + ") is not " +
					"named correctly. Only one unit named 'default' is allowed" );
		}

		return tree.getRegions().get( 0 );
	}

	private ConfigurationTree.Region getRegionFromArguments() throws IOException, ParserException,
			ConfigurationException
	{
		if ( configurationFile != null ) {
			File configFile = new File( configurationFile );
			COLParser parser = new COLParser(
					new Scanner( new FileInputStream( configFile ), configFile.toURI(), "US-ASCII" ),
					configFile.getParentFile()
			);

			inputTree = parser.parse();
			return inputTree.getRegions().stream()
					.filter( it -> it.getProfileName().equals( configurationProfile ) )
					.findAny()
					.orElseThrow( () -> new ConfigurationException(
							"Could not find requested configuration region." +
									" Was requested to find '" + configurationProfile + "' from '" +
									configurationFile + "'. This configuration should match package '" +
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
			} else if ( node instanceof InterfaceDefinition ) {
				nodes = processInterface( ( InterfaceDefinition ) node );
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
							configurationFile,
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

	private List< OLSyntaxNode > processInterface( InterfaceDefinition n )
	{
		ConfigurationTree.ExternalInterface iface = mergedRegion.getInterface( n.name() );
		if ( iface != null ) {
			List< OLSyntaxNode > result = new ArrayList<>();
			Program parsed = parsePackage( iface.fromPackage() );
			InterfaceDefinition target = parsed.children().stream()
					.filter( it -> it instanceof InterfaceDefinition )
					.map( it -> ( InterfaceDefinition ) it )
					.filter( it -> it.name().equals( iface.realName() ) )
					.findAny()
					.orElseThrow( () -> new ConfigurationException( "Unable to find interface '" + iface.realName() +
							"' from package '" + iface.fromPackage() + "'" ) );

			// We need to know all types to resolve links. This is usually done later, by the SemanticVerifier, but we
			// need this to happen before the SemanticVerifier runs. We will need to this to copy over the correct
			// type definitions from the target interface.
			Map< String, TypeDefinition > allTypes = new HashMap<>();
			parsed.children()
					.stream()
					.filter( it -> it instanceof TypeDefinition )
					.map( it -> ( TypeDefinition ) it )
					.forEach( it -> allTypes.put( it.id(), it ) ); // TODO Duplicates?

			for ( OperationDeclaration op : target.operationsMap().values() ) {
				if ( op instanceof OneWayOperationDeclaration ) {
					OneWayOperationDeclaration owDecl = ( OneWayOperationDeclaration ) op;
					result.addAll( resolveType( parsed, allTypes, owDecl.requestType() ) );
				} else if ( op instanceof RequestResponseOperationDeclaration ) {
					RequestResponseOperationDeclaration rrDecl = ( RequestResponseOperationDeclaration ) op;
					result.addAll( resolveType( parsed, allTypes, rrDecl.requestType() ) );
					result.addAll( resolveType( parsed, allTypes, rrDecl.responseType() ) );
				}
			}

			n.operationsMap().clear();
			target.copyTo( n );
			result.add( n );
			return result;
		} else {
			return Collections.singletonList( n );
		}
	}

	private Set< OLSyntaxNode > resolveType( Program program, Map< String, TypeDefinition > allTypes,
											 TypeDefinition definition )
	{
		Set< OLSyntaxNode > result = new HashSet<>();
		collectTypes( program, allTypes, result, true, definition );
		return result;
	}

	private void collectTypes( Program program, Map< String, TypeDefinition > allTypes,
							   Set< OLSyntaxNode > node, boolean topLevel, TypeDefinition definition )
	{
		if ( definition instanceof TypeChoiceDefinition ) {
			collectTypes( program, allTypes, node, topLevel, ( TypeChoiceDefinition ) definition );
		} else if ( definition instanceof TypeDefinitionLink ) {
			collectTypes( program, allTypes, node, topLevel, ( TypeDefinitionLink ) definition );
		} else if ( definition instanceof TypeInlineDefinition ) {
			collectTypes( program, allTypes, node, topLevel, ( TypeInlineDefinition ) definition );
		}
		// Do nothing for TypeDefinitionUndefined
	}

	private void collectTypes( Program program, Map< String, TypeDefinition > allTypes,
							   Set< OLSyntaxNode > node, boolean topLevel, TypeChoiceDefinition def )
	{
		if ( topLevel ) node.add( def );
		if ( !node.contains( def.left() ) ) {
			collectTypes( program, allTypes, node, false, def.left() );
		}

		if ( !node.contains( def.right() ) ) {
			collectTypes( program, allTypes, node, false, def.right() );
		}
	}

	private void collectTypes( Program program, Map< String, TypeDefinition > allTypes,
							   Set< OLSyntaxNode > node, boolean topLevel, TypeDefinitionLink def )
	{
		TypeDefinition definition = allTypes.get( def.linkedTypeName() );
		if ( definition == null ) {
			throw new ConfigurationException( "Undefined type '" + def.linkedTypeName() + "' [" +
					def.context().sourceName() + ":" + def.context().line() +
					"]. Happened while attempting to inject interface" );
		}
		if ( topLevel ) node.add( def );

		if ( !node.contains( definition ) ) {
			// Following a link always gives us a top-level definition.
			collectTypes( program, allTypes, node, true, definition );
		}
	}

	private void collectTypes( Program program, Map< String, TypeDefinition > allTypes,
							   Set< OLSyntaxNode > node, boolean topLevel, TypeInlineDefinition def )
	{
		if ( topLevel ) node.add( def );
		if ( def.hasSubTypes() ) {
			for ( Map.Entry< String, TypeDefinition > entry : def.subTypes() ) {
				if ( !node.contains( entry.getValue() ) ) {
					collectTypes( program, allTypes, node, false, entry.getValue() );
				}
			}
		}
	}

	private Program parsePackage( String packageName )
	{
		JoliePackage pack = knownPackages.get( packageName );
		if ( pack == null ) {
			throw new ConfigurationException( "Attempting to parse package '" + packageName + "'. " +
					"But this package is not known. Did you forget to configure it with --pkg?" );
		}
		File entryFile = new File( pack.getRoot(), pack.getEntryPoint() );
		Scanner scanner;
		try {
			scanner = new Scanner( new FileInputStream( entryFile ), entryFile.toURI(), "US-ASCII" );
		} catch ( IOException e ) {
			throw new ConfigurationException( "Unable to read package '" + packageName + "'" );
		}

		// Must add package local paths first
		List< String > includes = new ArrayList<>();
		includes.add( pack.getRoot() );
		includes.add( new File( pack.getRoot(), "include" ).getAbsolutePath() );
		Collections.addAll( includes, includePaths );
		String[] includePaths = includes.toArray( new String[ 0 ] );

		OLParser parser = new OLParser( scanner, includePaths, classLoader );
		try {
			return parser.parse();
		} catch ( IOException | ParserException e ) {
			e.printStackTrace();
			throw new ConfigurationException( "Unable to parse package '" + packageName +
					"'. " );
		}
	}

	private URI safeParse( String input )
	{
		try {
			return new URI( input );
		} catch ( URISyntaxException e ) {
			throw new ConfigurationException( e.getMessage() );
		}
	}

	public static class ConfigurationException extends RuntimeException
	{
		ConfigurationException( String message )
		{
			super( message );
		}
	}
}
