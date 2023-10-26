package es.uniovi.reflection.progquery.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.MethodInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.MethodState;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import org.neo4j.graphdb.Direction;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.CFGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PartialRelation;
import es.uniovi.reflection.progquery.database.relations.PartialRelationWithProperties;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.mig.HierarchyAnalysis;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.node_wrappers.WrapperUtils;
import es.uniovi.reflection.progquery.pdg.GetDeclarationFromExpression;
import es.uniovi.reflection.progquery.pdg.InterproceduralPDG;

public class ASTAuxiliarStorage {
	private Stack<List<Pair<NodeWrapper, List<MethodSymbol>>>> lastInvocationInStatementLists = new Stack<List<Pair<NodeWrapper, List<MethodSymbol>>>>();
	private Map<NodeWrapper, MethodInfo> methodInfo = new HashMap<>();
	public final Set<NodeWrapper> typeDecNodes = new HashSet<NodeWrapper>();
	private final Set<NodeWrapper> trustableInvocations = new HashSet<NodeWrapper>();
	private final Map<MethodSymbol, NodeWrapper> accesibleMethods = new HashMap<>();
	private final Map<NodeWrapper, Set<NodeWrapper>> callGraph = new HashMap<>();
	public static final int NO_VARG_ARG = -1;

	public void checkIfTrustableInvocation(MethodInvocationTree methodInvocationTree, MethodSymbol methodSymbol,
			NodeWrapper methodInvocationNode) {
		if (methodInvocationTree.getMethodSelect().getKind() == Kind.IDENTIFIER) {
			if (!methodSymbol.isConstructor())
				trustableInvocations.add(methodInvocationNode);
		} else {
			ExpressionTree memberSelectionExp = ((MemberSelectTree) methodInvocationTree.getMethodSelect())
					.getExpression();
			if (memberSelectionExp.getKind() == Kind.NEW_CLASS)
				trustableInvocations.add(methodInvocationNode);
			else if (memberSelectionExp.getKind() == Kind.IDENTIFIER
					&& ((IdentifierTree) memberSelectionExp).getName().contentEquals("super"))
				trustableInvocations.add(methodInvocationNode);
		}
	}

	public void addInfo(MethodTree methodTree, NodeWrapper methodNode, MethodState methodState, int varArgParamIndex) {

		methodInfo.put(methodNode,
				new MethodInfo(methodTree, methodNode, methodState.identificationForLeftAssignExprs,
						methodState.thisNode, methodState.thisRelationsOnThisMethod, methodState.paramsToPDGRelations,
						methodState.callsToParamsPreviouslyModified, methodState.callsToParamsMaybePreviouslyModified, varArgParamIndex));
	}

	public void addAccesibleMethod(MethodSymbol ms, NodeWrapper method) {
		accesibleMethods.put(ms, method);
	}

	public void deleteAccesibleMethod(MethodSymbol ms) {
		accesibleMethods.remove(ms);
	}

	public void enterInNewTry(TryTree tryTree, MethodState m) {
		lastInvocationInStatementLists.push(new ArrayList<Pair<NodeWrapper, List<MethodSymbol>>>());
		m.invocationsInStatements.put(tryTree, lastInvocationInStatementLists.peek());
	}

	public void exitTry() {
		lastInvocationInStatementLists.pop();
	}

	public void addInvocationInStatement(NodeWrapper statement, List<MethodSymbol> methodNames) {
		if (methodNames.size() > 0)
			lastInvocationInStatementLists.peek().add(Pair.create(statement, methodNames));
	}

	public void newMethodDeclaration(MethodState s) {
		enterInNewTry(null, s);
	}

	public void endMethodDeclaration() {
		exitTry();
	}

	public Map<TryTree, Map<Type, List<PartialRelation<CFGRelationTypes>>>> getTrysToExceptionalPartialRelations(
			Map<TryTree, List<Pair<NodeWrapper, List<MethodSymbol>>>> invocationsInStatements) {
		Map<TryTree, Map<Type, List<PartialRelation<CFGRelationTypes>>>> throwsTypesInStatementsGrouped = new HashMap<TryTree, Map<Type, List<PartialRelation<CFGRelationTypes>>>>();
		for (Entry<TryTree, List<Pair<NodeWrapper, List<MethodSymbol>>>> entry : invocationsInStatements.entrySet()) {
			Map<Type, List<PartialRelation<CFGRelationTypes>>> typesToRelations = new HashMap<Type, List<PartialRelation<CFGRelationTypes>>>();
			for (Pair<NodeWrapper, List<MethodSymbol>> invocationsInStatement : entry.getValue()) {
				for (MethodSymbol methodSymbol : invocationsInStatement.getSecond())
					for (Type excType : methodSymbol.getThrownTypes()) {
						if (!typesToRelations.containsKey(excType))
							typesToRelations.put(excType, new ArrayList<PartialRelation<CFGRelationTypes>>());

						String methodName = methodSymbol.owner.getQualifiedName() + ":" + methodSymbol.toString();
						if (methodSymbol.isConstructor())
							methodName = methodName.replaceAll(":(\\w)+\\(", ":<init>(");

						typesToRelations.get(excType)
								.add(new PartialRelationWithProperties<CFGRelationTypes>(
										invocationsInStatement.getFirst(), CFGRelationTypes.CFG_MAY_THROW,
										Pair.create("methodName", WrapperUtils.stringToNeo4jQueryString(methodName)),
										Pair.create("exceptionType",
												WrapperUtils.stringToNeo4jQueryString(excType.toString()))));
					}

			}

			throwsTypesInStatementsGrouped.put(entry.getKey(), typesToRelations);
		}
		return throwsTypesInStatementsGrouped;
	}

