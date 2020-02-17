package ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

import database.nodes.NodeTypes;
import database.querys.cypherWrapper.EdgeDirection;
import database.relations.CFGRelationTypes;
import database.relations.CGRelationTypes;
import database.relations.PDGRelationTypes;
import database.relations.PartialRelation;
import database.relations.PartialRelationWithProperties;
import database.relations.TypeRelations;
import mig.HierarchyAnalysis;
import node_wrappers.NodeWrapper;
import node_wrappers.RelationshipWrapper;
import node_wrappers.WrapperUtils;
import pdg.GetDeclarationFromExpression;
import pdg.InterproceduralPDG;
import utils.dataTransferClasses.MethodInfo;
import utils.dataTransferClasses.MethodState;
import utils.dataTransferClasses.Pair;

public class ASTAuxiliarStorage {
	private static final String OBJECT_CLASS_NAME = "java.lang.Object";

	// public static List<EnhancedForLoopTree> enhancedForLoopList = new
	// ArrayList<EnhancedForLoopTree>();
	// public static Map<MethodTree, List<StatementTree>>
	// nestedConstructorsToBlocks = new HashMap<MethodTree,
	// List<StatementTree>>();
	// public static List<Pair<AssertTree, NodeWrapper>> assertList = new
	// ArrayList<Pair<AssertTree, NodeWrapper>>();

	private Stack<List<Pair<NodeWrapper, List<MethodSymbol>>>> lastInvocationInStatementLists = new Stack<List<Pair<NodeWrapper, List<MethodSymbol>>>>();

	// private final Map<String, List<Type>> methodNamesToExceptionThrowsTypes =
	// new HashMap<String, List<Type>>();
	private Map<NodeWrapper, MethodInfo> methodInfo = new HashMap<>();
	public final Set<NodeWrapper> typeDecNodes = new HashSet<NodeWrapper>();
	private final Set<NodeWrapper> trustableInvocations = new HashSet<NodeWrapper>();

	// METHOD_DEC,CALL
	// private final Set<Pair<Node, NodeWrapper>> calls = new HashSet<>();
	private final Map<MethodSymbol, NodeWrapper> accesibleMethods = new HashMap<>();
	private final Map<NodeWrapper, Set<NodeWrapper>> callGraph = new HashMap<>();

	// public void addThrowsInfoToMethod(String methodName, List<Type>
	// exceptionTypes) {
	// methodNamesToExceptionThrowsTypes.put(methodName, exceptionTypes);
	// }
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

	public Map<NodeWrapper, MethodInfo> getMethodsInfo() {
		return methodInfo;
	}

	public void addInfo(MethodTree methodTree, NodeWrapper methodNode, MethodState methodState) {

		methodInfo.put(methodNode,
				new MethodInfo(methodTree, methodNode, methodState.identificationForLeftAssignExprs,
						methodState.thisNode, methodState.thisRelationsOnThisMethod, methodState.paramsToPDGRelations,
						methodState.callsToParamsPreviouslyModified, methodState.callsToParamsMaybePreviouslyModified));
	}

	public void addAccesibleMethod(MethodSymbol ms, NodeWrapper method) {
		accesibleMethods.put(ms, method);

	}

	public void deleteAccesibleMethod(MethodSymbol ms) {
		accesibleMethods.remove(ms);
	}
	// public void putConditionInCfgCache(ExpressionTree tree, NodeWrapper n) {
	// if (tree instanceof ParenthesizedTree)
	// n = n.getSingleRelationship(RelationTypes.PARENTHESIZED_ENCLOSES,
	// Direction.OUTGOING).getEndNode();
	// if (tree != null)
	// cfgNodeCache.put(tree, n);
	// }

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

		// s with INIT INVSTATEMENS
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
						
						String methodName=methodSymbol.owner.getQualifiedName() +":"+methodSymbol.toString();
						if(methodSymbol.isConstructor())
							methodName=	methodName.replaceAll(":(\\w)+\\(", ":<init>(");
						
