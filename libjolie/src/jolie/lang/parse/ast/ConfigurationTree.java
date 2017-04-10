package jolie.lang.parse.ast;

import jolie.lang.parse.context.ParsingContext;

import java.util.*;
import java.util.function.Consumer;

public class ConfigurationTree
{
	private final List< Region > regions = new ArrayList<>();

	public void addRegion( Region region )
	{
		regions.add( region );
	}

	public List< Region > getRegions()
	{
		return Collections.unmodifiableList( regions );
	}

	@Override
	public String toString()
	{
		return "ConfigurationTree{" +
				"regions=" + regions +
				'}';
	}

	public static class Region
	{
		private String profileName;
		private String packageName;
		private final Map< String, ExternalPort > inports = new HashMap<>();
		private final Map< String, ExternalPort > outports = new HashMap<>();
		private final Map< String, ExternalInterface > interfaces = new HashMap<>();
		private final Map< String, ExternalConstantNode > constants = new HashMap<>();

		public String getProfileName()
		{
			return profileName;
		}

		public void setProfileName( String profileName )
		{
			this.profileName = profileName;
		}

		public String getPackageName()
		{
			return packageName;
		}

		public void setPackageName( String packageName )
		{
			this.packageName = packageName;
		}

		public void addPort( ExternalPort port )
		{
			if ( port.getType() == PortType.INPUT ) {
				inports.put( port.getName(), port );
			} else if ( port.getType() == PortType.OUTPUT ) {
				outports.put( port.getName(), port );
			}
		}

		public void addInterface( ExternalInterface iface )
		{
			interfaces.put( iface.name(), iface );
		}

		public void addConstant( ExternalConstantNode constant )
		{
			constants.put( constant.name(), constant );
		}

		public ExternalPort getInputPort( String name )
		{
			return inports.get( name );
		}

		public ExternalPort getOutputPort( String name )
		{
			return outports.get( name );
		}

		public ExternalInterface getInterface( String name )
		{
			return interfaces.get( name );
		}

		public Collection< ExternalConstantNode > getConstants()
		{
			return Collections.unmodifiableCollection( constants.values() );
		}

		public static Region merge( Region region, Region defaultRegion )
		{
			if ( !region.getPackageName().equals( defaultRegion.getPackageName() ) ) {
				throw new IllegalArgumentException( "Default unit doesn't have a matching package name!" );
			}

			String profileName = region.getProfileName();
			String packageName = region.getPackageName();

			Map< String, ExternalPort > inports = new HashMap<>();
			Map< String, ExternalPort > outports = new HashMap<>();
			region.inports.values().forEach( it -> inports.put( it.getName(), it ) );
			region.outports.values().forEach( it -> outports.put( it.getName(), it ) );
			defaultRegion.inports.values().forEach( processPorts(inports) );
			defaultRegion.outports.values().forEach( processPorts(outports) );

			Map< String, ExternalConstantNode > constants = new HashMap<>();
			region.constants.values().forEach( it -> constants.put( it.name(), it ) );
			defaultRegion.constants.values().forEach( it -> {
				if ( !constants.containsKey( it.name() ) ) {
					constants.put( it.name(), it );
				}
			} );

			Map< String, ExternalInterface > interfaces = new HashMap<>();
			region.interfaces.values().forEach( it -> interfaces.put( it.name(), it ) );
			defaultRegion.interfaces.values().forEach( it -> {
				if ( !interfaces.containsKey( it.name() ) ) {
					interfaces.put( it.name(), it );
				}
			} );

			Region result = new Region();
			result.setPackageName( packageName );
			result.setProfileName( profileName );
			inports.values().forEach( result::addPort );
			outports.values().forEach( result::addPort );
			constants.values().forEach( result::addConstant );
			interfaces.values().forEach( result::addInterface );
			return result;
		}

		private static Consumer< ExternalPort > processPorts( Map< String, ExternalPort > destination )
		{
			return defaultPort -> {
				if ( destination.containsKey( defaultPort.getName() ) ) {
					// Already present, merge properties if not present in current
					ExternalPort destinationPort = destination.get( defaultPort.getName() );

					final String name = destinationPort.getName();
					final PortType type = destinationPort.getType();
					String location = destinationPort.getLocation();
					final PortProtocol protocol = destinationPort.getProtocol();
					String protocolType = protocol.getType();
					OLSyntaxNode protocolProperties = protocol.getProperties();

					if ( !destinationPort.isEmbedding() ) {
						if ( location == null ) location = defaultPort.getLocation();
						if ( protocolType == null ) protocolType = defaultPort.getProtocol().getType();
						if ( protocolProperties == null )
							protocolProperties = defaultPort.getProtocol().getProperties();

						ExternalPort replacement = new ExternalPort( name, type, location,
								new PortProtocol( protocolType, protocolProperties ), destinationPort.getContext() );
						destination.put( name, replacement );
					}
				} else {
					// If not present, put the entirety of the default in
					destination.put( defaultPort.getName(), defaultPort );
				}
			};
		}
	}

