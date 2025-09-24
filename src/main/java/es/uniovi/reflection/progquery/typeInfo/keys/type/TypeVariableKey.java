package es.uniovi.reflection.progquery.typeInfo.keys.type;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.typeInfo.keys.CallableKey;
import es.uniovi.reflection.progquery.typeInfo.keys.ElementKey;
import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;

import javax.lang.model.type.TypeVariable;
import java.util.Objects;


public class TypeVariableKey implements TypeKey {

    private String name;
    private ElementKey ownerKey;

    public TypeVariableKey(TypeVariable typeVar) {
        name = typeVar.toString();

        ownerKey =
                new CallableKey((Symbol.MethodSymbol) ((Type.TypeVar) typeVar).tsym.owner);
    }

    public TypeVariableKey(TypeVariable typeVar, TypeKey ownerKey) {
        name = typeVar.toString();
        this.ownerKey = ownerKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TypeVariableKey that))
            return false;
        return Objects.equals(name, that.name) && Objects.equals(ownerKey, that.ownerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ownerKey);
    }

    @Override
    public String toString() {
        return ownerKey + ":" + name ;
    }
}
