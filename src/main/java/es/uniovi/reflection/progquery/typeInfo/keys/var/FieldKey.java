package es.uniovi.reflection.progquery.typeInfo.keys.var;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.ComponentKey;
import es.uniovi.reflection.progquery.typeInfo.keys.VarKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;

import java.util.Objects;

public class FieldKey  extends ComponentKey implements VarKey {
  private final Symbol symbol;

    public FieldKey(Symbol s) {
        super(s);
        symbol = s;
    }


    public Symbol getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FieldKey fieldKey))
            return false;
        if (!super.equals(o))
            return false;
        return Objects.equals(getFieldName(), fieldKey.getFieldName()) &&
                Objects.equals(getOwnerKey(), fieldKey.getOwnerKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getFieldName(), getOwnerKey());
    }
}
