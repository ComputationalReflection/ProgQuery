package es.uniovi.reflection.progquery.typeInfo;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.pg.PackageManager;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.lang.model.type.TypeKind;

public class TypeHierarchy {
    public static void addTypeHierarchy(ClassSymbol symbol, NodeWrapper classNode, ASTTypesVisitor astVisitor,
                                        ASTAuxiliarStorage ast) {
        scanRelatedTypeSymbol(symbol.getSuperclass(), classNode, TypeRelations.EXTENDS_CLASS, astVisitor, symbol, ast);
        for (Type interfaceType : symbol.getInterfaces())
            scanRelatedTypeSymbol(interfaceType, classNode, TypeRelations.IMPLEMENTS_INTERFACE, astVisitor, symbol, ast);
        for (Type permittedSubtype : symbol.getPermittedSubclasses())
            scanRelatedTypeSymbol(permittedSubtype, classNode, TypeRelations.PERMITS_SUBTYPE, astVisitor, symbol, ast);
    }

    private static void scanRelatedTypeSymbol(Type endType, NodeWrapper startNode, TypeRelations rel,
                                              ASTTypesVisitor astVisitor, ClassSymbol startSymbol,
                                              ASTAuxiliarStorage ast) {
        if (endType.getKind() != TypeKind.NONE) {
            NodeWrapper superTypeClass = DefinitionCache.getOrCreateType(endType, ast);
            startNode.createRelationshipTo(superTypeClass, rel);
            if (astVisitor == null) {
                startNode.createRelationshipTo(superTypeClass, CDGRelationTypes.USES_TYPE_DEF);
                PackageManager.PACKAGE_MANAGER.get().handleNewDependency(startSymbol.packge(), endType.tsym.packge());
            } else
                astVisitor.addToTypeDependencies(superTypeClass, endType.tsym.packge());
        }
    }

}
