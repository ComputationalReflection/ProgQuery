package es.uniovi.reflection.progquery.utils;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.relations.PartialRelation;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import es.uniovi.reflection.progquery.utils.keys.cache.TypeKey;
import es.uniovi.reflection.progquery.visitors.KeyForNewTypeVarVisitor;
import es.uniovi.reflection.progquery.visitors.TypeVisitor;

public class GraphUtils {

    public static <T extends RelationTypesInterface> void connectWithParent(NodeWrapper child,
                                                                            Pair<PartialRelation<T>, Object> pair) {
        pair.getFirst().createRelationship(child);
    }

    public static <T extends RelationTypesInterface> void connectWithParent(NodeWrapper child, NodeWrapper parent,
                                                                            RelationTypes r) {
        parent.createRelationshipTo(child, r);
    }

    // This method does not take into account the previous relationship
    public static <T extends RelationTypesInterface> void connectWithParent(NodeWrapper child,
                                                                            Pair<PartialRelation<T>, Object> pair,
                                                                            RelationTypes r) {

        pair.getFirst().getStartingNode().createRelationshipTo(child, r);
    }

    // public static TypeMirror attachTyperrrr(Tree tree, NodeWrapper node,
    // TreePath
    // path) {
    //
    // TypeMirror fullyQualifiedType = JavacInfo.getTypeMirror(tree, path);
    //
    // if (fullyQualifiedType != null) {
    // node.setProperty("actualType", fullyQualifiedType.toString());
    //
    // TypeKind typeKind = fullyQualifiedType.getKind();
    // if (typeKind != null)
    // node.setProperty("typeKind", typeKind.toString());
    // }
    // return fullyQualifiedType;
    // }
    //
    // public static TypeMirror attachTyperrr(Tree tree, NodeWrapper node) {
    // TypeMirror fullyQualifiedType = JavacInfo.getTypeMirror(tree);
    //
    // if (fullyQualifiedType != null) {
    // node.setProperty("actualType", fullyQualifiedType.toString());
    //
    // TypeKind typeKind = fullyQualifiedType.getKind();
    // if (typeKind != null)
    // node.setProperty("typeKind", typeKind.toString());
    // }
    //
    // return fullyQualifiedType;
    // }

    // public static void attachTypeDirect(NodeWrapper node, Type type, String
    // actualType, String typeKind) {
    //
    // // OJO AL RETURNTYPE
    // // CACHE GET OR CREATE TYPEDEC -->ACTUALTYPE
    // }
    public static void attachTypeDirect(NodeWrapper node, ExpressionTree exp, ASTAuxiliarStorage ast) {
        Type type = JavacInfo.getTypeDirect(exp);
        attachType(node, type, ast);

    }

    public static Type attachTypeDirect(NodeWrapper node, TypeParameterTree typeParam, ASTAuxiliarStorage ast) {
        Type type = JavacInfo.getTypeDirect(typeParam);
        attachType(node, type, ast);
        return type;
    }
/*
	public static void attachTypeDirectIdent(NodeWrapper node, IdentifierTree exp) {
		Type type = JavacInfo.getTypeDirect(exp);

		if (type.getKind() == TypeKind.EXECUTABLE)
			attachType(node, type);

		else
			// if (type != null) {
			attachType(node, type);
	}

	public static void attachTypeDirectMemberSel(NodeWrapper node, MemberSelectTree exp) {
		Type type = JavacInfo.getTypeDirect(exp);

		if (type.getKind() == TypeKind.EXECUTABLE)
			attachType(node, type);

		else
			// if (type != null) {
			attachType(node, type);
	}*/

    public static void attachTypeDirect(NodeWrapper node, VariableTree varDec, ASTAuxiliarStorage ast) {
        Type type = JavacInfo.getTypeDirect(varDec);
        // if (type != null) {
        // } else
        // System.out.println("varDec: " + varDec.getName());
        attachType(node, type, ast);
    }
/*
	public static void attachType(NodeWrapper node, Type type, int a) {
		attachTypeDirect(node, DefinitionCache.getOrCreateType(type), type.toString(), type.getKind().toString());
	}*/

    public static void attachType(NodeWrapper node, Type type, ASTAuxiliarStorage ast) {
        attachTypeDirect(node, type, type.toString(), type.getKind().toString(), ast);
    }

    public static void attachTypeDirect(NodeWrapper node, ExpressionTree exp, String actualType, String typeKind,
                                        ASTAuxiliarStorage ast) {

        attachTypeDirect(node, JavacInfo.getTypeDirect(exp), actualType, typeKind, ast);
    }

    private static void attachTypeDirect(NodeWrapper node, Type type, String actualType, String typeKind,
                                         ASTAuxiliarStorage ast) {
        attachTypeDirect(node, DefinitionCache.getOrCreateType(type, ast), actualType, typeKind);
    }

    private static void attachTypeDirect(NodeWrapper node, NodeWrapper endNode, String actualType, String typeKind) {
        node.setProperty("actualType", actualType);
        node.setProperty("typeKind", typeKind);
        node.createRelationshipTo(endNode, TypeRelations.ITS_TYPE_IS);
    }

    public static NodeWrapper attachNewTypeParam(NodeWrapper typeParamNode, TypeParameterTree typeParamTree,
                                                 ASTAuxiliarStorage ast, String ownerFullName) {
        Type type = JavacInfo.getTypeDirect(typeParamTree);
        TypeKey key = type.accept(new KeyForNewTypeVarVisitor(ownerFullName), null);
        NodeWrapper typeVarNode = DefinitionCache.TYPE_CACHE.containsKey(key) ? DefinitionCache.TYPE_CACHE.get(key) :
                type.accept(new TypeVisitor(ast), key);
        attachTypeDirect(typeParamNode, typeVarNode, key.toString(), type.getKind().toString());
        return typeVarNode;
    }

    public static void attachTypeToNewMethod(Symbol.MethodSymbol symbol, String fullyQualifiedName,
                                             ASTAuxiliarStorage ast, NodeWrapper methodNode) {
        Type type = symbol.type;
        TypeKey key = type.accept(new KeyForNewTypeVarVisitor(fullyQualifiedName), null);
        NodeWrapper methodTypeNode = DefinitionCache.TYPE_CACHE.containsKey(key) ? DefinitionCache.TYPE_CACHE.get(key) :
                type.accept(new TypeVisitor(ast), key);
        final String execKind = "EXECUTABLE";
        attachTypeDirect(methodNode, methodTypeNode, key.toString(), execKind);
    }
}
