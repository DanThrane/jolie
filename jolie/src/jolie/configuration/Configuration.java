package jolie.configuration;

import jolie.configuration.ExternalConfigurationProcessor.ProcessedPort;
import jolie.lang.parse.ast.ConfigurationTree;
import jolie.runtime.Value;

import java.util.HashMap;
import java.util.Map;

public class Configuration
{
	private final String profileName;
	private final String packageName;
	private final Map< String, ProcessedPort > inputPorts;
	private final Map< String, ProcessedPort > outputPorts;
	private final Map< String, Value > constants;

	public Configuration( String profileName, String packageName, Map< String, ProcessedPort > inputPorts, Map< String, ProcessedPort > outputPorts,
						  Map< String, Value > constants )
	{
		this.profileName = profileName;
		this.packageName = packageName;
		this.inputPorts = inputPorts;
		this.outputPorts = outputPorts;
		this.constants = constants;
	}

	public static Configuration merge( Configuration unit, Configuration defaultUnit )
	{
		if ( !unit.getPackageName().equals( defaultUnit.getPackageName() ) ) {
			throw new IllegalArgumentException( "Default unit doesn't have a matching package name." );
		}

		String profileName = unit.getProfileName();
		String packageName = unit.getPackageName();
		Map< String, ProcessedPort > inputPorts = new HashMap<>();
		Map< String, ProcessedPort > outputPorts = new HashMap<>();
		Map< String, Value > constants = new HashMap<>();

		inputPorts.putAll( unit.inputPorts );
		outputPorts.putAll( unit.outputPorts );
		constants.putAll( unit.constants );

		mergePorts( inputPorts, defaultUnit.inputPorts );
		mergePorts( outputPorts, defaultUnit.outputPorts );

		return new Configuration( profileName, packageName, inputPorts, outputPorts, constants );
	}

	private static void mergePorts( Map< String, ProcessedPort > destinationPorts,
									Map< String, ProcessedPort > defaultPorts )
	{
		defaultPorts.forEach( ( portName, defaultPort ) -> {
			if ( destinationPorts.containsKey( portName ) ) {
				ProcessedPort destinationPort = destinationPorts.get( portName );

				String location = destinationPort.getLocation();
				String embedding = destinationPort.getEmbedding();
				String protocolType = destinationPort.getProtocolType();
				Value protocolProperties = destinationPort.getProtocolProperties();
				ConfigurationTree.PortType portType = destinationPort.getPortType();

				if ( embedding == null ) {
					// We provide no embedding ourselves, thus we must use locations. Accept defaults
					if ( destinationPort.getLocation() == null ) location = defaultPort.getLocation();
					if ( destinationPort.getProtocolType() == null ) protocolType = defaultPort.getProtocolType();
					if ( destinationPort.getProtocolProperties() == null )
						protocolProperties = defaultPort.getProtocolProperties();

					destinationPorts.put( portName,
							new ProcessedPort( portName, protocolType, protocolProperties, location, portType ) );
				} else if ( location == null ) {
					// Not providing a location, thus we must be embedding. Accept defaults.
					// TODO This isn't actually possible if we can only provide a single unit per default file.
					// Also doesn't make a lot of sense? What are the use-cases?
					if ( destinationPort.getEmbedding() == null ) embedding = defaultPort.getEmbedding();

					destinationPorts.put( portName,
							new ProcessedPort( portName, embedding, portType ) );
				} // TODO Else? Do we just let it crash later when we validate external ports?
			} else {
				destinationPorts.put( portName, defaultPort );
			}
		} );
	}

	public String getProfileName()
	{
		return profileName;
	}

	public String getPackageName()
	{
		return packageName;
	}

	public boolean hasInputPortLocation( String port )
	{
		return inputPorts.containsKey( port );
	}

	public boolean hasInputPortProtocol( String portName )
	{
		ProcessedPort port = inputPorts.get( portName );
		return port != null && port.getProtocolType() != null;
	}

	public boolean hasOutputPortLocation( String port )
	{
		return outputPorts.containsKey( port );
	}

	public boolean hasOutputPortProtocol( String portName )
	{
		ProcessedPort port = outputPorts.get( portName );
		return port != null && port.getProtocolType() != null;
	}

	public boolean hasOutputPortEmbedding( String portName )
	{
		ProcessedPort port = outputPorts.get( portName );
		return port != null && port.getEmbedding() != null;
	}

	public ProcessedPort getOutputPort( String portName )
	{
		return outputPorts.get( portName );
	}

	public ProcessedPort getInputPort( String portName )
	{
		return inputPorts.get( portName );
	}

	public Value getConstant( String constantName )
	{
		return constants.get( constantName );
	}

	@Override
	public String toString()
	{
		return "Configuration{" +
				"inputPorts=" + inputPorts +
				", outputPorts=" + outputPorts +
				", constants=" + constants +
				'}';
	}

	@Override
	public boolean equals( Object o )
	{
		if ( this == o ) return true;
		if ( o == null || getClass() != o.getClass() ) return false;

		Configuration that = ( Configuration ) o;

		if ( !inputPorts.equals( that.inputPorts ) ) return false;
		if ( !outputPorts.equals( that.outputPorts ) ) return false;
		return constants.equals( that.constants );

	}

	@Override
	public int hashCode()
	{
		int result = inputPorts.hashCode();
		result = 31 * result + outputPorts.hashCode();
		result = 31 * result + constants.hashCode();
		return result;
	}
}
