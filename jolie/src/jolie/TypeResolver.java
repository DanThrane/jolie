package jolie;

import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.types.TypeChoiceDefinition;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.ast.types.TypeDefinitionLink;
import jolie.lang.parse.ast.types.TypeInlineDefinition;
import jolie.lang.parse.context.ParsingContext;
import jolie.runtime.typing.Type;
import jolie.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeResolver
{
	private final Interpreter interpreter;

	private final Map< String, Type > types = new HashMap<>();
	private final List< Pair< Type.TypeLink, TypeDefinition > > typeLinks = new ArrayList<>();
	private boolean insideType;
	private Type currType;

	public TypeResolver( Interpreter interpreter )
	{
		this.interpreter = interpreter;
	}

	public void process(Program program)
	{
		for ( OLSyntaxNode node : program.children() ) {
			if ( node instanceof TypeDefinition ) {
				buildType( ( TypeDefinition ) node );
			}
		}
		resolveTypeLinks();
	}

	private Type buildType( TypeDefinition n )
	{
		if ( n instanceof TypeInlineDefinition ) {
			visit( ( TypeInlineDefinition ) n );
		} else if ( n instanceof TypeChoiceDefinition ) {
			visit( ( TypeChoiceDefinition ) n );
		} else if ( n instanceof TypeDefinitionLink ) {
			visit( ( TypeDefinitionLink ) n );
		}
		return currType;
	}

	private void visit( TypeInlineDefinition n )
	{
		boolean backupInsideType = insideType;
		insideType = true;

		if ( n.untypedSubTypes() ) {
			currType = Type.create( n.nativeType(), n.cardinality(), true, null );
		} else {
			Map< String, Type > subTypes = new HashMap<>();
			if ( n.subTypes() != null ) {
				for( Map.Entry< String, TypeDefinition > entry : n.subTypes() ) {
					subTypes.put( entry.getKey(), buildType( entry.getValue() ) );
				}
			}
			currType = Type.create(
				n.nativeType(),
				n.cardinality(),
				false,
				subTypes
			);
		}

		insideType = backupInsideType;

		if ( !insideType ) {
			types.put( n.id(), currType );
		}
	}

	private void visit( TypeDefinitionLink n )
	{
		Type.TypeLink link = Type.createLink( n.linkedTypeName(), n.cardinality() );
		currType = link;
		typeLinks.add( new Pair<>( link, n ) );

		if ( !insideType ) {
			types.put( n.id(), currType );
		}
	}

	private void visit( TypeChoiceDefinition n )
	{
		final boolean wasInsideType = insideType;
		insideType = true;

		currType = Type.createChoice( n.cardinality(), buildType( n.left() ), buildType( n.right() ) );

		insideType = wasInsideType;
		if ( !insideType ) {
			types.put( n.id(), currType );
		}
	}

	private void resolveTypeLinks()
	{
		Type type;
		for( Pair< Type.TypeLink, TypeDefinition > pair : typeLinks ) {
			type = types.get( pair.key().linkedTypeName() );
			pair.key().setLinkedType( type );
			if ( type == null ) {
				error( pair.value().context(), "type link to " + pair.key().linkedTypeName() + " cannot be resolved" );
			}
		}
	}

	private void error( ParsingContext context, String message )
	{
		String s = context.sourceName() + ":" + context.line() + ": " + message;
		interpreter.logSevere( s );
	}

	public Map< String, Type > types()
	{
		return types;
	}
}