	public static class ExternalInterface
	{
		private final ParsingContext context;
		private final String name;
		private final String realName;
		private final String fromPackage;

		public ExternalInterface( ParsingContext context, String name, String realName, String fromPackage )
		{
			this.context = context;
			this.name = name;
			this.realName = realName;
			this.fromPackage = fromPackage;
		}

		public ParsingContext context()
		{
			return context;
		}

		public String name()
		{
			return name;
		}

		public String realName()
		{
			return realName;
		}

		public String fromPackage()
		{
			return fromPackage;
		}
	}

	public static class ExternalConstantNode
	{
		private final ParsingContext context;
		private final String name;
		private final OLSyntaxNode expressionNode;

		public ExternalConstantNode( ParsingContext context, String name, OLSyntaxNode expressionNode )
		{
			this.context = context;
			this.name = name;
			this.expressionNode = expressionNode;
		}

		public ParsingContext context()
		{
			return context;
		}

		public String name()
		{
			return name;
		}

		public OLSyntaxNode expressionNode()
		{
			return expressionNode;
		}
	}

	public static class ExternalPort
	{
		private final String name;
		private final PortType type;
		private final String location;
		private final PortProtocol protocol;
		private final String embeds;
		private final ParsingContext context;

		public ExternalPort( String name, PortType type, String embeds, ParsingContext context )
		{
			this.name = name;
			this.type = type;
			this.embeds = embeds;
			this.context = context;
			this.location = null;
			this.protocol = null;

			assert embeds != null;
		}

		public ExternalPort( String name, PortType type, String location, PortProtocol protocol, ParsingContext context )
		{
			this.name = name;
			this.type = type;
			this.location = location;
			this.protocol = protocol;
			this.context = context;
			this.embeds = null;

			assert name != null;
			assert type != null;
		}

		public String getEmbeds()
		{
			return embeds;
		}

		public String getName()
		{
			return name;
		}

		public PortType getType()
		{
			return type;
		}

		public String getLocation()
		{
			return location;
		}

		public PortProtocol getProtocol()
		{
			return protocol;
		}

		public boolean isEmbedding()
		{
			return embeds != null;
		}

		public ParsingContext getContext()
		{
			return context;
		}

		@Override
		public boolean equals( Object o )
		{
			if ( this == o ) return true;
			if ( o == null || getClass() != o.getClass() ) return false;

			ExternalPort that = ( ExternalPort ) o;

			if ( !name.equals( that.name ) ) return false;
			if ( type != that.type ) return false;
			if ( location != null ? !location.equals( that.location ) : that.location != null ) return false;
			if ( protocol != null ? !protocol.equals( that.protocol ) : that.protocol != null ) return false;
			return embeds != null ? embeds.equals( that.embeds ) : that.embeds == null;

		}

		@Override
		public int hashCode()
		{
			int result = name.hashCode();
			result = 31 * result + type.hashCode();
			result = 31 * result + ( location != null ? location.hashCode() : 0 );
			result = 31 * result + ( protocol != null ? protocol.hashCode() : 0 );
			result = 31 * result + ( embeds != null ? embeds.hashCode() : 0 );
			return result;
		}

		@Override
		public String toString()
		{
			return "ExternalPort{" +
					"name='" + name + '\'' +
					", type=" + type +
					", location='" + location + '\'' +
					", protocol=" + protocol +
					", embeds='" + embeds + '\'' +
					'}';
		}
	}

	public static class PortProtocol
	{
		private final String type;
		private final OLSyntaxNode properties;

		public PortProtocol( String type, OLSyntaxNode properties )
		{
			this.type = type;
			this.properties = properties;

			assert type != null;
		}

		public String getType()
		{
			return type;
		}

		public OLSyntaxNode getProperties()
		{
			return properties;
		}

		@Override
		public boolean equals( Object o )
		{
			if ( this == o ) return true;
			if ( o == null || getClass() != o.getClass() ) return false;

			PortProtocol that = ( PortProtocol ) o;

			if ( !type.equals( that.type ) ) return false;
			return properties != null ? properties.equals( that.properties ) : that.properties == null;

		}

		@Override
		public int hashCode()
		{
			int result = type.hashCode();
			result = 31 * result + ( properties != null ? properties.hashCode() : 0 );
			return result;
		}

		@Override
		public String toString()
		{
			return "PortProtocol{" +
					"type='" + type + '\'' +
					", properties=" + properties +
					'}';
		}
	}

	public enum PortType
	{
		INPUT, OUTPUT
	}
}

