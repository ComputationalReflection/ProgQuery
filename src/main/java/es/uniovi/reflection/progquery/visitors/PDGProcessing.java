package es.uniovi.reflection.progquery.visitors;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.PDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PartialRelation;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.MethodState;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import org.neo4j.graphdb.Direction;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class PDGProcessing {
    public static final PDGRelationTypes[] USED = new PDGRelationTypes[]{PDGRelationTypes.USED_BY}, MODIFIED =
            new PDGRelationTypes[]{PDGRelationTypes.MODIFIED_BY}, STATE_MODIFIED =
            new PDGRelationTypes[]{PDGRelationTypes.STATE_MODIFIED_BY}, USED_AND_MOD =
            new PDGRelationTypes[]{PDGRelationTypes.USED_BY, PDGRelationTypes.MODIFIED_BY}, USED_AND_STATE_MOD =
            new PDGRelationTypes[]{PDGRelationTypes.USED_BY, PDGRelationTypes.STATE_MODIFIED_BY};
    private static final Map<PDGRelationTypes[], PDGRelationTypes[]> toModify = getMapToModify();
    private Map<Symbol, NodeWrapper> definitionTable = new HashMap<Symbol, NodeWrapper>();
    private Map<Symbol, List<Consumer<NodeWrapper>>> toDo = new HashMap<Symbol, List<Consumer<NodeWrapper>>>();

    public NodeWrapper lastAssignment;

    private Set<NodeWrapper> parametersPreviouslyModified, auxParamsModified, parametersMaybePrevioslyModified;

    public void visitNewMethod() {
        parametersPreviouslyModified = new HashSet<>();
        parametersMaybePrevioslyModified = new HashSet<>();
    }

    public void enteringNewBranch() {
        auxParamsModified = parametersPreviouslyModified;
        parametersPreviouslyModified = new HashSet<>(parametersPreviouslyModified);
    }

    public void copyParamsToMaybe() {
        for (NodeWrapper parameterDec : parametersPreviouslyModified)
            parametersMaybePrevioslyModified.add(parameterDec);
    }

    public Set<NodeWrapper> exitingCurrentBranch() {
        copyParamsToMaybe();
        Set<NodeWrapper> ret = parametersPreviouslyModified;
        parametersPreviouslyModified = auxParamsModified;
        return ret;
    }

    public void merge(Set<NodeWrapper> paramsOne, Set<NodeWrapper> paramsTwo) {
        parametersPreviouslyModified = paramsOne;
        parametersPreviouslyModified.retainAll(paramsTwo);
    }

    public void mergeParamsWithCurrent(Set<NodeWrapper> otherParams) {
        parametersPreviouslyModified.retainAll(otherParams);
    }

    public void unionWithCurrent(Set<NodeWrapper> otherParams) {
        parametersPreviouslyModified.addAll(otherParams);
    }

    public void setThisRefOfInstanceMethod(MethodState methodState, NodeWrapper currentClassDec) {
        NodeWrapper method = methodState.lastMethodDecVisited;
        if (!method.hasLabel(NodeTypes.ATTR_DEF)) {
            boolean isStatic = (boolean) method.getProperty("isStatic");
            if (!isStatic && methodState.thisNode == null)
                methodState.thisNode = getOrCreateThisNode(currentClassDec).getEndNode();
        }
    }

    public static PDGRelationTypes[] getExprStatementArg(ExpressionStatementTree expStatementTree) {
        return expStatementTree.getExpression().getKind() == Kind.ASSIGNMENT ? MODIFIED : null;
    }

    private static Map<PDGRelationTypes[], PDGRelationTypes[]> getMapToModify() {
        Map<PDGRelationTypes[], PDGRelationTypes[]> map = new HashMap<PDGRelationTypes[], PDGRelationTypes[]>();
        map.put(MODIFIED, STATE_MODIFIED);
        map.put(USED_AND_MOD, USED_AND_STATE_MOD);
        map.put(STATE_MODIFIED, STATE_MODIFIED);
        map.put(USED, USED);
        map.put(USED_AND_STATE_MOD, USED_AND_STATE_MOD);
        map.put(null, null);
        return map;
    }

    public void putDecInCache(Symbol s, NodeWrapper n) {
        if (toDo.containsKey(s)) {
            for (Consumer<NodeWrapper> consumer : toDo.get(s))
                consumer.accept(n);
            toDo.remove(s);
        }
        definitionTable.put(s, n);
    }

    public static Object getLefAssignmentArg(Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        return t.getSecond() == MODIFIED ? MODIFIED : USED_AND_MOD;
    }

    public static Object modifiedToStateModified(Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        return toModify.get(t.getSecond());
    }

    @FunctionalInterface
    private interface IdToStateModThis {
        public NodeWrapper apply(Boolean hasNoDec, NodeWrapper nodeDec, Boolean isNotThis, Symbol s);
    }

    private void addRels(Symbol s, NodeWrapper node, Object ASTVisitorParam, NodeWrapper currentClassDec,
                         boolean isIdent, MethodState methodState, NodeWrapper decNode, boolean isThis,
                         boolean isInstance, Tree tree) {
        List<Consumer<NodeWrapper>> list = null;
        if (decNode == null)
            if (isThis)
                definitionTable.put(s, decNode = getOrCreateThisNode(currentClassDec).getEndNode());
            else {
                list = toDo.get(s);
                if (list == null) {
                    list = new ArrayList<Consumer<NodeWrapper>>();
                    toDo.put(s, list);
                }
            }
        boolean isAttr = decNode == null || decNode.hasLabel(NodeTypes.ATTR_DEF);
        boolean isStatic = s.isStatic();
        if (ASTVisitorParam == null)
            addRelWithoutAnalysis(list, decNode, node, PDGRelationTypes.USED_BY, methodState, isAttr || isThis,
                    isInstance, isStatic);
        else
            for (PDGRelationTypes pdgRel : (PDGRelationTypes[]) ASTVisitorParam)
                addUnknownRel(list, decNode, node, pdgRel, methodState, isIdent, currentClassDec, isAttr, isThis,
                        isInstance, isStatic);
    }

    private void addUnknownRel(List<Consumer<NodeWrapper>> list, NodeWrapper dec, NodeWrapper concrete,
                               PDGRelationTypes rel, MethodState methodState, boolean isIdent,
                               NodeWrapper currentClassDec, boolean isAttr, boolean isThis, boolean isInstance,
                               boolean isStatic) {
        if (rel == PDGRelationTypes.USED_BY)
            addRelWithoutAnalysis(list, dec, concrete, rel, methodState, isAttr || isThis, isInstance, isStatic);
        else
            addNotUseRelWithAnalysis(lastAssignment, dec, rel, isIdent, methodState, list, currentClassDec, isAttr,
                    isThis, isInstance, isStatic);

    }

    public static void addNewPDGRelationFromParamToMethod(boolean mustBeExecuted, PDGRelationTypes currentRel,
                                                          Consumer<PDGRelationTypes> putNewRel) {
        PDGRelationTypes decAndMethodRel =
                mustBeExecuted ? PDGRelationTypes.STATE_MODIFIED_BY : PDGRelationTypes.STATE_MAY_BE_MODIFIED_BY;
        if (currentRel == null)
            putNewRel.accept(decAndMethodRel);
        else if (currentRel == PDGRelationTypes.STATE_MAY_BE_MODIFIED_BY &&
                decAndMethodRel == PDGRelationTypes.STATE_MODIFIED_BY)
            putNewRel.accept(PDGRelationTypes.STATE_MODIFIED_BY);
    }

    private static void addNewPDGRelationFromThisToMethod(NodeWrapper assignment, NodeWrapper thisNode,
                                                          MethodState methodState) {

        addNewPDGRelationFromParamToMethod((Boolean) assignment.getProperty("mustBeExecuted"),
                methodState.thisRelationsOnThisMethod, (newRel) -> {
                    methodState.thisRelationsOnThisMethod = newRel;
                    methodState.thisNode = thisNode;
                });
    }

    private static void addNewPDGRelationFromParamToMethod(NodeWrapper assignment, NodeWrapper paramDec,
                                                           MethodState methodState, boolean notMustAlwaysFalse) {
        addNewPDGRelationFromParamToMethod(false, methodState.paramsToPDGRelations.get(paramDec),
                (newRel) -> methodState.paramsToPDGRelations.put(paramDec, newRel));
    }

    private static void addNewPDGRelationFromParamToMethod(NodeWrapper assignment, NodeWrapper paramDec,
                                                           MethodState methodState) {
        addNewPDGRelationFromParamToMethod((Boolean) assignment.getProperty("mustBeExecuted"),
                methodState.paramsToPDGRelations.get(paramDec),
                (newRel) -> methodState.paramsToPDGRelations.put(paramDec, newRel));
    }

    private boolean mutationAnalysis(NodeWrapper concrete, NodeWrapper dec, PDGRelationTypes rel, boolean isIdent,
                                     MethodState methodState, NodeWrapper currentClassDec, boolean isOwnAccess,
                                     boolean isAttr, boolean isThis, boolean isStatic) {

        if (isIdent) {
            if (isAttr) {
                if (!isStatic) {
                    NodeWrapper implicitThis = getOrCreateThisNode(currentClassDec).getEndNode();
                    createRel(implicitThis, concrete, PDGRelationTypes.STATE_MODIFIED_BY, true, true, isStatic);
                    addNewPDGRelationFromThisToMethod(concrete, implicitThis, methodState);
                }
                return true;
            } else if (isThis) {
                addNewPDGRelationFromThisToMethod(concrete, getOrCreateThisNode(currentClassDec).getEndNode(),
                        methodState);
                return true;
            } else if (isNormalParameter(methodState, dec)) {
                if (rel == PDGRelationTypes.STATE_MODIFIED_BY) {
                    if (!parametersPreviouslyModified.contains(dec)) {
                        if (parametersMaybePrevioslyModified.contains(dec))
                            addNewPDGRelationFromParamToMethod(concrete, dec, methodState, false);
                        else
                            addNewPDGRelationFromParamToMethod(concrete, dec, methodState);
                    }
                } else if (rel == PDGRelationTypes.MODIFIED_BY)
                    parametersPreviouslyModified.add(dec);
            }
            // }
        }
        return false;
    }

    private boolean isNormalParameter(MethodState methodState, NodeWrapper dec) {
        if (dec.hasLabel(NodeTypes.PARAMETER_DEF)) {
            RelationshipWrapper paramRel =
                    dec.getRelationships(Direction.INCOMING, RelationTypes.CALLABLE_HAS_PARAMETER,
                            RelationTypes.LAMBDA_EXPRESSION_PARAMETERS).get(0);
            return paramRel.getStartNode() == methodState.lastMethodDecVisited;
        }
        return false;
    }

    private void addNotUseRelWithAnalysis(NodeWrapper concrete, NodeWrapper dec, PDGRelationTypes rel, boolean isIdent,
                                          MethodState currentMethodState, List<Consumer<NodeWrapper>> toDoListForSymbol,
                                          NodeWrapper currentClassDec, boolean isAttr, boolean isThis,
                                          boolean isInstanceRel, boolean isStatic) {

        if (dec == null)

            toDoListForSymbol
                    .add(decNode -> createRelsAndMutationAnalysis(concrete, decNode, rel, isIdent, currentMethodState,
                            currentClassDec, isAttr, isThis, isInstanceRel, isStatic));
        else
            createRelsAndMutationAnalysis(concrete, dec, rel, isIdent, currentMethodState, currentClassDec, isAttr,
                    isThis, isInstanceRel, isStatic);
    }

    private void createRelsAndMutationAnalysis(NodeWrapper concrete, NodeWrapper dec, PDGRelationTypes rel,
                                               boolean isIdent, MethodState currentMethodState,
                                               NodeWrapper currentClassDec, boolean isAttr, boolean isThis,
                                               boolean isOwnAccess, boolean isStatic) {
        mutationAnalysis(concrete, dec, rel, isIdent, currentMethodState, currentClassDec, isOwnAccess, isAttr, isThis,
                isStatic);
        createRel(dec, concrete, rel, isAttr || isThis, isOwnAccess, isStatic);
        currentMethodState.identificationForLeftAssignExprs.put(concrete, dec);
    }

    private void addRelWithoutAnalysis(List<Consumer<NodeWrapper>> list, NodeWrapper start, NodeWrapper end,
                                       PDGRelationTypes rel, MethodState methodState, boolean isAttrOrThis,
                                       boolean isOwnAccess, boolean isStatic) {
        if (list == null)
            createRel(start, end, rel, isAttrOrThis, isOwnAccess, isStatic);
        else
            futureCreateRelInToDoList(list, end, rel, methodState, isAttrOrThis, isOwnAccess, isStatic);
    }

    private static NodeWrapper createRel(NodeWrapper start, NodeWrapper end, PDGRelationTypes rel,
                                         boolean isAttrDecOrThis, boolean isInstanceRel, boolean isStatic) {
        RelationshipWrapper relationship = start.createRelationshipTo(end, rel);
        if (isAttrDecOrThis && !isStatic)
            relationship.setProperty("isOwnAccess", isInstanceRel);
        return start;
    }

    private void futureCreateRelInToDoList(List<Consumer<NodeWrapper>> list, NodeWrapper end, PDGRelationTypes rel,
                                           MethodState methodState, boolean isAttrDecOrThis, boolean isOwnAccess,
                                           boolean isStatic) {

        list.add(decNode -> createRel(decNode, end, rel, isAttrDecOrThis, isOwnAccess, isStatic));
    }

    public static void createVarDecInitRel(NodeWrapper currentClassDec, NodeWrapper varDecInit, boolean isAttr,
                                           boolean isStatic) {

        if (isAttr && !isStatic)
            createRel(getOrCreateThisNode(currentClassDec).getEndNode(), varDecInit, PDGRelationTypes.STATE_MODIFIED_BY,
                    true, true, false);
    }

    public boolean relationOnIdentifier(IdentifierTree identifierTree, NodeWrapper identifierNode,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t,
                                        NodeWrapper currentClassDec, MethodState methodState) {
        Symbol identSymbol = ((JCIdent) identifierTree).sym;

        if (identSymbol.getKind() == ElementKind.METHOD || identSymbol.getKind() == ElementKind.CONSTRUCTOR ||
                identSymbol.getKind() == ElementKind.TYPE_PARAMETER)
            return false;
        final String RECORD = "RECORD";
        if (identSymbol.getKind() == ElementKind.CLASS || identSymbol.getKind() == ElementKind.INTERFACE ||
                identSymbol.getKind() == ElementKind.ENUM || identSymbol.getKind() == ElementKind.PACKAGE ||
                identSymbol.getKind() == ElementKind.ANNOTATION_TYPE ||
                identSymbol.getKind().toString().contentEquals(RECORD))
            return true;
        NodeWrapper decNode = definitionTable.get(identSymbol);
        boolean isThis = identSymbol.name.contentEquals("this") || identSymbol.name.contentEquals("super"), isInstance =

                (decNode == null || decNode.hasLabel(NodeTypes.ATTR_DEF) || decNode.hasLabel(NodeTypes.THIS_REF)) &&
                        !identSymbol.isStatic();
        addRels(identSymbol, identifierNode, t.getSecond(), currentClassDec, true, methodState, decNode, isThis,
                isInstance, identifierTree);
        return isInstance;
    }

    private static RelationshipWrapper getThisRel(NodeWrapper classDecNode) {
        return classDecNode.getSingleRelationship(Direction.OUTGOING, PDGRelationTypes.HAS_THIS_REFERENCE);
    }

    private static RelationshipWrapper getOrCreateThisNode(NodeWrapper classDecNode) {
        RelationshipWrapper r = getThisRel(classDecNode);
        if (r != null)
            return r;
        return classDecNode.createRelationshipTo(
                DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.THIS_REF),
                PDGRelationTypes.HAS_THIS_REFERENCE);

    }

    public void createNotDeclaredAttrRels(ASTAuxiliarStorage ast) {
        for (Entry<Symbol, List<Consumer<NodeWrapper>>> entry : toDo.entrySet()) {
            VarSymbol symbol = (VarSymbol) entry.getKey();
            if (symbol.name.contentEquals("class"))
                continue;
            NodeWrapper fieldDec = createNotDeclaredAttr(symbol, ast);
            entry.getValue().forEach(decConsumer -> decConsumer.accept(fieldDec));

        }
        toDo.clear();
    }

    private NodeWrapper createNotDeclaredAttr(VarSymbol s, ASTAuxiliarStorage ast) {
        NodeWrapper decNode = DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.ATTR_DEF);

        decNode.setProperty("isDeclared", false);
        decNode.setProperty("name", s.name.toString());
        Set<Modifier> modifiers = Flags.asModifierSet(s.flags_field);
        ASTTypesVisitor.checkAttrDecModifiers(modifiers, decNode);
        GraphUtils.attachType(decNode, s.type, ast);
        DefinitionCache.getOrCreateType(s.owner.type, ast).createRelationshipTo(decNode, RelationTypes.DECLARES_FIELD);
        return decNode;
    }

    public void relationOnFieldAccess(MemberSelectTree memberSelectTree, NodeWrapper memberSelectNode,
                                      Pair<PartialRelation<RelationTypesInterface>, Object> t, MethodState methodState,
                                      NodeWrapper currentClassDec, boolean isInstance) {
        Symbol symbol = ((JCFieldAccess) memberSelectTree).sym;
		final String RECORD = "RECORD";
        if (symbol.getKind() == ElementKind.CLASS || symbol.getKind() == ElementKind.ANNOTATION_TYPE ||
                symbol.getKind() == ElementKind.INTERFACE || symbol.getKind() == ElementKind.ENUM ||
                symbol.getKind() == ElementKind.METHOD || symbol.getKind() == ElementKind.CONSTRUCTOR ||
                symbol.getKind() == ElementKind.PACKAGE || symbol.getKind().toString().contentEquals(RECORD))
            return;
        NodeWrapper decNode = definitionTable.get(symbol);

        addRels(symbol, memberSelectNode, t.getSecond(), currentClassDec,
                false, methodState, decNode, memberSelectTree.getIdentifier().contentEquals("this") ||
                        memberSelectTree.getIdentifier().contentEquals("super"), isInstance, memberSelectTree);
    }

    public void addParamsPrevModifiedForInv(NodeWrapper methodInvocationNode, MethodState methodState) {
        if (parametersPreviouslyModified == null)
            return;
        if (parametersPreviouslyModified.size() > 0) {
            methodState.callsToParamsPreviouslyModified
                    .put(methodInvocationNode, new HashSet<>(parametersPreviouslyModified));
        }
        if (parametersMaybePrevioslyModified.size() > 0) {
            methodState.callsToParamsMaybePreviouslyModified
                    .put(methodInvocationNode, new HashSet<>(parametersMaybePrevioslyModified));
        }
    }
}
