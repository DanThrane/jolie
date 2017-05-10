package jolie.lang.parse.ast;

import jolie.lang.parse.context.ParsingContext;

import java.util.*;
import java.util.function.Consumer;

public class ConfigurationTree
{
	private final Map< String, Map< String, Region > > regions = new HashMap<>();

	public void addRegion( Region region )
	{
		String packageName = region.getPackageName();
		Map< String, Region > namespaced = regions.getOrDefault( packageName, new HashMap<>() );
		namespaced.put( region.getProfileName(), region );
		regions.put( packageName, namespaced );
	}

	public Region getRegion( String moduleName, String profileName )
	{
		Map< String, Region > namespaced = regions.get( moduleName );
		if ( namespaced == null ) return null;
		return namespaced.get( profileName );
	}

	public Map< String, Map< String, Region > > getRegions()
	{
		return Collections.unmodifiableMap( regions );
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
		private String extendsProfile;
		private ParsingContext context;
		private final Map< String, ExternalPort > inPorts = new HashMap<>();
		private final Map< String, ExternalPort > outPorts = new HashMap<>();
		private final Map< String, ExternalInterface > interfaces = new HashMap<>();
		private final List< ExternalParamNode > parameters = new ArrayList<>();

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

		public String getExtendsProfile()
		{
			return extendsProfile;
		}

		public ParsingContext getContext()
		{
			return context;
		}

		public void setContext( ParsingContext context )
		{
			this.context = context;
		}

		public void setExtendsProfile( String extendsProfile )
		{
			this.extendsProfile = extendsProfile;
		}

		public void addPort( ExternalPort port )
		{
			if ( port.getType() == PortType.INPUT ) {
				inPorts.put( port.getName(), port );
			} else if ( port.getType() == PortType.OUTPUT ) {
				outPorts.put( port.getName(), port );
			}
		}

		public void addInterface( ExternalInterface iface )
		{
			interfaces.put( iface.name(), iface );
		}

		public void addParameter( ExternalParamNode node )
		{
			parameters.add( node );
		}

		public ExternalPort getInputPort( String name )
		{
			return inPorts.get( name );
		}

		public ExternalPort getOutputPort( String name )
		{
			return outPorts.get( name );
		}

		public ExternalInterface getInterface( String name )
		{
			return interfaces.get( name );
		}

		public List< ExternalParamNode > getParameters()
		{
			return parameters;
		}

		public static Region merge( Region region, Region parentRegion )
		{
			if ( !region.getPackageName().equals( parentRegion.getPackageName() ) ) {
				throw new IllegalArgumentException( "Parent unit doesn't have a matching package name!" );
			}

			String profileName = region.getProfileName();
			String packageName = region.getPackageName();

			Map< String, ExternalPort > inPorts = new HashMap<>();
			Map< String, ExternalPort > outPorts = new HashMap<>();
			region.inPorts.values().forEach( it -> inPorts.put( it.getName(), it ) );
			region.outPorts.values().forEach( it -> outPorts.put( it.getName(), it ) );
			parentRegion.inPorts.values().forEach( processPorts( inPorts ) );
			parentRegion.outPorts.values().forEach( processPorts( outPorts ) );

			Map< String, ExternalInterface > interfaces = new HashMap<>();
			region.interfaces.values().forEach( it -> interfaces.put( it.name(), it ) );
			parentRegion.interfaces.values().forEach( it -> {
				if ( !interfaces.containsKey( it.name() ) ) {
					interfaces.put( it.name(), it );
				}
			} );

			Region result = new Region();
			result.setPackageName( packageName );
			result.setProfileName( profileName );
			inPorts.values().forEach( result::addPort );
			outPorts.values().forEach( result::addPort );
			parentRegion.parameters.forEach( result::addParameter );
			region.parameters.forEach( result::addParameter );

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

	public static class ExternalParamNode
	{
		private final ParsingContext context;
		private final VariablePathNode path;
		private final OLSyntaxNode expressionNode;

		public ExternalParamNode( ParsingContext context, VariablePathNode path, OLSyntaxNode expressionNode )
		{
			this.context = context;
			this.path = path;
			this.expressionNode = expressionNode;
		}

		public ParsingContext context()
		{
			return context;
		}

		public VariablePathNode path()
		{
			return path;
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
		private final String profile;
		private final String module;
		private final ParsingContext context;

		public ExternalPort( String name, PortType type, String profile, String module, ParsingContext context )
		{
			this.name = name;
			this.type = type;
			this.profile = profile;
			this.module = module;
			this.context = context;
			this.location = null;
			this.protocol = null;

			assert profile != null;
		}

		public ExternalPort( String name, PortType type, String location, PortProtocol protocol, ParsingContext context )
		{
			this.name = name;
			this.type = type;
			this.location = location;
			this.protocol = protocol;
			this.context = context;
			this.profile = null;
			this.module = null;

			assert name != null;
			assert type != null;
		}

		public String getProfile()
		{
			return profile;
		}

		public String getModule()
		{
			return module;
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
			return profile != null;
		}

		public ParsingContext getContext()
		{
			return context;
		}

		@SuppressWarnings( "SimplifiableIfStatement" )
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
			return profile != null ? profile.equals( that.profile ) : that.profile == null;

		}

		@Override
		public int hashCode()
		{
			int result = name.hashCode();
			result = 31 * result + type.hashCode();
			result = 31 * result + ( location != null ? location.hashCode() : 0 );
			result = 31 * result + ( protocol != null ? protocol.hashCode() : 0 );
			result = 31 * result + ( profile != null ? profile.hashCode() : 0 );
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
					", profile='" + profile + '\'' +
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

