package es.uniovi.reflection.progquery.typeInfo.keys.type;

import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;

import java.util.Objects;

public class CapturedTypeKey implements TypeKey {
    private WildcardKey wildcardTypeKey;
    private TypeKey bound;
    private TypeKey lower;

    public CapturedTypeKey(WildcardKey wildcardTypeKey, TypeKey bound, TypeKey lower) {
        this.wildcardTypeKey = wildcardTypeKey;
        this.bound = bound;
        this.lower = lower;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CapturedTypeKey that))
            return false;
        return Objects.equals(wildcardTypeKey, that.wildcardTypeKey) && Objects.equals(bound, that.bound) &&
                Objects.equals(lower, that.lower);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wildcardTypeKey, bound, lower);
    }
}
