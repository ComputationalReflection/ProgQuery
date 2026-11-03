package es.uniovi.reflection.progquery.typeInfo.keys.type;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import es.uniovi.reflection.progquery.typeInfo.keys.CallableKey;
import es.uniovi.reflection.progquery.typeInfo.keys.ElementKey;
import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;
import es.uniovi.reflection.progquery.utils.JavacInfo;

import javax.lang.model.type.TypeVariable;
import java.util.Objects;
import java.util.regex.Pattern;


public class TypeVariableKey implements TypeKey {

    private static final Pattern CAPTURE_IDENTIFIER = Pattern.compile("capture#\\d+");

    private String name;
    private ElementKey ownerKey;

    public TypeVariableKey(TypeVariable typeVar) {
        name = stableName(typeVar);

        ownerKey =
                new CallableKey((Symbol.MethodSymbol) ((Type.TypeVar) typeVar).tsym.owner);
    }

    public TypeVariableKey(TypeVariable typeVar, TypeKey ownerKey) {
        name = stableName(typeVar);
        this.ownerKey = ownerKey;
    }

    private static String stableName(TypeVariable typeVar) {
        String typeName = typeVar.toString();
        if (!CAPTURE_IDENTIFIER.matcher(typeName).find())
            return typeName;
        String sourceLocation = JavacInfo.getCurrentTreeLocation();
        String normalizedTypeName = CAPTURE_IDENTIFIER.matcher(typeName).replaceAll("capture");
        return sourceLocation == null ? normalizedTypeName : sourceLocation + ':' + normalizedTypeName;
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
