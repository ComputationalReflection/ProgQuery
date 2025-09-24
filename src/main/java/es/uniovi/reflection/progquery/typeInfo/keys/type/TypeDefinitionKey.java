package es.uniovi.reflection.progquery.typeInfo.keys.type;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.ElementKey;
import es.uniovi.reflection.progquery.typeInfo.keys.PackageKey;
import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;

import javax.lang.model.element.ElementKind;
import java.util.Objects;

public class TypeDefinitionKey implements TypeKey {
    private final String typeName;
    private final ElementKey ownerKey;

    public TypeDefinitionKey(Symbol symbol) {
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) symbol;
        if(classSymbol.owner.getKind()== ElementKind.PACKAGE)
            ownerKey = new PackageKey(classSymbol.packge());
        else
            ownerKey = new TypeDefinitionKey(classSymbol.owner);
        typeName = classSymbol.name.toString();
    }
//Saved for generated symbols in case they have no owners
//    public TypeDefinitionKey(String ownerName, String typeName) {
//        this.ownerName = ownerName;
//        this.typeName = typeName;
//    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TypeDefinitionKey that))
            return false;
        return Objects.equals(typeName, that.typeName) && Objects.equals(ownerKey, that.ownerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, ownerKey);
    }

    @Override
    public String toString() {
        return ownerKey + "." + typeName;
    }
}
