package es.uniovi.reflection.progquery.utils.keys.external;

import es.uniovi.reflection.progquery.database.nodes.NodeCategory;

import java.util.Objects;

public class ExternalTypeDefKey extends ExternalNotDefinedTypeKey {

    private String fileName;

    public ExternalTypeDefKey(String fileName, String simpleName) {
        super(fileName +" : " +
                simpleName, NodeCategory.TYPE_DEFINITION.toString());
        this.fileName = fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExternalTypeDefKey that = (ExternalTypeDefKey) o;
        return Objects.equals(fileName, that.fileName) &&
                Objects.equals(fullyQualifiedTypeName, that.fullyQualifiedTypeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, fullyQualifiedTypeName);
    }


}