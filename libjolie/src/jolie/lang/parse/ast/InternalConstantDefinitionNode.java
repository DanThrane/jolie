package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

public class InternalConstantDefinitionNode extends ConstantDefinitionNode
{
	private final OLSyntaxNode value;

	public InternalConstantDefinitionNode( ParsingContext context, String name, OLSyntaxNode value )
	{
		super( context, name );
		this.value = value;
	}

	public OLSyntaxNode value()
	{
		return value;
	}

	@Override
	public void accept( OLVisitor visitor )
	{
		visitor.visit( this );
	}
}
