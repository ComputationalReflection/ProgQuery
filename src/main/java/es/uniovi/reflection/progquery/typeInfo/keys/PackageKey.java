package es.uniovi.reflection.progquery.typeInfo.keys;

import com.sun.tools.javac.code.Symbol;

import java.util.Objects;

public class PackageKey implements PackageKeyI {
    private final String packageName;
    private final ModuleKey moduleKey;

    public PackageKey(Symbol.PackageSymbol packageSymbol){
        packageName = packageSymbol.getQualifiedName().toString();
        moduleKey = new ModuleKey(packageSymbol.modle);
    }

    public static PackageKeyI newPackageKey(Symbol.PackageSymbol packageSymbol) {
        if(packageSymbol instanceof Symbol.RootPackageSymbol)
            return NoPackageKey.INSTANCE;
        return new PackageKey(packageSymbol);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PackageKey that))
            return false;
        return Objects.equals(packageName, that.packageName) && Objects.equals(moduleKey, that.moduleKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, moduleKey);
    }

    @Override
    public String toString() {
        return "PackageKey{" + "packageName='" + packageName + '\'' + ", moduleKey=" + moduleKey + '}';
    }

    @Override
    public String getPackageName() {
        return packageName;
    }
}
