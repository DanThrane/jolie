package jolie.configuration;

import jolie.process.Process;

public class Constant
{
	private final String name;
	private final Process initializationProcess;

	public Constant( String name, Process initializationProcess )
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
