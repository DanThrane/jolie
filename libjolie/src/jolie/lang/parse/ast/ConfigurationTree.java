package jolie.lang.parse.ast;

import java.util.*;

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
		private final List< ExternalPort > ports = new ArrayList<>();
		private final List< ExternalConstantConfigNode > constants = new ArrayList<>();

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
			ports.add( port );
		}

		public void addConstant( ExternalConstantConfigNode constant )
		{
			constants.add( constant );
		}

		public List< ExternalPort > getPorts()
		{
			return Collections.unmodifiableList( ports );
		}

		public List< ExternalConstantConfigNode > getConstants()
		{
			return Collections.unmodifiableList( constants );
		}

		@Override
		public String toString()
		{
			return "Region{" +
					"profileName='" + profileName + '\'' +
					", packageName='" + packageName + '\'' +
					", ports=" + ports +
					", constants=" + constants +
					'}';
		}
	}

	public static class ExternalConstantConfigNode
	{
		private final String name;
		private final OLSyntaxNode expressionNode;


		public ExternalConstantConfigNode( String name, OLSyntaxNode expressionNode )
		{
			this.name = name;
			this.expressionNode = expressionNode;
		}

		public String getName()
		{
			return name;
		}

		public OLSyntaxNode getExpressionNode()
		{
			return expressionNode;
		}

		@Override
		public boolean equals( Object o )
		{
			if ( this == o ) return true;
			if ( o == null || getClass() != o.getClass() ) return false;

			ExternalConstantConfigNode that = (ExternalConstantConfigNode ) o;

			if ( !name.equals( that.name ) ) return false;
			return expressionNode.equals( that.expressionNode );

		}

		@Override
		public int hashCode()
		{
			int result = name.hashCode();
			result = 31 * result + expressionNode.hashCode();
			return result;
		}

		@Override
		public String toString()
		{
			return "ExternalConstantConfigNode{" +
					"name='" + name + '\'' +
					", expressionNode=" + expressionNode +
					'}';
		}
	}

	public static class ExternalPort
	{
		private final String name;
		private final PortType type;
		private final String location;
		private final PortProtocol protocol;
		private final String embeds;

		public ExternalPort( String name, PortType type, String embeds )
		{
			this.name = name;
			this.type = type;
			this.embeds = embeds;
			this.location = null;
			this.protocol = null;

			assert embeds != null;
		}

		public ExternalPort( String name, PortType type, String location, PortProtocol protocol )
		{
			this.name = name;
			this.type = type;
			this.location = location;
			this.protocol = protocol;
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

		@Override
		public boolean equals( Object o )
		{
			if ( this == o ) return true;
			if ( o == null || getClass() != o.getClass() ) return false;

			ExternalPort that = (ExternalPort) o;

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

			PortProtocol that = (PortProtocol) o;

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
		INPUT, OUTPUT;
	}
}
