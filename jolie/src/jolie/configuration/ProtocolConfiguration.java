package jolie.configuration;

import jolie.process.Process;
import jolie.runtime.VariablePath;

import java.util.List;

public interface ProtocolConfiguration
{
	List<Process> configure( VariablePath protocolVariablePath );
	String getProtocolIdentifier();
}
