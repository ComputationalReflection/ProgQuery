package es.uniovi.reflection.progquery.typeInfo.keys;

import com.sun.tools.javac.code.Symbol;

import java.util.Objects;

public class ModuleKey implements ElementKey {
    private final String moduleName;

    public ModuleKey(Symbol.ModuleSymbol modle) {
        moduleName = modle.getQualifiedName().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ModuleKey moduleKey))
            return false;
        return Objects.equals(moduleName, moduleKey.moduleName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(moduleName);
    }

    @Override
    public String toString() {
        return "ModuleKey{" + "moduleName='" + moduleName + '\'' + '}';
    }
}
