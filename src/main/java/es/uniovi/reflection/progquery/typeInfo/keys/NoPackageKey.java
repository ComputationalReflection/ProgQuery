package es.uniovi.reflection.progquery.typeInfo.keys;

public class NoPackageKey implements PackageKeyI{

    private static final String ROOT_PACKAGE_NAME = "<Root Package>";

    private NoPackageKey() {
    }
    public static final NoPackageKey INSTANCE = new NoPackageKey();

    @Override
    public String getPackageName() {
        return ROOT_PACKAGE_NAME;
    }
}
