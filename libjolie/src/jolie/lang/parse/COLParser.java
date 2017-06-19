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

package jolie.lang.parse;

import jolie.lang.JoliePackage;
import jolie.lang.parse.ast.ConfigurationTree;
import jolie.lang.parse.ast.ConfigurationTree.ExternalPort;
import jolie.lang.parse.ast.ConfigurationTree.PortProtocol;
import jolie.lang.parse.ast.ConfigurationTree.Region;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.expression.*;
import jolie.lang.parse.context.ParsingContext;
import jolie.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static jolie.lang.parse.Scanner.TokenType.*;

/**
 * Parser for a .col file.
 *
 * @author Dan Sebastian Thrane <dthrane@gmail.com>
 */
public class COLParser extends AbstractParser
{
	private static final String INPUT_PORT = "inputPort";
	private static final String OUTPUT_PORT = "outputPort";
	private static final String INTERFACE = "interface";
	private static final String CONFIG_DIRECTORY = "conf";
	private static final String FILE_EXTENSION = ".col";

	private final ConfigurationTree configurationTree = new ConfigurationTree();
	private File workingDirectory;
	private final Map< String, JoliePackage > knownModules;
	private final Deque< String > usedModules = new ArrayDeque<>();
	private final Set< String > visitedModules = new HashSet<>();

	private Region currentRegion = null;
	private Set< URI > includedFiles = new HashSet<>();

	/**
	 * Constructor
	 *
	 * @param scanner          The scanner to use during the parsing procedure.
	 * @param workingDirectory The working directory. Used for resolving includes
	 * @param knownModules     The modules that are known to this parser
	 */
	public COLParser( Scanner scanner, File workingDirectory, Map< String, JoliePackage > knownModules )
	{
		super( scanner );
		this.workingDirectory = workingDirectory;
		this.knownModules = knownModules;
		includedFiles.add( scanner.source() );
	}

	public ConfigurationTree parse()
			throws IOException, ParserException
	{
		parseStartingAtCurrentScanner();

		while ( !usedModules.isEmpty() ) {
			String next = usedModules.pop();

			// Every time a module is configured it will be added to the usedModules queue
			// As a result we'll need to break out if we have already configured it before
			if ( visitedModules.contains( next ) ) continue;
			visitedModules.add( next );

			JoliePackage module = knownModules.get( next );
			assert module != null;

			// Parse all files found in the CONFIG_DIRECTORY of each module
			File confDirectory = new File( module.getRoot(), CONFIG_DIRECTORY );
			if ( confDirectory.exists() && confDirectory.isDirectory() ) {
				File[] files = confDirectory.listFiles();
				if ( files == null ) continue;
				for ( File includeFile : files ) {
					if ( includeFile.getName().endsWith( FILE_EXTENSION ) ) {
						try ( FileInputStream stream = new FileInputStream( includeFile ) ) {
							workingDirectory = confDirectory;
							// Include guard is needed to avoid circular includes
							URI includeURI = includeFile.toURI();
							if ( !includedFiles.contains( includeURI ) ) {
								setScanner( new Scanner( stream, includeURI, "US-ASCII" ) );
								parseStartingAtCurrentScanner();
							}
						}
					}
				}
			}
		}
		return configurationTree;
	}

	private void parseStartingAtCurrentScanner() throws IOException, ParserException
	{
		getToken();
		Scanner.Token t;
		do {
			t = token;
			parseInclude();
			parseRegions();
		} while ( t != token );

		if ( t.isNot( EOF ) ) {
			throwException( "Invalid token encountered" );
		}
	}

	private void parseInclude() throws IOException, ParserException
	{
		if ( token.type() == INCLUDE ) {
			getToken();

			assertToken( STRING, "expected file location" );
			File includeFile = new File( workingDirectory, token.content() );
			getToken();

			URI includeURI = includeFile.toURI();
			if ( !includedFiles.contains( includeURI ) ) {
				includedFiles.add( includeURI );
				Scanner oldScanner = scanner();
				Scanner.Token nextToken = token;
				File oldWorkingDirectory = workingDirectory;

				try ( FileInputStream stream = new FileInputStream( includeFile ) ) {
					workingDirectory = includeFile.getParentFile();
					setScanner( new Scanner( stream, includeURI, "US-ASCII" ) );
					parseStartingAtCurrentScanner();
				}

				workingDirectory = oldWorkingDirectory;
				setScanner( oldScanner );
				token = nextToken;
			}
		}
	}

	private void parseRegions() throws IOException, ParserException
	{
		while ( token.type() == PROFILE || token.type() == CONFIGURES ) {
			Region region = parseRegion();
			Region existing = configurationTree.getRegion( region.getPackageName(), region.getProfileName() );
			if ( existing != null ) {
				throw new ParserException( region.getContext(), String.format(
						"Attempting to redefine configuration unit '%s' of '%s', first defined at %s:%d",
						region.getProfileName(),
						region.getPackageName(),
						existing.getContext().sourceName(), existing.getContext().line()
				) );
			}
			configurationTree.addRegion( region );
		}
	}