						typesToRelations.get(excType)
								.add(new PartialRelationWithProperties<CFGRelationTypes>(
										invocationsInStatement.getFirst(), CFGRelationTypes.CFG_MAY_THROW,
										Pair.create("methodName",
												WrapperUtils.stringToNeo4jQueryString(
														methodName)),
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
		// Get Declarations Analysis
		GetDeclarationFromExpression getDecs = new GetDeclarationFromExpression();
		// thisRefsOfMethods.entrySet().forEach(e -> {
		// System.out.println(
		// "ENTRY :\n" + NodeUtils.nodeToString(e.getKey()) + "\n" +
		// NodeUtils.nodeToString(e.getValue()));
		// });

		methodInfo.values().forEach(mInfo -> {
			getDecs.setInfoForMethod(mInfo);
			Iterable<RelationshipWrapper> callRels = mInfo.methodNode.getRelationships(EdgeDirection.OUTGOING,
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

		// Interprocedural analysis
		InterproceduralPDG pdgAnalysis = new InterproceduralPDG(methodDecToCalls, getDecs.getInvocationsMayModifyVars(),
				methodInfo);
		methodInfo.values().forEach(mInfo -> pdgAnalysis.doInterproceduralPDGAnalysis(mInfo));

	}

	private void addCallToCallCache(RelationshipWrapper callRel) {
		NodeWrapper caller = callRel.getStartNode();
		Set<NodeWrapper> calleeList = callGraph.get(caller);
		if (calleeList == null)
			callGraph.put(caller, calleeList = new HashSet<NodeWrapper>());
		for (RelationshipWrapper r : callRel.getEndNode().getRelationships(EdgeDirection.OUTGOING,
				CGRelationTypes.REFERS_TO, CGRelationTypes.MAY_REFER_TO))
			calleeList.add(r.getEndNode());
	}

	// Aqu� tenemos dos opciones, bucle o traversal
	public void doDynamicMethodCallAnalysis() {
		HierarchyAnalysis dynMethodCallAnalysis = new HierarchyAnalysis(trustableInvocations);
//		System.out.println("TYPE DEC LIST PREV ANALYSIS ");
//		System.out.println(typeDecNodes);
		for (NodeWrapper typeDec : typeDecNodes)
			dynMethodCallAnalysis.dynamicMethodCallAnalysis(typeDec);
	}

	public void doInitializationAnalysis() {
		Set<NodeWrapper> newAccessibleMethods = new HashSet<>(accesibleMethods.values());

		for (NodeWrapper accMethod : accesibleMethods.values())
			addAllOverriders(newAccessibleMethods,
					accMethod.getRelationships(EdgeDirection.INCOMING, TypeRelations.OVERRIDES));

		for (NodeWrapper accMethod : new HashSet<>(newAccessibleMethods))
			addAllCallees(newAccessibleMethods, callGraph.get(accMethod));
		// newAccessibleMethods.forEach(n ->
		// System.out.println(n.getProperty("fullyQualifiedName")));
		for (MethodInfo mInfo : methodInfo.values())
			// SI NO TIENE NADIE QUE LO LLAME ... SE LO PREGUNTAMOS A ORTIN

			mInfo.methodNode.setProperty("isInitializer", !newAccessibleMethods.contains(mInfo.methodNode));
		// mInfo.instanceAssignments.entrySet().forEach(instanceAssignPair -> {
		//
		// if (isInitMethod)
		// instanceAssignPair.getKey().addLabel(NodeTypes.INITIALIZATION);
		// else if (instanceAssignPair.getValue())
		// // IF NEEDS TO BE LABELED AS ASIGNMENT
		// instanceAssignPair.getKey().addLabel(NodeTypes.ASSIGNMENT);
		// });
		// mInfo.getDecToInstanceInvRels().forEach(rel -> {
		// rel.setProperty("isInit", isInitMethod);
		// });

		// AWUI VENDR�A LA PREGUNTA DE, ALGUNO LO LLAM�???

	}

	private void addAllCallees(Set<NodeWrapper> newNodes, Set<NodeWrapper> callees) {
		if (callees == null)
			return;
		for (NodeWrapper callee : callees)
			if (!newNodes.contains(callee) && (Boolean) callee.getProperty("isDeclared")
					&& callee.hasLabel(NodeTypes.METHOD_DEF)) {
				newNodes.add(callee);
				addAllCallees(newNodes, callGraph.get(callee));
			}

	}

	private void addAllOverriders(Set<NodeWrapper> newNodes, Iterable<RelationshipWrapper> overriders) {
		if (overriders == null)
			return;
		for (RelationshipWrapper ovRel : overriders)
			if (!newNodes.contains(ovRel.getStartNode())) {
				newNodes.add(ovRel.getStartNode());
				addAllCallees(newNodes,
						ovRel.getStartNode().getRelationships(EdgeDirection.INCOMING, TypeRelations.OVERRIDES).stream()
								.map(r -> r.getStartNode()).collect(Collectors.toSet()));
			}

	}

	public void createAllParamsToMethodsPDGRels() {
		// System.out.println("STARTING CREATING PARAMS RELS ");
		methodInfo.values().forEach(methodInfo -> {
			// PORQUE LOS CONSTRUCTORES SE QUEDAN FUERA?!?!?! TIENE SENTIDO,
			// PORQUE AUNQUE TENGAN ARCOS CON THIS, LOS DE LOS PARAMETROS
			// DEBER�AN PROPAGARLOS
			// System.out.println("CREATING FOR METHOD " +
			// methodInfo.methodNode.getProperty("name"));
			for (Entry<NodeWrapper, PDGRelationTypes> paramEntry : methodInfo.paramsToPDGRelations.entrySet()) {
				// System.out.println(paramEntry.getKey().getLabels().iterator().next());
				// System.out.println(paramEntry.getValue());

				paramEntry.getKey().createRelationshipTo(methodInfo.methodNode, paramEntry.getValue());
			}

		});
	}
}
