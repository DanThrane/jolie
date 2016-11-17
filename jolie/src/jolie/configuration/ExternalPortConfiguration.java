package jolie.configuration;

import jolie.CommandLineException;
import jolie.CommandLineParser;
import jolie.Interpreter;
import jolie.configuration.ExternalConfigurationProcessor.ProcessedPort;
import jolie.process.DeepCopyProcess;
import jolie.process.Process;
import jolie.process.TransformationReason;
import jolie.runtime.ExitingException;
import jolie.runtime.FaultException;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;
import jolie.runtime.embedding.EmbeddedServiceLoadingException;
import jolie.runtime.embedding.JolieServiceLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ExternalPortConfiguration implements PortConfiguration
{
	private final ProcessedPort port;
	private final Interpreter interpreter;
	private final Map< String, Configuration > configurationTree;

	public ExternalPortConfiguration( ProcessedPort port, Interpreter interpreter,
									  Map< String, Configuration > configurationTree )
	{
		this.port = port;
		this.interpreter = interpreter;
		this.configurationTree = configurationTree;
	}

	@Override
	public List< Process > configure( VariablePath locationVariablePath, VariablePath protocolVariablePath )
	{
		if ( port.getEmbedding() != null ) try {
			Configuration configuration = configurationTree.get( port.getEmbedding() );
			if ( configuration == null ) {
				throw new IllegalStateException( "Cannot find configuration for '" + port.getEmbedding() +
						"'. Which was needed for embedding in output port '" + port.getName() + "'" );
			}

			CommandLineParser cli = interpreter.cmdParser().makeCopy( arguments -> {
				arguments.setPackageSelf( configuration.getPackageName() );
				arguments.setDeploymentProfile( configuration.getProfileName() );
				arguments.setProgramArguments( Collections.emptyList() );
			} );

			// TODO This isn't a pretty solution.
			// But we need to postpone this embedding procedure, such that the variables are setup properly.
			return Collections.singletonList( new Process()
			{
				@Override
				public void run() throws FaultException, ExitingException
				{
					try {
						JolieServiceLoader loader = new JolieServiceLoader( locationVariablePath, cli );
						loader.load();
					} catch ( EmbeddedServiceLoadingException | IOException e ) {
						throw new RuntimeException("Unable to embed external service", e); // TODO Make a more clean exit
					}
				}

				@Override
				public Process clone( TransformationReason reason )
				{
					return null;
				}

				@Override
				public boolean isKillable()
				{
					return false;
				}
			} );
		} catch ( IOException | CommandLineException e ) {
			throw new RuntimeException( e );
		}
		else {
			Value protocolValue = port.getProtocolProperties() != null ?
					port.getProtocolProperties().clone() :
					Value.create();

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
