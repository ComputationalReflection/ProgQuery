package es.uniovi.reflection.progquery.visitors;

import com.sun.source.tree.*;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeCategory;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.nodes.NodeUtils;
import es.uniovi.reflection.progquery.database.relations.*;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.typeInfo.PackageInfo;
import es.uniovi.reflection.progquery.typeInfo.TypeHierarchy;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.utils.MethodNameInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.*;
import org.neo4j.graphdb.Direction;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ASTTypesVisitor
        extends TreeScanner<ASTVisitorResult, Pair<PartialRelation<RelationTypesInterface>, Object>> {
    private NodeWrapper lastStaticConsVisited = null;
    private ClassTree typeDec;
    private boolean first;
    private PDGProcessing pdgUtils;
    public ASTAuxiliarStorage ast;
    private MethodState methodState = null;
    private ClassState classState = null;
    private boolean insideConstructor = false;
    private List<MethodSymbol> currentMethodInvocations = new ArrayList<MethodSymbol>();
    private final NodeWrapper currentCU;
    private boolean must = true, prevMust = true, auxMust = true;
    private boolean anyBreak;
    private Set<NodeWrapper> typeDecUses;
    private ClassSymbol currentTypeDecSymbol;
    private boolean outsideAnnotation = true;
    private boolean isInAccessibleContext = true;
    private Set<Name> gotoLabelsInDoWhile = new HashSet<>();
    private boolean inADoWhile = false, inALambda = false;

    public Set<NodeWrapper> getTypeDecUses() {
        return typeDecUses;
    }

    public ASTTypesVisitor(ClassTree typeDec, boolean first, PDGProcessing pdgUtils, ASTAuxiliarStorage ast,
                           NodeWrapper cu) {
        this.typeDec = typeDec;
        this.first = first;
        this.pdgUtils = pdgUtils;
        this.ast = ast;
        this.currentCU = cu;
    }

    private NodeWrapper addInvocationInStatement(NodeWrapper statement) {
        ast.addInvocationInStatement(statement, currentMethodInvocations);
        currentMethodInvocations = new ArrayList<MethodSymbol>();
        return statement;
    }

    @Override
    public ASTVisitorResult reduce(ASTVisitorResult n1, ASTVisitorResult n2) {
        return n2;
    }

    private NodeWrapper getNotDeclaredConsFromInv(Symbol methodSymbol, MethodNameInfo nameInfo) {
        NodeWrapper consDec = getNotDeclaredConstructorDecNode(methodSymbol, nameInfo);
        DefinitionCache.getOrCreateType(methodSymbol.owner.type, ast)
                .createRelationshipTo(consDec, RelationTypes.DECLARES_CONSTRUCTOR);
        return consDec;
    }

    public static NodeWrapper getNotDeclaredConstructorDuringTypeCreation(MethodNameInfo nameInfo,
                                                                          NodeWrapper classNode, Symbol s) {
        NodeWrapper consDec = getNotDeclaredConstructorDecNode(s, nameInfo);
        classNode.createRelationshipTo(consDec, RelationTypes.DECLARES_CONSTRUCTOR);
        return consDec;
    }

    public static NodeWrapper getNotDeclaredConstructorDuringTypeCreation(NodeWrapper classNode, Symbol s) {

        MethodNameInfo nameInfo = new MethodNameInfo((MethodSymbol) s);
        NodeWrapper consDec = getNotDeclaredConstructorDecNode(s, nameInfo);
        classNode.createRelationshipTo(consDec, RelationTypes.DECLARES_CONSTRUCTOR);
        return consDec;
    }

    private static NodeWrapper getNotDeclaredConstructorDecNode(Symbol s, MethodNameInfo nameInfo) {
        NodeWrapper constructorDef =
                DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.CONSTRUCTOR_DEF);
        constructorDef.setProperty("isDeclared", false);
        constructorDef.setProperty("name", nameInfo.getSimpleName());
        constructorDef.setProperty("fullyQualifiedName", nameInfo.getFullyQualifiedName());
        constructorDef.setProperty("completeName", nameInfo.getCompleteName());
        modifierAccessLevelToNode(s.getModifiers(), constructorDef);
        DefinitionCache.METHOD_DEF_CACHE.get().put(nameInfo.getFullyQualifiedName(), constructorDef);
        return constructorDef;
    }

    private NodeWrapper getNotDeclaredMethodDecNode(MethodSymbol symbol, MethodNameInfo nameInfo) {

        ClassSymbol ownerSymbol = (ClassSymbol) symbol.owner;

        NodeWrapper methodDec =
                createNonDeclaredMethodDuringTypeCreation(ownerSymbol.isInterface(), ast, symbol, nameInfo);
        DefinitionCache.getOrCreateType(symbol.owner.type, ast)
                .createRelationshipTo(methodDec, RelationTypes.DECLARES_METHOD);
        return methodDec;
    }

    public static NodeWrapper createNonDeclaredMethodDuringTypeCreation(MethodNameInfo nameInfo, NodeWrapper classNode,
                                                                        boolean isInterface, ASTAuxiliarStorage ast,
                                                                        MethodSymbol symbol) {
        NodeWrapper methodDec = createNonDeclaredMethodDuringTypeCreation(isInterface, ast, symbol, nameInfo);
        classNode.createRelationshipTo(methodDec, RelationTypes.DECLARES_METHOD);
        return methodDec;
    }

    public static NodeWrapper createNonDeclaredMethodDuringTypeCreation(NodeWrapper classNode, boolean isInterface, ASTAuxiliarStorage ast, MethodSymbol symbol) {
        NodeWrapper methodDec = createNonDeclaredMethodDuringTypeCreation(isInterface, ast, symbol, new MethodNameInfo(symbol));
        classNode.createRelationshipTo(methodDec, RelationTypes.DECLARES_METHOD);
        return methodDec;
    }

    private static NodeWrapper createNonDeclaredMethodWithoutSymbol(MethodNameInfo nameInfo) {
        NodeWrapper methodDecNode = DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.METHOD_DEF);
        methodDecNode.setProperty("isDeclared", false);
        methodDecNode.setProperty("name", nameInfo.getSimpleName());
        methodDecNode.setProperty("completeName", nameInfo.getCompleteName());
        methodDecNode.setProperty("fullyQualifiedName", nameInfo.getFullyQualifiedName());
        return methodDecNode;
    }

    private static NodeWrapper createNonDeclaredMethodDuringTypeCreation(boolean isInterface, ASTAuxiliarStorage ast, MethodSymbol symbol, MethodNameInfo nameInfo) {
        NodeWrapper methodDecNode = createNonDeclaredMethodWithoutSymbol(nameInfo);
        setMethodModifiers(Flags.asModifierSet(symbol.flags()), methodDecNode, isInterface);
        ast.addAccesibleMethod(symbol, methodDecNode);
        DefinitionCache.METHOD_DEF_CACHE.get().put(nameInfo.getFullyQualifiedName(), methodDecNode);
        return methodDecNode;
    }

    @Override
    public ASTVisitorResult visitAnnotatedType(AnnotatedTypeTree annotatedTypeTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper annotatedTypeNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(annotatedTypeTree, NodeTypes.ANNOTATED_TYPE);
        attachTypeDirect(annotatedTypeNode, annotatedTypeTree);
        GraphUtils.connectWithParent(annotatedTypeNode, t);
        scan(annotatedTypeTree.getAnnotations(), Pair.createPair(annotatedTypeNode, RelationTypes.HAS_ANNOTATIONS));
        scan(annotatedTypeTree.getUnderlyingType(), Pair.createPair(annotatedTypeNode, RelationTypes.UNDERLYING_TYPE));
        return null;
    }

    @Override
    public ASTVisitorResult visitAnnotation(AnnotationTree annotationTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper annotationNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(annotationTree, NodeTypes.ANNOTATION);
        GraphUtils.connectWithParent(annotationNode, t, RelationTypes.HAS_ANNOTATIONS);
        boolean prevInsideAnn = outsideAnnotation;
        outsideAnnotation = false;
        scan(annotationTree.getAnnotationType(), Pair.createPair(annotationNode, RelationTypes.HAS_ANNOTATION_TYPE));
        for (int i = 0; i < annotationTree.getArguments().size(); i++)
            scan(annotationTree.getArguments().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(annotationNode, RelationTypes.HAS_ANNOTATIONS_ARGUMENTS,
                            "argumentIndex", i + 1)));
        outsideAnnotation = prevInsideAnn;
        return null;
    }

    @Override
    public ASTVisitorResult visitArrayAccess(ArrayAccessTree arrayAccessTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper arrayAccessNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(arrayAccessTree, NodeTypes.ARRAY_ACCESS);
        attachTypeDirect(arrayAccessNode, arrayAccessTree);
        GraphUtils.connectWithParent(arrayAccessNode, t);
        ASTVisitorResult res = scan(arrayAccessTree.getExpression(),
                Pair.createPair(arrayAccessNode, RelationTypes.ARRAYACCESS_EXPR,
                        PDGProcessing.modifiedToStateModified(t)));
        scan(arrayAccessTree.getIndex(), Pair.createPair(arrayAccessNode, RelationTypes.ARRAYACCESS_INDEX));
        return res;
    }

    @Override
    public ASTVisitorResult visitArrayType(ArrayTypeTree arrayTypeTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper arrayTypeNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(arrayTypeTree, NodeTypes.ARRAY_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(arrayTypeNode, t);
        String fullyName = ((JCArrayTypeTree) arrayTypeTree).type.toString();
        arrayTypeNode.setProperty("fullyQualifiedName", fullyName);
        String[] splittedName = fullyName.split(".");
        arrayTypeNode.setProperty("simpleName", splittedName.length == 0 ? fullyName : splittedName[splittedName.length - 1]);
        scan(arrayTypeTree.getType(), Pair.createPair(arrayTypeNode, RelationTypes.TYPE_PER_ELEMENT));
        if (arrayTypeTree.getType() instanceof IdentifierTree)
            addClassIdentifier(((JCIdent) arrayTypeTree.getType()).type);
        return null;
    }

    @Override
    public ASTVisitorResult visitAssert(AssertTree assertTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper assertNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(assertTree, NodeTypes.ASSERT_STATEMENT);
        GraphUtils.connectWithParent(assertNode, t);
        scan(assertTree.getCondition(), Pair.createPair(assertNode, RelationTypes.ASSERT_CONDITION));
        addInvocationInStatement(assertNode);
        methodState.putCfgNodeInCache(assertTree, assertNode);
        scan(assertTree.getDetail(), Pair.createPair(assertNode, RelationTypes.ASSERT_DETAIL));
        return null;
    }

    private NodeWrapper beforeScanAnyAssign(NodeWrapper assignmentNode, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        assignmentNode.setProperty("mustBeExecuted", must);
        NodeWrapper previousAssignment = pdgUtils.lastAssignment;
        pdgUtils.lastAssignment = assignmentNode;
        return previousAssignment;
    }

    private void afterScanAnyAssign(NodeWrapper previousAssignment) {
        pdgUtils.lastAssignment = previousAssignment;
    }

    @Override
    public ASTVisitorResult visitAssignment(AssignmentTree assignmentTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper assignmentNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(assignmentTree, NodeTypes.ASSIGNMENT);

        GraphUtils.connectWithParent(assignmentNode, t);
        attachTypeDirect(assignmentNode, assignmentTree);
        if (outsideAnnotation) {
            NodeWrapper previousLastASsignInfo = beforeScanAnyAssign(assignmentNode, t);
            scan(assignmentTree.getVariable(), Pair.createPair(assignmentNode, RelationTypes.ASSIGNMENT_LHS,
                    PDGProcessing.getLefAssignmentArg(t)));

            afterScanAnyAssign(previousLastASsignInfo);
            scan(assignmentTree.getExpression(),
                    Pair.createPair(assignmentNode, RelationTypes.ASSIGNMENT_RHS, PDGProcessing.USED));
        } else {
            scan(assignmentTree.getVariable(), Pair.createPair(assignmentNode, RelationTypes.ASSIGNMENT_LHS,
                    PDGProcessing.getLefAssignmentArg(t)));
            scan(assignmentTree.getExpression(),
                    Pair.createPair(assignmentNode, RelationTypes.ASSIGNMENT_RHS, PDGProcessing.USED));
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitBinary(BinaryTree binaryTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper binaryNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(binaryTree, NodeTypes.BINARY_OPERATION);
        binaryNode.setProperty("operator", binaryTree.getKind().toString());
        attachTypeDirect(binaryNode, binaryTree);
        GraphUtils.connectWithParent(binaryNode, t);

        scan(binaryTree.getLeftOperand(), Pair.createPair(binaryNode, RelationTypes.BINOP_LHS));
        scan(binaryTree.getRightOperand(), Pair.createPair(binaryNode,

                binaryTree.getKind().toString().contentEquals("OR") ||
                        binaryTree.getKind().toString().contentEquals("AND") ? RelationTypes.BINOP_COND_RHS :
                        RelationTypes.BINOP_RHS));
        return null;
    }

    private NodeWrapper lastBlockVisited;

    @Override
    public ASTVisitorResult visitBlock(BlockTree blockTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        lastBlockVisited = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(blockTree, NodeTypes.BLOCK);
        lastBlockVisited.setProperty("isStatic", blockTree.isStatic());
        boolean isStaticInit = t.getFirst().getRelationType() == RelationTypes.HAS_STATIC_INIT;
        MethodState prevState = null;
        if (isStaticInit) {
            prevState = methodState;
            methodState = new MethodState(lastStaticConsVisited = lastBlockVisited);
            pdgUtils.visitNewMethod();
            ast.newMethodDeclaration(methodState);
        }

        GraphUtils.connectWithParent(lastBlockVisited, t);

        scan(blockTree.getStatements(), Pair.createPair(lastBlockVisited, RelationTypes.ENCLOSES));
        if (isStaticInit) {
            methodState = prevState;
            ast.endMethodDeclaration();
        }

        return null;
    }

    @Override
    public ASTVisitorResult visitBreak(BreakTree breakTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        anyBreak = false;
        NodeWrapper breakNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(breakTree, NodeTypes.BREAK_STATEMENT);
        methodState.putCfgNodeInCache(breakTree, breakNode);
        must = false;
        if (breakTree.getLabel() != null) {
            breakNode.setProperty("label", breakTree.getLabel().toString());
            if (inADoWhile) {
                gotoLabelsInDoWhile.add(breakTree.getLabel());
                auxMust = prevMust;
                prevMust = false;
            }
        }
        GraphUtils.connectWithParent(breakNode, t);

        return null;
    }

    @Override
    public ASTVisitorResult visitCase(CaseTree caseTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper caseNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(caseTree, NodeTypes.CASE_STATEMENT);
        GraphUtils.connectWithParent(caseNode, t);
        prevMust = must;
        boolean isAUnconditionalDefault = caseTree.getExpressions().isEmpty() && !anyBreak;
        must = prevMust && isAUnconditionalDefault;
        if (!isAUnconditionalDefault)
            pdgUtils.enteringNewBranch();
        scan(caseTree.getExpressions(), Pair.createPair(caseNode, RelationTypes.CASE_EXPR));
        scan(caseTree.getStatements(), Pair.createPair(caseNode, RelationTypes.CASE_STATEMENTS));

        must = prevMust;
        return isAUnconditionalDefault ? null : new VisitorResultImpl(pdgUtils.exitingCurrentBranch());

    }

    @Override
    public ASTVisitorResult visitCatch(CatchTree catchTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper catchNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(catchTree, NodeTypes.CATCH_BLOCK);
        methodState.putCfgNodeInCache(catchTree, catchNode);
        GraphUtils.connectWithParent(catchNode, t);

        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(catchTree.getParameter(), Pair.createPair(catchNode, RelationTypes.CATCH_PARAM));
        scan(catchTree.getBlock(), Pair.createPair(catchNode, RelationTypes.CATCH_ENCLOSES_BLOCK));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    @Override

    public ASTVisitorResult visitClass(ClassTree classTree,
                                       Pair<PartialRelation<RelationTypesInterface>, Object> pair) {

        ClassSymbol previousClassSymbol = currentTypeDecSymbol;
        currentTypeDecSymbol = ((JCClassDecl) classTree).sym;
        String simpleName = classTree.getSimpleName().toString();

        String fullyQualifiedType = currentTypeDecSymbol.toString();
        if (simpleName.equals("")) {
            String[] split = fullyQualifiedType.split(fullyQualifiedType.contains(".") ? "\\." : " ");
            simpleName = split[split.length - 1];
            simpleName = simpleName.substring(0, simpleName.length() - 1);
        }

        NodeWrapper classNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createTypeDecNode(classTree, simpleName, fullyQualifiedType);
        classNode.addLabel(NodeCategory.AST_NODE);

        ast.typeDecNodes.add(classNode);

        ClassState previousClassState = classState;
        classState = new ClassState(classNode);

        Set<NodeWrapper> previousTypeDecUses = typeDecUses;
        typeDecUses = new HashSet<>();

        Symbol outerMostClass = ((JCClassDecl) classTree).sym.outermostClass();

        if (currentTypeDecSymbol != outerMostClass)
            addClassIdentifier(outerMostClass);
        if (!pair.getFirst().getStartingNode().hasLabel(NodeTypes.COMPILATION_UNIT))
            currentCU.createRelationshipTo(classNode, CDGRelationTypes.HAS_INNER_TYPE_DEF);

        GraphUtils.connectWithParent(classNode, pair, RelationTypes.HAS_TYPE_DEF);

        DefinitionCache.putClassDefinition(currentTypeDecSymbol, classNode, ast.typeDecNodes, typeDecUses);

        TypeHierarchy.addTypeHierarchy(currentTypeDecSymbol, classNode, this, ast);
        boolean prevIsInAccesibleContext = isInAccessibleContext;
        if (pair.getFirst().getRelationType() == RelationTypes.NEW_CLASS_BODY) {
            visitAnonymousClassModifiers(classTree.getModifiers(), classNode);

            isInAccessibleContext = false;
        } else {
            scan(classTree.getModifiers(), Pair.createPair(classNode, null));

            isInAccessibleContext =
                    isInAccessibleContext && classNode.getProperty("accessLevel").toString().contentEquals("public");
        }

        int i = 0;
        for (; i < classTree.getTypeParameters().size(); i++)
            scan(classTree.getTypeParameters().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(classNode, RelationTypes.HAS_CLASS_TYPEPARAMETERS, "paramIndex",
                            i + 1)));
        if (i > 0)
            classNode.addLabel(NodeTypes.GENERIC_TYPE);
        scan(classTree.getExtendsClause(), Pair.createPair(classNode, RelationTypes.HAS_EXTENDS_CLAUSE));

        scan(classTree.getImplementsClause(), Pair.createPair(classNode, RelationTypes.HAS_IMPLEMENTS_CLAUSE));

        List<NodeWrapper> attrs = new ArrayList<NodeWrapper>(), staticAttrs = new ArrayList<NodeWrapper>(),
                constructors = new ArrayList<NodeWrapper>();
        NodeWrapper prevStaticCons = lastStaticConsVisited;

        scan(classTree.getMembers(), Pair.createPair(classNode, RelationTypes.HAS_STATIC_INIT,
                Pair.create(Pair.create(attrs, staticAttrs), constructors)));
        for (NodeWrapper constructor : constructors)
            for (NodeWrapper instanceAttr : attrs)
                callsFromVarDecToConstructor(instanceAttr, constructor);
        if (lastStaticConsVisited != null)
            for (NodeWrapper staticAttr : staticAttrs)
                callsFromVarDecToConstructor(staticAttr, lastStaticConsVisited);
        lastStaticConsVisited = prevStaticCons;
        classState = previousClassState;
        typeDecUses = previousTypeDecUses;
        currentTypeDecSymbol = previousClassSymbol;
        isInAccessibleContext = prevIsInAccesibleContext;
        return null;

    }

    private static void callsFromVarDecToConstructor(NodeWrapper attr, NodeWrapper constructor) {
        for (RelationshipWrapper r : attr.getRelationships(Direction.OUTGOING, CGRelationTypes.CALLS)) {
            RelationshipWrapper callRelation = constructor.createRelationshipTo(r.getEndNode(), CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", r.getProperty("mustBeExecuted"));
            r.delete();
        }
    }

    @Override
    public ASTVisitorResult visitCompilationUnit(CompilationUnitTree compilationUnitTree,
                                                 Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        if (first) {
            currentCU.setProperty("packageName", ((JCCompilationUnit) compilationUnitTree).packge.toString());
            scan(compilationUnitTree.getPackageAnnotations(), pair);
            scan(compilationUnitTree.getImports(), pair);
        }
        if (compilationUnitTree.getTypeDecls().size() == 0)
            return null;

        scan(typeDec, pair);

        return null;
    }

    @Override
    public ASTVisitorResult visitCompoundAssignment(CompoundAssignmentTree compoundAssignmentTree,
                                                    Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper assignmentNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(compoundAssignmentTree, NodeTypes.COMPOUND_ASSIGNMENT);
        assignmentNode.setProperty("operator", compoundAssignmentTree.getKind().toString());

        GraphUtils.connectWithParent(assignmentNode, t);
        attachTypeDirect(assignmentNode, compoundAssignmentTree);
        NodeWrapper lasAssignInfo = beforeScanAnyAssign(assignmentNode, t);

        scan(compoundAssignmentTree.getVariable(),
                Pair.createPair(assignmentNode, RelationTypes.COMPOUND_ASSIGNMENT_LHS,
                        PDGProcessing.getLefAssignmentArg(t)));
        afterScanAnyAssign(lasAssignInfo);
        scan(compoundAssignmentTree.getExpression(),
                Pair.createPair(assignmentNode, RelationTypes.COMPOUND_ASSIGNMENT_RHS, PDGProcessing.USED));
        return null;
    }

    @Override
    public ASTVisitorResult visitConditionalExpression(ConditionalExpressionTree conditionalTree,
                                                       Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper conditionalExprNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(conditionalTree, NodeTypes.CONDITIONAL_EXPRESSION);
        attachTypeDirect(conditionalExprNode, conditionalTree);
        GraphUtils.connectWithParent(conditionalExprNode, t);

        scan(conditionalTree.getCondition(),
                Pair.createPair(conditionalExprNode, RelationTypes.CONDITIONAL_EXPR_CONDITION));
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(conditionalTree.getTrueExpression(),
                Pair.createPair(conditionalExprNode, RelationTypes.CONDITIONAL_EXPR_THEN));
        Set<NodeWrapper> paramsThen = pdgUtils.exitingCurrentBranch();
        pdgUtils.enteringNewBranch();
        scan(conditionalTree.getFalseExpression(),
                Pair.createPair(conditionalExprNode, RelationTypes.CONDITIONAL_EXPR_ELSE));
        must = prevMust;
        pdgUtils.merge(paramsThen, pdgUtils.exitingCurrentBranch());
        return null;
    }

    @Override
    public ASTVisitorResult visitContinue(ContinueTree continueTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper continueNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(continueTree, NodeTypes.CONTINUE_STATEMENT);
        methodState.putCfgNodeInCache(continueTree, continueNode);
        if (continueTree.getLabel() != null) {
            continueNode.setProperty("label", continueTree.getLabel().toString());
            if (inADoWhile) {
                gotoLabelsInDoWhile.add(continueTree.getLabel());
                auxMust = prevMust;
                prevMust = false;
            }
        }
        must = false;
        GraphUtils.connectWithParent(continueNode, t);
        return null;
    }

    @Override
    public ASTVisitorResult visitDoWhileLoop(DoWhileLoopTree doWhileLoopTree,
                                             Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper doWhileLoopNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(doWhileLoopTree, NodeTypes.DO_WHILE_LOOP);
        GraphUtils.connectWithParent(doWhileLoopNode, t);
        boolean prevInWh = inADoWhile, prevMust = must;
        inADoWhile = true;

        scan(doWhileLoopTree.getStatement(), Pair.createPair(doWhileLoopNode, RelationTypes.DO_WHILE_STATEMENT));
        scan(doWhileLoopTree.getCondition(), Pair.createPair(doWhileLoopNode, RelationTypes.DO_WHILE_CONDITION));
        addInvocationInStatement(doWhileLoopNode);
        inADoWhile = prevInWh;
        if (t.getSecond() != null && gotoLabelsInDoWhile.size() > 0) {
            gotoLabelsInDoWhile.remove(t.getSecond());
            if (gotoLabelsInDoWhile.size() == 0) {
                prevMust = auxMust;
            }
        }
        must = prevMust;
        methodState.putCfgNodeInCache(doWhileLoopTree, doWhileLoopNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitEmptyStatement(EmptyStatementTree emptyStatementTree,
                                                Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper emptyStatementNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(emptyStatementTree, NodeTypes.EMPTY_STATEMENT);
        methodState.putCfgNodeInCache(emptyStatementTree, emptyStatementNode);
        GraphUtils.connectWithParent(emptyStatementNode, t);
        return null;
    }

    @Override
    public ASTVisitorResult visitEnhancedForLoop(EnhancedForLoopTree enhancedForLoopTree,
                                                 Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper enhancedForLoopNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(enhancedForLoopTree, NodeTypes.FOR_EACH_LOOP);
        GraphUtils.connectWithParent(enhancedForLoopNode, t);
        scan(enhancedForLoopTree.getVariable(), Pair.createPair(enhancedForLoopNode, RelationTypes.FOREACH_VAR));
        scan(enhancedForLoopTree.getExpression(), Pair.createPair(enhancedForLoopNode, RelationTypes.FOREACH_EXPR));
        addInvocationInStatement(enhancedForLoopNode);
        methodState.putCfgNodeInCache(enhancedForLoopTree, enhancedForLoopNode);
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(enhancedForLoopTree.getStatement(), Pair.createPair(enhancedForLoopNode, RelationTypes.FOREACH_STATEMENT));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    @Override
    public ASTVisitorResult visitErroneous(ErroneousTree erroneousTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper erroneousNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(erroneousTree, NodeTypes.ERRONEOUS_NODE);
        attachTypeDirect(erroneousNode, erroneousTree);
        GraphUtils.connectWithParent(erroneousNode, t);
        scan(erroneousTree.getErrorTrees(), Pair.createPair(erroneousNode, RelationTypes.ERRONEOUS_NODE_CAUSED_BY));
        return null;
    }

    @Override
    public ASTVisitorResult visitExpressionStatement(ExpressionStatementTree expressionStatementTree,
                                                     Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper expressionStatementNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(expressionStatementTree, NodeTypes.EXPRESSION_STATEMENT);
        GraphUtils.connectWithParent(expressionStatementNode, t);

        scan(expressionStatementTree.getExpression(),
                Pair.createPair(expressionStatementNode, RelationTypes.ENCLOSES_EXPR,
                        PDGProcessing.getExprStatementArg(expressionStatementTree)));
        addInvocationInStatement(expressionStatementNode);
        methodState.putCfgNodeInCache(expressionStatementTree, expressionStatementNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitForLoop(ForLoopTree forLoopTree,
                                         Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper forLoopNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(forLoopTree, NodeTypes.FOR_LOOP);
        GraphUtils.connectWithParent(forLoopNode, t);

        scan(forLoopTree.getInitializer(), Pair.createPair(forLoopNode, RelationTypes.FORLOOP_INIT));
        scan(forLoopTree.getCondition(), Pair.createPair(forLoopNode, RelationTypes.FORLOOP_CONDITION));
        addInvocationInStatement(forLoopNode);
        methodState.putCfgNodeInCache(forLoopTree, forLoopNode);
        prevMust = must;
        must = false;

        pdgUtils.enteringNewBranch();
        scan(forLoopTree.getStatement(), Pair.createPair(forLoopNode, RelationTypes.FORLOOP_STATEMENT));
        scan(forLoopTree.getUpdate(), Pair.createPair(forLoopNode, RelationTypes.FORLOOP_UPDATE));

        pdgUtils.exitingCurrentBranch();
        must = prevMust;

        return null;
    }

    @Override
    public ASTVisitorResult visitIdentifier(IdentifierTree identifierTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper identifierNode;
        if (((JCIdent) identifierTree).sym == null) {
            identifierNode =
                    DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(identifierTree, NodeTypes.IDENTIFIER);
            identifierNode.setProperty("name", identifierTree.getName().toString());
            GraphUtils.connectWithParent(identifierNode, t);
            return null;
        }
        ElementKind idKind = ((JCIdent) identifierTree).sym.getKind();
        if (idKind == ElementKind.PACKAGE)
            identifierNode =
                    DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(identifierTree, NodeTypes.IDENTIFIER);
        else if (idKind == ElementKind.CLASS || idKind == ElementKind.ENUM || idKind == ElementKind.INTERFACE ||
                idKind == ElementKind.ANNOTATION_TYPE || idKind == ElementKind.TYPE_PARAMETER)
            identifierNode = DatabaseFacade.CURRENT_DB_FACHADE
                    .createSkeletonNodeExplicitCats(identifierTree, NodeTypes.IDENTIFIER, NodeCategory.AST_TYPE);
        else
            identifierNode = DatabaseFacade.CURRENT_DB_FACHADE
                    .createSkeletonNodeExplicitCats(identifierTree, NodeTypes.IDENTIFIER, NodeCategory.LVALUE,
                            NodeCategory.EXPRESSION);
        identifierNode.setProperty("name", identifierTree.getName().toString());
        attachTypeDirect(identifierNode, identifierTree);
        GraphUtils.connectWithParent(identifierNode, t);
        if (outsideAnnotation)
            return new VisitorResultImpl(
                    pdgUtils.relationOnIdentifier(identifierTree, identifierNode, t, classState.currentClassDec,
                            methodState));
        else
            return null;
    }

    @Override
    public ASTVisitorResult visitIf(IfTree ifTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper ifNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(ifTree, NodeTypes.IF_STATEMENT);
        GraphUtils.connectWithParent(ifNode, t);
        scan(ifTree.getCondition(), Pair.createPair(ifNode, RelationTypes.IF_CONDITION));
        addInvocationInStatement(ifNode);
        methodState.putCfgNodeInCache(ifTree, ifNode);

        prevMust = must;
        must = false;

        pdgUtils.enteringNewBranch();
        scan(ifTree.getThenStatement(), Pair.createPair(ifNode, RelationTypes.IF_THEN));
        Set<NodeWrapper> paramsThen = pdgUtils.exitingCurrentBranch();
        scan(ifTree.getElseStatement(), Pair.createPair(ifNode, RelationTypes.IF_ELSE));

        pdgUtils.merge(paramsThen, pdgUtils.exitingCurrentBranch());
        must = prevMust;

        return null;
    }

    @Override
    public ASTVisitorResult visitImport(ImportTree importTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper importNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(importTree, NodeTypes.IMPORT);
        importNode.setProperty("qualifiedIdentifier", importTree.getQualifiedIdentifier().toString());
        importNode.setProperty("isStatic", importTree.isStatic());

        GraphUtils.connectWithParent(importNode, t, RelationTypes.IMPORTS);
        return null;
    }

    @Override
    public ASTVisitorResult visitInstanceOf(InstanceOfTree instanceOfTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper instanceOfNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(instanceOfTree, NodeTypes.INSTANCE_OF);
        GraphUtils.attachTypeDirect(instanceOfNode, instanceOfTree, "boolean", "BOOLEAN", ast);
        GraphUtils.connectWithParent(instanceOfNode, t);
        if (instanceOfTree.getType() instanceof IdentifierTree)
            addClassIdentifier(((JCIdent) instanceOfTree.getType()).type);

        scan(instanceOfTree.getExpression(), Pair.createPair(instanceOfNode, RelationTypes.INSTANCE_OF_EXPR));
        scan(instanceOfTree.getType(), Pair.createPair(instanceOfNode, RelationTypes.INSTANCE_OF_TYPE));

        return null;
    }

    @Override
    public ASTVisitorResult visitIntersectionType(IntersectionTypeTree intersectionTypeTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper intersectionTypeNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(intersectionTypeTree, NodeTypes.INTERSECTION_TYPE,
                        NodeCategory.AST_TYPE, NodeCategory.AST_NODE);

        GraphUtils.connectWithParent(intersectionTypeNode, t);

        scan(intersectionTypeTree.getBounds(),
                Pair.createPair(intersectionTypeNode, RelationTypes.AST_INTERSECTION_OF));

        return null;
    }

    @Override
    public ASTVisitorResult visitLabeledStatement(LabeledStatementTree labeledStatementTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper labeledStatementNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(labeledStatementTree, NodeTypes.LABELED_STATEMENT);
        labeledStatementNode.setProperty("name", labeledStatementTree.getLabel().toString());
        GraphUtils.connectWithParent(labeledStatementNode, t);
        methodState.putCfgNodeInCache(labeledStatementTree, labeledStatementNode);
        scan(labeledStatementTree.getStatement(),
                Pair.createPair(labeledStatementNode, RelationTypes.LABELED_STMT_ENCLOSES,
                        labeledStatementTree.getLabel()));
        return null;
    }

    @Override
    public ASTVisitorResult visitLambdaExpression(LambdaExpressionTree lambdaExpressionTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper lambdaExpressionNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(lambdaExpressionTree, NodeTypes.LAMBDA_EXPRESSION);
        lambdaExpressionNode.setProperty("bodyKind", lambdaExpressionTree.getBodyKind().toString());
        GraphUtils.connectWithParent(lambdaExpressionNode, t);
        attachTypeDirect(lambdaExpressionNode, lambdaExpressionTree);

        MethodState prevState = methodState;
        methodState = new MethodState(lambdaExpressionNode);
        pdgUtils.visitNewMethod();
        ast.newMethodDeclaration(methodState);

        boolean prevInside = insideConstructor;
        insideConstructor = false;
        boolean prevIsInAccesibleCtxt = isInAccessibleContext;
        isInAccessibleContext = false;
        inALambda = true;
        scan(lambdaExpressionTree.getBody(),
                Pair.createPair(lambdaExpressionNode, RelationTypes.LAMBDA_EXPRESSION_BODY));
        for (int i = 0; i < lambdaExpressionTree.getParameters().size(); i++)
            scan(lambdaExpressionTree.getParameters().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(lambdaExpressionNode,
                            RelationTypes.LAMBDA_EXPRESSION_PARAMETERS, "paramIndex", i + 1)));
        inALambda = false;
        insideConstructor = prevInside;
        isInAccessibleContext = prevIsInAccesibleCtxt;
        must = true;
        methodState = prevState;
        ast.endMethodDeclaration();

        return null;
    }

    @Override
    public ASTVisitorResult visitLiteral(LiteralTree literalTree,
                                         Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper literalNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(literalTree, NodeTypes.LITERAL);
        literalNode.setProperty("typetag", literalTree.getKind().toString());
        if (literalTree.getValue() != null)
            literalNode.setProperty("value", literalTree.getValue().toString());

        attachTypeDirect(literalNode, literalTree);
        GraphUtils.connectWithParent(literalNode, t);

        return null;

    }

    @Override
    public ASTVisitorResult visitMemberReference(MemberReferenceTree memberReferenceTree,
                                                 Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper memberReferenceNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(memberReferenceTree, NodeTypes.MEMBER_REFERENCE);
        memberReferenceNode.setProperty("mode", memberReferenceTree.getMode().name());
        memberReferenceNode.setProperty("name", memberReferenceTree.getName().toString());
        GraphUtils.connectWithParent(memberReferenceNode, t);
        attachTypeDirect(memberReferenceNode, memberReferenceTree);

        scan(memberReferenceTree.getQualifierExpression(),
                Pair.createPair(memberReferenceNode, RelationTypes.MEMBER_REFERENCE_EXPRESSION));

        if (memberReferenceTree.getTypeArguments() != null)
            for (int i = 0; i < memberReferenceTree.getTypeArguments().size(); i++)
                scan(memberReferenceTree.getTypeArguments().get(i), Pair.createPair(
                        new PartialRelationWithProperties<>(memberReferenceNode,
                                RelationTypes.MEMBER_REFERENCE_TYPE_ARGUMENTS, "argumentIndex", i + 1)));
        return null;
    }

    private void attachTypeDirect(NodeWrapper exprNode, ExpressionTree exprTree) {
        GraphUtils.attachTypeDirect(exprNode, exprTree, ast);
    }

    @Override
    public ASTVisitorResult visitMemberSelect(MemberSelectTree memberSelectTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper memberSelectNode;
        Symbol memberSymbol = ((JCFieldAccess) memberSelectTree).sym;
        ElementKind idKind = memberSymbol.getKind();
        if (idKind == ElementKind.PACKAGE)
            memberSelectNode =
                    DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(memberSelectTree, NodeTypes.MEMBER_SELECTION);
        else if (idKind == ElementKind.CLASS || idKind == ElementKind.ENUM || idKind == ElementKind.INTERFACE ||
                idKind == ElementKind.ANNOTATION_TYPE || idKind == ElementKind.TYPE_PARAMETER)
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACHADE
                    .createSkeletonNodeExplicitCats(memberSelectTree, NodeTypes.MEMBER_SELECTION,
                            NodeCategory.AST_TYPE);
        else
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACHADE
                    .createSkeletonNodeExplicitCats(memberSelectTree, NodeTypes.MEMBER_SELECTION, NodeCategory.LVALUE,
                            NodeCategory.EXPRESSION);
        memberSelectNode.setProperty("memberName", memberSelectTree.getIdentifier().toString());

        attachTypeDirect(memberSelectNode, memberSelectTree);
        GraphUtils.connectWithParent(memberSelectNode, t);

        if (idKind == ElementKind.CLASS || idKind == ElementKind.INTERFACE || idKind == ElementKind.ENUM)
            addClassIdentifier(memberSymbol);

        ASTVisitorResult memberSelResult = scan(memberSelectTree.getExpression(),
                Pair.createPair(memberSelectNode, RelationTypes.MEMBER_SELECT_EXPR,
                        PDGProcessing.modifiedToStateModified(t)));
        if (outsideAnnotation) {
            boolean isInstance = memberSelResult != null && !memberSymbol.isStatic() && memberSelResult.isInstance();
            pdgUtils.relationOnFieldAccess(memberSelectTree, memberSelectNode, t, methodState,
                    classState.currentClassDec, isInstance);
            memberSelResult = new VisitorResultImpl(isInstance);

        }
        return memberSelResult;
    }

    private void setMethodModifiersAndAnnotations(Set<Modifier> modifiers, NodeWrapper methodNode, boolean isInterface,
                                                  List<? extends AnnotationTree> annotations) {
        scan(annotations, Pair.createPair(methodNode, RelationTypes.HAS_ANNOTATIONS));
        setMethodModifiers(modifiers, methodNode, isInterface);
    }

    private static void setMethodModifiers(Set<Modifier> modifiers, NodeWrapper methodNode, boolean isInterface) {
        boolean isAbstract = false;
        if (isInterface) {
            boolean isStatic;
            methodNode.setProperty("isStatic", isStatic = modifiers.contains(Modifier.STATIC));
            methodNode.setProperty("isAbstract", isAbstract = !(isStatic || modifiers.contains(Modifier.DEFAULT)));
            if (isAbstract)
                methodNode.setProperty("isStrictfp", false);
            else
                checkStrictfpMod(modifiers, methodNode);
            methodNode.setProperty("isNative", false);
            methodNode.setProperty("isSynchronized", false);
            methodNode.setProperty("isFinal", false);
            methodNode.setProperty("accessLevel", "public");
        } else {
            methodNode.setProperty("isAbstract", isAbstract = modifiers.contains(Modifier.ABSTRACT));
            if (isAbstract) {
                methodNode.setProperty("isFinal", false);
                methodNode.setProperty("isSynchronized", false);
                methodNode.setProperty("isStatic", false);
                methodNode.setProperty("isNative", false);
                methodNode.setProperty("isStrictfp", false);
                modifierAccessLevelToNodeExceptPrivate(modifiers, methodNode);
            } else {
                checkStaticMod(modifiers, methodNode);
                checkFinalMod(modifiers, methodNode);
                checkSynchroMod(modifiers, methodNode);
                checkNativeMod(modifiers, methodNode);
                checkStrictfpMod(modifiers, methodNode);
                modifierAccessLevelToNode(modifiers, methodNode);
            }
        }
    }

    private void addClassIdentifier(TypeMirror typeMirror) {
        if (typeMirror instanceof ClassType)
            addClassIdentifier(((ClassType) typeMirror).tsym);
    }

    private void addClassIdentifier(Symbol symbol) {

        NodeWrapper newTypeDec = DefinitionCache.getOrCreateType(symbol.type, ast);
        addToTypeDependencies(newTypeDec, symbol.packge());
    }

    public void addToTypeDependencies(NodeWrapper newTypeDec, Symbol newPackageSymbol) {
        addToTypeDependencies(classState.currentClassDec, newTypeDec, newPackageSymbol, typeDecUses,
                PackageInfo.PACKAGE_INFO.get().currentPackage);
    }

    public static void addToTypeDependencies(NodeWrapper currentClass, NodeWrapper newTypeDec, Symbol newPackageSymbol,
                                             Set<NodeWrapper> typeDecUses, Symbol dependentPackage) {
        if (!typeDecUses.contains(newTypeDec) && !currentClass.equals(newTypeDec)) {
            PackageInfo.PACKAGE_INFO.get().handleNewDependency(dependentPackage, newPackageSymbol);
            currentClass.createRelationshipTo(newTypeDec, CDGRelationTypes.USES_TYPE_DEF);
            typeDecUses.add(newTypeDec);
        }
    }

    @Override
    public ASTVisitorResult visitMethod(MethodTree methodTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        MethodSymbol methodSymbol = ((JCMethodDecl) methodTree).sym;
        MethodNameInfo nameInfo = new MethodNameInfo(methodSymbol);
        NodeWrapper methodNode;

        boolean prev = false;
        RelationTypes rel;
        if (methodSymbol.isConstructor()) {
            prev = insideConstructor;
            insideConstructor = true;
            methodNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(methodTree, NodeTypes.CONSTRUCTOR_DEF);

            ((Pair<Pair, List<NodeWrapper>>) t.getSecond()).getSecond().add(methodNode);
            rel = RelationTypes.DECLARES_CONSTRUCTOR;
        } else {
            methodNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(methodTree, NodeTypes.METHOD_DEF);
            rel = RelationTypes.DECLARES_METHOD;
        }

        if (DefinitionCache.METHOD_DEF_CACHE.get().containsKey(nameInfo.getFullyQualifiedName())) {
            ast.deleteAccesibleMethod(methodSymbol);
            DefinitionCache.METHOD_DEF_CACHE.get().putDefinition(nameInfo.getFullyQualifiedName(), methodNode);

            if (!methodNode.hasRelationship(rel, Direction.INCOMING))
                GraphUtils.connectWithParent(methodNode, t, rel);
        } else {
            DefinitionCache.METHOD_DEF_CACHE.get().putDefinition(nameInfo.getFullyQualifiedName(), methodNode);
            GraphUtils.connectWithParent(methodNode, t, rel);
        }

        setMethodModifiersAndAnnotations(methodTree.getModifiers().getFlags(), methodNode,
                t.getFirst().getStartingNode().hasLabel(NodeTypes.INTERFACE_DEF),
                methodTree.getModifiers().getAnnotations());
        String accessLevel = methodNode.getProperty("accessLevel").toString();

        if (!methodSymbol.isConstructor() && isInAccessibleContext && (accessLevel.contentEquals("public") ||
                accessLevel.contentEquals("protected") && !(Boolean) classState.currentClassDec.getProperty("isFinal")))

            ast.addAccesibleMethod(methodSymbol, methodNode);

        boolean prevIsInAccesibleCtxt = isInAccessibleContext;
        isInAccessibleContext = false;
        MethodState prevState = methodState;
        must = true;
        methodState = new MethodState(methodNode);
        pdgUtils.visitNewMethod();
        ast.newMethodDeclaration(methodState);
        methodNode.setProperty("name", nameInfo.getSimpleName());
        methodNode.setProperty("fullyQualifiedName", nameInfo.getFullyQualifiedName());
        methodNode.setProperty("completeName", nameInfo.getCompleteName());
        methodNode.setProperty("isDeclared", true);
        methodNode.setProperty("isVarArgs", methodSymbol.isVarArgs());

        scan(methodTree.getReturnType(), Pair.createPair(methodNode, RelationTypes.CALLABLE_RETURN_TYPE));
        GraphUtils.attachType(methodNode, ((JCMethodDecl) methodTree).type, ast);

        if (!methodSymbol.isConstructor())
            addClassIdentifier(((JCTree) methodTree.getReturnType()).type);

        for (int i = 0; i < methodTree.getTypeParameters().size(); i++)
            scan(methodTree.getTypeParameters().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(methodNode, RelationTypes.CALLABLE_HAS_TYPEPARAMETERS,
                            "paramIndex", i + 1)));
        int nParams = 0;
        for (nParams = 0; nParams < methodTree.getParameters().size(); nParams++)
            scan(methodTree.getParameters().get(nParams), Pair.createPair(
                    new PartialRelationWithProperties<>(methodNode, RelationTypes.CALLABLE_HAS_PARAMETER, "paramIndex",
                            nParams + 1)));

        methodTree.getThrows().forEach((throwsTree) -> {
            TypeMirror type = ((JCExpression) throwsTree).type;
            addClassIdentifier(type);
            scan(throwsTree, Pair.createPair(methodNode, RelationTypes.CALLABLE_HAS_THROWS));
        });

        scan(methodTree.getBody(), Pair.createPair(methodNode, RelationTypes.CALLABLE_HAS_BODY));
        scan(methodTree.getDefaultValue(), Pair.createPair(methodNode, RelationTypes.HAS_DEFAULT_VALUE));
        scan(methodTree.getReceiverParameter(), Pair.createPair(methodNode, RelationTypes.HAS_RECEIVER_PARAMETER));

        pdgUtils.setThisRefOfInstanceMethod(methodState, classState.currentClassDec);
        ast.addInfo(methodTree, methodNode, methodState,
                methodSymbol.isVarArgs() ? nParams : ASTAuxiliarStorage.NO_VARG_ARG);
        if (methodTree.getBody() != null)
            CFGVisitor.doCFGAnalysis(methodNode, methodTree, methodState.cfgNodeCache,
                    ast.getTrysToExceptionalPartialRelations(methodState.invocationsInStatements),
                    methodState.finallyCache);
        insideConstructor = prev;
        isInAccessibleContext = prevIsInAccesibleCtxt;
        must = true;
        methodState = prevState;
        ast.endMethodDeclaration();
        return null;
    }

    private boolean isUnknownOrErrorType(Type type) {
        return type == null || type instanceof ErrorType || type instanceof Type.UnknownType;
    }

    private boolean isDynamicallyGenerated(Symbol symbol, MethodInvocationTree methodInvocationTree) {
        Type methodType = JavacInfo.getTypeDirect(methodInvocationTree.getMethodSelect());
        Type invocationType = JavacInfo.getTypeDirect(methodInvocationTree);

        return symbol instanceof ClassSymbol && isUnknownOrErrorType(methodType) &&
                isUnknownOrErrorType(invocationType) && ((ClassSymbol) symbol).sourcefile == null;
    }

    @Override
    public ASTVisitorResult visitMethodInvocation(MethodInvocationTree methodInvocationTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        NodeWrapper methodInvocationNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNode(methodInvocationTree, NodeTypes.METHOD_INVOCATION);
        attachTypeDirect(methodInvocationNode, methodInvocationTree);
        GraphUtils.connectWithParent(methodInvocationNode, pair);

        Symbol symbol = JavacInfo.getSymbolFromTree(methodInvocationTree.getMethodSelect());
        NodeWrapper decNode = null;

        if (isDynamicallyGenerated(symbol, methodInvocationTree)) {
            String methodName = (methodInvocationTree.getMethodSelect() instanceof JCIdent ?
                    ((JCIdent) methodInvocationTree.getMethodSelect()).name :
                    ((JCFieldAccess) methodInvocationTree.getMethodSelect()).name).toString();
            final String GEN_CLASS = "GENERATED_CLASS";
            String completeName = GEN_CLASS + ":" + methodName;
            decNode = createNonDeclaredMethodWithoutSymbol(new MethodNameInfo(methodName, completeName, completeName));
        } else {
            if (symbol == null) {
                System.err.println("Invocation " + methodInvocationTree + " with no symbol, at" + currentTypeDecSymbol);
                return null;

            } else if (symbol instanceof ClassSymbol && ((ClassSymbol) symbol).sourcefile == null) {
                System.err.println("Invocation " + methodInvocationTree +
                        " probably from constructor, class symbol and no source file; at" + currentTypeDecSymbol);
                return null;
            }
            MethodSymbol methodSymbol = (MethodSymbol) symbol;

            if (methodInvocationTree.getMethodSelect() instanceof IdentifierTree)
                addClassIdentifier(methodSymbol.owner);

            if (methodSymbol.getThrownTypes().size() > 0)
                currentMethodInvocations.add(methodSymbol);

            MethodNameInfo nameInfo = new MethodNameInfo(methodSymbol);
            decNode = DefinitionCache.METHOD_DEF_CACHE.get().containsKey(nameInfo.getFullyQualifiedName()) ?
                    DefinitionCache.METHOD_DEF_CACHE.get().get(nameInfo.getFullyQualifiedName()) :
                    methodSymbol.isConstructor() ? getNotDeclaredConsFromInv(methodSymbol, nameInfo) :
                            getNotDeclaredMethodDecNode(methodSymbol, nameInfo);
            ast.checkIfTrustableInvocation(methodInvocationTree, methodSymbol, methodInvocationNode);
        }

        if (!inALambda) {
            RelationshipWrapper callRelation =
                    methodState.lastMethodDecVisited.createRelationshipTo(methodInvocationNode, CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", must);
        }

        methodInvocationNode.createRelationshipTo(decNode, CGRelationTypes.HAS_DEF);
        methodInvocationNode.createRelationshipTo(decNode, CGRelationTypes.REFERS_TO);

        pdgUtils.addParamsPrevModifiedForInv(methodInvocationNode, methodState);

        scan(methodInvocationTree.getMethodSelect(),
                Pair.createPair(methodInvocationNode, RelationTypes.METHODINVOCATION_METHOD_SELECT));

        for (int i = 0; i < methodInvocationTree.getTypeArguments().size(); i++)
            scan(methodInvocationTree.getTypeArguments().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(methodInvocationNode,
                            RelationTypes.METHODINVOCATION_TYPE_ARGUMENTS, "argumentIndex", i + 1)));
        for (int i = 0; i < methodInvocationTree.getArguments().size(); i++)
            scan(methodInvocationTree.getArguments().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(methodInvocationNode, RelationTypes.METHODINVOCATION_ARGUMENTS,
                            "argumentIndex", i + 1)));
        return null;
    }

    public static void modifierAccessLevelToNodeForClasses(Set<Modifier> modifiers, NodeWrapper modNode) {
        modNode.setProperty("accessLevel", modifiers.contains(Modifier.PUBLIC) ? "public" :
                modifiers.contains(Modifier.PRIVATE) ? "private" : "package");
    }

    public static void modifierAccessLevelToNode(Set<Modifier> modifiers, NodeWrapper modNode) {
        modNode.setProperty("accessLevel", modifiers.contains(Modifier.PUBLIC) ? "public" :
                modifiers.contains(Modifier.PROTECTED) ? "protected" :
                        modifiers.contains(Modifier.PRIVATE) ? "private" : "package");
    }

    public static void modifierAccessLevelToNodeExceptPrivate(Set<Modifier> modifiers, NodeWrapper modNode) {
        modNode.setProperty("accessLevel", modifiers.contains(Modifier.PUBLIC) ? "public" :
                modifiers.contains(Modifier.PROTECTED) ? "protected" : "package");
    }

    public static void modifierAccessLevelLimitedToNode(Set<Modifier> modifiers, NodeWrapper modNode) {
        modNode.setProperty("accessLevel", modifiers.contains(Modifier.PUBLIC) ? "public" : "package");
    }

    public static void checkFinalMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isFinal", modifiers.contains(Modifier.FINAL));
    }

    public static void checkStaticMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isStatic", modifiers.contains(Modifier.STATIC));
    }

    public static void checkVolatileMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isVolatile", modifiers.contains(Modifier.VOLATILE));
    }

    public static void checkTransientMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isTransient", modifiers.contains(Modifier.TRANSIENT));
    }

    public static void checkAbstractMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isAbstract", modifiers.contains(Modifier.ABSTRACT));
    }

    public static void checkSynchroMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isSynchronized", modifiers.contains(Modifier.SYNCHRONIZED));
    }

    public static void checkNativeMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isNative", modifiers.contains(Modifier.NATIVE));
    }

    public static void checkStrictfpMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isStrictfp", modifiers.contains(Modifier.STRICTFP));
    }

    private void visitAnonymousClassModifiers(ModifiersTree modifiersTree, NodeWrapper classNode) {
        Pair<PartialRelation<RelationTypesInterface>, Object> n =
                Pair.createPair(classNode, RelationTypes.HAS_ANNOTATIONS);
        scan(modifiersTree.getAnnotations(), n);
        classNode.setProperty("isStatic", false);
        classNode.setProperty("isAbstract", false);
        classNode.setProperty("isFinal", false);
        classNode.setProperty("accessLevel", "private");
    }

    public static void checkAttrDecModifiers(Set<Modifier> modifiers, NodeWrapper node) {
        checkStaticMod(modifiers, node);
        checkFinalMod(modifiers, node);
        checkVolatileMod(modifiers, node);
        checkTransientMod(modifiers, node);
        modifierAccessLevelToNode(modifiers, node);
    }

    @Override
    public ASTVisitorResult visitModifiers(ModifiersTree modifiersTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper parent = t.getFirst().getStartingNode();
        Set<Modifier> modifiers = modifiersTree.getFlags();
        scan(modifiersTree.getAnnotations(), Pair.createPair(parent, RelationTypes.HAS_ANNOTATIONS));
        if (parent.hasLabel(NodeTypes.LOCAL_VAR_DEF) || parent.hasLabel(NodeTypes.PARAMETER_DEF))
            checkFinalMod(modifiers, parent);
        else if (parent.hasLabel(NodeTypes.ATTR_DEF)) {

            checkAttrDecModifiers(modifiers, parent);

        } else if (parent.hasLabel(NodeTypes.CONSTRUCTOR_DEF)) {
            modifierAccessLevelToNode(modifiers, parent);
        } else if (parent.hasLabel(NodeTypes.CLASS_DEF)) {
            checkStaticMod(modifiers, parent);
            checkAbstractMod(modifiers, parent);
            checkFinalMod(modifiers, parent);
            modifierAccessLevelToNodeForClasses(modifiers, parent);
        } else if (parent.hasLabel(NodeTypes.INTERFACE_DEF)) {
            checkAbstractMod(modifiers, parent);
            modifierAccessLevelLimitedToNode(modifiers, parent);
            parent.setProperty("isFinal", false);
            parent.setProperty("isStatic", false);

        } else if (parent.hasLabel(NodeTypes.ENUM_ELEMENT)) {
            parent.setProperty("isStatic", true);
            parent.setProperty("isFinal", true);
            parent.setProperty("accessLevel", "public");

        } else if (parent.hasLabel(NodeTypes.ENUM_DEF)) {
            modifierAccessLevelLimitedToNode(modifiers, parent);
            parent.setProperty("isFinal", true);
            parent.setProperty("isStatic", false);
        } else
            throw new IllegalStateException(
                    "Label with modifiers no checked.\n" + NodeUtils.nodeToString(t.getFirst().getStartingNode()));
        return null;
    }

    @Override
    public ASTVisitorResult visitNewArray(NewArrayTree newArrayTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper newArrayNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(newArrayTree, NodeTypes.NEW_ARRAY);
        attachTypeDirect(newArrayNode, newArrayTree);
        GraphUtils.connectWithParent(newArrayNode, t);

        scan(newArrayTree.getType(), Pair.createPair(newArrayNode, RelationTypes.NEW_ARRAY_TYPE));
        scan(newArrayTree.getDimensions(), Pair.createPair(newArrayNode, RelationTypes.NEW_ARRAY_DIMENSION));
        scan(newArrayTree.getInitializers(), Pair.createPair(newArrayNode, RelationTypes.NEW_ARRAY_INIT));
        return null;
    }

    @Override
    public ASTVisitorResult visitNewClass(NewClassTree newClassTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        NodeWrapper newClassNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(newClassTree, NodeTypes.NEW_INSTANCE);
        scan(newClassTree.getClassBody(), Pair.createPair(newClassNode, RelationTypes.NEW_CLASS_BODY));

        Type type = JavacInfo.getTypeDirect(newClassTree.getIdentifier());
        Symbol newClassConstructor = ((JCNewClass) newClassTree).constructor;
        NodeWrapper constructorDef;
        if (newClassConstructor instanceof ClassSymbol && isUnknownOrErrorType(type) && ((ClassSymbol) newClassConstructor).sourcefile == null) {
            NodeWrapper generatedCLassNode =
                    TypeVisitor.generatedClassType((ClassSymbol) newClassConstructor.owner, ast);

            if (!typeDecUses.contains(generatedCLassNode)) {
                classState.currentClassDec.createRelationshipTo(generatedCLassNode, CDGRelationTypes.USES_TYPE_DEF);
                typeDecUses.add(generatedCLassNode);
            }

            String methodName = "<init>";
            String completeName = ((ClassSymbol) newClassConstructor.owner).getQualifiedName() + ":" + methodName;
            constructorDef =
                    createNonDeclaredMethodWithoutSymbol(new MethodNameInfo(methodName, completeName, completeName));
            generatedCLassNode.createRelationshipTo(constructorDef, RelationTypes.DECLARES_CONSTRUCTOR);
        } else {
            if (newClassConstructor == null) {
                System.err.println("Invocation " + newClassTree + " with no symbol, at" + currentTypeDecSymbol);
                return null;
            }
            addClassIdentifier(type);
            MethodSymbol consSymbol = (MethodSymbol) newClassConstructor;
            MethodNameInfo nameInfo = new MethodNameInfo(consSymbol);
            constructorDef = DefinitionCache.METHOD_DEF_CACHE.get().get(nameInfo.getFullyQualifiedName());
            if (constructorDef == null)
                constructorDef = getNotDeclaredConsFromInv(consSymbol, nameInfo);
            if (consSymbol.getThrownTypes().size() > 0)
                currentMethodInvocations.add(consSymbol);
        }
        GraphUtils.attachType(newClassNode, type, ast);
        GraphUtils.connectWithParent(newClassNode, pair);
        pdgUtils.addParamsPrevModifiedForInv(newClassNode, methodState);
        scan(newClassTree.getEnclosingExpression(),
                Pair.createPair(newClassNode, RelationTypes.NEWCLASS_ENCLOSING_EXPRESSION));
        scan(newClassTree.getIdentifier(), Pair.createPair(newClassNode, RelationTypes.NEWCLASS_IDENTIFIER));
        for (int i = 0; i < newClassTree.getTypeArguments().size(); i++)
            scan(newClassTree.getTypeArguments().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(newClassNode, RelationTypes.NEW_CLASS_TYPE_ARGUMENTS,
                            "argumentIndex", i + 1)));
        for (int i = 0; i < newClassTree.getArguments().size(); i++)
            scan(newClassTree.getArguments().get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(newClassNode, RelationTypes.NEW_CLASS_ARGUMENTS,
                            "argumentIndex", i + 1)));

        newClassNode.createRelationshipTo(constructorDef, CGRelationTypes.HAS_DEF);
        newClassNode.createRelationshipTo(constructorDef, CGRelationTypes.REFERS_TO);

        if (!inALambda) {
            RelationshipWrapper callRelation =
                    methodState.lastMethodDecVisited.createRelationshipTo(newClassNode, CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", must);
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitOther(Tree arg0, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        throw new IllegalArgumentException(
                "[EXCEPTION] Tree not included in the visitor: " + arg0.getClass() + "\n" + arg0);
    }

    @Override
    public ASTVisitorResult visitParameterizedType(ParameterizedTypeTree parameterizedTypeTree,
                                                   Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper parameterizedNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(parameterizedTypeTree, NodeTypes.PARAMETERIZED_TYPE,
                        NodeCategory.AST_TYPE, NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(parameterizedNode, t);
        scan(parameterizedTypeTree.getType(), Pair.createPair(parameterizedNode, RelationTypes.PARAMETERIZES_AST_TYPE));
        addClassIdentifier(((JCTree) parameterizedTypeTree.getType()).type);
        for (int i = 0; i < parameterizedTypeTree.getTypeArguments().size(); i++) {
            Tree typeArg = parameterizedTypeTree.getTypeArguments().get(i);
            addClassIdentifier(((JCTree) typeArg).type);
            scan(typeArg, Pair.createPair(
                    new PartialRelationWithProperties<>(parameterizedNode, RelationTypes.AST_TYPE_ARGUMENT,
                            "argumentIndex", i + 1)));
        }
        parameterizedNode
                .setProperty("actualType", ((JCTypeApply) parameterizedTypeTree).type.tsym.getQualifiedName() + "<>");
        return null;
    }

    @Override
    public ASTVisitorResult visitParenthesized(ParenthesizedTree parenthesizedTree,
                                               Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        return scan(parenthesizedTree.getExpression(), t);
    }

    @Override
    public ASTVisitorResult visitPrimitiveType(PrimitiveTypeTree primitiveTypeTree,
                                               Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper primitiveTypeNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(primitiveTypeTree, NodeTypes.PRIMITIVE_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);

        primitiveTypeNode.setProperty("fullyQualifiedName", primitiveTypeTree.toString());
        primitiveTypeNode.setProperty("simpleName", primitiveTypeTree.toString());

        GraphUtils.connectWithParent(primitiveTypeNode, t);
        return null;
    }

    @Override
    public ASTVisitorResult visitReturn(ReturnTree returnTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper returnNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(returnTree, NodeTypes.RETURN_STATEMENT);
        methodState.putCfgNodeInCache(returnTree, returnNode);
        GraphUtils.connectWithParent(returnNode, t);
        int hash = returnTree.hashCode();
        scan(returnTree.getExpression(), Pair.createPair(returnNode, RelationTypes.RETURN_EXPR));
        if (returnTree.hashCode() != hash)
            throw new IllegalStateException();
        must = false;
        addInvocationInStatement(returnNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitSwitch(SwitchTree switchTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper switchNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(switchTree, NodeTypes.SWITCH_STATEMENT);
        GraphUtils.connectWithParent(switchNode, t);
        scan(switchTree.getExpression(), Pair.createPair(switchNode, RelationTypes.SWITCH_EXPR));
        addInvocationInStatement(switchNode);
        methodState.putCfgNodeInCache(switchTree, switchNode);
        if (switchTree.getCases().size() > 0) {
            ASTVisitorResult caseResult = visitCase(switchTree.getCases().get(0),
                    Pair.createPair(switchNode, RelationTypes.SWITCH_ENCLOSES_CASE));
            Set<NodeWrapper> paramsModifiedInAllCases =
                    caseResult == null ? new HashSet<NodeWrapper>() : caseResult.paramsPreviouslyModifiedForSwitch();
            boolean unconditionalFound = caseResult == null;
            for (int i = 1; i < switchTree.getCases().size(); i++) {
                caseResult = scan(switchTree.getCases().get(i),
                        Pair.createPair(switchNode, RelationTypes.SWITCH_ENCLOSES_CASE));
                if (caseResult != null)
                    paramsModifiedInAllCases.retainAll(caseResult.paramsPreviouslyModifiedForSwitch());
                else
                    unconditionalFound = true;
            }
            if (!unconditionalFound &&
                    switchTree.getCases().get(switchTree.getCases().size() - 1).getExpression() == null)
                pdgUtils.unionWithCurrent(paramsModifiedInAllCases);
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitSynchronized(SynchronizedTree synchronizedTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper synchronizedNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(synchronizedTree, NodeTypes.SYNCHRONIZED_BLOCK);
        GraphUtils.connectWithParent(synchronizedNode, t);
        methodState.putCfgNodeInCache(synchronizedTree, synchronizedNode);
        scan(synchronizedTree.getExpression(), Pair.createPair(synchronizedNode, RelationTypes.SYNCHRONIZED_EXPR));
        addInvocationInStatement(synchronizedNode);
        scan(synchronizedTree.getBlock(), Pair.createPair(synchronizedNode, RelationTypes.SYNCHRONIZED_ENCLOSES_BLOCK));
        return null;
    }

    @Override
    public ASTVisitorResult visitThrow(ThrowTree throwTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper throwNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(throwTree, NodeTypes.THROW_STATEMENT);
        methodState.putCfgNodeInCache(throwTree, throwNode);
        GraphUtils.connectWithParent(throwNode, t);

        scan(throwTree.getExpression(), Pair.createPair(throwNode, RelationTypes.THROW_EXPR));
        addInvocationInStatement(throwNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitTry(TryTree tryTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper tryNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(tryTree, NodeTypes.TRY_STATEMENT);
        GraphUtils.connectWithParent(tryNode, t);
        boolean hasCatchingComponent = tryTree.getCatches().size() > 0 || tryTree.getFinallyBlock() != null;
        if (hasCatchingComponent)
            ast.enterInNewTry(tryTree, methodState);
        scan(tryTree.getResources(), Pair.createPair(tryNode, RelationTypes.TRY_RESOURCES));
        scan(tryTree.getBlock(), Pair.createPair(tryNode, RelationTypes.TRY_BLOCK));
        if (hasCatchingComponent)
            ast.exitTry();
        scan(tryTree.getCatches(), Pair.createPair(tryNode, RelationTypes.TRY_CATCH));

        methodState.putCfgNodeInCache(tryTree, tryNode);
        scan(tryTree.getFinallyBlock(), Pair.createPair(tryNode, RelationTypes.TRY_FINALLY));
        NodeWrapper finallyNode = lastBlockVisited;

        if (tryTree.getFinallyBlock() != null) {
            finallyNode.removeLabel(NodeTypes.BLOCK);
            finallyNode.addLabel(NodeTypes.FINALLY_BLOCK);
            NodeWrapper lastStmtInFinally = DatabaseFacade.CURRENT_DB_FACHADE
                    .createNodeWithoutExplicitTree(NodeTypes.CFG_LAST_STATEMENT_IN_FINALLY);
            methodState.putFinallyInCache(tryTree.getFinallyBlock(), finallyNode, lastStmtInFinally);
            finallyNode.createRelationshipTo(lastStmtInFinally, CFGRelationTypes.CFG_FINALLY_TO_LAST_STMT);
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitTypeCast(TypeCastTree typeCastTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper typeCastNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(typeCastTree, NodeTypes.TYPE_CAST);
        attachTypeDirect(typeCastNode, typeCastTree);
        GraphUtils.connectWithParent(typeCastNode, t);
        scan(typeCastTree.getType(), Pair.createPair(typeCastNode, RelationTypes.CAST_TYPE));
        scan(typeCastTree.getExpression(), Pair.createPair(typeCastNode, RelationTypes.CAST_ENCLOSES));
        if (typeCastTree.getType() instanceof IdentifierTree)
            addClassIdentifier(((JCIdent) typeCastTree.getType()).type);
        return null;
    }

    @Override
    public ASTVisitorResult visitTypeParameter(TypeParameterTree typeParameterTree,
                                               Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper typeParameterNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(typeParameterTree, NodeTypes.TYPE_PARAM, NodeCategory.AST_NODE);
        typeParameterNode.setProperty("name", typeParameterTree.getName().toString());
        GraphUtils.connectWithParent(typeParameterNode, t);
        scan(typeParameterTree.getAnnotations(), Pair.createPair(typeParameterNode, RelationTypes.HAS_ANNOTATIONS));
        scan(typeParameterTree.getBounds(), Pair.createPair(typeParameterNode, RelationTypes.TYPEPARAMETER_EXTENDS));
        return null;
    }

    @Override
    public ASTVisitorResult visitUnary(UnaryTree unaryTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper unaryNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(unaryTree, NodeTypes.UNARY_OPERATION);
        unaryNode.setProperty("operator", unaryTree.getKind().toString());
        boolean impliesModification =
                unaryTree.getKind() == Kind.POSTFIX_INCREMENT || unaryTree.getKind() == Kind.POSTFIX_DECREMENT ||
                        unaryTree.getKind() == Kind.PREFIX_INCREMENT || unaryTree.getKind() == Kind.PREFIX_DECREMENT;
        GraphUtils.connectWithParent(unaryNode, t);
        attachTypeDirect(unaryNode, unaryTree);
        if (impliesModification) {
            NodeWrapper lastAssignInfo = beforeScanAnyAssign(unaryNode, t);
            scan(unaryTree.getExpression(),
                    Pair.createPair(unaryNode, RelationTypes.UNARY_ENCLOSES, PDGProcessing.getLefAssignmentArg(t)));
            afterScanAnyAssign(lastAssignInfo);
        } else
            scan(unaryTree.getExpression(), Pair.createPair(unaryNode, RelationTypes.UNARY_ENCLOSES));
        return null;
    }

    @Override
    public ASTVisitorResult visitUnionType(UnionTypeTree unionTypeTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper unionTypeNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(unionTypeTree, NodeTypes.UNION_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(unionTypeNode, t);
        scan(unionTypeTree.getTypeAlternatives(), Pair.createPair(unionTypeNode, RelationTypes.AST_UNION_ALTERNATIVE));
        return null;
    }

    private void createVarInit(VariableTree varTree, NodeWrapper varDecNode, boolean isAttr, boolean isStatic) {
        if (varTree.getInitializer() != null) {
            NodeWrapper initNode =
                    DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(varTree, NodeTypes.INITIALIZATION);
            RelationshipWrapper r = varDecNode.createRelationshipTo(initNode, RelationTypes.HAS_VARIABLEDECL_INIT);
            if (isAttr)
                r.setProperty("isOwnAccess", true);
            scan(varTree.getInitializer(), Pair.createPair(initNode, RelationTypes.INITIALIZATION_EXPR));
            PDGProcessing.createVarDecInitRel(classState.currentClassDec, initNode, isAttr, isStatic);
        }
    }

    @Override
    public ASTVisitorResult visitVariable(VariableTree variableTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        boolean isAttr = t.getFirst().getRelationType().equals(RelationTypes.HAS_STATIC_INIT);
        boolean isMethodParam = t.getFirst().getRelationType().equals(RelationTypes.CALLABLE_HAS_PARAMETER) ||
                t.getFirst().getRelationType().equals(RelationTypes.LAMBDA_EXPRESSION_PARAMETERS);
        boolean isEnum = false;
        NodeWrapper variableNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(variableTree, isAttr ?
                (isEnum = variableTree.toString().contains("/*")) ? NodeTypes.ENUM_ELEMENT : NodeTypes.ATTR_DEF :
                isMethodParam ? NodeTypes.PARAMETER_DEF : NodeTypes.LOCAL_VAR_DEF);
        variableNode.setProperty("name", variableTree.getName().toString());

        Type type = ((JCVariableDecl) variableTree).type;
        GraphUtils.attachType(variableNode, type, ast);
        addClassIdentifier(type);
        scan(variableTree.getModifiers(), Pair.createPair(variableNode, null));
        Symbol s = ((JCVariableDecl) variableTree).sym;
        MethodState previousState = methodState;
        if (isAttr) {
            variableNode.setProperty("isDeclared", true);
            GraphUtils.connectWithParent(variableNode, t,
                    isEnum ? RelationTypes.HAS_ENUM_ELEMENT : RelationTypes.DECLARES_FIELD);
            methodState = new MethodState(variableNode);
            Pair<List<NodeWrapper>, List<NodeWrapper>> param =
                    ((Pair<Pair<List<NodeWrapper>, List<NodeWrapper>>, List<NodeWrapper>>) t.getSecond()).getFirst();
            (s.isStatic() ? param.getSecond() : param.getFirst()).add(methodState.lastMethodDecVisited);

        } else
            GraphUtils.connectWithParent(variableNode, t);
        createVarInit(variableTree, variableNode, isAttr, s.isStatic());
        if (!(isMethodParam || isAttr)) {
            methodState.putCfgNodeInCache(variableTree, variableNode);
            addInvocationInStatement(variableNode);
        }
        pdgUtils.putDecInCache(s, variableNode);
        scan(variableTree.getType(), Pair.createPair(variableNode, RelationTypes.HAS_VARIABLEDECL_TYPE));
        if (isAttr) {
            methodState = previousState;
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitWhileLoop(WhileLoopTree whileLoopTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper whileLoopNode = DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(whileLoopTree, NodeTypes.WHILE_LOOP);
        GraphUtils.connectWithParent(whileLoopNode, t);
        scan(whileLoopTree.getCondition(), Pair.createPair(whileLoopNode, RelationTypes.WHILE_CONDITION));
        addInvocationInStatement(whileLoopNode);
        methodState.putCfgNodeInCache(whileLoopTree, whileLoopNode);
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(whileLoopTree.getStatement(), Pair.createPair(whileLoopNode, RelationTypes.WHILE_STATEMENT));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    @Override
    public ASTVisitorResult visitWildcard(WildcardTree wildcardTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper wildcardNode = DatabaseFacade.CURRENT_DB_FACHADE
                .createSkeletonNodeExplicitCats(wildcardTree, NodeTypes.WILDCARD_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        wildcardNode.setProperty("typeBoundKind", wildcardTree.getKind().toString());
        GraphUtils.connectWithParent(wildcardNode, t);
        scan(wildcardTree.getBound(), Pair.createPair(wildcardNode, RelationTypes.WILDCARD_BOUND));
        return null;
    }
}
