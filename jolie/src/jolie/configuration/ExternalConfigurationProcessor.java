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

/**
 * Processes configuration coming from an external source.
 *
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

	public void process()
	{

	}

	private Configuration evaluateValues( Region region )
	{
		Map< String, Value > inputPorts = new HashMap<>();
		Map< String, Value > outputPorts = new HashMap<>();
		Map< String, Value > constants = new HashMap<>();

		for ( ConfigurationTree.ExternalPort port : region.getPorts() ) {
			if ( port.isEmbedding() ) {

			} else {
				Value portValue = Value.create();
				portValue.getNewChild( "location" ).setValue( port.getLocation() );
				Value protocol = portValue.getNewChild( "protocol" );
				protocol.setValue( port.getProtocol().getType() );
				OLSyntaxNode properties = port.getProtocol().getProperties();
				if ( properties != null ) {
					Value value = evaluateNode( properties );
					value.assignValue( protocol );
					portValue.getFirstChild( "protocol" ).setValue( value );
				}
			}
		}
		return null;
	}

	private Value evaluateNode( OLSyntaxNode node )
	{
		if ( node instanceof InlineTreeExpressionNode ) {
			InlineTreeExpressionNode inlineNode = (InlineTreeExpressionNode) node;
			Value root = evaluateNode( inlineNode.rootExpression() );

			Pair< VariablePath, Expression >[] assignments = new Pair[ inlineNode.assignments().length ];
			int i = 0;
			for( Pair< VariablePathNode, OLSyntaxNode > pair : inlineNode.assignments() ) {
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
		} else {
			throw new IllegalStateException( "Unsupported node in configuration format. This is a bug." );
		}
	}

	private VariablePath buildVariablePath( VariablePathNode path )
	{
		Pair< Expression, Expression >[] internalPath = new Pair[ path.path().size() ];
		int i = 0;
		for( Pair< OLSyntaxNode, OLSyntaxNode > pair : path.path() ) {
			Expression keyExpr = evaluateNode( pair.key() );
			Expression value = pair.value() != null ? evaluateNode( pair.value() ) : null;
			internalPath[ i++ ] = new Pair<>( keyExpr, value );
		}
		return new VariablePath( internalPath );
	}
}
