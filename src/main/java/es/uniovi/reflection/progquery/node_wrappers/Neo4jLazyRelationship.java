package es.uniovi.reflection.progquery.node_wrappers;

import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.database.nodes.NodeUtils;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;

public class Neo4jLazyRelationship extends AbstractNeo4jLazyServerDriverElement implements RelationshipWrapper {
    private volatile Neo4jLazyNode start,end;
    private final RelationTypesInterface type;
    private final InfoToInsert owner=InfoToInsert.INFO_TO_INSERT.get();
    private volatile boolean deleted;

    public Neo4jLazyRelationship(NodeWrapper start,NodeWrapper end,RelationTypesInterface type){
        this(start,type);this.end=(Neo4jLazyNode)end;
    }
    public Neo4jLazyRelationship(NodeWrapper start,RelationTypesInterface type){
        this.start=(Neo4jLazyNode)start;this.type=type;owner.addNewRel(this);
    }
    @Override public RelationTypesInterface getType(){return type;}
    @Override public String getTypeString(){return type.toString();}
    @Override public NodeWrapper getStartNode(){return start.canonical();}
    @Override public NodeWrapper getEndNode(){return end==null?null:end.canonical();}
    @Override public void delete(){
        var lock=start.access.topology.readLock();lock.lock();
        try {
            var a=start.canonical();var b=end.canonical();
            Neo4jLazyNode.withNodes(a,b,()->{
                if(deleted)return;
                deleted=true;a.unlinkOutgoing(this);b.unlinkIncoming(this);owner.deleteRel(this);
            });
        } finally {lock.unlock();}
    }
    void replaceEndpoint(Neo4jLazyNode previous,Neo4jLazyNode next){
        if(deleted)return;
        start.unlinkOutgoing(this);end.unlinkIncoming(this);
        if(start==previous)start=next;if(end==previous)end=next;
        start.addOutgoing(this);end.addIncoming(this);
    }
    @Override public String toString(){return NodeUtils.nodeToStringNoRels(getStartNode())+"--["+type.name()+"]-->"+NodeUtils.nodeToStringNoRels(getEndNode());}
}