	private Region parseRegion() throws IOException, ParserException
	{
		ParsingContext context = getContext();
		String profileName = null;
		String extendsProfile = null;
		if ( token.type() == PROFILE ) {
			getToken();
			assertToken( STRING, "expected profile name" );
			profileName = token.content();
			getToken();
		}

		assertToken( CONFIGURES, "expected configures" );
		getToken();

		assertToken( STRING, "expected module name" );
		String moduleName = token.content();
		getToken();

		if ( !knownModules.containsKey( moduleName ) ) {
			throwException( String.format(
					"Attempting to configure module %s, but module is not known by the engine\n" +
							"The following modules are known: %s",
					moduleName,
					knownModules.keySet()
			) );
		}


		usedModules.add( moduleName );

		if ( token.type() == EXTENDS ) {
			getToken();
			assertToken( STRING, "expected extends profile name" );
			extendsProfile = token.content();
			getToken();
		}

		if ( profileName == null ) profileName = moduleName;

		return parseInlineRegion( profileName, moduleName, extendsProfile, context );
	}

	private Region parseInlineRegion( String profileName, String packageName, String extendsProfile,
									  ParsingContext context )
			throws IOException, ParserException
	{
		currentRegion = new Region();
		currentRegion.setProfileName( profileName );
		currentRegion.setPackageName( packageName );
		currentRegion.setExtendsProfile( extendsProfile );
		currentRegion.setContext( context );

		eat( LCURLY, "expected region body after region definition" );

		if ( token.type() != RCURLY ) {
			parseDefinition();

			if ( token.type() == COMMA ) {
				while ( token.type() != RCURLY ) {
					assertToken( COMMA, "expected comma before new region definition" );
					getToken();
					if ( token.type() == RCURLY ) {
						break; // Allow trailing comma
					}
					parseDefinition();
				}
			}
		}

		assertToken( RCURLY, "expected '}' at end of region body" );
		getToken();
		return currentRegion;
	}

	private void parseDefinition() throws ParserException, IOException
	{
		if ( token.isKeyword( INPUT_PORT ) || token.isKeyword( OUTPUT_PORT ) ) {
			currentRegion.addPort( parsePort() );
		} else if ( token.isKeyword( INTERFACE ) ) {
			currentRegion.addInterface( parseInterface() );
		} else {
			currentRegion.addParameter( parseConstantDefinition() );
		}
	}

	private ConfigurationTree.ExternalInterface parseInterface() throws ParserException, IOException
	{
		ParsingContext context = getContext();
		getToken();
		assertToken( ID, "expected identifier to name the interface" );
		String name = token.content();
		getToken();
		assertToken( ASSIGN, "expected '='" );
		getToken();
		assertToken( ID, "expected identifier to replace interface" );
		String realName = token.content();
		getToken();
		assertToken( FROM, "expected 'from'" );
		getToken();
		assertToken( STRING, "expected package name" );
		String packageName = token.content();
		getToken();
		return new ConfigurationTree.ExternalInterface( context, name, realName, packageName );
	}

	private ConfigurationTree.ExternalParamNode parseConstantDefinition() throws ParserException, IOException
	{
		VariablePathNode path = parseVariablePath();
		assertToken( ASSIGN, "expected '=' in constant definition" );
		getToken();
		OLSyntaxNode value = parseConstantValue();
		return new ConfigurationTree.ExternalParamNode( getContext(), path, value );
	}

	private ExternalPort parsePort() throws ParserException, IOException
	{
		ConfigurationTree.PortType type;
		String name;
		String location = null;
		String protocolType = null;
		OLSyntaxNode protocolProperties = null;
		ParsingContext context = getContext();

		boolean isInputPort = token.isKeyword( INPUT_PORT );
		type = isInputPort ? ConfigurationTree.PortType.INPUT : ConfigurationTree.PortType.OUTPUT;
		getToken();

		assertToken( ID, "expected port name" );
		name = token.content();
		getToken();

		if ( token.type() == EMBEDS ) {
			if ( isInputPort ) {
				throwException( "Cannot embed in an input port!" );
			}

			getToken();

			assertToken( STRING, "expected module name" );
			String module = token.content();
			getToken();
			eat( WITH, "expected with" );
			assertToken( STRING, "expected embedding name" );
			String embeds = token.content();
			getToken();
			usedModules.add( module );
			return new ExternalPort( name, type, embeds, module, context );
		} else if ( token.type() == LCURLY ) {
			getToken();

			while ( token.type() != RCURLY ) {
				if ( token.isKeyword( "Location" ) ) {
					getToken();
					eat( COLON, "expected ':'" );

					assertToken( STRING, "expected service location" );
					location = token.content();
					getToken();
				} else if ( token.isKeyword( "Protocol" ) ) {
					getToken();
					eat( COLON, "expected ':'" );

					assertToken( ID, "expected protocol type" );
					protocolType = token.content();
					getToken();

					if ( token.type() == LCURLY ) {
						protocolProperties = parseInlineTreeExpression( new VoidExpressionNode( getContext() ) );
					}
				} else {
					throwException( "expected 'Location' or 'Protocol' definition in port body" );
				}
			}

			assertToken( RCURLY, "expected end of port body" );
			getToken();

			return new ExternalPort( name, type, location, new PortProtocol( protocolType, protocolProperties ),
					context );
		} else {
			throwException( "expected port body" );
			return null;
		}
	}

