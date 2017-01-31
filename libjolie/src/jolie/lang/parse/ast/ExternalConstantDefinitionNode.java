package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.context.ParsingContext;

public class ExternalConstantDefinitionNode extends ConstantDefinitionNode
{
	private final TypeDefinition typeDefinition;

	public ExternalConstantDefinitionNode( ParsingContext context, String name, TypeDefinition typeDefinition )
	{
		super( context, name );
		this.typeDefinition = typeDefinition;
	}

	public TypeDefinition typeDefinition()
	{
		return typeDefinition;
	}

	@Override
	public void accept( OLVisitor visitor )
	{
		visitor.visit( this );
	}
}
