package es.uniovi.reflection.progquery.ast;

import com.sun.tools.javac.code.Symbol;

import javax.lang.model.type.ExecutableType;
import java.util.Objects;

public class MethodSigInfo {
        private String name;
        private int numParams;
        private ExecutableType executableType;
        public MethodSigInfo(Symbol.MethodSymbol methodSymbol) {
            this.name = methodSymbol.getSimpleName().toString();
            this.numParams = methodSymbol.getParameters().size();
            this.executableType = (ExecutableType) methodSymbol.type;
        }

    public ExecutableType getExecutableType() {
        return executableType;
    }

    public void setExecutableType(ExecutableType executableType) {
        this.executableType = executableType;
    }

    @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MethodSigInfo that = (MethodSigInfo) o;
            return numParams == that.numParams && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, numParams);
        }

    @Override
    public String toString() {
        return "MethodSigInfo{" + "name='" + name + '\'' + ", numParams=" + numParams + '}';
    }
}
