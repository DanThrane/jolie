package jolie.configuration;

import jolie.lang.parse.context.ParsingContext;
import jolie.runtime.typing.Type;

public class ProcessedParameterDefinition
{
	private final ParsingContext context;
	private final String name;
	private final Type type;

	public ProcessedParameterDefinition( ParsingContext context, String name, Type type )
	{
		this.context = context;
		this.name = name;
		this.type = type;
	}

	public ParsingContext context()
	{
		return context;
	}

	public String name()
	{
		return name;
	}

	public Type type()
	{
		return type;
	}
}
