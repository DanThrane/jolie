package jolie.lang.parse.ast;

import jolie.lang.parse.context.ParsingContext;

public abstract class ConstantDefinitionNode extends OLSyntaxNode
{
	private final String name;

	public ConstantDefinitionNode( ParsingContext context, String name )
	{
		super( context );
		this.name = name;
	}

	public String name()
	{
		return name;
	}
}
