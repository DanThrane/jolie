package jolie.configuration;

import jolie.runtime.Value;

import java.util.HashMap;
import java.util.Map;

public class ConfigurationHolder
{
	private final Configuration configuration;

	public ConfigurationHolder()
	{
		Map< String, Value > inputPorts = new HashMap<>();
		Map< String, Value > outputPorts = new HashMap<>();
		Map< String, Value > constants = new HashMap<>();

		Value fooInput = Value.create();
		fooInput.getNewChild( "location" ).setValue( "socket://localhost:9001" );
		fooInput.getNewChild( "protocol" ).setValue( "sodep" );
		inputPorts.put( "Foo", fooInput );

		Value fooOutput = Value.create();
		fooOutput.getNewChild( "location" ).setValue( "socket://localhost:9001" );
		Value protocol = fooOutput.getNewChild( "protocol" );
		protocol.setValue( "sodep" );
		protocol.getNewChild( "debug" ).setValue( true );
		outputPorts.put( "Foo", fooOutput );

		configuration = new Configuration( inputPorts, outputPorts, constants );
	}

	public Configuration getConfiguration()
	{
		return configuration;

	}
}
