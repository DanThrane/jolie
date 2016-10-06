package jolie.configuration;

import jolie.process.DeepCopyProcess;
import jolie.process.Process;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

public class ExternalProtocolConfiguration implements ProtocolConfiguration
{
	private final Value value;

	public ExternalProtocolConfiguration( Value value )
	{
		this.value = value;
	}

	@Override
	public List<Process> configure( VariablePath protocolVariablePath )
	{
		return Collections.singletonList( new DeepCopyProcess( protocolVariablePath, value ) );
	}

	@Override
	public String getProtocolIdentifier()
	{
		return value.strValue();
	}
}
