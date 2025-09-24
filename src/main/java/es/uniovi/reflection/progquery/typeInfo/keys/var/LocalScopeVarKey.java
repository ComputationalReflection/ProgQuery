package es.uniovi.reflection.progquery.typeInfo.keys.var;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.VarKey;

import java.util.Objects;

public class LocalScopeVarKey implements VarKey {
    private final Symbol symbol;

    public LocalScopeVarKey(Symbol symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LocalScopeVarKey that))
            return false;
        return Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(symbol);
    }

    @Override
    public String toString() {
        return "LocalScopeVarKey{" + "symbol=" + symbol + '}';
    }
}
