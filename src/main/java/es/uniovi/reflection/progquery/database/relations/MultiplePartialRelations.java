package es.uniovi.reflection.progquery.database.relations;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;

import java.util.List;

public class MultiplePartialRelations<T extends RelationTypesInterface> implements PartialRelation<T> {
    private PartialRelation[] partialRels;

    public MultiplePartialRelations(PartialRelation... partialRels) {
        this.partialRels = partialRels;
    }

    @Override
    public NodeWrapper getStartingNode() {
        throw new UnsupportedOperationException("Multiple partial relations do not have a single starting node.");
    }

    @Override
    public T getRelationType() {
        throw new UnsupportedOperationException("Multiple partial relations do not have a single relation type.");
    }

    @Override
    public RelationshipWrapper createRelationship(NodeWrapper endNode) {
        RelationshipWrapper ret = null;
        for (PartialRelation partialRelation : partialRels)
            ret = partialRelation.createRelationship(endNode);
        return ret;
    }

    @Override
    public RelationshipWrapper createRelationship(NodeWrapper endNode, T rel) {
        RelationshipWrapper ret = null;
        for (PartialRelation partialRelation : partialRels)
            ret = partialRelation.createRelationship(endNode, rel);
        return ret;
    }

    @Override
    public List<Pair<String, Object>> getProperties() {
        throw new UnsupportedOperationException("Multiple partial relations do not have a single property map.");
    }
}
