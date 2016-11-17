package jolie.configuration;

import jolie.process.AssignmentProcess;
import jolie.process.Process;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;

import java.util.LinkedList;
import java.util.List;

public class ASTPortConfiguration implements PortConfiguration
{
	private final String protocolId;
	private final Process configurationProcess;

	public ASTPortConfiguration( String protocolId, Process configurationProcess )
	{
		this.protocolId = protocolId;
		this.configurationProcess = configurationProcess;
	}

	@Override
	public List< Process > configure( VariablePath locationVariablePath, VariablePath protocolVariablePath )
	{
		List< Process > children = new LinkedList<>();
		if ( protocolId != null ) {
			children.add( new AssignmentProcess( protocolVariablePath, Value.create( protocolId ) ) );
		}
		children.add( configurationProcess );
		return children;
	}

	@Override
	public String getProtocolIdentifier()
	{
		return protocolId;
	}

}
