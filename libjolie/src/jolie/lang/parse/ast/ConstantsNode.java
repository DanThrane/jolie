package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

import java.util.*;

public class ConstantsNode extends OLSyntaxNode
{
	private final Map< String, ConstantDefinitionNode > constantDefinitions = new HashMap<>();

	public ConstantsNode( ParsingContext context )
	{
		super( context );
	}

	public void addDefinition( ConstantDefinitionNode node )
	{
		constantDefinitions.put( node.name(), node );
	}

	public Map< String, ConstantDefinitionNode > constants()
	{
		return Collections.unmodifiableMap( constantDefinitions );
	}

	@Override
	public void accept( OLVisitor visitor )
	{
		visitor.visit( this );
	}
}
