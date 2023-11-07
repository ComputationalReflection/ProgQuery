package es.uniovi.reflection.progquery.utils.dataTransferClasses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.source.tree.MethodTree;

import es.uniovi.reflection.progquery.database.relations.PDGRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;

public class MethodInfo {
	MethodTree tree;
	public NodeWrapper methodNode;
	public Map<NodeWrapper, NodeWrapper> identificationForLeftAssignExprs;
	public Map<NodeWrapper, PDGRelationTypes> paramsToPDGRelations;
	public NodeWrapper thisNodeIfNotStatic;
	public Map<NodeWrapper, Set<NodeWrapper>> callsToParamsPreviouslyModified,
			callsToParamsMaybePreviouslyModified;
public final int varArgParamIndex;
	public MethodInfo(MethodTree tree, NodeWrapper methodNode,
			Map<NodeWrapper, NodeWrapper> identificationForLeftAssignExprs,
			NodeWrapper thisNodeForMethod,
			PDGRelationTypes thisRelationsOnThisMethod, Map<NodeWrapper, PDGRelationTypes> paramsToPDGRelations,
			Map<NodeWrapper, Set<NodeWrapper>> callsToParamsPreviouslyModified,
			Map<NodeWrapper, Set<NodeWrapper>> callsToParamsMaybePreviouslyModified, int varArgParamIndex) {
		super();
		this.tree = tree;
		this.methodNode = methodNode;
		this.identificationForLeftAssignExprs = identificationForLeftAssignExprs;
		this.paramsToPDGRelations = paramsToPDGRelations;
		this.thisNodeIfNotStatic = thisNodeForMethod;
		if (thisRelationsOnThisMethod != null)
			this.paramsToPDGRelations.put(thisNodeForMethod, thisRelationsOnThisMethod);
		this.callsToParamsPreviouslyModified = callsToParamsPreviouslyModified;
		this.callsToParamsMaybePreviouslyModified = callsToParamsMaybePreviouslyModified;
		this.varArgParamIndex=varArgParamIndex;
	}
}
