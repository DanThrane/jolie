package jolie.configuration;

import jolie.lang.parse.ast.ConfigurationTree;
import jolie.lang.parse.ast.ConfigurationTree.Region;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.expression.*;
import jolie.runtime.Value;
import jolie.runtime.VariablePath;
import jolie.runtime.expression.Expression;
import jolie.runtime.expression.InlineTreeExpression;
import jolie.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Processes configuration coming from an external source.
 * <p>
 * This will take the .col AST. From this it will gather which packages are
 * used and merge these together. Expression nodes will then be fully
 * evaluated such that they are ready to be used. The result this will be
 * fully merged and processed configuration units.
 *
 * @author Dan Sebastian Thrane (dthrane@gmail.com)
 */
public class ExternalConfigurationProcessor
{
	private final ConfigurationTree tree;

	public ExternalConfigurationProcessor( ConfigurationTree tree )
	{
		this.tree = tree;
	}

	public Map< String, Configuration > process()
	{
		return tree.getRegions().stream()
				.map( this::evaluateValues )
				.collect( Collectors.toMap( Configuration::getProfileName, it -> it ) );
	}

	private Configuration evaluateValues( Region region )
	{
		Map< String, ProcessedPort > inputPorts = new HashMap<>();
		Map< String, ProcessedPort > outputPorts = new HashMap<>();
		Map< String, Value > constants = new HashMap<>();

		region.getPorts().stream().map( this::processPort ).forEach( it -> {
			switch ( it.portType ) {
				case INPUT:
					inputPorts.put( it.getName(), it );
					break;
				case OUTPUT:
					outputPorts.put( it.getName(), it );
					break;
			}
		} );

		region.getConstants().stream()
				.map( this::processConstant )
				.forEach( it -> constants.put( it.key(), it.value() ) );

		return new Configuration( region.getProfileName(), region.getPackageName(), inputPorts, outputPorts,
				constants );
	}

	private ProcessedPort processPort( ConfigurationTree.ExternalPort port )
	{
		if ( port.isEmbedding() ) {
			return new ProcessedPort( port.getName(), port.getEmbeds(), port.getLocation(), port.getType() );
		} else {
			String location = port.getLocation();
			String protocolType = null;
			Value protocolProperties = null;
			ConfigurationTree.PortProtocol protocol = port.getProtocol();
			if ( protocol != null ) {
				protocolType = protocol.getType();
				if ( protocol.getProperties() != null ) {
					protocolProperties = evaluateNode( protocol.getProperties() );
				}
			}
			return new ProcessedPort( port.getName(), protocolType, protocolProperties, location, port.getType() );
		}
	}

	private Pair< String, Value > processConstant( ConfigurationTree.ExternalConstant constant )
	{
		return new Pair<>( constant.getName(), evaluateNode( constant.getExpressionNode() ) );
	}

	private Value evaluateNode( OLSyntaxNode node )
	{
		if ( node instanceof InlineTreeExpressionNode ) {
			InlineTreeExpressionNode inlineNode = (InlineTreeExpressionNode) node;
			Value root = evaluateNode( inlineNode.rootExpression() );

			Pair< VariablePath, Expression >[] assignments = new Pair[ inlineNode.assignments().length ];
			int i = 0;
			for ( Pair< VariablePathNode, OLSyntaxNode > pair : inlineNode.assignments() ) {
				assignments[ i++ ] = new Pair<>(
						buildVariablePath( pair.key() ),
						evaluateNode( pair.value() )
				);
			}
			return new InlineTreeExpression( root, assignments ).evaluate();
		} else if ( node instanceof ConstantBoolExpression ) {
			return Value.create( ( (ConstantBoolExpression) node ).value() );
		} else if ( node instanceof ConstantDoubleExpression ) {
			return Value.create( ( (ConstantDoubleExpression) node ).value() );
		} else if ( node instanceof ConstantLongExpression ) {
			return Value.create( ( (ConstantLongExpression) node ).value() );
		} else if ( node instanceof ConstantIntegerExpression ) {
			return Value.create( ( (ConstantIntegerExpression) node ).value() );
		} else if ( node instanceof ConstantStringExpression ) {
			return Value.create( ( (ConstantStringExpression) node ).value() );
		} else if ( node instanceof VoidExpressionNode ) {
			return Value.create();
		} else {
			throw new IllegalStateException( "Unsupported node type '" + node.getClass() +
					"' in configuration format. This is a bug." );
		}
	}

	private VariablePath buildVariablePath( VariablePathNode path )
	{
		Pair< Expression, Expression >[] internalPath = new Pair[ path.path().size() ];
		int i = 0;
		for ( Pair< OLSyntaxNode, OLSyntaxNode > pair : path.path() ) {
			Expression keyExpr = evaluateNode( pair.key() );
			Expression value = pair.value() != null ? evaluateNode( pair.value() ) : null;
			internalPath[ i++ ] = new Pair<>( keyExpr, value );
		}
		return new VariablePath( internalPath );
	}

	public static class ProcessedPort
	{
		private final String name;
		private final String location;
		private final String embedding;
		private final String protocolType;
		private final Value protocolProperties;
		private final ConfigurationTree.PortType portType;

		public ProcessedPort( String name, String embedding, String location, ConfigurationTree.PortType portType )
		{
			this.name = name;
			this.embedding = embedding;
			this.location = location;
			this.portType = portType;
			this.protocolProperties = null;
			this.protocolType = null;
		}

		public ProcessedPort( String name, String protocolType, Value protocolProperties, String location, ConfigurationTree.PortType portType )
		{
			this.name = name;
			this.portType = portType;
			this.embedding = null;
			this.protocolType = protocolType;
			this.protocolProperties = protocolProperties;
			this.location = location;
		}

		public String getLocation()
		{
			return location;
		}

		public String getEmbedding()
		{
			return embedding;
		}

		public String getProtocolType()
		{
			return protocolType;
		}

		public Value getProtocolProperties()
		{
			return protocolProperties;
		}

		public String getName()
		{
			return name;
		}
	}
}