	private OLSyntaxNode parseConstantValue() throws IOException, ParserException
	{
		OLSyntaxNode root = parsePrimitiveValue();
		if ( root == null ) throwException( "expected value" );

		if ( token.type() == LCURLY ) {
			return parseInlineTreeExpression( root );
		} else {
			return root;
		}
	}

	private OLSyntaxNode parsePrimitiveValue() throws IOException, ParserException
	{
		OLSyntaxNode result;
		switch ( token.type() ) {
			case STRING:
				result = new ConstantStringExpression( getContext(), token.content() );
				break;
			case INT:
				result = new ConstantIntegerExpression( getContext(), Integer.parseInt( token.content() ) );
				break;
			case LONG:
				result = new ConstantLongExpression( getContext(), Long.parseLong( token.content() ) );
				break;
			case DOUBLE:
				result = new ConstantDoubleExpression( getContext(), Double.parseDouble( token.content() ) );
				break;
			case TRUE:
				result = new ConstantBoolExpression( getContext(), true );
				break;
			case FALSE:
				result = new ConstantBoolExpression( getContext(), false );
				break;
			case LCURLY:
				return parseInlineTreeExpression( new VoidExpressionNode( getContext() ) );
			default:
				return null;
		}

		getToken();
		return result;
	}

	private OLSyntaxNode parseUnsignedInteger() throws ParserException, IOException
	{
		assertToken( INT, "expected int" );
		int value = Integer.parseInt( token.content() );
		ConstantIntegerExpression expr = new ConstantIntegerExpression( getContext(), value );
		if ( value < 0 ) {
			throwException( "integer value was " + value + ", but value cannot be negative here" );
		}
		getToken();
		return expr;
	}

	private OLSyntaxNode parseInlineTreeExpression( OLSyntaxNode rootExpression )
			throws IOException, ParserException
	{
		eat( Scanner.TokenType.LCURLY, "expected {" );

		boolean keepRun = true;
		VariablePathNode path;
		OLSyntaxNode expression;

		List< Pair< VariablePathNode, OLSyntaxNode > > assignments = new ArrayList<>();

		while ( keepRun ) {
			eat( Scanner.TokenType.DOT, "expected ." );

			path = parseVariablePath();
			eat( Scanner.TokenType.ASSIGN, "expected =" );
			expression = parseConstantValue();

			assignments.add( new Pair<>( path, expression ) );

			if ( token.is( Scanner.TokenType.COMMA ) ) {
				getToken();
			} else {
				keepRun = false;
			}
		}

		eat( Scanner.TokenType.RCURLY, "expected }" );

		//noinspection unchecked
		return new InlineTreeExpressionNode( rootExpression.context(), rootExpression,
				assignments.toArray( new Pair[ 0 ] ) );
	}

	private VariablePathNode parseVariablePath()
			throws ParserException, IOException
	{
		String varId;

		if ( token.is( Scanner.TokenType.ID ) ) {
			varId = token.content();
			getToken();
		} else if ( token.is( Scanner.TokenType.LPAREN ) ) {
			getToken();
			assertToken( Scanner.TokenType.STRING, "Expected string" );
			varId = token.content();
			getToken();
			eat( Scanner.TokenType.RPAREN, "Expected ')'" );
		} else {
			throwException( "Expected variable path" );
			return null;
		}

		return parseVariablePathFromRootIdentifier( varId );
	}

	private VariablePathNode parseVariablePathFromRootIdentifier( String varId )
			throws IOException, ParserException
	{
		VariablePathNode path = new VariablePathNode( getContext(), VariablePathNode.Type.NORMAL );
		OLSyntaxNode temporaryExpression;

		temporaryExpression = parseVariablePathIndexing();
		path.append( new Pair<>( new ConstantStringExpression( getContext(), varId ), temporaryExpression ) );

		OLSyntaxNode nodeExpr = null;
		while ( token.is( Scanner.TokenType.DOT ) ) {
			getToken();
			if ( token.is( Scanner.TokenType.LPAREN ) ) {
				getToken();
				assertToken( Scanner.TokenType.STRING, "Expected string in variable path" );
				nodeExpr = new ConstantStringExpression( getContext(), token.content() );
				getToken();
				eat( Scanner.TokenType.RPAREN, "Expected ')'" );
			} else if ( token.isIdentifier() ) {
				nodeExpr = new ConstantStringExpression( getContext(), token.content() );
				getToken();
			} else {
				throwException( "expected nested node identifier" );
			}

			temporaryExpression = parseVariablePathIndexing();
			path.append( new Pair<>( nodeExpr, temporaryExpression ) );
		}
		return path;
	}

	private OLSyntaxNode parseVariablePathIndexing() throws IOException, ParserException
	{
		if ( token.type() != LSQUARE ) return null;
		getToken();
		OLSyntaxNode expr = parseUnsignedInteger();
		eat( Scanner.TokenType.RSQUARE, "expected ]" );
		return expr;
	}
}
