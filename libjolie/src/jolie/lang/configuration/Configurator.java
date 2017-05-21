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

package jolie.lang.configuration;

import jolie.lang.Constants;
import jolie.lang.JoliePackage;
import jolie.lang.parse.COLParser;
import jolie.lang.parse.OLParser;
import jolie.lang.parse.ParserException;
import jolie.lang.parse.Scanner;
import jolie.lang.parse.ast.*;
import jolie.lang.parse.ast.ConfigurationTree.Region;
import jolie.lang.parse.ast.types.*;

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
	private JoliePackage parentPackage;
	private final Program inputProgram;
	private final String configurationFile;
	private final String configurationProfile;
	private final String[] includePaths;
	private final ClassLoader classLoader;
	private final Map< String, JoliePackage > knownPackages;
	private Region mergedRegion;
	private ConfigurationTree inputTree;
	private File origConfigFile;

	public Configurator( Program program, String thisPackageName, Map< String, JoliePackage > knownPackages,
						 String configurationFile, String configurationProfile, String[] includePaths,
						 ClassLoader classLoader, String parentPackage )
	{
		this.inputProgram = program;
		thisPackage = knownPackages.get( thisPackageName );
		this.configurationFile = configurationFile;
		this.configurationProfile = configurationProfile;
		this.knownPackages = knownPackages;
		this.includePaths = includePaths;
		this.classLoader = classLoader;
		this.parentPackage = parentPackage != null ? knownPackages.get( parentPackage ) : null;
	}

	public Program process() throws ConfigurationException, ParserException, IOException
	{
		if ( thisPackage != null ) {
			mergedRegion = mergeRegions( getRegionFromArguments() );
			return doProcess( inputProgram );
		}
		return inputProgram;
	}

	private Region mergeRegions( Region inputRegion )
	{
		if ( inputRegion.getExtendsProfile() != null ) {
			Map< String, Region > namespaced = inputTree.getRegions()
					.get( thisPackage.getName() );
			assert namespaced != null;
			Region extendsRegion = namespaced.get( inputRegion.getExtendsProfile() );
			if ( extendsRegion == null ) {
				throw new ConfigurationException( String.format(
						"Region '%s' (%s:%d) is attempting to extend '%s' but no such profile exists.",
						inputRegion.getProfileName(),
						inputRegion.getContext().sourceName(),
						inputRegion.getContext().line(),
						inputRegion.getExtendsProfile()
				) );
			}
			extendsRegion = mergeRegions( extendsRegion );
			return Region.merge( inputRegion, extendsRegion );
		}
		return inputRegion;
	}

	private Region getRegionFromArguments() throws IOException, ParserException,
			ConfigurationException
	{
		if ( configurationFile != null ) {
			File configFile = new File( configurationFile );
			if ( !configFile.isAbsolute() && parentPackage != null ) {
				File root = new File( parentPackage.getRoot() );
				configFile = new File( root, configurationFile );
			}
			origConfigFile = configFile;
			COLParser parser = new COLParser(
					new Scanner( new FileInputStream( configFile ), configFile.toURI(), "US-ASCII" ),
					configFile.getParentFile(),
					knownPackages
			);

			inputTree = parser.parse();
			Map< String, Region > namespaced = inputTree.getRegions().get( thisPackage.getName() );
			if ( namespaced == null ) {
				throw new ConfigurationException( "Could not find profile configuring " + thisPackage.getName() );
			}

			Region region = namespaced.get( configurationProfile );
			if ( region == null ) {
				throw new ConfigurationException(
						"Could not find requested configuration region." +
								" Was requested to find '" + configurationProfile + "' from '" +
								configurationFile + "'. This configuration should match package '" +
								thisPackage.getName() + "'" );
			}
			return region;
		} else { // TODO Is this still needed? Might just be dead code at this point.
			Region region = new Region();
			region.setProfileName( "empty-unit-" + thisPackage.getName() );
			region.setPackageName( thisPackage.getName() );
			return region;
		}
	}

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
			} else if ( node instanceof InterfaceDefinition ) {
				nodes = processInterface( ( InterfaceDefinition ) node );
			} else {
				nodes = Collections.singletonList( node );
			}

			nodes.forEach( output::addChild );
		}

		mergedRegion.getParameters().forEach( output::addChild );
		return output;
	}

	private List< OLSyntaxNode > processOutputPort( OutputPortInfo n )
	{
		List< OLSyntaxNode > result = new ArrayList<>();
		ConfigurationTree.ExternalPort port = mergedRegion.getOutputPort( n.id() );
		if ( port != null ) {
			if ( n.isDynamic() ) {
				throw new ConfigurationException( String.format(
						"Attempting to configure dynamic output port '%s' defined at %s:%d.\n  " +
								"Configuration took place at %s:%d",
						n.id(), n.context().source().toString(), n.context().line(),
						port.getContext().source().toString(), port.getContext().line()
				) );
			}

			if ( port.getLocation() != null ) {
				if ( n.location() != null ) {
					throw new ConfigurationException( String.format(
							"Attempting to override location of static output port (%s)\n" +
									"Port defined at %s:%d\n" +
									"Configuration took place at %s:%d",
							n.id(),
							n.context().sourceName(), n.context().line(),
							port.getContext().sourceName(), port.getContext().line()
					) );
				}
				n.setLocation( safeParse( port.getLocation() ) );
			}

			if ( port.getProtocol() != null && port.getProtocol().getType() != null ) {
				if ( n.protocolId() != null ) {
					throw new ConfigurationException( String.format(
							"Attempting to override protocol definition of static output port (%s).\n" +
									"Port defined at %s:%d.\n" +
									"Configuration took place at %s:%d",
							n.id(),
							n.context().sourceName(), n.context().line(),
							port.getContext().sourceName(), port.getContext().line()
					) );
				}
				n.setProtocolId( port.getProtocol().getType() );

				if ( port.getProtocol().getProperties() != null ) {
					n.setProtocolConfiguration( port.getProtocol().getProperties() );
				}
			}
		}

		result.add( n );

		// Embedding needs to go after the port in the AST
		if ( port != null && port.isEmbedding() ) {
			if ( n.location() != null ) {
				throw new ConfigurationException( String.format(
						"Attempting to override location of static output port (%s)\n" +
								"Port defined at %s:%d\n" +
								"Configuration took place at %s:%d",
						n.id(),
						n.context().sourceName(), n.context().line(),
						port.getContext().sourceName(), port.getContext().line()
				) );
			}

			Region region = null;
			Map< String, Region > namespaced = inputTree.getRegions().get( port.getModule() );

			if ( namespaced != null ) {
				region = namespaced.get( port.getProfile() );
			}

			if ( region == null ) {
				throw new ConfigurationException( String.format(
						"Attempting to embed profile '%s', but could not find profile.\n  " +
								"Configuration took place at %s:%d",
						port.getProfile(), port.getContext().source().toString(), port.getContext().line()
				) );
			}

			result.add( new EmbeddedServiceNode(
					n.context(),
					Constants.EmbeddedServiceType.JOLIE,
					String.format( "--conf %s %s %s.pkg",
							port.getProfile(), // TODO I think the file part is dead code.
							// origConfigFile != null appears to always be true
							origConfigFile != null ? origConfigFile.getAbsolutePath() :
									new File( thisPackage.getRoot(), configurationFile ).getAbsolutePath(),
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
		if ( port != null ) {
			URI location = n.location();
			String protocolId = n.protocolId();
			OLSyntaxNode properties = n.protocolConfiguration();
			if ( port.getLocation() != null ) {
				if ( location != null ) {
					throw new ConfigurationException( String.format(
							"Attempting to override location of static input port (%s)\n" +
									"Port defined at %s:%d\n" +
									"Configuration took place at %s:%d",
							n.id(),
							n.context().sourceName(), n.context().line(),
							port.getContext().sourceName(), port.getContext().line()
					) );
				}
				location = safeParse( port.getLocation() );
			}

			if ( port.getProtocol() != null && port.getProtocol().getType() != null ) {
				if ( protocolId != null ) {
					throw new ConfigurationException( String.format(
							"Attempting to override protocol definition of static input port (%s).\n" +
									"Port defined at %s:%d.\n" +
									"Configuration took place at %s:%d",
							n.id(),
							n.context().sourceName(), n.context().line(),
							port.getContext().sourceName(), port.getContext().line()
					) );
				}
				protocolId = port.getProtocol().getType();

				if ( port.getProtocol().getProperties() != null ) {
					properties = port.getProtocol().getProperties();
				}
			}

			InputPortInfo newPort = new InputPortInfo( n.context(), n.id(), location, protocolId, properties,
					n.aggregationList(), n.redirectionMap() );

			// Copy over interfaces and operations
			n.getInterfaceList().forEach( newPort::addInterface );
			newPort.operationsMap().putAll( n.operationsMap() );

			result.add( newPort );
		} else {
			result.add( n );
		}
		return result;
	}

	private List< OLSyntaxNode > processInterface( InterfaceDefinition n )
	{
		ConfigurationTree.ExternalInterface iface = mergedRegion.getInterface( n.name() );
		if ( iface != null ) {
			if ( !n.operationsMap().isEmpty() ) {
				throw new ConfigurationException( String.format(
						"Attempting to configure non-empty interface %s defined at %s:%d.\n  " +
								"Configuration took place at %s:%d",
						n.name(), n.context().source().toString(), n.context().line(),
						iface.context().source().toString(), iface.context().line()
				) );
			}

			List< OLSyntaxNode > result = new ArrayList<>();
			Program parsed = parsePackage( iface.fromPackage() );
			InterfaceDefinition target = parsed.children().stream()
					.filter( it -> it instanceof InterfaceDefinition )
					.map( it -> ( InterfaceDefinition ) it )
					.filter( it -> it.name().equals( iface.realName() ) )
					.findAny()
					.orElseThrow( () -> new ConfigurationException( "Unable to find interface '" + iface.realName() +
							"' from package '" + iface.fromPackage() + "'" ) );

			if ( target.operationsMap().isEmpty() ) {
				throw new ConfigurationException( String.format(
						"Attempting to inject the empty interface %s [%s:%d] into %s [%s:%d].\n  " +
								"Configuration took place at %s:%d",
						iface.realName(), iface.context().source().toString(), iface.context().line(),
						n.name(), n.context().source().toString(), n.context().line(),
						iface.context().source().toString(), iface.context().line()
				) );
			}

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
		if ( def.linkedTypeName().equals( "undefined" ) ) {
			definition = TypeDefinitionUndefined.getInstance();
		}
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
		String[] includePaths = includes.toArray( new String[0] );

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
