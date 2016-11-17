package jolie.configuration;

import jolie.process.Process;
import jolie.runtime.VariablePath;

import java.util.List;

public interface PortConfiguration
{
	List<Process> configure( VariablePath locationVariablePath, VariablePath protocolVariablePath );
	String getProtocolIdentifier();
}
