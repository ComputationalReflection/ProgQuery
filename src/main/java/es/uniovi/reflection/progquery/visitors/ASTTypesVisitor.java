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
import es.uniovi.reflection.progquery.database.nodes.NodeProperties;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.*;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.pg.PackageManager;
import es.uniovi.reflection.progquery.typeInfo.TypeHierarchy;
import es.uniovi.reflection.progquery.typeInfo.keys.CallableKey;
import es.uniovi.reflection.progquery.typeInfo.keys.ComponentKey;
import es.uniovi.reflection.progquery.typeInfo.keys.VarKey;
import es.uniovi.reflection.progquery.typeInfo.keys.var.FieldKey;
import es.uniovi.reflection.progquery.typeInfo.keys.var.LocalScopeVarKey;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.*;
import org.neo4j.graphdb.Direction;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.*;

public class ASTTypesVisitor
        extends TreeScanner<ASTVisitorResult, Pair<PartialRelation<RelationTypesInterface>, Object>> {
    public ASTAuxiliarStorage ast;
    private final NodeWrapper currentCU;
    private NodeWrapper lastStaticConsVisited = null;
    private ClassTree typeDec;
    private boolean first;
    private PDGProcessing pdgUtils;
    private MethodState methodState = null;
    private ClassState classState = null;
    private boolean insideConstructor = false;
    private List<MethodSymbol> currentMethodInvocations = new ArrayList<>();
    private boolean must = true, prevMust = true, auxMust = true;
    private boolean anyBreak;
    private Set<NodeWrapper> typeDecUses;
    private ClassSymbol currentTypeDecSymbol;
    private boolean outsideAnnotation = true;
    private boolean isInAccessibleContext = true;
    private Set<Name> gotoLabelsInDoWhile = new HashSet<>();
    private boolean inADoWhile = false, inALambda = false;
    private NodeWrapper lastBlockVisited;

    public ASTTypesVisitor(ClassTree typeDec, boolean first, PDGProcessing pdgUtils, ASTAuxiliarStorage ast,
                           NodeWrapper cu) {
        this.typeDec = typeDec;
        this.first = first;
        this.pdgUtils = pdgUtils;
        this.ast = ast;
        this.currentCU = cu;
    }

    public Set<NodeWrapper> getTypeDecUses() {
        return typeDecUses;
    }

    @Override
    public ASTVisitorResult reduce(ASTVisitorResult n1, ASTVisitorResult n2) {
        return n2;
    }

    public static NodeWrapper createAndLinkNonDeclaredCallable(NodeWrapper classNode, boolean isConstructor) {
        NodeWrapper methodDec = createNonDeclaredCallable(isConstructor);
        classNode.createRelationshipTo(methodDec,
                isConstructor ? ASTRelationTypes.DECLARES_CONSTRUCTOR : ASTRelationTypes.DECLARES_METHOD);
        return methodDec;
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
        scanListWithPropertyIndex(annotationTree.getArguments(), annotationNode, ASTRelationTypes.ANNOTATION_ARG,
                RelationProperties.ARGUMENT_INDEX);
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
        arrayTypeNode.setProperty(FULL_NAME, fullyName);
        String[] splittedName = fullyName.split(".");
        arrayTypeNode.setProperty(SIMPLE_NAME,
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
        binaryNode.setProperty(OPERATOR, binaryTree.getKind().toString());
        attachTypeDirect(binaryNode, binaryTree);
        GraphUtils.connectWithParent(binaryNode, t);

        scan(binaryTree.getLeftOperand(), Pair.createPair(binaryNode, ASTRelationTypes.BINARY_OP_LHS));
        scan(binaryTree.getRightOperand(), Pair.createPair(binaryNode,

                binaryTree.getKind().toString().contentEquals("OR") ||
                        binaryTree.getKind().toString().contentEquals("AND") ? ASTRelationTypes.BINARY_OP_COND_RHS :
                        ASTRelationTypes.BINARY_OP_RHS));
        return null;
    }

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
            CFGVisitor.doCFGAnalysis(blockNode, blockTree, methodState.cfgNodeCache,
                    ASTAuxiliarStorage.getTrysToExceptionalPartialRelations(methodState.invocationsInStatements),
                    methodState.finallyCache);
            pdgUtils.exitingCurrentMethod();
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
    public ASTVisitorResult visitConstantCaseLabel(ConstantCaseLabelTree constantCaseLabelTree,
                                                   Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        scan(constantCaseLabelTree.getConstantExpression(),
                Pair.createPair(t.getFirst().getStartingNode(), ASTRelationTypes.CASE_CONSTANT_LABEL));
        return null;
    }

    @Override
    public ASTVisitorResult visitPatternCaseLabel(PatternCaseLabelTree patternCaseLabelTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        scan(patternCaseLabelTree.getPattern(),
                Pair.createPair(t.getFirst().getStartingNode(), ASTRelationTypes.CASE_PATTERN_LABEL));
        return null;
    }

    @Override
    public ASTVisitorResult visitDefaultCaseLabel(DefaultCaseLabelTree patternCaseLabelTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        t.getFirst().getStartingNode().setProperty(HAS_DEFAULT_LABEL, true);
        return null;
    }

    @Override
    public ASTVisitorResult visitCase(CaseTree caseTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        boolean isArrow = caseTree.getCaseKind() == CaseTree.CaseKind.RULE;
        NodeWrapper caseNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(caseTree, isArrow ? NodeTypes.CASE_ARROW : NodeTypes.CASE_COLON);
        methodState.putCfgNodeInCache(caseTree, caseNode);
        GraphUtils.connectWithParent(caseNode, t);
        caseNode.setProperty(HAS_DEFAULT_LABEL, false);
        scan(caseTree.getLabels(), Pair.createPair(caseNode, null));
        prevMust = must;
        int numberOfCases = (Integer) t.getSecond();
        boolean hasDefault = (Boolean) caseNode.getProperty(HAS_DEFAULT_LABEL);
        //Unconditional case if
        // Switch with colons, default case and no previous breaks
        // Any switch with one case with default label
        // Switch that requires full coverage with one case
        boolean isAUnconditionalDefault = numberOfCases == 1 &&
                ((Boolean) t.getFirst().getStartingNode().getProperty(REQUIRES_FULL_COVERAGE) || hasDefault) ||
                caseTree.getCaseKind() == CaseTree.CaseKind.STATEMENT && hasDefault && !anyBreak;
        must = prevMust && isAUnconditionalDefault;
        if (!isAUnconditionalDefault)
            pdgUtils.enteringNewBranch();
        scan(caseTree.getGuard(), Pair.createPair(caseNode, ASTRelationTypes.CASE_GUARD));
        scan(caseTree.getStatements(), Pair.createPair(caseNode, ASTRelationTypes.CASE_STATEMENT));
        scan(caseTree.getBody(), Pair.createPair(caseNode, ASTRelationTypes.CASE_BODY));
        must = prevMust;
        return isAUnconditionalDefault ? null : new VisitorResultImpl(pdgUtils.exitingCurrentBranch());

    }

    @Override
    public ASTVisitorResult visitCatch(CatchTree catchTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper catchNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(catchTree, NodeTypes.CATCH_CLAUSE);
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

    public static void typeSymbolPropsAndNestingLabels(ClassSymbol classSymbol, Set<Modifier> modifiers,
                                                       NodeWrapper typeDecNode) {
        if (classSymbol.getNestingKind() == NestingKind.TOP_LEVEL) {
            typeDecNode.addLabel(NestingTypes.TOP_LEVEL_TYPE);
            typeDecNode.setProperty(ACCESS_LEVEL_PROP, classSymbol.isPublic() ? PUBLIC_ACCESS : PACKAGE_ACCESS);
        } else {
            typeDecNode.addLabel(NestingTypes.NESTED_TYPE);
            typeDecNode.setProperty(IS_INNER, classSymbol.isInner());
            if (classSymbol.getNestingKind() == NestingKind.MEMBER) {
                typeDecNode.addLabel(NestingTypes.MEMBER_TYPE);
                setAccessLevel(classSymbol, modifiers, typeDecNode);
            } else {
                if (classSymbol.getNestingKind() == NestingKind.LOCAL) {
                    typeDecNode.addLabel(NestingTypes.LOCAL_TYPE);
                    typeDecNode.addLabel(NodeCategory.STATEMENT);
                } else
                    typeDecNode.addLabel(NestingTypes.ANONYMOUS_CLASS);
                typeDecNode.setProperty(ACCESS_LEVEL_PROP, PRIVATE_ACCESS);
            }
        }
        typeDecNode.setProperty("isSealed", classSymbol.isSealed());
        checkStrictfpMod(modifiers, typeDecNode);
        typeDecNode.setProperty(IS_ABSTRACT_PROP, classSymbol.isAbstract());
        checkFinalMod(classSymbol, typeDecNode);
    }

    public static void setNonDeclaredRecordMembers(ClassSymbol classSymbol, NodeWrapper recordNode,
                                                   ASTAuxiliarStorage ast) {
        classSymbol.getRecordComponents().forEach(componentSymbol -> {
            NodeWrapper componentNode = recordComponent(componentSymbol, recordNode, ast);
            DefinitionCache.COMPONENT_CACHE.get().put(new ComponentKey(componentSymbol), componentNode);
            componentNode.setProperty(IS_USER_CODE, false);
        });
    }

    public void setDeclaredRecord(ClassSymbol classSymbol, NodeWrapper recordNode, ClassTree classTree) {
        if (!DefinitionCache.TYPE_CACHE.get().containsKey(classSymbol.type.accept(KeyTypeVisitor.INSTANCE, null)))
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

        boolean isNested = !pair.getFirst().getStartingNode().hasLabel(NodeTypes.ORDINARY_CU);
        if (currentTypeDecSymbol.isInner())
            addClassAndDep(outerMostClass);
        if (isNested)
            currentCU.createRelationshipTo(typeNode, CDGRelationTypes.HAS_NESTED_TYPE);

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


        scanListWithPropertyIndex(classTree.getTypeParameters(), typeNode, ASTRelationTypes.GENERIC_TYPE_PARAM,
                RelationProperties.PARAM_INDEX);
        if (classTree.getTypeParameters().size() > 0)
            typeNode.addLabel(NodeTypes.GENERIC_TYPE_DEC);

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

    @Override
    public ASTVisitorResult visitCompilationUnit(CompilationUnitTree compilationUnitTree,
                                                 Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        //        System.out.println("Visiting CU: " + compilationUnitTree.getSourceFile().getName());
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
        assignmentNode.setProperty(OPERATOR, compoundAssignmentTree.getKind().toString());

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
    public ASTVisitorResult visitDeconstructionPattern(DeconstructionPatternTree recordPatternTree,
                                                       Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper recordPatternNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(recordPatternTree, NodeTypes.RECORD_PATTERN);
        GraphUtils.connectWithParent(recordPatternNode, t);
        scan(recordPatternTree.getDeconstructor(),
                Pair.createPair(recordPatternNode, ASTRelationTypes.RECORD_PATTERN_TYPE));
        scanListWithPropertyIndex(recordPatternTree.getNestedPatterns(), recordPatternNode,
                ASTRelationTypes.COMPONENT_PATTERN, "patternIndex");
        return null;
    }

    @Override
    public ASTVisitorResult visitAnyPattern(AnyPatternTree anyPatternTree,
                                            Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper anyPatternNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(anyPatternTree, NodeTypes.DISCARD_PATTERN);
        GraphUtils.connectWithParent(anyPatternNode, t);
        return null;
    }

    @Override
    public ASTVisitorResult visitBindingPattern(BindingPatternTree bindingPatternTree,
                                                Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        return visitVariable(bindingPatternTree.getVariable(), t);
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
        scan(enhancedForLoopTree.getVariable(),
                Pair.createPair(enhancedForLoopNode, ASTRelationTypes.FOREACH_VARIABLE));
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
            identifierNode.setProperty(NAME_PROP, identifierTree.getName().toString());
            GraphUtils.connectWithParent(identifierNode, t);
            System.err.println("Warning: Creating an IDENTIFIER with name %s without symbol (%s).".formatted(
                    identifierTree.getName().toString(), identifierTree.toString()));
            return null;
        }
        ElementKind idKind = idSymbol.getKind();
        boolean isThis = false;
        boolean needPDGAnalysis = false, fastPDGResult = false;
        if (idKind == ElementKind.PACKAGE) {
            identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(identifierTree, NodeTypes.PACKAGE_IDENTIFIER);
            fastPDGResult = true;
        } else {
            if (isASTType(idKind)) {
                if (idKind != ElementKind.TYPE_PARAMETER)
                    fastPDGResult = true;
                identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                        .createSkeletonNode(identifierTree, NodeTypes.TYPE_IDENTIFIER);
                astTypeRefersTo(identifierNode, idSymbol);
            } else {
                NodeTypes nodeType = NodeTypes.VARIABLE;
                if (idKind == ElementKind.METHOD)
                    nodeType = NodeTypes.METHOD_IDENTIFIER;
                else {
                    isThis = idSymbol.name.contentEquals("this") || idSymbol.name.contentEquals("super");
                    needPDGAnalysis = true;
                }
                identifierNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                        .createSkeletonNode(identifierTree, isThis ? NodeTypes.RESERVED_IDENTIFIER : nodeType);
                attachTypeDirect(identifierNode, identifierTree);
            }
        }
        identifierNode.setProperty(NAME_PROP, identifierTree.getName().toString());
        GraphUtils.connectWithParent(identifierNode, t);
        if (outsideAnnotation)
            return new VisitorResultImpl(needPDGAnalysis ?
                    pdgUtils.relationOnIdentifier(identifierTree, identifierNode, t, classState.currentClassDec,
                            methodState, isThis) : fastPDGResult);
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
        scan(instanceOfTree.getPattern(), Pair.createPair(instanceOfNode, ASTRelationTypes.INSTANCE_OF_PATTERN));
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
        labeledStatementNode.setProperty(NAME_PROP, labeledStatementTree.getLabel().toString());
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
        scanListWithPropertyIndex(lambdaExpressionTree.getParameters(), lambdaExpressionNode,
                ASTRelationTypes.LAMBDA_PARAM, RelationProperties.PARAM_INDEX);
        scan(lambdaExpressionTree.getBody(), Pair.createPair(lambdaExpressionNode, ASTRelationTypes.LAMBDA_BODY));

        if (lambdaExpressionTree.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT)
            CFGVisitor.doCFGAnalysis(lambdaExpressionNode, (BlockTree) lambdaExpressionTree.getBody(),
                    methodState.cfgNodeCache,
                    ASTAuxiliarStorage.getTrysToExceptionalPartialRelations(methodState.invocationsInStatements),
                    methodState.finallyCache);

        pdgUtils.exitingCurrentMethod();
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
        literalNode.setProperty(TYPE_TAG, literalTree.getKind().toString());
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
        memberReferenceNode.setProperty(NAME_PROP, memberReferenceTree.getName().toString());
        GraphUtils.connectWithParent(memberReferenceNode, t);
        attachTypeDirect(memberReferenceNode, memberReferenceTree);

        scan(memberReferenceTree.getQualifierExpression(),
                Pair.createPair(memberReferenceNode, ASTRelationTypes.CALLABLE_REFERENCE_QUALIFIER));

        if (memberReferenceTree.getTypeArguments() != null)
            scanListWithPropertyIndex(memberReferenceTree.getTypeArguments(), memberReferenceNode,
                    ASTRelationTypes.CALLABLE_REFERENCE_TYPE_ARG, RelationProperties.ARGUMENT_INDEX);
        return null;
    }

    @Override
    public ASTVisitorResult visitMemberSelect(MemberSelectTree memberSelectTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper memberSelectNode;
        Symbol memberSymbol = ((JCFieldAccess) memberSelectTree).sym;
        ElementKind idKind = memberSymbol.getKind();
        boolean fieldOrEnum = false, isThis = false;
        if (idKind == ElementKind.PACKAGE)
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, NodeTypes.PACKAGE_SELECTION);
        else if (isASTType(idKind)) {
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, NodeTypes.TYPE_SELECTION);
            astTypeRefersTo(memberSelectNode, memberSymbol);
        } else {
            NodeTypes nodeType = NodeTypes.ATTR_SELECTION;
            if (idKind == ElementKind.METHOD)
                nodeType = NodeTypes.METHOD_SELECTION;
            else
                isThis = memberSelectTree.getIdentifier().contentEquals("this") ||
                        memberSelectTree.getIdentifier().contentEquals("super");
            memberSelectNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNode(memberSelectTree, isThis ? NodeTypes.RESERVED_SELECTION : nodeType);
            fieldOrEnum = idKind == ElementKind.FIELD || idKind == ElementKind.ENUM_CONSTANT;
            attachTypeDirect(memberSelectNode, memberSelectTree);
        }

        memberSelectNode.setProperty(SELECTED_NAME, memberSelectTree.getIdentifier().toString());
        GraphUtils.connectWithParent(memberSelectNode, t);

        ASTVisitorResult memberSelResult = scan(memberSelectTree.getExpression(),
                Pair.createPair(memberSelectNode, ASTRelationTypes.IDENT_SELECTION_FROM,
                        PDGProcessing.modifiedToStateModified(t)));
        if (outsideAnnotation) {
            boolean isInstance = memberSelResult != null && !memberSymbol.isStatic() && memberSelResult.isInstance();
            if (fieldOrEnum) {
                if (memberSelectTree.getIdentifier().toString().contentEquals("class"))
                    isInstance = false;
                else
                    pdgUtils.relationOnFieldAccess(isThis, memberSelectTree, memberSelectNode, t, methodState,
                            classState.currentClassDec, isInstance);
            }
            memberSelResult = new VisitorResultImpl(isInstance);

        }
        return memberSelResult;
    }

    public void addToTypeDependencies(NodeWrapper newTypeDec, Symbol.PackageSymbol newPackageSymbol) {
        addToTypeDependencies(classState.currentClassDec, newTypeDec, newPackageSymbol, typeDecUses,
                PackageManager.PACKAGE_MANAGER.get().currentPackage);
    }

    public static void addToTypeDependencies(NodeWrapper currentClass, NodeWrapper newTypeDec,
                                             Symbol.PackageSymbol newPackageSymbol, Set<NodeWrapper> typeDecUses,
                                             Symbol.PackageSymbol dependentPackage) {
        if (!typeDecUses.contains(newTypeDec) && !currentClass.equals(newTypeDec)) {
            PackageManager.PACKAGE_MANAGER.get().handleNewDependency(dependentPackage, newPackageSymbol);
            currentClass.createRelationshipTo(newTypeDec, CDGRelationTypes.USES_TYPE_DEC);
            typeDecUses.add(newTypeDec);
        }
    }

    @Override
    public ASTVisitorResult visitMethod(MethodTree methodTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        MethodSymbol methodSymbol = ((JCMethodDecl) methodTree).sym;
        CallableKey callableKey = new CallableKey(methodSymbol);
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
            ast.addMethodNodeSymbol(methodSymbol, methodNode);
            rel = ASTRelationTypes.DECLARES_METHOD;
        }

        methodNode.setProperty(IS_USER_CODE, isUserCode);
        boolean alreadyHasType = false;
        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(callableKey)) {
            ast.deleteAccessibleMethod(methodSymbol);
            ast.removeMethodNodeSymbol(DefinitionCache.CALLABLE_DEC_CACHE.get().get(callableKey));
            DefinitionCache.CALLABLE_DEC_CACHE.get().putDefinition(callableKey, methodNode);
            alreadyHasType = methodNode.hasRelationship(TypeRelations.ITS_TYPE_IS, Direction.OUTGOING);
            if (!methodNode.hasRelationship(rel, Direction.INCOMING))
                GraphUtils.connectWithParent(methodNode, t, rel);
        } else {
            DefinitionCache.CALLABLE_DEC_CACHE.get().putDefinition(callableKey, methodNode);
            GraphUtils.connectWithParent(methodNode, t, rel);
        }

        declaredCallablePropsAndAnnotations(methodSymbol, methodTree.getModifiers(), methodNode, callableKey);

        boolean prevIsInAccesibleCtxt = isInAccessibleContext;
        isInAccessibleContext = false;
        MethodState prevState = methodState;
        must = true;
        methodState = new MethodState(methodNode);
        pdgUtils.visitNewMethod();
        ast.newMethodDeclaration(methodState);
        if (!isConstructor)
            scan(methodTree.getReturnType(), Pair.createPair(methodNode, ASTRelationTypes.METHOD_RETURN_TYPE));
        Type methodType = ((JCMethodDecl) methodTree).type;
        if (alreadyHasType)
            GraphUtils.attachTypeProperties(methodNode, methodType);
        else
            GraphUtils.attachType(methodNode, methodType, ast);

        scanListWithPropertyIndex(methodTree.getTypeParameters(), methodNode, ASTRelationTypes.CALLABLE_TYPE_PARAM,
                RelationProperties.PARAM_INDEX);
        scanListWithPropertyIndex(methodTree.getParameters(), methodNode, ASTRelationTypes.CALLABLE_PARAM,
                RelationProperties.PARAM_INDEX);

        methodTree.getThrows().forEach(
                (throwsTree) -> scan(throwsTree, Pair.createPair(methodNode, ASTRelationTypes.CALLABLE_THROWS)));

        scan(methodTree.getBody(), Pair.createPair(methodNode, ASTRelationTypes.CALLABLE_BODY));
        scan(methodTree.getDefaultValue(), Pair.createPair(methodNode, ASTRelationTypes.METHOD_DEFAULT_VALUE));
        scan(methodTree.getReceiverParameter(), Pair.createPair(methodNode, ASTRelationTypes.CALLABLE_RECEIVER_PARAM));

        pdgUtils.setThisRefOfInstanceMethod(methodState, classState.currentClassDec);
        ast.addInfo(methodTree, methodNode, methodState,
                methodSymbol.isVarArgs() ? methodTree.getParameters().size() : ASTAuxiliarStorage.NO_VARG_ARG);
        if (methodTree.getBody() != null)
            CFGVisitor.doCFGAnalysis(methodNode, methodTree.getBody(), methodState.cfgNodeCache,
                    ASTAuxiliarStorage.getTrysToExceptionalPartialRelations(methodState.invocationsInStatements),
                    methodState.finallyCache);
        pdgUtils.exitingCurrentMethod();
        insideConstructor = prev;
        isInAccessibleContext = prevIsInAccesibleCtxt;
        must = true;
        methodState = prevState;
        ast.endMethodDeclaration();
        return null;
    }

    @Override
    public ASTVisitorResult visitMethodInvocation(MethodInvocationTree methodInvocationTree,
                                                  Pair<PartialRelation<RelationTypesInterface>, Object> pair) {
        Symbol symbol = JavacInfo.getSymbolFromTree(methodInvocationTree.getMethodSelect());
        NodeWrapper decNode;
        NodeWrapper methodInvocationNode;
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
                //Save symbol.owner.toString() and appropriate constructors if needed
                setCallableJustSymbolProps(symbol, decNode, new CallableKey(methodName, symbol.owner));
            }

            methodInvocationNode = createInvocationNodeFromDec(methodInvocationTree, decNode);
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

            methodInvocationNode = createInvocationNodeFromDec(methodInvocationTree, decNode);
            ast.checkIfTrustableInvocation(methodInvocationTree, methodSymbol, methodInvocationNode);
        }

        attachTypeDirect(methodInvocationNode, methodInvocationTree);
        GraphUtils.connectWithParent(methodInvocationNode, pair);

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

        scanListWithPropertyIndex(methodInvocationTree.getTypeArguments(), methodInvocationNode,
                ASTRelationTypes.INVOCATION_TYPE_ARG, RelationProperties.ARGUMENT_INDEX);
        scanListWithPropertyIndex(methodInvocationTree.getArguments(), methodInvocationNode,
                ASTRelationTypes.INVOCATION_ARG, RelationProperties.ARGUMENT_INDEX);
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
        node.setProperty(IS_TRANSIENT, modifiers.contains(Modifier.TRANSIENT));
    }

    public static void checkSynchroMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty(IS_SYNCHRONIZED_PROP, modifiers.contains(Modifier.SYNCHRONIZED));
    }

    public static void checkNativeMod(Set<Modifier> modifiers, NodeWrapper node) {
        node.setProperty(IS_NATIVE_PROP, modifiers.contains(Modifier.NATIVE));
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
        scanListWithPropertyIndex(newClassTree.getTypeArguments(), newClassNode, ASTRelationTypes.NEW_INSTANCE_TYPE_ARG,
                RelationProperties.ARGUMENT_INDEX);

        scanListWithPropertyIndex(newClassTree.getArguments(), newClassNode, ASTRelationTypes.NEW_INSTANCE_ARG,
                RelationProperties.ARGUMENT_INDEX);
        newClassNode.createRelationshipTo(constructorDef, CGRelationTypes.CALLEE);
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
        NodeWrapper parameterizedNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNodeExplicitCats(parameterizedTypeTree, NodeTypes.PARAMETERIZED_TYPE,
                        NodeCategory.AST_TYPE, NodeCategory.AST_NODE);
        GraphUtils.connectWithParent(parameterizedNode, t);
        scan(parameterizedTypeTree.getType(),
                Pair.createPair(parameterizedNode, ASTRelationTypes.PARAMETERIZES_AST_TYPE));
        scanListWithPropertyIndex(parameterizedTypeTree.getTypeArguments(), parameterizedNode,
                ASTRelationTypes.AST_TYPE_ARG, RelationProperties.ARGUMENT_INDEX);
        parameterizedNode.setProperty(ACTUAL_TYPE,
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
                .createSkeletonNodeExplicitCats(primitiveTypeTree,
                        primitiveTypeTree.getPrimitiveTypeKind() == TypeKind.VOID ? NodeTypes.VOID_TYPE :
                                NodeTypes.PRIMITIVE_TYPE, NodeCategory.AST_TYPE, NodeCategory.AST_NODE);
        primitiveTypeNode.setProperty(FULL_NAME, primitiveTypeTree.toString());
        primitiveTypeNode.setProperty(SIMPLE_NAME, primitiveTypeTree.toString());
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

    @Override
    public ASTVisitorResult visitSwitch(SwitchTree switchTree,
                                        Pair<PartialRelation<RelationTypesInterface>, Object> t) {

        NodeWrapper switchNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(switchTree, NodeTypes.SWITCH_STATEMENT);
        GraphUtils.connectWithParent(switchNode, t);
        scan(switchTree.getExpression(), Pair.createPair(switchNode, ASTRelationTypes.SWITCH_SELECTOR));
        addInvocationInStatement(switchNode);
        methodState.putCfgNodeInCache(switchTree, switchNode);
        if (switchTree.getCases().size() == 0)
            switchNode.setProperty(NodeProperties.REQUIRES_FULL_COVERAGE, false);
        else {
            visitCases(switchTree.getCases(), switchNode, switchTree.getCases().stream().anyMatch(
                    c -> c.getLabels().stream().anyMatch(l -> l.getKind() == Kind.PATTERN_CASE_LABEL ||
                            l.getKind() == Kind.CONSTANT_CASE_LABEL && (l instanceof ConstantCaseLabelTree clabel &&
                                    clabel.getConstantExpression().getKind() == Kind.NULL_LITERAL))));
        }
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
        visitCases(switchExpressionTree.getCases(), switchNode, true);
        return null;
    }

    @Override
    public ASTVisitorResult visitSynchronized(SynchronizedTree synchronizedTree,
                                              Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        NodeWrapper synchronizedNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createSkeletonNode(synchronizedTree, NodeTypes.SYNCHRONIZED_STATEMENT);
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
        typeParameterNode.addLabel(NodeCategory.AST_NODE);
        typeParameterNode.setProperty(IS_USER_CODE, true);
        typeParameterNode.setProperty(NAME_PROP, typeParameterTree.getName().toString());
        GraphUtils.connectWithParent(typeParameterNode, t);
        scan(typeParameterTree.getAnnotations(), Pair.createPair(typeParameterNode, ASTRelationTypes.HAS_ANNOTATION));
        scan(typeParameterTree.getBounds(),
                Pair.createPair(typeParameterNode, ASTRelationTypes.AST_TYPE_PARAM_EXTENDS));
        return null;
    }

    @Override
    public ASTVisitorResult visitUnary(UnaryTree unaryTree, Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        boolean impliesModification =
                unaryTree.getKind() == Kind.POSTFIX_INCREMENT || unaryTree.getKind() == Kind.POSTFIX_DECREMENT ||
                        unaryTree.getKind() == Kind.PREFIX_INCREMENT || unaryTree.getKind() == Kind.PREFIX_DECREMENT;
        NodeWrapper unaryNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(unaryTree,
                impliesModification ? NodeTypes.UNARY_ASSIGNMENT : NodeTypes.UNARY_OPERATION);
        unaryNode.setProperty(OPERATOR, unaryTree.getKind().toString());
        GraphUtils.connectWithParent(unaryNode, t);
        attachTypeDirect(unaryNode, unaryTree);
        if (impliesModification) {
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

    @Override
    public ASTVisitorResult visitVariable(VariableTree variableTree,
                                          Pair<PartialRelation<RelationTypesInterface>, Object> t) {
        boolean isAttrOrEnum = t.getFirst().getRelationType().equals(ASTRelationTypes.TYPE_STATIC_INIT);
        boolean isLocalVarStmt = false;
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
        } else if (varSymbol.getKind() == ElementKind.BINDING_VARIABLE)
            variableNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                    .createSkeletonNodeExplicitCats(variableTree, NodeTypes.TYPE_PATTERN);
        else
            variableNode = DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(variableTree,
                    (isLocalVarStmt = varSymbol.getKind() != ElementKind.PARAMETER) ? NodeTypes.LOCAL_VAR_DEC :
                            NodeTypes.PARAMETER_DEC);


        variableNode.setProperty(NAME_PROP, variableTree.getName().toString());

        NodeWrapper varTypeNode = processVarType(variableTree, variableNode);

        ModifiersTree modifiers = variableTree.getModifiers();
        if (isAttrOrEnum)
            setAttrDecModifiers(varSymbol, modifiers.getFlags(), variableNode);
        else
            checkFinalMod(varSymbol, variableNode);

        scan(modifiers.getAnnotations(), Pair.createPair(variableNode, null));
        MethodState previousState = methodState;
        VarKey varKey = null;
        if (isAttrOrEnum) {
            variableNode.setProperty(IS_USER_CODE, !isImplicitField);
            GraphUtils.connectWithParent(variableNode, t,
                    isEnum ? ASTRelationTypes.ENUM_DECLARES_ELEMENT : ASTRelationTypes.DECLARES_FIELD);
            methodState = new MethodState(variableNode);
            Pair<List<NodeWrapper>, List<NodeWrapper>> param =
                    ((Pair<Pair<List<NodeWrapper>, List<NodeWrapper>>, List<NodeWrapper>>) t.getSecond()).getFirst();
            (varSymbol.isStatic() ? param.getSecond() : param.getFirst()).add(methodState.lastMethodDecVisited);
            varKey = new FieldKey(varSymbol);

        } else {
            GraphUtils.connectWithParent(variableNode, t);
            varKey = new LocalScopeVarKey(varSymbol);
        }
        NodeWrapper initNode =
                createVarInit(variableTree, variableNode, isAttrOrEnum, varSymbol.isStatic(), varTypeNode);
        if (isEnum && !varSymbol.owner.isFinal())
            processEnumElementMembers(variableNode, initNode);
        if (isLocalVarStmt) {
            methodState.putCfgNodeInCache(variableTree, variableNode);
            addInvocationInStatement(variableNode);
        }
        varKey.putDecInCache(pdgUtils, variableNode);
        if (isAttrOrEnum) {
            methodState = previousState;
        }
        return null;
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
        scan(wildcardTree.getBound(), Pair.createPair(wildcardNode, ASTRelationTypes.AST_WILDCARD_BOUND));
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

    static NodeWrapper getCallableDuringTypeCreation(MethodSymbol symbol, ASTAuxiliarStorage ast, NodeWrapper typeDec) {
        return getNonDeclaredCallable(symbol, typeDec, ast, false);
    }

    private static NodeWrapper createInvocationNodeFromDec(MethodInvocationTree methodInvocationTree,
                                                           NodeWrapper decNode) {
        return DatabaseFacade.CURRENT_DB_FACADE.get().createSkeletonNode(methodInvocationTree,
                decNode.hasLabel(NodeTypes.METHOD_DEC) ? NodeTypes.METHOD_INVOCATION : NodeTypes.RESERVED_INVOCATION);
    }

    private static NodeWrapper createAndLinkNonDeclaredCallable(NodeWrapper classNode, MethodSymbol symbol,
                                                                ASTAuxiliarStorage ast, boolean needType) {
        NodeWrapper callable = createAndLinkNonDeclaredCallable(classNode, symbol.isConstructor());
        if(!symbol.isConstructor())
            ast.addMethodNodeSymbol(symbol, callable);
        if(needType)
            GraphUtils.attachType(callable, symbol.type, ast);
        for (Symbol.TypeVariableSymbol typeSymbol : symbol.getTypeParameters()) {
            callable.createRelationshipTo(ast.getTypeVisitor().createTypeParameterNode((TypeVariable) typeSymbol.type),
                    ASTRelationTypes.GENERIC_TYPE_PARAM);
        }
        return callable;
    }

    private NodeWrapper addInvocationInStatement(NodeWrapper statement) {
        ast.addInvocationInStatement(statement, currentMethodInvocations);
        currentMethodInvocations = new ArrayList<>();
        return statement;
    }

    private NodeWrapper getCallableDecFromCall(MethodSymbol symbol) {
        return getNonDeclaredCallable(symbol, DefinitionCache.getOrCreateType(symbol.owner.type, ast), ast, true);
    }

    private static NodeWrapper getNonDeclaredCallable(MethodSymbol symbol, NodeWrapper typeDec,
                                                      ASTAuxiliarStorage ast, boolean needType) {
        CallableKey callableKey = new CallableKey(symbol);
        NodeWrapper methodDecNode;
        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(callableKey)) {
            methodDecNode = DefinitionCache.CALLABLE_DEC_CACHE.get().get(callableKey);
            if(needType && !methodDecNode.hasRelationship(TypeRelations.ITS_TYPE_IS, Direction.OUTGOING))
                GraphUtils.attachType(methodDecNode, symbol.type, ast);
            return methodDecNode;
        }
        methodDecNode = createAndLinkNonDeclaredCallable(typeDec, symbol, ast, needType);
        setCallableMethodSymbolProps(symbol, methodDecNode);
        if (!symbol.isConstructor())
            ast.addAccessibleMethod(symbol, methodDecNode);
        DefinitionCache.CALLABLE_DEC_CACHE.get().put(callableKey, methodDecNode);
        return methodDecNode;
    }

    private static NodeWrapper createNonDeclaredCallable(boolean isConstructor) {
        NodeWrapper methodDecNode = DatabaseFacade.CURRENT_DB_FACADE.get()
                .createNodeWithoutExplicitTree(isConstructor ? NodeTypes.CONSTRUCTOR_DEC : NodeTypes.METHOD_DEC);
        methodDecNode.setProperty(IS_USER_CODE, false);
        return methodDecNode;
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

    private void typeSymbolInfoAndAnnotations(ClassSymbol symbol, ModifiersTree modifiersTree, NodeWrapper typeNode) {
        scan(modifiersTree.getAnnotations(), Pair.createPair(typeNode, ASTRelationTypes.HAS_ANNOTATION));
        typeSymbolPropsAndNestingLabels(symbol, modifiersTree.getFlags(), typeNode);
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

        componentNode.setProperties(new Object[]{NAME_PROP, componentSymbol.getSimpleName().toString()});
        GraphUtils.attachType(componentNode, componentSymbol.type, ast);

        componentNode.createRelationshipTo(getCallableDuringTypeCreation(componentSymbol.accessor, ast, recordNode),
                ASTRelationTypes.COMPONENT_ACCESSOR);

        return componentNode;
    }

    private void declaredRecordComponent(Symbol.RecordComponent componentSymbol, NodeWrapper recordNode,
                                         NodeWrapper fieldNode) {
        NodeWrapper componentNode =
                DefinitionCache.COMPONENT_CACHE.get().containsKey(new ComponentKey(componentSymbol)) ?
                        DefinitionCache.COMPONENT_CACHE.get().get(new ComponentKey(componentSymbol)) :
                        recordComponent(componentSymbol, recordNode, ast);
        componentNode.setProperty(IS_USER_CODE, true);
        componentNode.addLabel(NodeCategory.AST_NODE);
        componentNode.setProperties(JavacInfo.getPosition(recordNode));

        //        PartialWithEnd componentTypeRel = new PartialWithEnd<>(componentNode, ASTRelationTypes
        //        .COMPONENT_TYPE);
        //        componentSymbol.declarationFor().getType().accept(this, Pair.createPair(componentTypeRel));
        //        componentTypeRel.getEndNode().setProperties(JavacInfo.getPosition(recordNode));
        DefinitionCache.COMPONENT_CACHE.get().updateToDefinition(new ComponentKey(componentSymbol), componentNode);

        componentNode.createRelationshipTo(fieldNode, ASTRelationTypes.COMPONENT_FIELD);
    }

    private static void callsFromVarDecToConstructor(NodeWrapper attr, NodeWrapper constructor) {
        for (RelationshipWrapper r : attr.getRelationships(Direction.OUTGOING, CGRelationTypes.CALLS)) {
            RelationshipWrapper callRelation = constructor.createRelationshipTo(r.getEndNode(), CGRelationTypes.CALLS);
            callRelation.setProperty("mustBeExecuted", r.getProperty("mustBeExecuted"));
            r.delete();
        }
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

    private void declaredCallablePropsAndAnnotations(MethodSymbol methodSymbol, ModifiersTree modifiers,
                                                     NodeWrapper methodNode, CallableKey nameInfo) {
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

    private static void setMethodNames(NodeWrapper callableNode, CallableKey nameInfo) {
        callableNode.setProperty(NAME_PROP, nameInfo.getSimpleName());
        callableNode.setProperty(COMPLETE_NAME, nameInfo.getCompleteName());
        callableNode.setProperty(FULL_NAME, nameInfo.getFullyQualifiedName());
    }

    private static void setCallableJustSymbolProps(Symbol symbol, NodeWrapper callableNode, CallableKey nameInfo) {
        setCallableCommonProps(callableNode, symbol, symbol.getModifiers(), nameInfo, false);
    }

    private static void setCallableCommonProps(NodeWrapper callableNode, Symbol symbol, Set<Modifier> modifiers,
                                               CallableKey nameInfo, boolean isDefault) {
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
            boolean isAbstract = symbol.isAbstract();
            callableNode.setProperty(IS_ABSTRACT_PROP, isAbstract);
            setFPSynchroNative(modifiers, callableNode, isAbstract);
        }
    }

    private static void setCallableMethodSymbolProps(MethodSymbol methodSymbol, NodeWrapper callableNode) {
        setCallableMethodSymbolProps(methodSymbol, methodSymbol.getModifiers(), callableNode,
                new CallableKey(methodSymbol));
    }

    private static void setCallableMethodSymbolProps(MethodSymbol methodSymbol, Set<Modifier> modifiers,
                                                     NodeWrapper callableNode, CallableKey nameInfo) {
        callableNode.setProperty("isVarArgs", methodSymbol.isVarArgs());
        setCallableCommonProps(callableNode, methodSymbol, modifiers, nameInfo, methodSymbol.isDefault());
    }

    private void addClassAndDep(Symbol symbol) {
        NodeWrapper newTypeDec = DefinitionCache.getOrCreateType(symbol.type, ast);
        addToTypeDependencies(newTypeDec, symbol.packge());
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

    private NodeWrapper createDynamicallyGenConstructor(Symbol newClassConstructor) {
        CallableKey callableKey = new CallableKey("<init>", newClassConstructor.owner);
        if (DefinitionCache.CALLABLE_DEC_CACHE.get().containsKey(callableKey))
            return DefinitionCache.CALLABLE_DEC_CACHE.get().get(callableKey);
        NodeWrapper generatedClassNode =
                TypeVisitor.generatedClassType(callableKey.getOwnerKey(), (ClassSymbol) newClassConstructor.owner, ast);

        if (!typeDecUses.contains(generatedClassNode)) {
            classState.currentClassDec.createRelationshipTo(generatedClassNode, CDGRelationTypes.USES_TYPE_DEC);
            typeDecUses.add(generatedClassNode);
        }
        NodeWrapper constructorDef = createAndLinkNonDeclaredCallable(generatedClassNode, true);
        setCallableJustSymbolProps(newClassConstructor, constructorDef, callableKey);
        return constructorDef;
    }

    private void scanListWithPropertyIndex(List<? extends Tree> childList, NodeWrapper parentNode,
                                           ASTRelationTypes relationType, String propertyName) {
        for (int i = 0; i < childList.size(); i++)
            scan(childList.get(i), Pair.createPair(
                    new PartialRelationWithProperties<>(parentNode, relationType, propertyName, i + 1)));
    }

    private void visitCases(List<? extends CaseTree> cases, NodeWrapper switchNode, boolean requiresFullCoverage) {

        switchNode.setProperty(NodeProperties.REQUIRES_FULL_COVERAGE, requiresFullCoverage);
        switchNode.setProperty(NodeProperties.IS_ARROW_SWITCH, cases.get(0).getCaseKind() == CaseTree.CaseKind.RULE);

        ASTVisitorResult caseResult = visitCase(cases.get(0), Pair.create(
                new PartialRelationWithProperties<>(switchNode, ASTRelationTypes.SWITCH_CASE,
                        RelationProperties.CASE_INDEX, 1), cases.size()));
        Set<NodeWrapper> paramsModifiedInAllCases =
                caseResult == null ? new HashSet<>() : caseResult.paramsPreviouslyModifiedForSwitch();
        boolean unconditionalFound = caseResult == null;
        for (int i = 1; i < cases.size(); i++) {
            caseResult = scan(cases.get(i), Pair.create(
                    new PartialRelationWithProperties<>(switchNode, ASTRelationTypes.SWITCH_CASE,
                            RelationProperties.CASE_INDEX, i + 1), cases.size()));
            if (caseResult != null)
                paramsModifiedInAllCases.retainAll(caseResult.paramsPreviouslyModifiedForSwitch());
            else
                unconditionalFound = true;
        }
        //If at least one case is mandatory, and no unconditional case found (since this one would dominate de params
        // modified with no branches created)
        //When at least one case is mandatory??
        // Any switch with a default case
        //Any switch that requires full coverage
        //Then, the common params modified in all cases are added to the params must be modified SET
        if (!unconditionalFound && (requiresFullCoverage ||
                cases.getLast().getLabels().getLast().getKind() == Tree.Kind.DEFAULT_CASE_LABEL))
            pdgUtils.unionWithCurrent(paramsModifiedInAllCases);

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
                r.setProperty(RelationProperties.IS_OWN_ACCESS, true);
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
            ASTVarType.setProperty(SIMPLE_NAME, VAR_NAME);
            ASTVarType.setProperty(FULL_NAME, VAR_NAME);
        } else
            scan(variableTree.getType(), Pair.createPair(varDecNode, ASTRelationTypes.VAR_DEC_TYPE));
        return varTypeNode;
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
}
