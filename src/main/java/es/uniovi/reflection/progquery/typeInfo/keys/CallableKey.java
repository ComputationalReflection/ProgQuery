package es.uniovi.reflection.progquery.typeInfo.keys;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;

import java.util.Objects;

public class CallableKey implements ElementKey {
    private final String simpleName, methodType, completeName;
    private final TypeDefinitionKey ownerKey;

    public String getSimpleName() {
        return simpleName;
    }

    public String getCompleteName() {
        return completeName;
    }

    public String getFullyQualifiedName() {
        return completeName + methodType;
    }

    public CallableKey(Symbol.MethodSymbol methodSymbol) {
        String methodType = methodSymbol.type.toString();
        if (methodSymbol.isConstructor()) {
            simpleName = "<init>";
            this.methodType = methodType.substring(0, methodType.length() - 4);
        } else {
            simpleName = methodSymbol.name.toString();
            this.methodType = methodType;
        }
        completeName = methodSymbol.owner + ":" + simpleName;
        ownerKey = new TypeDefinitionKey(methodSymbol.owner);
    }

    public CallableKey(String simpleName, Symbol owner) {
        this.simpleName = simpleName;
        this.completeName = owner + ":" + simpleName;
        this.methodType = "";
        this.ownerKey = new TypeDefinitionKey(owner);
    }

/*Saved for generated symbols in case they have no owners
    public CallableKey(String simpleName, String owner) {
        this.simpleName = simpleName;
        this.completeName = owner + ":" + simpleName;
        this.methodType = "";
        if(owner.contains(".")){
            int lastDotIndex = owner.lastIndexOf(".");
            this.ownerKey = new TypeDefinitionKey(owner.substring(0, lastDotIndex), owner.substring(lastDotIndex+1));
        }
        else
            this.ownerKey = new TypeDefinitionKey("", owner);
    }*/

    public TypeDefinitionKey getOwnerKey() {
        return ownerKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CallableKey that))
            return false;
        return Objects.equals(simpleName, that.simpleName) && Objects.equals(methodType, that.methodType) &&
                Objects.equals(ownerKey, that.ownerKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(simpleName, methodType, ownerKey);
    }
}
