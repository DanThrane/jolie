package jolie.configuration;

import jolie.process.Process;

public class ExternalConstant
{
	private final String name;
	private final Process initializationProcess;

	public ExternalConstant( String name, Process initializationProcess )
	{
		this.name = name;
		this.initializationProcess = initializationProcess;
	}

	public String name()
	{
		return name;
	}

	public Process initializationProcess()
	{
		return initializationProcess;
	}
}
