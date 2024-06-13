package es.uniovi.reflection.progquery.database;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.tree.JCTree;
import es.uniovi.reflection.progquery.database.nodes.NodeCategory;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.WrapperUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.lang.model.element.ElementKind;

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
                new Object[]{"lineNumber", IMPLICIT_POSITION, "column", IMPLICIT_POSITION, "position",
                        IMPLICIT_POSITION} : getPosition(tree));
        return node;
    }

    public NodeWrapper createSkeletonNodeExplicitCats(Tree tree, NodeTypes nodeType, NodeCategory... cats) {
        NodeWrapper node = createSkeletonNode(tree, nodeType);
        for (NodeCategory cat : cats)
            node.addLabel(cat);
        return node;
    }

    private static Object[] getPosition(Tree tree) {
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

    public static Object[] getTypeDecProperties(String fullyQualifiedType) {
        return new Object[]{"fullyQualifiedName", WrapperUtils.stringToNeo4jQueryString(fullyQualifiedType)};
    }

    public static Object[] getTypeDecProperties(String simpleName, String fullyQualifiedType) {
        return join(getTypeDecProperties(fullyQualifiedType),
                new Object[]{"simpleName", WrapperUtils.stringToNeo4jQueryString(simpleName)});
    }

    public static Object[] getTypeDecProperties(String simpleName, String fullyQualifiedType, boolean isUserCode) {
        return join(getTypeDecProperties(simpleName, fullyQualifiedType),
                new Object[]{ASTTypesVisitor.IS_USER_CODE_PROP, isUserCode});
    }


    private static Object[] getTypeDecProperties(ClassSymbol symbol, boolean isUserCode) {
        return getTypeDecProperties(symbol.getSimpleName().toString(), symbol.getQualifiedName().toString(),
                isUserCode);

    }

    public NodeWrapper createTypeDecNode(ClassSymbol symbol, boolean isUserCode) {
        NodeWrapper typeDef = createNode(symbol.getKind() == ElementKind.CLASS ? NodeTypes.CLASS_DEC :
                        symbol.getKind() == ElementKind.INTERFACE ? NodeTypes.INTERFACE_DEC :
                                symbol.getKind() == ElementKind.ENUM ? NodeTypes.ENUM_DEC : NodeTypes.ANNOTATION_DEC,
                getTypeDecProperties(symbol, isUserCode));
        return typeDef;
    }

    public NodeWrapper createUserTypeDecNode(ClassTree classTree, ClassSymbol symbol){
        NodeWrapper typeDef = createTypeDecNode(symbol, true);
        typeDef.addLabel(NodeCategory.AST_NODE);
        typeDef.setProperties(getPosition(classTree));
        return typeDef;
    }
     public NodeWrapper createExternalTypeDecNode(ClassType c) {
        return createExternalTypeDecNode((ClassSymbol) c.tsym);

    }

    public NodeWrapper createExternalTypeDecNode(ClassSymbol symbol) {
        NodeWrapper typeDecNode = createTypeDecNode(symbol, false);
        ASTTypesVisitor.typeSymbolPropsAndNestingLabels(symbol, symbol.getModifiers(), typeDecNode);
        return typeDecNode;
    }
}
