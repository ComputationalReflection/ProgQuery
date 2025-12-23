package es.uniovi.reflection.progquery.pdg;

import es.uniovi.reflection.progquery.database.nodes.NodeCategory;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.ASTRelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationProperties;
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

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.IS_STATIC_PROP;
import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.IS_USER_CODE;

public class GetDeclarationFromExpression {
	enum IsInstance {
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

		return n.hasLabel(NodeCategory.IDENTIFIER) ? scanIdentifier(n) :
				n.hasLabel(NodeCategory.IDENTIFIER_SELECTION) ? scanMemberSel(n) :
						n.hasLabel(NodeTypes.METHOD_INVOCATION) ? scanMethodInvocation(n) :
								n.hasLabel(NodeTypes.ASSIGNMENT) ? scanAPart(n, ASTRelationTypes.ASSIGNMENT_LHS) :
										n.hasLabel(NodeTypes.ARRAY_ACCESS) ?
												scanAPart(n, ASTRelationTypes.ARRAY_ACCESS_EXPR) :
												n.hasLabel(NodeTypes.TYPE_CAST) ?
														scanAPart(n, ASTRelationTypes.CAST_EXPR) :
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
						.getSingleRelationship(Direction.INCOMING, ASTRelationTypes.INVOCATION_METHOD_SELECTION);
				if (methodInvocationRelIfExists == null || identOrMemberSel.hasLabel(NodeCategory.IDENTIFIER_SELECTION)) {
					return null;
				}
				return currentThisRef;
			}
		return decNode;
	}

	private Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanMemberSel(NodeWrapper memberSel) {
		Pair<List<PDGMutatedDecInfoInMethod>, Boolean> defsToTheLeft =
				scanAPart(memberSel, ASTRelationTypes.IDENT_SELECTION_FROM);
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
					dec.hasLabel(NodeTypes.FIELD_DEC) && !(Boolean) dec.getProperty(IS_STATIC_PROP) ||
							dec.hasLabel(NodeTypes.THIS_REFERENCE)
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
				scan(conditionalExpr.getSingleRelationship(Direction.OUTGOING, ASTRelationTypes.CONDITIONAL_EXPR_THEN)
						.getEndNode()), retElse =
				scan(conditionalExpr.getSingleRelationship(Direction.OUTGOING, ASTRelationTypes.CONDITIONAL_EXPR_ELSE)
						.getEndNode());
		ret.addAll(convertMustToMay(retElse));
		ret.addAll(convertMustToMay(retThen));
		return Pair.create(ret, false);
	}

	public Pair<List<PDGMutatedDecInfoInMethod>, Boolean> scanAPart(NodeWrapper memberSelection, ASTRelationTypes r) {
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
				methodInvocation.getSingleRelationship(Direction.OUTGOING, CGRelationTypes.CALLEE).getEndNode();
		boolean isDeclared = (Boolean) calleeMethodNode.getProperty(IS_USER_CODE);
		thisArgRet = calleeMethodNode.hasLabel(NodeTypes.CONSTRUCTOR_DEC) ||
				(isDeclared && !(Boolean) calleeMethodNode.getProperty(IS_STATIC_PROP)) ? scan(methodInvocation
				.getSingleRelationship(Direction.OUTGOING, ASTRelationTypes.INVOCATION_METHOD_SELECTION).getEndNode()) :
				Pair.create(new ArrayList<>(), false);
		varDecsInArguments.put(0, thisArgRet.getFirst());
		for (RelationshipWrapper argumentRel : methodInvocation
				.getRelationships(Direction.OUTGOING, ASTRelationTypes.INVOCATION_ARG))
			varDecsInArguments.put((int) argumentRel.getProperty(RelationProperties.ARGUMENT_INDEX),
					isDeclared ? scan(argumentRel.getEndNode()).getFirst() : new ArrayList<>());
		invocationsMayModifyVars.put(methodInvocation, varDecsInArguments);
		return Pair.create(new ArrayList<>(), thisArgRet.getSecond());
	}

	public List<Pair<NodeWrapper, Boolean>> scanNewClass(NodeWrapper newClass) {

		Map<Integer, List<PDGMutatedDecInfoInMethod>> varDecsInArguments = new HashMap<>();
		varDecsInArguments.put(0, new ArrayList<>());

		for (RelationshipWrapper argumentRel : newClass
				.getRelationships(Direction.OUTGOING, ASTRelationTypes.NEW_INSTANCE_ARG))
			varDecsInArguments
					.put((int) argumentRel.getProperty(RelationProperties.ARGUMENT_INDEX), scan(argumentRel.getEndNode()).getFirst());
		invocationsMayModifyVars.put(newClass, varDecsInArguments);
		return new ArrayList<Pair<NodeWrapper, Boolean>>();
	}
}