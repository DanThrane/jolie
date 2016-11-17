package jolie.configuration;

import jolie.process.NullProcess;
import jolie.process.Process;
import jolie.runtime.VariablePath;

import java.util.Collections;
import java.util.List;

public enum NullPortConfiguration implements PortConfiguration
{
	INSTANCE;

	@Override
	public List< Process > configure( VariablePath locationVariablePath, VariablePath protocolVariablePath )
	{
		return Collections.singletonList( NullProcess.getInstance() );
	}

	@Override
	public String getProtocolIdentifier()
	{
		return null;
	}

}
