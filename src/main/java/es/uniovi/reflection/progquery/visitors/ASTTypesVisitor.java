package es.uniovi.reflection.progquery.visitors;

import com.sun.source.tree.*;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.tree.JCTree.*;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NestingTypes;
import es.uniovi.reflection.progquery.database.nodes.NodeCategory;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.*;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.typeInfo.PackageInfo;
import es.uniovi.reflection.progquery.typeInfo.TypeHierarchy;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.utils.CallableNameInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.*;
import org.neo4j.graphdb.Direction;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ASTTypesVisitor
        extends TreeScanner<ASTVisitorResult, Pair<PartialRelation<RelationTypesInterface>, Object>> {
    private static final String PUBLIC_ACCESS = "public";
    private static final String PRIVATE_ACCESS = "private";
    private static final String PACKAGE_ACCESS = "package";
    private static final String PROTECTED_ACCESS = "protected";
    private static final String ACCESS_LEVEL_PROP = "accessLevel";
    public static final String IS_USER_CODE_PROP = "isUserCode";
    private static final String IS_STATIC_PROP = "isStatic";
    private static final String IS_ABSTRACT_PROP = "isAbstract";
    private static final String IS_FINAL_PROP = "isFinal";
    private static final String IS_SYNCHRONIZED_PROP = "isSynchronized";
    private static final String IS_STRICTFP_PROP = "isStrictfp";
    public static final String IS_NATIVE_PROP = "isNative";
    private NodeWrapper lastStaticConsVisited = null;
    private ClassTree typeDec;
    private boolean first;
    private PDGProcessing pdgUtils;
    public ASTAuxiliarStorage ast;
    private MethodState methodState = null;
    private ClassState classState = null;
    private boolean insideConstructor = false;
    private List<MethodSymbol> currentMethodInvocations = new ArrayList<>();
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
        currentMethodInvocations = new ArrayList<>();
        return statement;
    }

    @Override
    public ASTVisitorResult reduce(ASTVisitorResult n1, ASTVisitorResult n2) {
        return n2;
    }

    static NodeWrapper getCallableDuringTypeCreation(MethodSymbol symbol, ASTAuxiliarStorage ast, NodeWrapper typeDec) {
        return getNonDeclaredCallable(symbol, typeDec, ast);
    }

    private NodeWrapper getCallableDecFromCall(MethodSymbol symbol) {
        return getNonDeclaredCallable(symbol, DefinitionCache.getOrCreateType(symbol.owner.type, ast), ast);
    }

    private static NodeWrapper getNonDeclaredCallable(MethodSymbol symbol, NodeWrapper typeDec,
                                                      ASTAuxiliarStorage ast) {
        CallableNameInfo nameInfo = new CallableNameInfo(symbol);
        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(nameInfo.getFullyQualifiedName()))
            return DefinitionCache.CALLABLE_DEC_CACHE.get().get(nameInfo.getFullyQualifiedName());

        NodeWrapper methodDecNode = createAndLinkNonDeclaredCallable(typeDec, symbol);
        setCallableMethodSymbolProps(symbol, methodDecNode);
        if (!symbol.isConstructor())
            ast.addAccessibleMethod(symbol, methodDecNode);
        DefinitionCache.CALLABLE_DEC_CACHE.get().put(nameInfo.getFullyQualifiedName(), methodDecNode);
        return methodDecNode;
    }

    public static NodeWrapper createAndLinkNonDeclaredCallable(NodeWrapper classNode, boolean isConstructor) {
        NodeWrapper methodDec = createNonDeclaredCallable(isConstructor);
        classNode.createRelationshipTo(methodDec,
                isConstructor ? ASTRelationTypes.DECLARES_CONSTRUCTOR : ASTRelationTypes.DECLARES_METHOD);
        return methodDec;
    }

    public static NodeWrapper createAndLinkNonDeclaredCallable(NodeWrapper classNode, MethodSymbol symbol) {
        return createAndLinkNonDeclaredCallable(classNode, symbol.isConstructor());
    }

    private static NodeWrapper createNonDeclaredCallable(boolean isConstructor) {
        NodeWrapper methodDecNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createNodeWithoutExplicitTree(isConstructor ? NodeTypes.CONSTRUCTOR_DEC : NodeTypes.METHOD_DEC);
        methodDecNode.setProperty(IS_USER_CODE_PROP, false);
        return methodDecNode;
    }


    @Override
    public ASTVisitorResult visitAnnotatedType(AnnotatedTypeTree annotatedTypeTree,
                                               Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper annotatedTypeNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(annotatedTypeTree, NodeTypes.ANNOTATED_TYPE);
        GraphUtils.connectWithParent(annotatedTypeNode, t);
        scan(annotatedTypeTree.getAnnotations(), Pair.createPair(annotatedTypeNode, ASTRelationTypes.HAS_ANNOTATION));
        scan(annotatedTypeTree.getUnderlyingType(),
                Pair.createPair(annotatedTypeNode, ASTRelationTypes.UNDERLYING_AST_TYPE));
        return null;
    }

    @Override
    public ASTVisitorResult visitAnnotation(AnnotationTree annotationTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper annotationNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(annotationTree, NodeTypes.ANNOTATION);
        GraphUtils.connectWithParent(annotationNode, t, ASTRelationTypes.HAS_ANNOTATION);
        boolean prevInsideAnn = outsideAnnotation;
        outsideAnnotation = false;
        scan(annotationTree.getAnnotationType(), Pair.createPair(annotationNode, ASTRelationTypes.ANNOTATION_NAME));
        visitListWithPropertyIndex(annotationTree.getArguments(), annotationNode, ASTRelationTypes.ANNOTATION_ARG,
                "argumentIndex");
        outsideAnnotation = prevInsideAnn;
        return null;
    }

    @Override
    public ASTVisitorResult visitArrayAccess(ArrayAccessTree arrayAccessTree,
                                             Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper arrayAccessNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(arrayAccessTree, NodeTypes.ARRAY_ACCESS);
        attachTypeDirect(arrayAccessNode, arrayAccessTree);
        GraphUtils.connectWithParent(arrayAccessNode, t);
        ASTVisitorResult res = scan(arrayAccessTree.getExpression(),
                Pair.createPair(arrayAccessNode, ASTRelationTypes.ARRAY_ACCESS_EXPR,
                        PDGProcessing.modifiedToStateModified(t)));
        scan(arrayAccessTree.getIndex(), Pair.createPair(arrayAccessNode, ASTRelationTypes.ARRAY_ACCESS_INDEX));
        return res;
    }

    @Override
    public ASTVisitorResult visitArrayType(ArrayTypeTree arrayTypeTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper arrayTypeNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(arrayTypeTree, NodeTypes.ARRAY_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(arrayTypeNode, t);
        String fullyName = ((JCArrayTypeTree) arrayTypeTree).type.toString();
        arrayTypeNode.setProperty("fullyQualifiedName", fullyName);
        String[] splittedName = fullyName.split(".");
        arrayTypeNode.setProperty("simpleName",
                splittedName.length == 0 ? fullyName : splittedName[splittedName.length - 1]);
        scan(arrayTypeTree.getType(), Pair.createPair(arrayTypeNode, ASTRelationTypes.AST_ARRAY_ELEMENT_TYPE));
        return null;
    }

    @Override
    public ASTVisitorResult visitAssert(AssertTree assertTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper assertNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(assertTree, NodeTypes.ASSERT_STATEMENT);
        GraphUtils.connectWithParent(assertNode, t);
        scan(assertTree.getCondition(), Pair.createPair(assertNode, ASTRelationTypes.ASSERT_CONDITION));
        addInvocationInStatement(assertNode);
        methodState.putCfgNodeInCache(assertTree, assertNode);
        scan(assertTree.getDetail(), Pair.createPair(assertNode, ASTRelationTypes.ASSERT_DETAIL));
        return null;
    }

    private NodeWrapper beforeScanAnyAssign(NodeWrapper assignmentNode,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
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
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(assignmentTree, NodeTypes.ASSIGNMENT);

        GraphUtils.connectWithParent(assignmentNode, t);
        attachTypeDirect(assignmentNode, assignmentTree);
        if (outsideAnnotation) {
            NodeWrapper previousLastAssignInfo = beforeScanAnyAssign(assignmentNode, t);
            scan(assignmentTree.getVariable(), Pair.createPair(assignmentNode, ASTRelationTypes.ASSIGNMENT_LHS,
                    PDGProcessing.getLefAssignmentArg(t)));

            afterScanAnyAssign(previousLastAssignInfo);
            scan(assignmentTree.getExpression(),
                    Pair.createPair(assignmentNode, ASTRelationTypes.ASSIGNMENT_RHS, PDGProcessing.USED));
        } else {
            scan(assignmentTree.getVariable(), Pair.createPair(assignmentNode, ASTRelationTypes.ASSIGNMENT_LHS,
                    PDGProcessing.getLefAssignmentArg(t)));
            scan(assignmentTree.getExpression(),
                    Pair.createPair(assignmentNode, ASTRelationTypes.ASSIGNMENT_RHS, PDGProcessing.USED));
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitBinary(BinaryTree binaryTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper binaryNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(binaryTree, NodeTypes.BINARY_OPERATION);
        binaryNode.setProperty("operator", binaryTree.getKind().toString());
        attachTypeDirect(binaryNode, binaryTree);
        GraphUtils.connectWithParent(binaryNode, t);

        scan(binaryTree.getLeftOperand(), Pair.createPair(binaryNode, ASTRelationTypes.BINARY_OP_LHS));
        scan(binaryTree.getRightOperand(), Pair.createPair(binaryNode,

                binaryTree.getKind().toString().contentEquals("OR") ||
                        binaryTree.getKind().toString().contentEquals("AND") ? ASTRelationTypes.BINARY_OP_COND_RHS :
                        ASTRelationTypes.BINARY_OP_RHS));
        return null;
    }

    private NodeWrapper lastBlockVisited;

    @Override
    public ASTVisitorResult visitBlock(BlockTree blockTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper blockNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(blockTree, NodeTypes.BLOCK);
        lastBlockVisited = blockNode;
        blockNode.setProperty(IS_STATIC_PROP, blockTree.isStatic());
        boolean isStaticInit = t.getFirst().getRelationType() == ASTRelationTypes.TYPE_STATIC_INIT;
        MethodState prevState = null;
        if (isStaticInit) {
            prevState = methodState;
            methodState = new MethodState(lastStaticConsVisited = blockNode);
            pdgUtils.visitNewMethod();
            ast.newMethodDeclaration(methodState);
        }

        GraphUtils.connectWithParent(blockNode, t);

        scan(blockTree.getStatements(), Pair.createPair(blockNode, ASTRelationTypes.BLOCK_ENCLOSES));
        lastBlockVisited = blockNode;
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
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(breakTree, NodeTypes.BREAK_STATEMENT);
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

        NodeWrapper caseNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(caseTree,
                caseTree.getCaseKind() == CaseTree.CaseKind.RULE ? NodeTypes.CASE_ARROW : NodeTypes.CASE_COLON);
        GraphUtils.connectWithParent(caseNode, t);
        prevMust = must;
        boolean isAUnconditionalDefault = caseTree.getExpressions().isEmpty() && !anyBreak;
        must = prevMust && isAUnconditionalDefault;
        if (!isAUnconditionalDefault)
            pdgUtils.enteringNewBranch();
        scan(caseTree.getExpressions(), Pair.createPair(caseNode, ASTRelationTypes.CASE_EXPR));
        scan(caseTree.getStatements(), Pair.createPair(caseNode, ASTRelationTypes.CASE_STATEMENT));

        must = prevMust;
        return isAUnconditionalDefault ? null : new VisitorResultImpl(pdgUtils.exitingCurrentBranch());

    }

    @Override
    public ASTVisitorResult visitCatch(CatchTree catchTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper catchNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(catchTree, NodeTypes.CATCH_BLOCK);
        methodState.putCfgNodeInCache(catchTree, catchNode);
        GraphUtils.connectWithParent(catchNode, t);

        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(catchTree.getParameter(), Pair.createPair(catchNode, ASTRelationTypes.CATCH_VARIABLE));
        scan(catchTree.getBlock(), Pair.createPair(catchNode, ASTRelationTypes.CATCH_BLOCK));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    private void typeSymbolInfoAndAnnotations(ClassSymbol symbol, ModifiersTree modifiersTree, NodeWrapper typeNode) {
        scan(modifiersTree.getAnnotations(), Pair.createPair(typeNode, ASTRelationTypes.HAS_ANNOTATION));
        typeSymbolPropsAndNestingLabels(symbol, modifiersTree.getFlags(), typeNode);
    }

    public static void typeSymbolPropsAndNestingLabels(ClassSymbol classSymbol, Set<Modifier> modifiers,
                                                       NodeWrapper typeDecNode) {
        if (classSymbol.getNestingKind() == NestingKind.TOP_LEVEL) {
            typeDecNode.addLabel(NestingTypes.TOP_LEVEL_TYPE);
            typeDecNode.setProperty(ACCESS_LEVEL_PROP, classSymbol.isPublic() ? PUBLIC_ACCESS : PACKAGE_ACCESS);
        } else {
            typeDecNode.addLabel(NestingTypes.NESTED_TYPE);
            typeDecNode.setProperty("isInner", classSymbol.isInner());
            if (classSymbol.getNestingKind() == NestingKind.MEMBER) {
                typeDecNode.addLabel(NestingTypes.MEMBER_TYPE);
                setAccessLevel(classSymbol, modifiers, typeDecNode);
            } else {
                if (classSymbol.getNestingKind() == NestingKind.LOCAL) {
                    typeDecNode.addLabel(NestingTypes.LOCAL_TYPE);
                    typeDecNode.addLabel(NodeCategory.STATEMENT);
                } else
                    typeDecNode.addLabel(NestingTypes.ANONYMOUS_TYPE);
                typeDecNode.setProperty(ACCESS_LEVEL_PROP, PRIVATE_ACCESS);
            }
        }
        typeDecNode.setProperty("isSealed", classSymbol.isSealed());
        checkStrictfpMod(modifiers, typeDecNode);
        typeDecNode.setProperty(IS_ABSTRACT_PROP, classSymbol.isAbstract());
        checkFinalMod(classSymbol, typeDecNode);
    }

    private void setUserTypeParent(NestingKind nestingKind, NodeWrapper typeNode,
                                   Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        if (nestingKind == NestingKind.LOCAL || nestingKind == NestingKind.ANONYMOUS) {
            if (nestingKind == NestingKind.LOCAL)
                methodState.lastMethodDecVisited.createRelationshipTo(typeNode, ASTRelationTypes.DECLARES_TYPE);
            GraphUtils.connectWithParent(typeNode, pair);
        } else
            GraphUtils.connectWithParent(typeNode, pair, ASTRelationTypes.DECLARES_TYPE);
    }


    private static NodeWrapper recordComponent(Symbol.RecordComponent componentSymbol, NodeWrapper recordNode,
                                               ASTAuxiliarStorage ast) {
        NodeWrapper componentNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.RECORD_COMPONENT);
        recordNode.createRelationshipTo(componentNode, ASTRelationTypes.DECLARES_COMPONENT);

        componentNode.setProperties(new Object[]{"name", componentSymbol.getSimpleName().toString()});
        GraphUtils.attachType(componentNode, componentSymbol.type, ast);

        componentNode.createRelationshipTo(getCallableDuringTypeCreation(componentSymbol.accessor, ast, recordNode),
                ASTRelationTypes.COMPONENT_ACCESSOR);

        return componentNode;
    }

    private void declaredRecordComponent(Symbol.RecordComponent componentSymbol, NodeWrapper recordNode,
                                         NodeWrapper fieldNode) {
        NodeWrapper componentNode = DefinitionCache.COMPONENT_CACHE.get().containsKey(componentSymbol) ?
                DefinitionCache.COMPONENT_CACHE.get().get(componentSymbol) :
                recordComponent(componentSymbol, recordNode, ast);
        componentNode.setProperty(IS_USER_CODE_PROP, true);
        componentNode.addLabel(NodeCategory.AST_NODE);
        componentNode.setProperties(JavacInfo.getPosition(recordNode));

        PartialWithEnd componentTypeRel = new PartialWithEnd<>(componentNode, ASTRelationTypes.COMPONENT_TYPE);
        componentSymbol.declarationFor().getType().accept(this, Pair.createPair(componentTypeRel));
        componentTypeRel.getEndNode().setProperties(JavacInfo.getPosition(recordNode));
        DefinitionCache.COMPONENT_CACHE.get().updateToDefinition(componentSymbol);

        componentNode.createRelationshipTo(fieldNode, ASTRelationTypes.COMPONENT_FIELD);
    }

    public static void setNonDeclaredRecordMembers(ClassSymbol classSymbol, NodeWrapper recordNode,
                                                   ASTAuxiliarStorage ast) {
        classSymbol.getRecordComponents().forEach(componentSymbol -> {
            NodeWrapper componentNode = recordComponent(componentSymbol, recordNode, ast);
            DefinitionCache.COMPONENT_CACHE.get().put(componentSymbol, componentNode);
            componentNode.setProperty(IS_USER_CODE_PROP, false);
        });
    }

    public void setDeclaredRecord(ClassSymbol classSymbol, NodeWrapper recordNode, ClassTree classTree) {
        if (!DefinitionCache.TYPE_CACHE.get().containsKey(classSymbol.type.accept(new KeyTypeVisitor(), null)))
            classSymbol.getEnclosedElements().stream().filter(symbol -> symbol.getKind() == ElementKind.METHOD &&
                    (symbol.name.toString().contentEquals("toString") ||
                            symbol.name.toString().contentEquals("equals") ||
                            symbol.name.toString().contentEquals("hashCode"))).forEach(
                    elementSymbol -> getCallableDuringTypeCreation((Symbol.MethodSymbol) elementSymbol, ast,
                            recordNode));
    }

    @Override
    public ASTVisitorResult visitClass(ClassTree classTree,
                                       Pair<PartialRelation<RelationTypesInterface>, Object> pair) {

        ClassSymbol previousClassSymbol = currentTypeDecSymbol;
        currentTypeDecSymbol = ((JCClassDecl) classTree).sym;
        NodeWrapper typeNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createUserTypeDecNode(classTree, currentTypeDecSymbol);
        if (currentTypeDecSymbol.isRecord())
            setDeclaredRecord(currentTypeDecSymbol, typeNode, classTree);
        ast.typeDecNodes.add(typeNode);

        ClassState previousClassState = classState;
        classState = new ClassState(typeNode);
        Set<NodeWrapper> previousTypeDecUses = typeDecUses;
        typeDecUses = new HashSet<>();

        Symbol outerMostClass = currentTypeDecSymbol.outermostClass();

        boolean isNested = !pair.getFirst().getStartingNode().hasLabel(NodeTypes.COMPILATION_UNIT);
        if (currentTypeDecSymbol.isInner())
            addClassAndDep(outerMostClass);
        if (isNested)
            currentCU.createRelationshipTo(typeNode, CDGRelationTypes.NESTED_TYPE_DEF);

        setUserTypeParent(currentTypeDecSymbol.getNestingKind(), typeNode, pair);
        DefinitionCache.putClassDefinition(currentTypeDecSymbol, typeNode, ast.typeDecNodes, typeDecUses);

        TypeHierarchy.addTypeHierarchy(currentTypeDecSymbol, typeNode, this, ast);
        boolean prevIsInAccessibleContext = isInAccessibleContext;
        typeSymbolInfoAndAnnotations(currentTypeDecSymbol, classTree.getModifiers(), typeNode);

        if (pair.getFirst().getRelationType() == ASTRelationTypes.NEW_INSTANCE_BODY)
            isInAccessibleContext = false;
        else
            isInAccessibleContext = isInAccessibleContext &&
                    typeNode.getProperty(ACCESS_LEVEL_PROP).toString().contentEquals(PUBLIC_ACCESS);


        visitListWithPropertyIndex(classTree.getTypeParameters(), typeNode, ASTRelationTypes.TYPE_HAS_TYPE_PARAM,
                "paramIndex");
        if (classTree.getTypeParameters().size() > 0)
            typeNode.addLabel(NodeTypes.GENERIC_TYPE);

        scan(classTree.getExtendsClause(), Pair.createPair(typeNode, ASTRelationTypes.EXTENDS_CLAUSE));
        scan(classTree.getImplementsClause(), Pair.createPair(typeNode, ASTRelationTypes.IMPLEMENTS_CLAUSE));
        scan(classTree.getPermitsClause(), Pair.createPair(typeNode, ASTRelationTypes.PERMITS_CLAUSE));

        List<NodeWrapper> attrs = new ArrayList<>(), staticAttrs = new ArrayList<NodeWrapper>(), constructors =
                new ArrayList<>();
        NodeWrapper prevStaticCons = lastStaticConsVisited;

        scan(classTree.getMembers(), Pair.createPair(typeNode, ASTRelationTypes.TYPE_STATIC_INIT,
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
        isInAccessibleContext = prevIsInAccessibleContext;
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
        NodeWrapper assignmentNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(compoundAssignmentTree, NodeTypes.COMPOUND_ASSIGNMENT);
        assignmentNode.setProperty("operator", compoundAssignmentTree.getKind().toString());

        GraphUtils.connectWithParent(assignmentNode, t);
        attachTypeDirect(assignmentNode, compoundAssignmentTree);
        NodeWrapper lasAssignInfo = beforeScanAnyAssign(assignmentNode, t);

        scan(compoundAssignmentTree.getVariable(),
                Pair.createPair(assignmentNode, ASTRelationTypes.COMPOUND_ASSIGNMENT_LHS,
                        PDGProcessing.getLefAssignmentArg(t)));
        afterScanAnyAssign(lasAssignInfo);
        scan(compoundAssignmentTree.getExpression(),
                Pair.createPair(assignmentNode, ASTRelationTypes.COMPOUND_ASSIGNMENT_RHS, PDGProcessing.USED));
        return null;
    }

    @Override
    public ASTVisitorResult visitConditionalExpression(ConditionalExpressionTree conditionalTree,
                                                       Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper conditionalExprNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(conditionalTree, NodeTypes.CONDITIONAL_EXPRESSION);
        attachTypeDirect(conditionalExprNode, conditionalTree);
        GraphUtils.connectWithParent(conditionalExprNode, t);

        scan(conditionalTree.getCondition(),
                Pair.createPair(conditionalExprNode, ASTRelationTypes.CONDITIONAL_EXPR_CONDITION));
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(conditionalTree.getTrueExpression(),
                Pair.createPair(conditionalExprNode, ASTRelationTypes.CONDITIONAL_EXPR_THEN));
        Set<NodeWrapper> paramsThen = pdgUtils.exitingCurrentBranch();
        pdgUtils.enteringNewBranch();
        scan(conditionalTree.getFalseExpression(),
                Pair.createPair(conditionalExprNode, ASTRelationTypes.CONDITIONAL_EXPR_ELSE));
        must = prevMust;
        pdgUtils.merge(paramsThen, pdgUtils.exitingCurrentBranch());
        return null;
    }

    @Override
    public ASTVisitorResult visitContinue(ContinueTree continueTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper continueNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(continueTree, NodeTypes.CONTINUE_STATEMENT);
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
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(doWhileLoopTree, NodeTypes.DO_WHILE_LOOP);
        GraphUtils.connectWithParent(doWhileLoopNode, t);
        boolean prevInWh = inADoWhile, prevMust = must;
        inADoWhile = true;

        scan(doWhileLoopTree.getStatement(), Pair.createPair(doWhileLoopNode, ASTRelationTypes.DO_WHILE_STATEMENT));
        scan(doWhileLoopTree.getCondition(), Pair.createPair(doWhileLoopNode, ASTRelationTypes.DO_WHILE_CONDITION));
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
        NodeWrapper emptyStatementNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(emptyStatementTree, NodeTypes.EMPTY_STATEMENT);
        methodState.putCfgNodeInCache(emptyStatementTree, emptyStatementNode);
        GraphUtils.connectWithParent(emptyStatementNode, t);
        return null;
    }

    @Override
    public ASTVisitorResult visitEnhancedForLoop(EnhancedForLoopTree enhancedForLoopTree,
                                                 Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper enhancedForLoopNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(enhancedForLoopTree, NodeTypes.FOR_EACH_LOOP);
        GraphUtils.connectWithParent(enhancedForLoopNode, t);
        scan(enhancedForLoopTree.getVariable(), Pair.createPair(enhancedForLoopNode, ASTRelationTypes.FOREACH_VAR));
        scan(enhancedForLoopTree.getExpression(), Pair.createPair(enhancedForLoopNode, ASTRelationTypes.FOREACH_EXPR));
        addInvocationInStatement(enhancedForLoopNode);
        methodState.putCfgNodeInCache(enhancedForLoopTree, enhancedForLoopNode);
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(enhancedForLoopTree.getStatement(),
                Pair.createPair(enhancedForLoopNode, ASTRelationTypes.FOREACH_STATEMENT));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    @Override
    public ASTVisitorResult visitErroneous(ErroneousTree erroneousTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper erroneousNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(erroneousTree, NodeTypes.ERRONEOUS_NODE);
        attachTypeDirect(erroneousNode, erroneousTree);
        GraphUtils.connectWithParent(erroneousNode, t);
        scan(erroneousTree.getErrorTrees(), Pair.createPair(erroneousNode, ASTRelationTypes.ERRONEOUS_CAUSED_BY));
        return null;
    }

    @Override
    public ASTVisitorResult visitExpressionStatement(ExpressionStatementTree expressionStatementTree,
                                                     Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper expressionStatementNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(expressionStatementTree, NodeTypes.EXPRESSION_STATEMENT);
        GraphUtils.connectWithParent(expressionStatementNode, t);

        scan(expressionStatementTree.getExpression(),
                Pair.createPair(expressionStatementNode, ASTRelationTypes.ENCLOSES_EXPR,
                        PDGProcessing.getExprStatementArg(expressionStatementTree)));
        addInvocationInStatement(expressionStatementNode);
        methodState.putCfgNodeInCache(expressionStatementTree, expressionStatementNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitForLoop(ForLoopTree forLoopTree,
                                         Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper forLoopNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(forLoopTree, NodeTypes.FOR_LOOP);
        GraphUtils.connectWithParent(forLoopNode, t);

        scan(forLoopTree.getInitializer(), Pair.createPair(forLoopNode, ASTRelationTypes.FOR_LOOP_INIT));
        scan(forLoopTree.getCondition(), Pair.createPair(forLoopNode, ASTRelationTypes.FOR_LOOP_CONDITION));
        addInvocationInStatement(forLoopNode);
        methodState.putCfgNodeInCache(forLoopTree, forLoopNode);
        prevMust = must;
        must = false;

        pdgUtils.enteringNewBranch();
        scan(forLoopTree.getStatement(), Pair.createPair(forLoopNode, ASTRelationTypes.FOR_LOOP_STATEMENT));
        scan(forLoopTree.getUpdate(), Pair.createPair(forLoopNode, ASTRelationTypes.FOR_LOOP_UPDATE));

        pdgUtils.exitingCurrentBranch();
        must = prevMust;

        return null;
    }

    @Override
    public ASTVisitorResult visitIdentifier(IdentifierTree identifierTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper identifierNode;
        Symbol idSymbol = ((JCIdent) identifierTree).sym;
        if (idSymbol == null) {
            identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(identifierTree, NodeTypes.UNKNOWN_IDENTIFIER);
            identifierNode.setProperty("name", identifierTree.getName().toString());
            GraphUtils.connectWithParent(identifierNode, t);
            System.err.println("Warning: Creating an IDENTIFIER with name %s without symbol (%s).".formatted(
                    identifierTree.getName().toString(), identifierTree.toString()));
            return null;
        }
        ElementKind idKind = idSymbol.getKind();
        if (idKind == ElementKind.PACKAGE)
            identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(identifierTree, NodeTypes.PACKAGE_IDENTIFIER);
        else {
            if (isASTType(idKind)) {
                identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                        .createSkeletonNode(identifierTree, NodeTypes.TYPE_IDENTIFIER);
                astTypeRefersTo(identifierNode, idSymbol);
            } else {
                identifierNode =
                        DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(identifierTree, NodeTypes.VARIABLE);
                attachTypeDirect(identifierNode, identifierTree);
            }
        }
        identifierNode.setProperty("name", identifierTree.getName().toString());
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
        NodeWrapper ifNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(ifTree, NodeTypes.IF_STATEMENT);
        GraphUtils.connectWithParent(ifNode, t);
        scan(ifTree.getCondition(), Pair.createPair(ifNode, ASTRelationTypes.IF_CONDITION));
        addInvocationInStatement(ifNode);
        methodState.putCfgNodeInCache(ifTree, ifNode);

        prevMust = must;
        must = false;

        pdgUtils.enteringNewBranch();
        scan(ifTree.getThenStatement(), Pair.createPair(ifNode, ASTRelationTypes.IF_THEN));
        Set<NodeWrapper> paramsThen = pdgUtils.exitingCurrentBranch();
        scan(ifTree.getElseStatement(), Pair.createPair(ifNode, ASTRelationTypes.IF_ELSE));

        pdgUtils.merge(paramsThen, pdgUtils.exitingCurrentBranch());
        must = prevMust;

        return null;
    }

    @Override
    public ASTVisitorResult visitImport(ImportTree importTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper importNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(importTree, NodeTypes.IMPORT);
        importNode.setProperty("qualifiedIdentifier", importTree.getQualifiedIdentifier().toString());
        importNode.setProperty(IS_STATIC_PROP, importTree.isStatic());

        GraphUtils.connectWithParent(importNode, t, ASTRelationTypes.CU_IMPORTS);
        return null;
    }

    @Override
    public ASTVisitorResult visitInstanceOf(InstanceOfTree instanceOfTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper instanceOfNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(instanceOfTree, NodeTypes.INSTANCE_OF);
        GraphUtils.attachTypeDirect(instanceOfNode, instanceOfTree, "boolean", "BOOLEAN", ast);
        GraphUtils.connectWithParent(instanceOfNode, t);

        scan(instanceOfTree.getExpression(), Pair.createPair(instanceOfNode, ASTRelationTypes.INSTANCE_OF_EXPR));
        scan(instanceOfTree.getType(), Pair.createPair(instanceOfNode, ASTRelationTypes.INSTANCE_OF_TYPE));

        return null;
    }

    @Override
    public ASTVisitorResult visitIntersectionType(IntersectionTypeTree intersectionTypeTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper intersectionTypeNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(intersectionTypeTree, NodeTypes.INTERSECTION_TYPE,
                        NodeCategory.AST_TYPE, NodeCategory.AST_NODE);

        GraphUtils.connectWithParent(intersectionTypeNode, t);

        scan(intersectionTypeTree.getBounds(),
                Pair.createPair(intersectionTypeNode, ASTRelationTypes.AST_INTERSECTION_OF));

        return null;
    }

    @Override
    public ASTVisitorResult visitLabeledStatement(LabeledStatementTree labeledStatementTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper labeledStatementNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(labeledStatementTree, NodeTypes.LABELED_STATEMENT);
        labeledStatementNode.setProperty("name", labeledStatementTree.getLabel().toString());
        GraphUtils.connectWithParent(labeledStatementNode, t);
        methodState.putCfgNodeInCache(labeledStatementTree, labeledStatementNode);
        scan(labeledStatementTree.getStatement(),
                Pair.createPair(labeledStatementNode, ASTRelationTypes.LABELED_STATEMENT_ENCLOSES,
                        labeledStatementTree.getLabel()));
        return null;
    }

    @Override
    public ASTVisitorResult visitLambdaExpression(LambdaExpressionTree lambdaExpressionTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper lambdaExpressionNode = DatabaseFacade.CURRENT_DB_FACADE.get()
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
        scan(lambdaExpressionTree.getBody(), Pair.createPair(lambdaExpressionNode, ASTRelationTypes.LAMBDA_BODY));
        visitListWithPropertyIndex(lambdaExpressionTree.getParameters(), lambdaExpressionNode,
                ASTRelationTypes.LAMBDA_PARAM, "paramIndex");
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

        NodeWrapper literalNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(literalTree, NodeTypes.LITERAL);
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
        NodeWrapper memberReferenceNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(memberReferenceTree, NodeTypes.CALLABLE_REFERENCE);
        memberReferenceNode.setProperty("mode", memberReferenceTree.getMode().name());
        memberReferenceNode.setProperty("name", memberReferenceTree.getName().toString());
        GraphUtils.connectWithParent(memberReferenceNode, t);
        attachTypeDirect(memberReferenceNode, memberReferenceTree);

        scan(memberReferenceTree.getQualifierExpression(),
                Pair.createPair(memberReferenceNode, ASTRelationTypes.CALLABLE_REFERENCE_QUALIFIER));

        if (memberReferenceTree.getTypeArguments() != null)
            visitListWithPropertyIndex(memberReferenceTree.getTypeArguments(), memberReferenceNode,
                    ASTRelationTypes.CALLABLE_REFERENCE_TYPE_ARG, "argumentIndex");
        return null;
    }

    private void attachTypeDirect(NodeWrapper exprNode, ExpressionTree exprTree) {
        GraphUtils.attachTypeDirect(exprNode, exprTree, ast);
    }

    private boolean isASTType(ElementKind kind) {
        return kind == ElementKind.CLASS || kind == ElementKind.INTERFACE || kind == ElementKind.ENUM ||
                kind == ElementKind.RECORD || kind == ElementKind.TYPE_PARAMETER || kind == ElementKind.ANNOTATION_TYPE;
    }

    private void astTypeRefersTo(NodeWrapper astType, Symbol symbol) {
        astType.createRelationshipTo(DefinitionCache.getOrCreateType(symbol.type, ast), TypeRelations.REFERS_TO_TYPE);
        addClassAndDep(symbol);
    }

    @Override
    public ASTVisitorResult visitMemberSelect(MemberSelectTree memberSelectTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper memberSelectNode;
        Symbol memberSymbol = ((JCFieldAccess) memberSelectTree).sym;
        ElementKind idKind = memberSymbol.getKind();
        boolean fieldOrEnum = false;
        if (idKind == ElementKind.PACKAGE)
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, NodeTypes.PACKAGE_SELECTION);
        else if (isASTType(idKind)) {
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, NodeTypes.TYPE_SELECTION);
            astTypeRefersTo(memberSelectNode, memberSymbol);
        } else {
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, NodeTypes.MEMBER_SELECTION);
            fieldOrEnum = idKind == ElementKind.FIELD || idKind == ElementKind.ENUM_CONSTANT;
            attachTypeDirect(memberSelectNode, memberSelectTree);
        }

        memberSelectNode.setProperty("identifierName", memberSelectTree.getIdentifier().toString());
        GraphUtils.connectWithParent(memberSelectNode, t);

        ASTVisitorResult memberSelResult = scan(memberSelectTree.getExpression(),
                Pair.createPair(memberSelectNode, ASTRelationTypes.IDENT_SELECTION_FROM,
                        PDGProcessing.modifiedToStateModified(t)));
        if (outsideAnnotation) {
            boolean isInstance = memberSelResult != null && !memberSymbol.isStatic() && memberSelResult.isInstance();
            if (fieldOrEnum)
                pdgUtils.relationOnFieldAccess(memberSelectTree, memberSelectNode, t, methodState,
                        classState.currentClassDec, isInstance);
            memberSelResult = new VisitorResultImpl(isInstance);

        }
        return memberSelResult;
    }

    private void declaredCallablePropsAndAnnotations(MethodSymbol methodSymbol, ModifiersTree modifiers,
                                                     NodeWrapper methodNode, CallableNameInfo nameInfo) {
        scan(modifiers.getAnnotations(), Pair.createPair(methodNode, ASTRelationTypes.HAS_ANNOTATION));
        setCallableMethodSymbolProps(methodSymbol, modifiers.getFlags(), methodNode, nameInfo);
        String accessLevel = methodNode.getProperty(ACCESS_LEVEL_PROP).toString();
        if (!methodSymbol.isConstructor() && isInAccessibleContext && (accessLevel.contentEquals(PUBLIC_ACCESS) ||
                accessLevel.contentEquals(PROTECTED_ACCESS) &&
                        !(Boolean) classState.currentClassDec.getProperty(IS_FINAL_PROP)))
            ast.addAccessibleMethod(methodSymbol, methodNode);
    }

    private static void setFPSynchroNative(Set<Modifier> modifiers, NodeWrapper methodNode,
                                           boolean abstractOrConstructor) {
        if (abstractOrConstructor) {
            methodNode.setProperty(IS_NATIVE_PROP, false);
            methodNode.setProperty(IS_STRICTFP_PROP, false);
            methodNode.setProperty(IS_SYNCHRONIZED_PROP, false);
        } else {
            checkStrictfpMod(modifiers, methodNode);
            checkSynchroMod(modifiers, methodNode);
            checkNativeMod(modifiers, methodNode);
        }
    }

    private static void setMethodNames(NodeWrapper callableNode, CallableNameInfo nameInfo) {
        callableNode.setProperty("name", nameInfo.getSimpleName());
        callableNode.setProperty("completeName", nameInfo.getCompleteName());
        callableNode.setProperty("fullyQualifiedName", nameInfo.getFullyQualifiedName());
    }

    private static void setCallableJustSymbolProps(Symbol symbol, NodeWrapper callableNode, CallableNameInfo nameInfo) {
        setCallableCommonProps(callableNode, symbol, symbol.getModifiers(), nameInfo, false);
    }

    private static void setCallableCommonProps(NodeWrapper callableNode, Symbol symbol, Set<Modifier> modifiers,
                                               CallableNameInfo nameInfo, boolean isDefault) {
        setMethodNames(callableNode, nameInfo);
        setAccessLevel(symbol, modifiers, callableNode);
        if (symbol.isConstructor()) {
            callableNode.setProperty(IS_FINAL_PROP, true);
            callableNode.setProperty(IS_STATIC_PROP, false);
            callableNode.setProperty(IS_ABSTRACT_PROP, false);
            setFPSynchroNative(modifiers, callableNode, true);
        } else {
            checkStaticMod(symbol, callableNode);
            checkFinalMod(symbol, callableNode);
            boolean isAbstract = symbol.isAbstract() && isDefault;
            callableNode.setProperty(IS_ABSTRACT_PROP, isAbstract);
            setFPSynchroNative(modifiers, callableNode, isAbstract);
        }
    }

    private static void setCallableMethodSymbolProps(MethodSymbol methodSymbol, NodeWrapper callableNode) {
        setCallableMethodSymbolProps(methodSymbol, methodSymbol.getModifiers(), callableNode,
                new CallableNameInfo(methodSymbol));
    }

    private static void setCallableMethodSymbolProps(MethodSymbol methodSymbol, Set<Modifier> modifiers,
                                                     NodeWrapper callableNode, CallableNameInfo nameInfo) {
        callableNode.setProperty("isVarArgs", methodSymbol.isVarArgs());
        setCallableCommonProps(callableNode, methodSymbol, modifiers, nameInfo, methodSymbol.isDefault());
    }


    private void addClassAndDep(Symbol symbol) {
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
    public ASTVisitorResult visitMethod(MethodTree methodTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        MethodSymbol methodSymbol = ((JCMethodDecl) methodTree).sym;
        CallableNameInfo nameInfo = new CallableNameInfo(methodSymbol);
        NodeWrapper methodNode;

        boolean prev = false;
        ASTRelationTypes rel;
        boolean isConstructor = methodSymbol.isConstructor();
        boolean isUserCode = true;
        if (isConstructor) {
            prev = insideConstructor;
            insideConstructor = true;
            methodNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNodeExplicitCats(methodTree, NodeTypes.CONSTRUCTOR_DEC, NodeCategory.AST_NODE);

            ((Pair<Pair, List<NodeWrapper>>) t.getSecond()).getSecond().add(methodNode);
            rel = ASTRelationTypes.DECLARES_CONSTRUCTOR;
        } else {
            methodNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNodeExplicitCats(methodTree, NodeTypes.METHOD_DEC, NodeCategory.AST_NODE);
            rel = ASTRelationTypes.DECLARES_METHOD;
        }

        methodNode.setProperty(IS_USER_CODE_PROP, isUserCode);

        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(nameInfo.getFullyQualifiedName())) {
            ast.deleteAccessibleMethod(methodSymbol);
            DefinitionCache.CALLABLE_DEC_CACHE.get().putDefinition(nameInfo.getFullyQualifiedName(), methodNode);

            if (!methodNode.hasRelationship(rel, Direction.INCOMING))
                GraphUtils.connectWithParent(methodNode, t, rel);
        } else {
            DefinitionCache.CALLABLE_DEC_CACHE.get().putDefinition(nameInfo.getFullyQualifiedName(), methodNode);
            GraphUtils.connectWithParent(methodNode, t, rel);
        }

        declaredCallablePropsAndAnnotations(methodSymbol, methodTree.getModifiers(), methodNode, nameInfo);

        boolean prevIsInAccesibleCtxt = isInAccessibleContext;
        isInAccessibleContext = false;
        MethodState prevState = methodState;
        must = true;
        methodState = new MethodState(methodNode);
        pdgUtils.visitNewMethod();
        ast.newMethodDeclaration(methodState);

        scan(methodTree.getReturnType(), Pair.createPair(methodNode, ASTRelationTypes.METHOD_RETURN_TYPE));
        GraphUtils.attachType(methodNode, ((JCMethodDecl) methodTree).type, ast);

        visitListWithPropertyIndex(methodTree.getTypeParameters(), methodNode, ASTRelationTypes.CALLABLE_TYPE_PARAM,
                "paramIndex");
        visitListWithPropertyIndex(methodTree.getParameters(), methodNode, ASTRelationTypes.CALLABLE_PARAM,
                "paramIndex");

        methodTree.getThrows().forEach(
                (throwsTree) -> scan(throwsTree, Pair.createPair(methodNode, ASTRelationTypes.CALLABLE_THROWS)));

        scan(methodTree.getBody(), Pair.createPair(methodNode, ASTRelationTypes.CALLABLE_BODY));
        scan(methodTree.getDefaultValue(), Pair.createPair(methodNode, ASTRelationTypes.DEFAULT_VALUE));
        scan(methodTree.getReceiverParameter(), Pair.createPair(methodNode, ASTRelationTypes.RECEIVER_PARAM));

        pdgUtils.setThisRefOfInstanceMethod(methodState, classState.currentClassDec);
        ast.addInfo(methodTree, methodNode, methodState,
                methodSymbol.isVarArgs() ? methodTree.getParameters().size() : ASTAuxiliarStorage.NO_VARG_ARG);
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
        NodeWrapper methodInvocationNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(methodInvocationTree, NodeTypes.METHOD_INVOCATION);
        attachTypeDirect(methodInvocationNode, methodInvocationTree);
        GraphUtils.connectWithParent(methodInvocationNode, pair);

        Symbol symbol = JavacInfo.getSymbolFromTree(methodInvocationTree.getMethodSelect());
        NodeWrapper decNode;

        if (isDynamicallyGenerated(symbol, methodInvocationTree)) {
            String methodName = (methodInvocationTree.getMethodSelect() instanceof JCIdent ?
                    ((JCIdent) methodInvocationTree.getMethodSelect()).name :
                    ((JCFieldAccess) methodInvocationTree.getMethodSelect()).name).toString();
            System.err.println(
                    "Invocation %s with methodName %s and no method symbol %s:%s detected as dynamically generated.".formatted(
                            methodInvocationTree, methodName, symbol, symbol.getClass()));
            if (methodName.contentEquals("this") || methodName.contentEquals("super"))
                decNode = createDynamicallyGenConstructor(symbol);
            else {
                decNode = createNonDeclaredCallable(false);
                setCallableJustSymbolProps(symbol, decNode, new CallableNameInfo(methodName, symbol.owner.toString()));
            }
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

            if (methodSymbol.getThrownTypes().size() > 0)
                currentMethodInvocations.add(methodSymbol);

            decNode = getCallableDecFromCall(methodSymbol);
            ast.checkIfTrustableInvocation(methodInvocationTree, methodSymbol, methodInvocationNode);
        }

        if (!inALambda) {
            RelationshipWrapper callRelation =
                    methodState.lastMethodDecVisited.createRelationshipTo(methodInvocationNode, CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", must);
        }

        methodInvocationNode.createRelationshipTo(decNode, CGRelationTypes.CALLEE);
        methodInvocationNode.createRelationshipTo(decNode, CGRelationTypes.REFERS_TO);

        pdgUtils.addParamsPrevModifiedForInv(methodInvocationNode, methodState);

        scan(methodInvocationTree.getMethodSelect(),
                Pair.createPair(methodInvocationNode, ASTRelationTypes.INVOCATION_METHOD_SELECTION));

        visitListWithPropertyIndex(methodInvocationTree.getTypeArguments(), methodInvocationNode,
                ASTRelationTypes.INVOCATION_TYPE_ARG, "argumentIndex");
        visitListWithPropertyIndex(methodInvocationTree.getArguments(), methodInvocationNode,
                ASTRelationTypes.INVOCATION_ARG, "argumentIndex");
        return null;
    }

    public static void setAccessLevel(Symbol symbol, Set<Modifier> modifiers, NodeWrapper modNode) {
        modNode.setProperty(ACCESS_LEVEL_PROP, symbol.isPublic() ? PUBLIC_ACCESS : symbol.isPrivate() ? PRIVATE_ACCESS :
                modifiers.contains(Modifier.PROTECTED) ? PROTECTED_ACCESS : PACKAGE_ACCESS);
    }

    public static void checkFinalMod(Symbol symbol, NodeWrapper node) {
        node.setProperty(IS_FINAL_PROP, symbol.isFinal());
    }

    public static void checkStaticMod(Symbol symbol, NodeWrapper node) {
        node.setProperty(IS_STATIC_PROP, symbol.isStatic());
    }

    public static void checkVolatileMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isVolatile", modifiers.contains(Modifier.VOLATILE));
    }

    public static void checkTransientMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isTransient", modifiers.contains(Modifier.TRANSIENT));
    }

    public static void checkSynchroMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty(IS_SYNCHRONIZED_PROP, modifiers.contains(Modifier.SYNCHRONIZED));
    }

    public static void checkNativeMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty("isNative", modifiers.contains(Modifier.NATIVE));
    }

    public static void checkStrictfpMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty(IS_STRICTFP_PROP, modifiers.contains(Modifier.STRICTFP));
    }


    public static void setAttrDecModifiers(Symbol symbol, Set<Modifier> modifiers, NodeWrapper node) {
        checkStaticMod(symbol, node);
        checkFinalMod(symbol, node);
        checkVolatileMod(modifiers, node);
        checkTransientMod(modifiers, node);
        setAccessLevel(symbol, modifiers, node);
    }

    @Override
    public ASTVisitorResult visitModifiers(ModifiersTree modifiersTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        throw new IllegalStateException(
                "The modifiers of %s node should be handled in the caller.".formatted(t.getFirst().getStartingNode()));
    }

    @Override
    public ASTVisitorResult visitNewArray(NewArrayTree newArrayTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper newArrayNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(newArrayTree, NodeTypes.NEW_ARRAY);
        attachTypeDirect(newArrayNode, newArrayTree);
        GraphUtils.connectWithParent(newArrayNode, t);

        scan(newArrayTree.getType(), Pair.createPair(newArrayNode, ASTRelationTypes.NEW_ARRAY_TYPE));
        scan(newArrayTree.getDimensions(), Pair.createPair(newArrayNode, ASTRelationTypes.NEW_ARRAY_DIMENSION));
        scan(newArrayTree.getInitializers(), Pair.createPair(newArrayNode, ASTRelationTypes.NEW_ARRAY_INIT));
        return null;
    }

    @Override
    public ASTVisitorResult visitNewClass(NewClassTree newClassTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        NodeWrapper newClassNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(newClassTree, NodeTypes.NEW_INSTANCE);
        scan(newClassTree.getClassBody(), Pair.createPair(newClassNode, ASTRelationTypes.NEW_INSTANCE_BODY));

        Type type = JavacInfo.getTypeDirect(newClassTree.getIdentifier());
        Symbol newClassConstructor = ((JCNewClass) newClassTree).constructor;
        NodeWrapper constructorDef;
        if (newClassConstructor instanceof ClassSymbol && isUnknownOrErrorType(type) &&
                ((ClassSymbol) newClassConstructor).sourcefile == null) {
            constructorDef = createDynamicallyGenConstructor(newClassConstructor);
            System.err.println("Invocation of %s with class symbol %s:%s detected as dynamically generated.".formatted(
                    newClassTree, newClassConstructor, newClassConstructor.getClass()));
        } else {
            if (newClassConstructor == null) {
                System.err.println("Invocation " + newClassTree + " with no symbol, at" + currentTypeDecSymbol);
                return null;
            }
            MethodSymbol consSymbol = (MethodSymbol) newClassConstructor;
            constructorDef = getCallableDecFromCall(consSymbol);
            if (consSymbol.getThrownTypes().size() > 0)
                currentMethodInvocations.add(consSymbol);
        }
        GraphUtils.attachType(newClassNode, type, ast);
        GraphUtils.connectWithParent(newClassNode, pair);
        pdgUtils.addParamsPrevModifiedForInv(newClassNode, methodState);
        scan(newClassTree.getEnclosingExpression(),
                Pair.createPair(newClassNode, ASTRelationTypes.NEW_INSTANCE_ENCLOSING_EXPR));
        scan(newClassTree.getIdentifier(), Pair.createPair(newClassNode, ASTRelationTypes.NEW_INSTANCE_NAME));
        visitListWithPropertyIndex(newClassTree.getTypeArguments(), newClassNode,
                ASTRelationTypes.NEW_INSTANCE_TYPE_ARG, "argumentIndex");

        visitListWithPropertyIndex(newClassTree.getArguments(), newClassNode, ASTRelationTypes.NEW_INSTANCE_ARG,
                "argumentIndex");
        newClassNode.createRelationshipTo(constructorDef, CGRelationTypes.CALLEE);
        newClassNode.createRelationshipTo(constructorDef, CGRelationTypes.REFERS_TO);

        if (!inALambda) {
            RelationshipWrapper callRelation =
                    methodState.lastMethodDecVisited.createRelationshipTo(newClassNode, CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", must);
        }
        return null;
    }

    private NodeWrapper createDynamicallyGenConstructor(Symbol newClassConstructor) {
        CallableNameInfo nameInfo =
                new CallableNameInfo("<init>", newClassConstructor.owner.getQualifiedName().toString());

        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(nameInfo.getFullyQualifiedName()))
            return DefinitionCache.CALLABLE_DEC_CACHE.get().get(nameInfo.getFullyQualifiedName());
        NodeWrapper generatedClassNode = TypeVisitor.generatedClassType((ClassSymbol) newClassConstructor.owner, ast);

        if (!typeDecUses.contains(generatedClassNode)) {
            classState.currentClassDec.createRelationshipTo(generatedClassNode, CDGRelationTypes.USES_TYPE_DEF);
            typeDecUses.add(generatedClassNode);
        }
        NodeWrapper constructorDef = createAndLinkNonDeclaredCallable(generatedClassNode, true);
        setCallableJustSymbolProps(newClassConstructor, constructorDef, nameInfo);
        return constructorDef;
    }

    private void visitListWithPropertyIndex(List<? extends Tree> childList, NodeWrapper parentNode,
                                            ASTRelationTypes relationType, String propertyName) {
        for (int i = 0; i < childList.size(); i++)
            scan(childList.get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(parentNode, relationType, propertyName, i + 1)));
    }

    @Override
    public ASTVisitorResult visitOther(Tree arg0, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        throw new IllegalArgumentException(
                "[EXCEPTION] Tree not included in the visitor: " + arg0.getClass() + "\n" + arg0);
    }

    @Override
    public ASTVisitorResult visitParameterizedType(ParameterizedTypeTree parameterizedTypeTree,
                                                   Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper parameterizedNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(parameterizedTypeTree, NodeTypes.PARAMETERIZED_TYPE,
                        NodeCategory.AST_TYPE, NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(parameterizedNode, t);
        scan(parameterizedTypeTree.getType(),
                Pair.createPair(parameterizedNode, ASTRelationTypes.PARAMETERIZES_AST_TYPE));
        visitListWithPropertyIndex(parameterizedTypeTree.getTypeArguments(), parameterizedNode,
                ASTRelationTypes.AST_TYPE_ARG, "argumentIndex");
        parameterizedNode.setProperty("actualType",
                ((JCTypeApply) parameterizedTypeTree).type.tsym.getQualifiedName() + "<>");
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
        NodeWrapper primitiveTypeNode = DatabaseFacade.CURRENT_DB_FACADE.get()
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
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(returnTree, NodeTypes.RETURN_STATEMENT);
        methodState.putCfgNodeInCache(returnTree, returnNode);
        GraphUtils.connectWithParent(returnNode, t);
        int hash = returnTree.hashCode();
        scan(returnTree.getExpression(), Pair.createPair(returnNode, ASTRelationTypes.RETURN_EXPR));
        if (returnTree.hashCode() != hash)
            throw new IllegalStateException();
        must = false;
        addInvocationInStatement(returnNode);
        return null;
    }

    private void visitCases(List<? extends CaseTree> cases, NodeWrapper switchNode) {

        switchNode.setProperty("isArrow", cases.get(0).getCaseKind() == CaseTree.CaseKind.RULE);
        ASTVisitorResult caseResult =
                visitCase(cases.get(0), Pair.createPair(switchNode, ASTRelationTypes.SWITCH_CASE));
        Set<NodeWrapper> paramsModifiedInAllCases =
                caseResult == null ? new HashSet<>() : caseResult.paramsPreviouslyModifiedForSwitch();
        boolean unconditionalFound = caseResult == null;
        for (int i = 1; i < cases.size(); i++) {
            caseResult = scan(cases.get(i), Pair.createPair(switchNode, ASTRelationTypes.SWITCH_CASE));
            if (caseResult != null)
                paramsModifiedInAllCases.retainAll(caseResult.paramsPreviouslyModifiedForSwitch());
            else
                unconditionalFound = true;
        }
        if (!unconditionalFound && cases.get(cases.size() - 1).getExpression() == null)
            pdgUtils.unionWithCurrent(paramsModifiedInAllCases);

    }

    @Override
    public ASTVisitorResult visitSwitch(SwitchTree switchTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper switchNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(switchTree, NodeTypes.SWITCH_STATEMENT);
        GraphUtils.connectWithParent(switchNode, t);
        scan(switchTree.getExpression(), Pair.createPair(switchNode, ASTRelationTypes.SWITCH_SELECTOR));
        addInvocationInStatement(switchNode);
        methodState.putCfgNodeInCache(switchTree, switchNode);
        if (switchTree.getCases().size() > 0)
            visitCases(switchTree.getCases(), switchNode);
        return null;
    }


    @Override
    public ASTVisitorResult visitSwitchExpression(SwitchExpressionTree switchExpressionTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper switchNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(switchExpressionTree, NodeTypes.SWITCH_EXPRESSION);
        attachTypeDirect(switchNode, switchExpressionTree);
        GraphUtils.connectWithParent(switchNode, t);
        scan(switchExpressionTree.getExpression(), Pair.createPair(switchNode, ASTRelationTypes.SWITCH_SELECTOR));
        visitCases(switchExpressionTree.getCases(), switchNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitSynchronized(SynchronizedTree synchronizedTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper synchronizedNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(synchronizedTree, NodeTypes.SYNCHRONIZED_BLOCK);
        GraphUtils.connectWithParent(synchronizedNode, t);
        methodState.putCfgNodeInCache(synchronizedTree, synchronizedNode);
        scan(synchronizedTree.getExpression(), Pair.createPair(synchronizedNode, ASTRelationTypes.SYNCHRONIZED_EXPR));
        addInvocationInStatement(synchronizedNode);
        scan(synchronizedTree.getBlock(), Pair.createPair(synchronizedNode, ASTRelationTypes.SYNCHRONIZED_BLOCK));
        return null;
    }

    @Override
    public ASTVisitorResult visitThrow(ThrowTree throwTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper throwNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(throwTree, NodeTypes.THROW_STATEMENT);
        methodState.putCfgNodeInCache(throwTree, throwNode);
        GraphUtils.connectWithParent(throwNode, t);

        scan(throwTree.getExpression(), Pair.createPair(throwNode, ASTRelationTypes.THROW_EXPR));
        addInvocationInStatement(throwNode);
        return null;
    }

    @Override
    public ASTVisitorResult visitTry(TryTree tryTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper tryNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(tryTree, NodeTypes.TRY_STATEMENT);
        GraphUtils.connectWithParent(tryNode, t);
        boolean hasCatchingComponent = tryTree.getCatches().size() > 0 || tryTree.getFinallyBlock() != null;
        if (hasCatchingComponent)
            ast.enterInNewTry(tryTree, methodState);
        scan(tryTree.getResources(), Pair.createPair(tryNode, ASTRelationTypes.TRY_RESOURCES));
        scan(tryTree.getBlock(), Pair.createPair(tryNode, ASTRelationTypes.TRY_BLOCK));
        if (hasCatchingComponent)
            ast.exitTry();
        scan(tryTree.getCatches(), Pair.createPair(tryNode, ASTRelationTypes.TRY_CATCH));

        methodState.putCfgNodeInCache(tryTree, tryNode);
        scan(tryTree.getFinallyBlock(), Pair.createPair(tryNode, ASTRelationTypes.TRY_FINALLY));
        NodeWrapper finallyNode = lastBlockVisited;

        if (tryTree.getFinallyBlock() != null) {
            finallyNode.removeLabel(NodeTypes.BLOCK);
            finallyNode.removeLabel(NodeCategory.STATEMENT);
            finallyNode.addLabel(NodeTypes.FINALLY_BLOCK);

            NodeWrapper lastStmtInFinally =
                    DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.CFG_FINALLY_END);
            methodState.putFinallyInCache(tryTree.getFinallyBlock(), finallyNode, lastStmtInFinally);
            //finallyNode.createRelationshipTo(lastStmtInFinally, CFGRelationTypes.CFG_FINALLY_TO_LAST_STMT);
        }
        return null;
    }

    @Override
    public ASTVisitorResult visitTypeCast(TypeCastTree typeCastTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper typeCastNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(typeCastTree, NodeTypes.TYPE_CAST);
        attachTypeDirect(typeCastNode, typeCastTree);
        GraphUtils.connectWithParent(typeCastNode, t);
        scan(typeCastTree.getType(), Pair.createPair(typeCastNode, ASTRelationTypes.CAST_TYPE));
        scan(typeCastTree.getExpression(), Pair.createPair(typeCastNode, ASTRelationTypes.CAST_EXPR));
        return null;
    }

    @Override
    public ASTVisitorResult visitTypeParameter(TypeParameterTree typeParameterTree,
                                               Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper typeParameterNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(typeParameterTree, NodeTypes.TYPE_PARAM);
        typeParameterNode.setProperty("name", typeParameterTree.getName().toString());
        GraphUtils.connectWithParent(typeParameterNode, t);
        scan(typeParameterTree.getAnnotations(), Pair.createPair(typeParameterNode, ASTRelationTypes.HAS_ANNOTATION));
        scan(typeParameterTree.getBounds(),
                Pair.createPair(typeParameterNode, ASTRelationTypes.AST_TYPE_PARAM_EXTENDS));
        return null;
    }

    @Override
    public ASTVisitorResult visitUnary(UnaryTree unaryTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper unaryNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(unaryTree, NodeTypes.UNARY_OPERATION);
        unaryNode.setProperty("operator", unaryTree.getKind().toString());
        boolean impliesModification =
                unaryTree.getKind() == Kind.POSTFIX_INCREMENT || unaryTree.getKind() == Kind.POSTFIX_DECREMENT ||
                        unaryTree.getKind() == Kind.PREFIX_INCREMENT || unaryTree.getKind() == Kind.PREFIX_DECREMENT;
        GraphUtils.connectWithParent(unaryNode, t);
        attachTypeDirect(unaryNode, unaryTree);
        if (impliesModification) {
            unaryNode.addLabel(NodeCategory.ANY_ASSIGNMENT);
            NodeWrapper lastAssignInfo = beforeScanAnyAssign(unaryNode, t);
            scan(unaryTree.getExpression(),
                    Pair.createPair(unaryNode, ASTRelationTypes.UNARY_OPERAND, PDGProcessing.getLefAssignmentArg(t)));
            afterScanAnyAssign(lastAssignInfo);
        } else
            scan(unaryTree.getExpression(), Pair.createPair(unaryNode, ASTRelationTypes.UNARY_OPERAND));
        return null;
    }

    @Override
    public ASTVisitorResult visitUnionType(UnionTypeTree unionTypeTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper unionTypeNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(unionTypeTree, NodeTypes.UNION_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(unionTypeNode, t);
        scan(unionTypeTree.getTypeAlternatives(),
                Pair.createPair(unionTypeNode, ASTRelationTypes.AST_UNION_ALTERNATIVE));
        return null;
    }

    private NodeWrapper createVarInit(VariableTree varTree, NodeWrapper varDecNode, boolean isAttr, boolean isStatic,
                                      NodeWrapper varTypeNode) {
        if (varTree.getInitializer() != null) {
            NodeWrapper initNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(varTree.getInitializer(), NodeTypes.INITIALIZATION);
            initNode.createRelationshipTo(varTypeNode, TypeRelations.ITS_TYPE_IS);
            varDecNode.createRelationshipTo(initNode, ASTRelationTypes.VAR_DEC_INIT);
            RelationshipWrapper r = varDecNode.createRelationshipTo(initNode, PDGRelationTypes.MODIFIED_BY);
            if (isAttr && !isStatic)
                r.setProperty("isOwnAccess", true);
            scan(varTree.getInitializer(), Pair.createPair(initNode, ASTRelationTypes.INITIALIZATION_EXPR));
            PDGProcessing.createVarDecInitRel(classState.currentClassDec, initNode, isAttr, isStatic);
            return initNode;
        }
        return null;
    }

    private void addClassAndDep(TypeMirror typeMirror) {
        if (typeMirror instanceof ClassType)
            addClassAndDep(((ClassType) typeMirror).tsym);
    }

    private NodeWrapper processVarType(VariableTree variableTree, NodeWrapper varDecNode) {
        JCVariableDecl varDec = (JCVariableDecl) variableTree;
        Type type = varDec.vartype.type;
        NodeWrapper varTypeNode = GraphUtils.attachType(varDecNode, type, ast);
        if (varDec.declaredUsingVar()) {
            addClassAndDep(type);
            NodeWrapper ASTVarType =
                    DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(variableTree, NodeTypes.VAR_TYPE);
            ASTVarType.createRelationshipTo(varTypeNode, TypeRelations.INFERRED_TYPE);
            varDecNode.createRelationshipTo(ASTVarType, ASTRelationTypes.VAR_DEC_TYPE);
            final String VAR_NAME = "var";
            ASTVarType.setProperty("simpleName", VAR_NAME);
            ASTVarType.setProperty("fullyQualifiedName", VAR_NAME);
        } else
            scan(variableTree.getType(), Pair.createPair(varDecNode, ASTRelationTypes.VAR_DEC_TYPE));
        return varTypeNode;
    }

    @Override
    public ASTVisitorResult visitVariable(VariableTree variableTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        boolean isAttrOrEnum = t.getFirst().getRelationType().equals(ASTRelationTypes.TYPE_STATIC_INIT);
        boolean isMethodParam = t.getFirst().getRelationType().equals(ASTRelationTypes.CALLABLE_PARAM) ||
                t.getFirst().getRelationType().equals(ASTRelationTypes.LAMBDA_PARAM);
        boolean isEnum = false;
        NodeWrapper variableNode;
        Symbol varSymbol = ((JCVariableDecl) variableTree).sym;
        boolean isImplicitField = false;
        if (isAttrOrEnum) {
            variableNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNodeExplicitCats(variableTree,
                    (isEnum = varSymbol.getKind() == ElementKind.ENUM_CONSTANT) ? NodeTypes.ENUM_ELEMENT :
                            NodeTypes.ATTR_DEC, NodeCategory.AST_NODE);
            ClassSymbol owner = (ClassSymbol) varSymbol.owner;
            if (isImplicitField = owner.isRecord() && !varSymbol.isStatic())
                declaredRecordComponent(owner.getRecordComponent((Symbol.VarSymbol) varSymbol),
                        classState.currentClassDec, variableNode);
        } else
            variableNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(variableTree,
                    isMethodParam ? NodeTypes.PARAMETER_DEC : NodeTypes.LOCAL_VAR_DEC);
        variableNode.setProperty("name", variableTree.getName().toString());

        NodeWrapper varTypeNode = processVarType(variableTree, variableNode);

        ModifiersTree modifiers = variableTree.getModifiers();
        if (isAttrOrEnum)
            setAttrDecModifiers(varSymbol, modifiers.getFlags(), variableNode);
        else
            checkFinalMod(varSymbol, variableNode);

        scan(modifiers.getAnnotations(), Pair.createPair(variableNode, null));
        MethodState previousState = methodState;
        if (isAttrOrEnum) {
            variableNode.setProperty(IS_USER_CODE_PROP, !isImplicitField);
            GraphUtils.connectWithParent(variableNode, t,
                    isEnum ? ASTRelationTypes.ENUM_DECLARES_ELEMENT : ASTRelationTypes.DECLARES_FIELD);
            methodState = new MethodState(variableNode);
            Pair<List<NodeWrapper>, List<NodeWrapper>> param =
                    ((Pair<Pair<List<NodeWrapper>, List<NodeWrapper>>, List<NodeWrapper>>) t.getSecond()).getFirst();
            (varSymbol.isStatic() ? param.getSecond() : param.getFirst()).add(methodState.lastMethodDecVisited);

        } else
            GraphUtils.connectWithParent(variableNode, t);

        NodeWrapper initNode =
                createVarInit(variableTree, variableNode, isAttrOrEnum, varSymbol.isStatic(), varTypeNode);
        if (isEnum && !varSymbol.owner.isFinal())
            processEnumElementMembers(variableNode, initNode);
        if (!(isMethodParam || isAttrOrEnum)) {
            methodState.putCfgNodeInCache(variableTree, variableNode);
            addInvocationInStatement(variableNode);
        }
        pdgUtils.putDecInCache(varSymbol, variableNode);
        if (isAttrOrEnum) {
            methodState = previousState;
        }
        return null;
    }

    private void processEnumElementMembers(NodeWrapper enumElement, NodeWrapper initNode) {
        RelationshipWrapper anonymousClassRel =
                initNode.getSingleRelationship(Direction.OUTGOING, ASTRelationTypes.INITIALIZATION_EXPR).getEndNode()
                        .getSingleRelationship(Direction.OUTGOING, ASTRelationTypes.NEW_INSTANCE_BODY);
        if (anonymousClassRel != null)
            anonymousClassRel.getEndNode().getRelationships(Direction.OUTGOING, ASTRelationTypes.DECLARES_TYPE,
                    ASTRelationTypes.DECLARES_METHOD, ASTRelationTypes.DECLARES_FIELD).forEach(
                    (memberRel) -> enumElement.createRelationshipTo(memberRel.getEndNode(), memberRel.getType()));
    }

    @Override
    public ASTVisitorResult visitWhileLoop(WhileLoopTree whileLoopTree,
                                           Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper whileLoopNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(whileLoopTree, NodeTypes.WHILE_LOOP);
        GraphUtils.connectWithParent(whileLoopNode, t);
        scan(whileLoopTree.getCondition(), Pair.createPair(whileLoopNode, ASTRelationTypes.WHILE_CONDITION));
        addInvocationInStatement(whileLoopNode);
        methodState.putCfgNodeInCache(whileLoopTree, whileLoopNode);
        prevMust = must;
        must = false;
        pdgUtils.enteringNewBranch();
        scan(whileLoopTree.getStatement(), Pair.createPair(whileLoopNode, ASTRelationTypes.WHILE_STATEMENT));
        pdgUtils.exitingCurrentBranch();
        must = prevMust;
        return null;
    }

    @Override
    public ASTVisitorResult visitWildcard(WildcardTree wildcardTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper wildcardNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(wildcardTree, NodeTypes.WILDCARD_TYPE, NodeCategory.AST_TYPE,
                        NodeCategory.AST_NODE);
        wildcardNode.setProperty("typeBoundKind", wildcardTree.getKind().toString());
        GraphUtils.connectWithParent(wildcardNode, t);
        scan(wildcardTree.getBound(), Pair.createPair(wildcardNode, ASTRelationTypes.WILDCARD_BOUND));
        return null;
    }


    @Override
    public ASTVisitorResult visitYield(YieldTree yieldTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper yieldNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(yieldTree, NodeTypes.YIELD_STATEMENT);
        GraphUtils.connectWithParent(yieldNode, t);
//        methodState.putCfgNodeInCache(yieldTree, yieldNode);
        scan(yieldTree.getValue(), Pair.createPair(yieldNode, ASTRelationTypes.YIELD_EXPR));
//        addInvocationInStatement(yieldNode);
        return null;
    }
}
