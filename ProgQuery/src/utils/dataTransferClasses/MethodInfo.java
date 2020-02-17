package utils.dataTransferClasses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.source.tree.MethodTree;

import database.relations.PDGRelationTypes;
import node_wrappers.NodeWrapper;
import node_wrappers.RelationshipWrapper;

public class MethodInfo {

	// SimpleTreeNodeCache<Tree> cfgCache;
	// Map<TryTree, List<Pair<NodeWrapper, List<Symbol>>>>
	// invocationsInStatements;
	MethodTree tree;
	// List<StatementTree> originalStatements;
	public NodeWrapper methodNode;
	public Map<NodeWrapper, NodeWrapper> identificationForLeftAssignExprs;
//	public Map<NodeWrapper, Boolean> instanceAssignmentsXX;
	public Map<NodeWrapper, PDGRelationTypes> paramsToPDGRelations;
	public NodeWrapper thisNodeIfNotStatic;

	private List<RelationshipWrapper> decToInstanceInvRels = new ArrayList<>();
	public Map<NodeWrapper, Set<NodeWrapper>> callsToParamsPreviouslyModified,
			callsToParamsMaybePreviouslyModified;

	public MethodInfo(MethodTree tree, NodeWrapper methodNode,
			Map<NodeWrapper, NodeWrapper> identificationForLeftAssignExprs,
//			Map<NodeWrapper, Boolean> instanceAssignments,
			NodeWrapper thisNodeForMethod,
			PDGRelationTypes thisRelationsOnThisMethod, Map<NodeWrapper, PDGRelationTypes> paramsToPDGRelations,
			Map<NodeWrapper, Set<NodeWrapper>> callsToParamsPreviouslyModified,
			Map<NodeWrapper, Set<NodeWrapper>> callsToParamsMaybePreviouslyModified) {
		super();
		this.tree = tree;
		this.methodNode = methodNode;
		this.identificationForLeftAssignExprs = identificationForLeftAssignExprs;
//		this.instanceAssignments = instanceAssignments;
//		this.paramsToPDGRelations = new HashMap<NodeWrapper, PDGRelationTypes>();
		this.paramsToPDGRelations = paramsToPDGRelations;
		// System.out.println("METHOD INFO " + methodNode.getProperty("name"));
		// System.out.println(thisNodeForMethod);
		// System.out.println(thisNodeForMethod);
		this.thisNodeIfNotStatic = thisNodeForMethod;
		if (thisRelationsOnThisMethod != null)
			this.paramsToPDGRelations.put(thisNodeForMethod, thisRelationsOnThisMethod);
		this.callsToParamsPreviouslyModified = callsToParamsPreviouslyModified;
		this.callsToParamsMaybePreviouslyModified = callsToParamsMaybePreviouslyModified;
	}


	public void addRelTodecToInstanceInvRels(RelationshipWrapper rel) {
		// System.out.println("ADDING INV REL " + rel.getStartNodeId() + "\t" +
		// rel.getEndNodeId());
		decToInstanceInvRels.add(rel);
	}

	public List<RelationshipWrapper> getDecToInstanceInvRelsxx() {
		return decToInstanceInvRels;
	}

}
