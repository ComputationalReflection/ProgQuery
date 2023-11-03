package es.uniovi.reflection.progquery.pdg;

import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.MethodInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetDeclarationFromExpression {
	static enum IsInstance {
		YES, MAYBE, NO

	}
	private NodeWrapper currentThisRef;
	private Map<NodeWrapper, NodeWrapper> identificationForLeftAssignIdents;
	private Map<NodeWrapper, Map<Integer, List<PDGMutatedDecInfoInMethod>>> invocationsMayModifyVars = new HashMap<>();
	public Map<NodeWrapper, Map<Integer, List<PDGMutatedDecInfoInMethod>>> getInvocationsMayModifyVars() {
		return invocationsMayModifyVars;
	}
	public void setInfoForMethod(MethodInfo methodInfo) {
		this.identificationForLeftAssignIdents = methodInfo.identificationForLeftAssignExprs;
		this.currentThisRef = methodInfo.thisNodeIfNotStatic;
	}

	public GetDeclarationFromExpression() {	}

	private Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scan(NodeWrapper n) {

		return n.hasLabel(NodeTypes.IDENTIFIER) ? scanIdentifier(n) :
				n.hasLabel(NodeTypes.MEMBER_SELECTION) ? scanMemberSel(n) :
						n.hasLabel(NodeTypes.METHOD_INVOCATION) ? scanMethodInvocation(n) :
								n.hasLabel(NodeTypes.ASSIGNMENT) ? scanAPart(n, RelationTypes.ASSIGNMENT_LHS) :
										n.hasLabel(NodeTypes.ARRAY_ACCESS) ?
												scanAPart(n, RelationTypes.ARRAYACCESS_EXPR) :
												n.hasLabel(NodeTypes.TYPE_CAST) ?
														scanAPart(n, RelationTypes.CAST_ENCLOSES) :
														n.hasLabel(NodeTypes.CONDITIONAL_EXPRESSION) ?
																scanConditionalExpression(n) : unknownScan(n);
	}

	private Pair<List<PDGMutatedDecInfoInMethod>, Boolean> unknownScan(NodeWrapper n) {
		return Pair.create(new ArrayList<>(), true);
	}

	private NodeWrapper getDecFromExp(NodeWrapper identOrMemberSel) {
		NodeWrapper decNode = identificationForLeftAssignIdents.get(identOrMemberSel);
		if (decNode == null)
			if (identOrMemberSel.hasRelationship(PDGRelationTypes.USED_BY, Direction.INCOMING))
				return identOrMemberSel.getSingleRelationship(Direction.INCOMING, PDGRelationTypes.USED_BY)
						.getStartNode();
			else {
				RelationshipWrapper methodInvocationRelIfExists = identOrMemberSel
						.getSingleRelationship(Direction.INCOMING, RelationTypes.METHODINVOCATION_METHOD_SELECT);
				if (methodInvocationRelIfExists == null || identOrMemberSel.hasLabel(NodeTypes.MEMBER_SELECTION)) {
					return null;
				}
				return currentThisRef;
			}
		return decNode;
	}

	private Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanMemberSel(NodeWrapper memberSel) {
		Pair<List<PDGMutatedDecInfoInMethod>, Boolean> defsToTheLeft =
				scanAPart(memberSel, RelationTypes.MEMBER_SELECT_EXPR);
		NodeWrapper dec = getDecFromExp(memberSel);
		if (dec != null)
			defsToTheLeft.getFirst().add(new PDGMutatedDecInfoInMethod(defsToTheLeft.getSecond(),
					defsToTheLeft.getFirst().size() > 0 ? defsToTheLeft.getFirst().get(0).isOuterMostImplicitThisOrP :
							IsInstance.NO
					, dec));
		return defsToTheLeft;
	}

	public Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanIdentifier(NodeWrapper identifier) {
		NodeWrapper dec = getDecFromExp(identifier);
		if (dec != null) {
			List<PDGMutatedDecInfoInMethod> identInfo = new ArrayList<>();
			identInfo.add(new PDGMutatedDecInfoInMethod(false,
					dec.hasLabel(NodeTypes.ATTR_DEF) && !(Boolean) dec.getProperty("isStatic") ||
							dec.hasLabel(NodeTypes.THIS_REF)
							? IsInstance.YES : IsInstance.NO, dec));
			return Pair.create(identInfo, false);
		} else
			return Pair.create(new ArrayList<>(), false);
	}

	private IsInstance getCompoundIsInstance(IsInstance i1, IsInstance i2) {
		if (i1 == i2)
			return i1;
		return IsInstance.MAYBE;
	}

	public Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanConditionalExpression(NodeWrapper conditionalExpr) {
		List<PDGMutatedDecInfoInMethod> ret = new ArrayList<>();
		Pair<List<PDGMutatedDecInfoInMethod>, Boolean> retThen =
				scan(conditionalExpr.getSingleRelationship(Direction.OUTGOING, RelationTypes.CONDITIONAL_EXPR_THEN)
						.getEndNode()), retElse =
				scan(conditionalExpr.getSingleRelationship(Direction.OUTGOING, RelationTypes.CONDITIONAL_EXPR_ELSE)
						.getEndNode());
		ret.addAll(convertMustToMay(retElse));
		ret.addAll(convertMustToMay(retThen));
		return Pair.create(ret, false);
	}

	public Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanAPart(NodeWrapper memberSelection, RelationTypes r) {
		return scan(memberSelection.getSingleRelationship(Direction.OUTGOING, r).getEndNode());
	}

	private List<PDGMutatedDecInfoInMethod> convertMustToMay(Pair<List<PDGMutatedDecInfoInMethod>, Boolean> previous) {
		return previous.getFirst().stream().map(previousPdgInfo -> new PDGMutatedDecInfoInMethod(true,
				previousPdgInfo.isOuterMostImplicitThisOrP, previousPdgInfo.dec)).collect(Collectors.toList());
	}

	public Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanMethodInvocation(NodeWrapper methodInvocation) {

		Map<Integer, List<PDGMutatedDecInfoInMethod>> varDecsInArguments = new HashMap<>();
		Pair<List<PDGMutatedDecInfoInMethod>, Boolean> thisArgRet;
		NodeWrapper calleeMethodNode =
				methodInvocation.getSingleRelationship(Direction.OUTGOING, CGRelationTypes.HAS_DEF).getEndNode();
		boolean isDeclared = (Boolean) calleeMethodNode.getProperty("isDeclared");
		thisArgRet = calleeMethodNode.hasLabel(NodeTypes.CONSTRUCTOR_DEF) ||
				(isDeclared && !(Boolean) calleeMethodNode.getProperty("isStatic")) ? scan(methodInvocation
				.getSingleRelationship(Direction.OUTGOING, RelationTypes.METHODINVOCATION_METHOD_SELECT).getEndNode()) :
				Pair.create(new ArrayList<>(), false);
		varDecsInArguments.put(0, thisArgRet.getFirst());
		for (RelationshipWrapper argumentRel : methodInvocation
				.getRelationships(Direction.OUTGOING, RelationTypes.METHODINVOCATION_ARGUMENTS))
			varDecsInArguments.put((int) argumentRel.getProperty("argumentIndex"),
					isDeclared ? scan(argumentRel.getEndNode()).getFirst() : new ArrayList<>());
		invocationsMayModifyVars.put(methodInvocation, varDecsInArguments);
		return Pair.create(new ArrayList<>(), thisArgRet.getSecond());
	}

	public List<Pair<NodeWrapper, Boolean>> scanNewClass(NodeWrapper newClass) {

		Map<Integer, List<PDGMutatedDecInfoInMethod>> varDecsInArguments = new HashMap<>();
		varDecsInArguments.put(0, new ArrayList<>());

		for (RelationshipWrapper argumentRel : newClass
				.getRelationships(Direction.OUTGOING, RelationTypes.NEW_CLASS_ARGUMENTS))
			varDecsInArguments
					.put((int) argumentRel.getProperty("argumentIndex"), scan(argumentRel.getEndNode()).getFirst());
		invocationsMayModifyVars.put(newClass, varDecsInArguments);
		return new ArrayList<Pair<NodeWrapper, Boolean>>();
	}
}