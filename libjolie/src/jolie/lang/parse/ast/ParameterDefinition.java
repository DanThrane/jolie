package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.context.ParsingContext;

public class ParameterDefinition extends OLSyntaxNode
{
	private final String name;
	private final TypeDefinition type;

	public ParameterDefinition( ParsingContext context, String name, TypeDefinition type )
	{
		super(context);
		this.name = name;
		this.type = type;
	}

	@Override
	public void accept( OLVisitor visitor )
	{
		visitor.visit( this );
	}

	public String name()
	{
		return name;
	}

	public TypeDefinition type()
	{
		return type;
	}
}
