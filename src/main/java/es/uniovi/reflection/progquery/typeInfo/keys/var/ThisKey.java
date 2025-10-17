package es.uniovi.reflection.progquery.typeInfo.keys.var;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.typeInfo.keys.VarKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;
import es.uniovi.reflection.progquery.visitors.PDGProcessing;

import java.util.Objects;

public class ThisKey implements VarKey {
    private final TypeDefinitionKey ownerKey;

    public ThisKey(Symbol owner) {
        ownerKey = new TypeDefinitionKey(owner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ThisKey thisKey))
            return false;
        return Objects.equals(ownerKey, thisKey.ownerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ownerKey);
    }

    @Override
    public String toString() {
        return "ThisKey{" + "ownerKey=" + ownerKey + '}';
    }

    @Override
    public void putDecInCache(PDGProcessing pdgProcessing, NodeWrapper definitionNode) {
        pdgProcessing.putDecInCache(this, definitionNode, pdgProcessing.getThisCache());
    }

    @Override
    public NodeWrapper getNode(PDGProcessing pdgProcessing) {
        return pdgProcessing.getNode(this, pdgProcessing.getThisCache());
    }
}
