package es.uniovi.reflection.progquery.cache;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.utils.types.TypeKey;
import es.uniovi.reflection.progquery.visitors.KeyTypeVisitor;
import es.uniovi.reflection.progquery.visitors.TypeVisitor;
import org.neo4j.graphdb.Direction;

import javax.lang.model.type.TypeMirror;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DefinitionCache<TKEY> {
    public static ThreadLocal<DefinitionCache<TypeKey>> TYPE_CACHE  = new ThreadLocal<>();
    public static ThreadLocal<DefinitionCache<String>> METHOD_DEF_CACHE  = new ThreadLocal<>();

    private final Map<TKEY, NodeWrapper> auxNodeCache = new HashMap<>();
    protected final Map<TKEY, NodeWrapper> definitionNodeCache = new HashMap<>();
    public void put(TKEY k, NodeWrapper v) {
        if (auxNodeCache.containsKey(k))
            throw new IllegalArgumentException("Key " + k + " twice ");
        if (!definitionNodeCache.containsKey(k))
            auxNodeCache.put(k, v);
        else
            throw new IllegalArgumentException("Key " + k + " already in definition");
    }

    public static void putClassDefinition(Symbol.ClassSymbol classSymbol, NodeWrapper classDec,
                                          Set<NodeWrapper> typeDecNodeList, Set<NodeWrapper> typeDecsUses) {
        TYPE_CACHE.get().putClassDefinition(classSymbol.type.accept(new KeyTypeVisitor(), null), classDec, typeDecNodeList,
                typeDecsUses);
    }

    public void putClassDefinition(TKEY classSymbol, NodeWrapper classDec, Set<NodeWrapper> typeDecNodeList,
                                   Set<NodeWrapper> typeDecsUses) {
        NodeWrapper oldClassNode = null;
        if (auxNodeCache.containsKey(classSymbol)) {
            oldClassNode = auxNodeCache.get(classSymbol);
            for (RelationshipWrapper r : oldClassNode.getRelationships(Direction.OUTGOING,
                    RelationTypes.DECLARES_FIELD, TypeRelations.IS_SUBTYPE_EXTENDS,
                    TypeRelations.IS_SUBTYPE_IMPLEMENTS))
                r.delete();
            typeDecNodeList.remove(oldClassNode);
            oldClassNode.getRelationships(Direction.OUTGOING, CDGRelationTypes.USES_TYPE_DEF).forEach(usesTypeDecRel ->
            typeDecsUses.add(usesTypeDecRel.getEndNode()));
        }
        putDefinition(classSymbol, classDec, oldClassNode);
    }

    public void putDefinition(TKEY k, NodeWrapper v) {
        putDefinition(k, v, auxNodeCache.get(k));
    }

    private void putDefinition(TKEY k, NodeWrapper v, NodeWrapper previousNode) {
        if (previousNode != null) {
            for (RelationshipWrapper r : previousNode.getRelationships(Direction.INCOMING)) {
                r.getStartNode().createRelationshipTo(v, r.getType());
                r.delete();
            }
            for (RelationshipWrapper r : previousNode.getRelationships(Direction.OUTGOING)) {
                v.createRelationshipTo(r.getEndNode(), r.getType());
                r.delete();
            }
            auxNodeCache.remove(k);
            previousNode.delete();
        }
        definitionNodeCache.put(k, v);
    }

    public NodeWrapper get(TKEY k) {
        return definitionNodeCache.containsKey(k) ? definitionNodeCache.get(k) : auxNodeCache.get(k);
    }

    public boolean containsKey(TKEY k) {
        return auxNodeCache.containsKey(k) || definitionNodeCache.containsKey(k);
    }

    public boolean containsDef(TKEY k) {
        return definitionNodeCache.containsKey(k);
    }

    public int totalTypesCached() {
        return auxNodeCache.size();
    }

    public int totalDefsCached() {
        return definitionNodeCache.size();
    }

    public static NodeWrapper getOrCreateType(TypeMirror type, TypeKey key, ASTAuxiliarStorage ast) {
        if (DefinitionCache.TYPE_CACHE.get().containsKey(key))
            return DefinitionCache.TYPE_CACHE.get().get(key);
        return createTypeDec(type, key, ast);
    }

    public static NodeWrapper getOrCreateType (TypeMirror type, ASTAuxiliarStorage ast){
        return getOrCreateType(type, type.accept(new KeyTypeVisitor(), null), ast);
    }

    public static NodeWrapper createTypeDec (TypeMirror typeSymbol, ASTAuxiliarStorage ast){
        return createTypeDec(typeSymbol, typeSymbol.accept(new KeyTypeVisitor(), null), ast);
    }

    private static NodeWrapper createTypeDec (TypeMirror type, TypeKey key, ASTAuxiliarStorage ast){
        return type.accept(new TypeVisitor(ast), key);
    }
}
