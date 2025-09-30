package es.uniovi.reflection.progquery.database.nodes;

import java.util.Map.Entry;

import org.neo4j.graphdb.Label;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.LINE_NUMBER;
import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.NAME_PROP;

public class NodeUtils {
	public static String getNameFromDec(NodeWrapper dec) {
		return dec.hasLabel(NodeTypes.THIS_REFERENCE) ? "THIS" : (String) dec.getProperty(NAME_PROP);
	}
	public static String nodeToString(NodeWrapper n) {
		if (n == null)
			return "NULL";
		String res = "Node[" + n.getId() + "]\n";
		for (RelationshipWrapper r : n.getRelationships()) {
			String relType = r.getTypeString();
			res += (r.getStartNode().equals(n)
					? "NODE--[" + relType + "]->"
							+ (r.getEndNode().getLabels().iterator().hasNext()
									? r.getEndNode().getLabels().iterator().next() : "NO LABEL")
							+ "(ID " + r.getEndNode().getId() + ")"
							+ (r.getEndNode().hasProperty(LINE_NUMBER)
									? "(line " + r.getEndNode().getProperty(LINE_NUMBER) + ")" : "")
					: "NODE<-[" + relType + "]--"
							+ (r.getStartNode().getLabels().iterator().hasNext()
									? r.getStartNode().getLabels().iterator().next() : "NO LABEL")
							+ "(ID " + r.getStartNode().getId() + ")" + (r.getStartNode().hasProperty(LINE_NUMBER)
									? "(line " + r.getStartNode().getProperty(LINE_NUMBER) + ")" : ""))

					+ "\n";
			for (Entry<String, Object> prop : r.getAllProperties())
				res += prop.getKey() + "=" + prop.getValue() + "\n";
		}
		for (Label label : n.getLabels())
			res += "Label:\t" + label + "\n";
		for (Entry<String, Object> prop : n.getAllProperties())
			res += prop.getKey() + "=" + prop.getValue() + "\n";

		return res;
	}

	public static String nodeToStringNoRels(NodeWrapper n) {
		if (n == null)
			return "NULL";
		String res = "Node[" + n.getId() + "]\n";
		for (Label label : n.getLabels())
			res += "Label:\t" + label + "\n";
		for (Entry<String, Object> prop : n.getAllProperties())
			res += prop.getKey() + "=" + prop.getValue() + "\n";

		return res;
	}
	public static String reducedClassMethodToString(NodeWrapper n) {
		if (n == null)
			return "NULL";
		String res = "Node[" + n.getId() + "]\n";
		res +=  n.getProperty(NodeProperties.FULL_NAME)+ "\n";
		return res;
	}
}