package es.uniovi.reflection.progquery.typeInfo.keys;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;
import es.uniovi.reflection.progquery.typeInfo.keys.var.FieldKey;

import java.util.Objects;

public class ComponentKey {
    private final String fieldName;
    private final TypeDefinitionKey ownerKey;

    public ComponentKey(Symbol s) {
        fieldName = s.getSimpleName().toString();
        ownerKey = new TypeDefinitionKey(s.owner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ComponentKey that))
            return false;
        return Objects.equals(fieldName, that.fieldName) && Objects.equals(ownerKey, that.ownerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, ownerKey);
    }

    @Override
    public String toString() {
        return "ComponentKey{" + "fieldName='" + fieldName + '\'' + ", ownerKey=" + ownerKey + '}';
    }

    public String getFieldName() {
        return fieldName;
    }

    public TypeDefinitionKey getOwnerKey() {
        return ownerKey;
    }
}
