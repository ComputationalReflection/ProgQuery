package es.uniovi.reflection.progquery.typeInfo.keys.type;

import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;

public class NullTypeKey implements TypeKey {

    public static final NullTypeKey NULL_TYPE_KEY =new NullTypeKey();

    private NullTypeKey(){    }

    @Override public String toString() { return "null"; }

}
