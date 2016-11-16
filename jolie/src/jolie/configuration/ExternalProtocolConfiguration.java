package jolie.configuration;

import jolie.CommandLineException;
import jolie.Interpreter;
import jolie.configuration.ExternalConfigurationProcessor.ProcessedPort;
import jolie.process.DeepCopyProcess;
import jolie.process.Process;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;
import jolie.runtime.embedding.EmbeddedServiceLoadingException;
import jolie.runtime.embedding.JolieServiceLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExternalProtocolConfiguration implements ProtocolConfiguration
{
	private final ProcessedPort port;
	private final Interpreter interpreter;
	private final Map< String, Configuration > configurationTree;

	public ExternalProtocolConfiguration( ProcessedPort port, Interpreter interpreter,
										  Map< String, Configuration > configurationTree )
	{
		this.port = port;
		this.interpreter = interpreter;
		this.configurationTree = configurationTree;
	}

	@Override
	public List< Process > configure( VariablePath protocolVariablePath )
	{
		if ( port.getEmbedding() != null ) {
			// TODO We need to put this on the port not on the protocol.
			try {
				Configuration configuration = configurationTree.get( port.getEmbedding() );
				if ( configuration == null ) {
					throw new IllegalStateException( "Cannot find configuration for '" + port.getEmbedding() +
							"'. Which was needed for embedding in output port '" + port.getName() + "'" );
				}
				JolieServiceLoader loader = new JolieServiceLoader(
						protocolVariablePath,
						interpreter,
						// TODO Need to know which config file to load from
						// TODO Do we always want to pass configuration from the root directory?
						"--deploy " + configuration.getProfileName() + " " + "deployment.col"
				);
				loader.load();
			} catch ( IOException | CommandLineException | EmbeddedServiceLoadingException e ) {
				throw new RuntimeException( e );
			}
			return Collections.emptyList();
		} else {
			Value protocolValue = port.getProtocolProperties().clone();
			protocolValue.setValue( port.getProtocolType() );

			return Collections.singletonList( new DeepCopyProcess( protocolVariablePath, protocolValue ) );
		}
	}

	@Override
	public String getProtocolIdentifier()
	{
		return port.getProtocolType();
	}
}
