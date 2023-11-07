package es.uniovi.reflection.progquery.typeInfo;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.lang.model.type.TypeKind;

public class TypeHierarchy {
    public static void addTypeHierarchy(ClassSymbol symbol, NodeWrapper classNode, ASTTypesVisitor astVisitor,
                                        ASTAuxiliarStorage ast) {
        scanBaseClassSymbol(symbol.getSuperclass(), classNode, TypeRelations.IS_SUBTYPE_EXTENDS, astVisitor, symbol, ast);
        for (Type interfaceType : symbol.getInterfaces())
            scanBaseClassSymbol(interfaceType, classNode, TypeRelations.IS_SUBTYPE_IMPLEMENTS, astVisitor, symbol, ast);
    }

    private static void scanBaseClassSymbol(Type baseType, NodeWrapper classNode, TypeRelations rel,
                                            ASTTypesVisitor astVisitor, ClassSymbol classSymbol,
                                            ASTAuxiliarStorage ast) {
        if (baseType.getKind() != TypeKind.NONE) {
            NodeWrapper superTypeClass = DefinitionCache.getOrCreateType(baseType, ast);
            classNode.createRelationshipTo(superTypeClass, rel);
            if (astVisitor == null) {
                classNode.createRelationshipTo(superTypeClass, CDGRelationTypes.USES_TYPE_DEF);
                PackageInfo.PACKAGE_INFO.handleNewDependency(classSymbol.packge(), baseType.tsym.packge());
            } else
                astVisitor.addToTypeDependencies(superTypeClass, baseType.tsym.packge());
        }
    }
}
