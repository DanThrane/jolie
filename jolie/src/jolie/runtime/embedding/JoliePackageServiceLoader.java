package jolie.runtime.embedding;

import jolie.CommandLineException;
import jolie.CommandLineParser;
import jolie.Interpreter;
import jolie.configuration.Configuration;
import jolie.configuration.ExternalConfigurationProcessor;
import jolie.lang.parse.ast.ConfigurationTree;
import jolie.runtime.expression.Expression;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class JoliePackageServiceLoader extends EmbeddedServiceLoader
{
	private final Interpreter currentInterpreter;
	private final EmbeddedPackageServiceConfiguration configuration;
	private final Expression channelDest;
	private final JolieServiceLoader serviceLoader;

	protected JoliePackageServiceLoader( Expression channelDest, Interpreter currentInterpreter,
										 EmbeddedPackageServiceConfiguration configuration )
			throws IOException, CommandLineException
	{
		super( channelDest );
		this.channelDest = channelDest;
		this.currentInterpreter = currentInterpreter;
		this.configuration = configuration;
		this.serviceLoader = createLoader();
	}

	private JolieServiceLoader createLoader() throws IOException, CommandLineException
	{
		// Process region
		ConfigurationTree tree = new ConfigurationTree();
		tree.addRegion( configuration.configurationRegion() );
		ExternalConfigurationProcessor processor = new ExternalConfigurationProcessor( tree );
		Map< String, Configuration > processedTree = processor.process();

		CommandLineParser cli = currentInterpreter.cmdParser().makeCopy( arguments -> {
			arguments.setPackageSelf( configuration.configurationRegion().getPackageName() );
			arguments.setDeploymentProfile( "inline" );
			arguments.setProgramArguments( Collections.emptyList() );
			arguments.setInternalConfiguration( processedTree );
		} );

		return new JolieServiceLoader( channelDest, cli );
	}

	@Override
	public void load() throws EmbeddedServiceLoadingException
	{
		serviceLoader.load();
	}
}
