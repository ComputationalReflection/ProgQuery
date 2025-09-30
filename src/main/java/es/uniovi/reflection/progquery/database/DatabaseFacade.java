package es.uniovi.reflection.progquery.database;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.tree.JCTree;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.database.nodes.NodeCategory;
import es.uniovi.reflection.progquery.database.nodes.NodeProperties;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.ASTRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PartialWithEnd;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.WrapperUtils;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.lang.model.element.ElementKind;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.*;

public class DatabaseFacade {
    private final InsertionStrategy insertionStrategy;
    public static InsertionStrategy CURRENT_INSERTION_STRATEGY;

    public static ThreadLocal<DatabaseFacade> CURRENT_DB_FACADE = new ThreadLocal<>();

    public static void init(InsertionStrategy current) {
        CURRENT_INSERTION_STRATEGY = current;
        CURRENT_DB_FACADE.set(new DatabaseFacade(current));
    }

    public DatabaseFacade(InsertionStrategy insertionStrategy) {
        this.insertionStrategy = insertionStrategy;
    }

    public NodeWrapper createNodeWithoutExplicitTree(NodeTypes type) {
        return createNode(type, new Object[]{});
    }

    public NodeWrapper createNode(NodeTypes type, Object[] properties) {
        NodeWrapper node = insertionStrategy.createNode(type, properties);
        addMultiLabelHypernyms(node, type);
        return node;
    }

    private static void addMultiLabelHypernyms(NodeWrapper node, NodeTypes type) {
        for (NodeCategory nodeCategory : type.hypernyms)
            node.addLabel(nodeCategory);
    }

    private static final int IMPLICIT_POSITION = -1;

    public NodeWrapper createSkeletonNode(Tree tree, NodeTypes nodeType) {
        NodeWrapper node = createNodeWithoutExplicitTree(nodeType);

        node.setProperties(((JCTree) tree).pos == IMPLICIT_POSITION ?
                new Object[]{LINE_NUMBER, IMPLICIT_POSITION, "column", IMPLICIT_POSITION, "position",
                        IMPLICIT_POSITION} : getPosition(tree));
        return node;
    }

    public NodeWrapper createSkeletonNodeExplicitCats(Tree tree, NodeTypes nodeType, NodeCategory... cats) {
        NodeWrapper node = createSkeletonNode(tree, nodeType);
        for (NodeCategory cat : cats)
            node.addLabel(cat);
        return node;
    }

    public static Object[] getPosition(Tree tree) {
        return JavacInfo.getPosition(tree);
    }

    private static Object[] join(Object[] a1, Object[] a2) {
        Object[] res = new Object[a1.length + a2.length];
        int i = 0;
        for (Object o : a1)
            res[i++] = o;
        for (Object o : a2)
            res[i++] = o;
        return res;
    }

    public static Object[] getTypeProperties(String fullyQualifiedType) {
        return new Object[]{NodeProperties.FULL_NAME, WrapperUtils.stringToNeo4jQueryString(fullyQualifiedType)};
    }

    public static Object[] getTypeProperties(String simpleName, String fullyQualifiedType) {
        return join(getTypeProperties(fullyQualifiedType),
                new Object[]{SIMPLE_NAME, WrapperUtils.stringToNeo4jQueryString(simpleName)});
    }

    public static Object[] getTypeDecProperties(String simpleName, String fullyQualifiedType, boolean isUserCode) {
        return join(getTypeProperties(simpleName, fullyQualifiedType),
                new Object[]{IS_USER_CODE, isUserCode});
    }

    public NodeWrapper createTypeDecNode(ClassSymbol symbol, String simpleName, String fullyQualifiedName,
                                         boolean isUserCode) {
        NodeWrapper typeDef = createNode(symbol.getKind() == ElementKind.CLASS ? NodeTypes.CLASS_DEC :
                        symbol.getKind() == ElementKind.INTERFACE ? NodeTypes.INTERFACE_DEC :
                                symbol.getKind() == ElementKind.ENUM ? NodeTypes.ENUM_DEC :
                                        symbol.getKind() == ElementKind.RECORD ? NodeTypes.RECORD_DEC :
                                                NodeTypes.ANNOTATION_DEC,
                getTypeDecProperties(simpleName, fullyQualifiedName, isUserCode));
        return typeDef;
    }

    private void setRecordMembers(ClassSymbol symbol, NodeWrapper typeDef, ClassTree classTree,
                                  ASTTypesVisitor astVisitor, ASTAuxiliarStorage ast) {
        symbol.getRecordComponents().forEach(recordComponent -> {
            NodeWrapper recordComponentNode;
            if (classTree != null) {
                recordComponentNode =
                        createSkeletonNodeExplicitCats(classTree, NodeTypes.RECORD_COMPONENT, NodeCategory.AST_NODE);
                PartialWithEnd componentTypeRel =
                        new PartialWithEnd<>(recordComponentNode, ASTRelationTypes.COMPONENT_TYPE);
                recordComponent.declarationFor().accept(astVisitor, Pair.createPair(componentTypeRel));
                componentTypeRel.getEndNode().setProperties(getPosition(classTree));
            } else {
                recordComponentNode = createNodeWithoutExplicitTree(NodeTypes.RECORD_COMPONENT);
            }
            recordComponentNode.setProperties(new Object[]{"name", recordComponent.getSimpleName().toString()});
            GraphUtils.attachType(recordComponentNode, recordComponent.type, astVisitor.ast);
        });
        //ASTTypesVisitor.getCallableDuringTypeCreation((Symbol.MethodSymbol) elementSymbol, ast, declaredType);
    }

    public NodeWrapper createUserTypeDecNode(ClassTree classTree, ClassSymbol symbol) {
        String simpleName = classTree.getSimpleName().toString();
        String fullyQualifiedType = symbol.toString();
        if (simpleName.equals("")) {
            String[] split = fullyQualifiedType.split(fullyQualifiedType.contains(".") ? "\\." : " ");
            simpleName = split[split.length - 1];
            simpleName = simpleName.substring(0, simpleName.length() - 1);
        }
        NodeWrapper typeDef = createTypeDecNode(symbol, simpleName, fullyQualifiedType, true);
        typeDef.addLabel(NodeCategory.AST_NODE);
        typeDef.setProperties(getPosition(classTree));
        return typeDef;
    }

    public NodeWrapper createExternalTypeDecNode(ClassType c) {
        return createExternalTypeDecNode((ClassSymbol) c.tsym);

    }

    public NodeWrapper createExternalTypeDecNode(ClassSymbol symbol) {
        NodeWrapper typeDecNode =
                createTypeDecNode(symbol, symbol.getSimpleName().toString(), symbol.getQualifiedName().toString(),
                        false);
        ASTTypesVisitor.typeSymbolPropsAndNestingLabels(symbol, symbol.getModifiers(), typeDecNode);
        return typeDecNode;
    }
}
