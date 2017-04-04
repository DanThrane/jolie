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

import jolie.lang.parse.ast.ConfigurationTree;
import jolie.lang.parse.ast.ConfigurationTree.ExternalPort;
import jolie.lang.parse.ast.ConfigurationTree.PortProtocol;
import jolie.lang.parse.ast.ConfigurationTree.Region;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.expression.*;
import jolie.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

	private final ConfigurationTree configurationTree = new ConfigurationTree();
	private File workingDirectory;

	private Region currentRegion = null;

	/**
	 * Constructor
	 *
	 * @param scanner          The scanner to use during the parsing procedure.
	 * @param workingDirectory The working directory. Used for resolving includes
	 */
	public COLParser( Scanner scanner, File workingDirectory )
	{
		super( scanner );
		this.workingDirectory = workingDirectory;
	}

	public ConfigurationTree parse()
			throws IOException, ParserException
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
		return configurationTree;
	}

	private void parseInclude() throws IOException, ParserException
	{
		if ( token.type() == INCLUDE ) {
			getToken();

			assertToken( STRING, "expected file location" );
			File includeFile = new File( workingDirectory, token.content() );
			getToken();

			Scanner oldScanner = scanner();
			Scanner.Token nextToken = token;
			File oldWorkingDirectory = workingDirectory;

			try ( FileInputStream stream = new FileInputStream( includeFile ) ) {
				workingDirectory = includeFile.getParentFile();
				setScanner( new Scanner( stream, includeFile.toURI(), "US-ASCII" ) );
				parse();
			}

			workingDirectory = oldWorkingDirectory;
			setScanner( oldScanner );
			token = nextToken;
		}
	}

	private void parseRegions() throws IOException, ParserException
	{
		while ( token.type() == PROFILE || token.type() == CONFIGURES ) {
			configurationTree.addRegion( parseRegion() );
		}
	}

	private Region parseRegion() throws IOException, ParserException
	{
		String profileName = null;
		if ( token.type() == PROFILE ) {
			getToken();

			assertToken( STRING, "expected profile name" );
			profileName = token.content();
			getToken();
		}

		assertToken( CONFIGURES, "expected configures" );
		getToken();

		assertToken( STRING, "expected package name" );
		String packageName = token.content();
		getToken();

		if ( profileName == null ) profileName = packageName;

		return parseInlineRegion( profileName, packageName );
	}

	private Region parseInlineRegion( String profileName, String packageName ) throws IOException, ParserException
	{
		currentRegion = new Region();
		currentRegion.setProfileName( profileName );
		currentRegion.setPackageName( packageName );

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
		} else {
			currentRegion.addConstant( parseConstantDefinition() );
		}
	}

	private ConfigurationTree.ExternalConstantNode parseConstantDefinition() throws ParserException, IOException
	{
		assertToken( ID, "expected identifier for beginning of constant definition" );
		String name = token.content();
		getToken();
		assertToken( ASSIGN, "expected '=' in constant definition" );
		getToken();
		OLSyntaxNode value = parseConstantValue();
		return new ConfigurationTree.ExternalConstantNode( getContext(), name, value );
	}

	private ExternalPort parsePort() throws ParserException, IOException
	{
		ConfigurationTree.PortType type;
		String name;
		String location = null;
		String protocolType = null;
		OLSyntaxNode protocolProperties = null;

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

			assertToken( STRING, "expected embedding name" );
			String embeds = token.content();
			getToken();
			return new ExternalPort( name, type, embeds );
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

			return new ExternalPort( name, type, location, new PortProtocol( protocolType, protocolProperties ) );
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
			// TODO Should we allow multiplication, addition, subtraction, division
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
