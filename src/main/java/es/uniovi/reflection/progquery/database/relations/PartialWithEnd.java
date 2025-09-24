package es.uniovi.reflection.progquery.database.relations;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;

public class PartialWithEnd<T extends RelationTypesInterface> extends SimplePartialRelation<T> {
    private NodeWrapper endNode;

    public PartialWithEnd(NodeWrapper startingNode, T relationType) {
        super(startingNode, relationType);
    }

    public NodeWrapper getEndNode() {
        return endNode;
    }

    @Override
    public RelationshipWrapper createRelationship(NodeWrapper endNode, T rel) {
        this.endNode = endNode;
        return super.createRelationship(endNode, rel);
    }
}
