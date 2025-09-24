package es.uniovi.reflection.progquery.typeInfo.keys.var;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.VarKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;

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
}
