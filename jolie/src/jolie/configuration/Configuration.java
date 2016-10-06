package jolie.configuration;

import jolie.runtime.Value;

import java.util.Map;

public class Configuration
{
	private final Map< String, Value > inputPorts;
	private final Map< String, Value > outputPorts;
	private final Map< String, Value > constants;

	public Configuration( Map< String, Value > inputPorts, Map< String, Value > outputPorts, Map< String, Value > constants )
	{
		this.inputPorts = inputPorts;
		this.outputPorts = outputPorts;
		this.constants = constants;
	}

	public Map< String, Value > getInputPorts()
	{
		return inputPorts;
	}

	public Map< String, Value > getOutputPorts()
	{
		return outputPorts;
	}

	public Map< String, Value > getConstants()
	{
		return constants;
	}

	public boolean hasInputPortLocation( String port )
	{
		return inputPorts.containsKey( port ) && inputPorts.get( port ).hasChildren( "location" );
	}

	public boolean hasInputPortProtocol( String port )
	{
		return inputPorts.containsKey( port ) && inputPorts.get( port ).hasChildren( "protocol" );
	}

	public boolean hasOutputPortLocation( String port )
	{
		return outputPorts.containsKey( port ) && outputPorts.get( port ).hasChildren( "location" );
	}

	public boolean hasOutputPortProtocol( String port )
	{
		return outputPorts.containsKey( port ) && outputPorts.get( port ).hasChildren( "protocol" );
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

		Configuration that = (Configuration) o;

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
