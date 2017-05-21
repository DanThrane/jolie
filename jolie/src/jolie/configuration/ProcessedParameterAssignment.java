package jolie.configuration;

import jolie.lang.parse.context.ParsingContext;
import jolie.runtime.VariablePath;
import jolie.runtime.expression.Expression;

public class ProcessedParameterAssignment
{
	private final ParsingContext assignmentContext;
	private final VariablePath path;
	private final Expression expression;

	public ProcessedParameterAssignment( ParsingContext assignmentContext, VariablePath path, Expression expression )
	{
		this.assignmentContext = assignmentContext;
		this.path = path;
		this.expression = expression;
	}

	public ParsingContext context()
	{
		return assignmentContext;
	}

	public VariablePath path()
	{
		return path;
	}

	public Expression expression()
	{
		return expression;
	}
}