	public void doInterproceduralPDGAnalysis() {
		Map<NodeWrapper, Iterable<RelationshipWrapper>> methodDecToCalls = new HashMap<NodeWrapper, Iterable<RelationshipWrapper>>();
		GetDeclarationFromExpression getDecs = new GetDeclarationFromExpression();
		methodInfo.values().forEach(mInfo -> {
			getDecs.setInfoForMethod(mInfo);
			Iterable<RelationshipWrapper> callRels = mInfo.methodNode.getRelationships(Direction.OUTGOING,
					CGRelationTypes.CALLS);
			methodDecToCalls.put(mInfo.methodNode, callRels);
			for (RelationshipWrapper callRel : callRels) {
				addCallToCallCache(callRel);
				if (callRel.getEndNode().hasLabel(NodeTypes.NEW_INSTANCE))
					getDecs.scanNewClass(callRel.getEndNode());
				else
					getDecs.scanMethodInvocation(callRel.getEndNode());
			}
		});

		InterproceduralPDG pdgAnalysis = new InterproceduralPDG(methodDecToCalls, getDecs.getInvocationsMayModifyVars(), methodInfo);
		methodInfo.values().forEach(mInfo -> pdgAnalysis.doInterproceduralPDGAnalysis(mInfo));
	}

	private void addCallToCallCache(RelationshipWrapper callRel) {
		NodeWrapper caller = callRel.getStartNode();
		Set<NodeWrapper> calleeList = callGraph.get(caller);
		if (calleeList == null)
			callGraph.put(caller, calleeList = new HashSet<NodeWrapper>());
		for (RelationshipWrapper r : callRel.getEndNode().getRelationships(Direction.OUTGOING,
				CGRelationTypes.REFERS_TO, CGRelationTypes.MAY_REFER_TO))
			calleeList.add(r.getEndNode());
	}

	public void doDynamicMethodCallAnalysis() {
		HierarchyAnalysis dynMethodCallAnalysis = new HierarchyAnalysis(trustableInvocations);
		for (NodeWrapper typeDec : typeDecNodes)
			dynMethodCallAnalysis.dynamicMethodCallAnalysis(typeDec);
	}

	public void doInitializationAnalysis() {
		Set<NodeWrapper> newAccessibleMethods = new HashSet<>(accesibleMethods.values());
		for (NodeWrapper accMethod : accesibleMethods.values())
			addAllOverriders(newAccessibleMethods,
					accMethod.getRelationships(Direction.INCOMING, TypeRelations.OVERRIDES));
		for (NodeWrapper accMethod : new HashSet<>(newAccessibleMethods))
			addAllCallees(newAccessibleMethods, callGraph.get(accMethod));
		for (MethodInfo mInfo : methodInfo.values())
			mInfo.methodNode.setProperty("isInitializer", !newAccessibleMethods.contains(mInfo.methodNode));
	}

	private void addAllCallees(Set<NodeWrapper> newNodes, Set<NodeWrapper> callees) {
		if (callees == null) return;
		for (NodeWrapper callee : callees) {
			if (!newNodes.contains(callee) && (Boolean) callee.getProperty("isDeclared")
					&& callee.hasLabel(NodeTypes.METHOD_DEF)) {
				newNodes.add(callee);
				addAllCallees(newNodes, callGraph.get(callee));
			}
		}
	}

	private void addAllOverriders(Set<NodeWrapper> newNodes, Iterable<RelationshipWrapper> overriders) {
		if (overriders == null)	return;
		for (RelationshipWrapper ovRel : overriders) {
			if (!newNodes.contains(ovRel.getStartNode())) {
				newNodes.add(ovRel.getStartNode());
				addAllCallees(newNodes,
						ovRel.getStartNode().getRelationships(Direction.INCOMING, TypeRelations.OVERRIDES).stream()
								.map(r -> r.getStartNode()).collect(Collectors.toSet()));
			}
		}
	}

	public void createAllParamsToMethodsPDGRels() {
		methodInfo.values().forEach(methodInfo -> {
			for (Entry<NodeWrapper, PDGRelationTypes> paramEntry : methodInfo.paramsToPDGRelations.entrySet()) {
				paramEntry.getKey().createRelationshipTo(methodInfo.methodNode, paramEntry.getValue());
			}
		});
	}
}