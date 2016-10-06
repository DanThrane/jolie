package jolie.configuration;

import jolie.process.AssignmentProcess;
import jolie.process.Process;
import jolie.process.SequentialProcess;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

public class ASTProtocolConfiguration implements ProtocolConfiguration
{
	private final String protocolId;
	private final Process configurationProcess;

	public ASTProtocolConfiguration( String protocolId, Process configurationProcess )
	{
		this.protocolId = protocolId;
		this.configurationProcess = configurationProcess;
	}

	@Override
	public List< Process > configure( VariablePath protocolVariablePath )
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
