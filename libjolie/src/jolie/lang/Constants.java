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

package jolie.lang;

import java.nio.charset.Charset;
import jolie.lang.parse.Scanner;
import jolie.util.Range;

public final class Constants
{
	static public class Manifest
	{
		final public static String ChannelExtension = "X-JOLIE-ChannelExtension";
		final public static String ListenerExtension = "X-JOLIE-ListenerExtension";
		final public static String ProtocolExtension = "X-JOLIE-ProtocolExtension";
	}

	static public enum Predefined
	{
		ATTRIBUTES( "@Attributes", "@Attributes" ),
		CHARSET( "@Charset", "@Charset" ),
		CONTENT_TYPE( "@ContentType", "@ContentType" ),
		CONTENT_TRANSFER_ENCODING( "@ContentTransferEncoding", "@ContentTransferEncoding" ),
		COOKIES( "@Cookies", "@Cookies" ),
		FORMAT( "@Format", "@Format" ),
		HTTP_BASIC_AUTHENTICATION( "@HttpBasicAuthentication", "@HttpBasicAuthentication" ),
		PI( "PI", java.lang.Math.PI ),
		REDIRECT( "@Redirect", "@Redirect" ),
		QUERY_STRING( "@QueryString", "@QueryString" ),
		USER_AGENT( "@UserAgent", "@UserAgent" );
		private final String id;
		private final Scanner.Token token;

		public static Predefined get( String id )
		{
			for ( Predefined p : Predefined.values() ) {
				if ( p.id.equals( id ) ) {
					return p;
				}
			}
			return null;
		}

		Predefined( String id, String content )
		{
			this.id = id;
			this.token = new Scanner.Token( Scanner.TokenType.STRING, content );
		}

		Predefined( String id, Integer content )
		{
			this.id = id;
			this.token = new Scanner.Token( Scanner.TokenType.INT, content.toString() );
		}

		Predefined( String id, Double content )
		{
			this.id = id;
			this.token = new Scanner.Token( Scanner.TokenType.REAL, content.toString() );
		}

		public String id()
		{
			return id;
		}

		public Scanner.Token token()
		{
			return token;
		}
	}

	static public final Range RANGE_ONE_TO_ONE = new Range( 1, 1 );

	public static interface Keywords
	{
		public static final String DEFAULT_HANDLER_NAME = "default";
	}

	public static final String TYPE_MISMATCH_FAULT_NAME = "TypeMismatch";

	public static final String INPUT_PORTS_NODE_NAME = "inputPorts";
	public static final String PROTOCOL_NODE_NAME = "protocol";
	public static final String LOCATION_NODE_NAME = "location";
	public static final String LOCAL_LOCATION_KEYWORD = "local";
	public static final String VERSION = "JOLIE 1.0_alpha4";
	public static final String COPYRIGHT = "(C) 2006-2007-2008 the JOLIE team";
	//public static String newLineString = System.getProperty( "line.separator" );
	public static final String fileSeparator = System.getProperty( "file.separator" );
	public static final String pathSeparator = System.getProperty( "path.separator" );
	public static final String GLOBAL = "global";
	public static final Charset defaultCharset;
	

	static {
		defaultCharset = Charset.forName( "UTF-8" );
	}

	public enum EmbeddedServiceType
	{
		JOLIE, JAVA, JAVASCRIPT, UNSUPPORTED
	}

	public static EmbeddedServiceType stringToEmbeddedServiceType( String str )
	{
		if ( "jolie".equalsIgnoreCase( str ) ) {
			return EmbeddedServiceType.JOLIE;
		} else if ( "java".equalsIgnoreCase( str ) ) {
			return EmbeddedServiceType.JAVA;
		}

		return EmbeddedServiceType.UNSUPPORTED;
	}

	public enum ExecutionMode
	{
		SINGLE, SEQUENTIAL, CONCURRENT
	}

	public enum OperationType
	{
		ONE_WAY,
		REQUEST_RESPONSE
	}

	public enum OperandType
	{
		ADD, SUBTRACT,
		MULTIPLY, DIVIDE,
		MODULUS
	}

	public static long serialVersionUID()
	{
		return 1L;
	}
}