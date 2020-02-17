package utils.dataTransferClasses;

import java.util.ArrayList;
import java.util.List;

import database.querys.cypherWrapper.EdgeDirection;
import database.relations.RelationTypes;
import node_wrappers.NodeWrapper;

public class ClassState {

	public NodeWrapper currentClassDec;
	public List<NodeWrapper> attrsInClassDec;

	public ClassState(NodeWrapper currentClassDec) {
		this.currentClassDec = currentClassDec;
		attrsInClassDec = new ArrayList<NodeWrapper>();
		currentClassDec.getRelationships(EdgeDirection.OUTGOING, RelationTypes.DECLARES_FIELD)
				.forEach(rel -> attrsInClassDec.add(rel.getEndNode()));
	}

}
