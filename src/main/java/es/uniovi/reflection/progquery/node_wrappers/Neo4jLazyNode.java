package es.uniovi.reflection.progquery.node_wrappers;

import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.nodes.NodeUtils;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class Neo4jLazyNode extends AbstractNeo4jLazyServerDriverElement implements NodeWrapper {
    private static final AtomicLong NEXT_ORDER = new AtomicLong();
    final long lockOrder = NEXT_ORDER.getAndIncrement(); // In-memory only; never a Neo4j identifier.
    final InfoToInsert owner = InfoToInsert.INFO_TO_INSERT.get();
    final GraphAccess access = owner == null ? GraphAccess.STANDALONE : owner.graphAccess;
    private final Map<RelationTypesInterface,List<RelationshipWrapper>> incomingRels = new HashMap<>(), outgoingRels = new HashMap<>();
    private final List<RelationshipWrapper> allRels = new ArrayList<>();
    private final Set<Label> labels = new HashSet<>();
    private volatile Neo4jLazyNode replacement;
    private volatile boolean shared;
    private volatile long id = -1;

    public Neo4jLazyNode(long id) { this.id = id; }
    public Neo4jLazyNode() { owner.addNewNode(this); }
    public Neo4jLazyNode(NodeTypes... labels) { this(); Collections.addAll(this.labels, labels); }
    public Neo4jLazyNode(NodeTypes label, Object... props) { this(props); labels.add(label); }
    public Neo4jLazyNode(Object... props) { super(props); owner.addNewNode(this); }

    Neo4jLazyNode canonical() {
        Neo4jLazyNode result = this;
        while (result.replacement != null) result = result.replacement;
        return result;
    }
    public void markShared() { shared = true; }
    @Override protected AbstractNeo4jLazyServerDriverElement canonicalElement() { return canonical(); }
    @Override protected GraphAccess propertyAccess() { return shared ? access : null; }

    private <T> T read(Function<Neo4jLazyNode,T> action) {
        var lock = access.topology.readLock(); lock.lock();
        try { var node = canonical(); synchronized (node) { return action.apply(node); } }
        finally { lock.unlock(); }
    }
    static void withNodes(Neo4jLazyNode a, Neo4jLazyNode b, Runnable action) {
        var first = a.lockOrder < b.lockOrder ? a : b;
        var second = first == a ? b : a;
        synchronized (first) { synchronized (second) { action.run(); } }
    }
    void addOutgoing(Neo4jLazyRelationship rel) {
        outgoingRels.computeIfAbsent(rel.getType(), ignored -> new ArrayList<>()).add(rel); allRels.add(rel);
    }
    void addIncoming(Neo4jLazyRelationship rel) {
        incomingRels.computeIfAbsent(rel.getType(), ignored -> new ArrayList<>()).add(rel); allRels.add(rel);
    }
    public void removeIncomingRel(RelationshipWrapper rel) {
        read(node -> { node.allRels.remove(rel); var list=node.incomingRels.get(rel.getType()); if(list!=null)list.remove(rel); return null; });
    }
    public void removeOutgoingRel(RelationshipWrapper rel) {
        read(node -> { node.allRels.remove(rel); var list=node.outgoingRels.get(rel.getType()); if(list!=null)list.remove(rel); return null; });
    }
    void unlinkIncoming(RelationshipWrapper rel) { allRels.remove(rel); var list=incomingRels.get(rel.getType()); if(list!=null)list.remove(rel); }
    void unlinkOutgoing(RelationshipWrapper rel) { allRels.remove(rel); var list=outgoingRels.get(rel.getType()); if(list!=null)list.remove(rel); }

    /** Retains relationship identities and redirects late references to the source declaration. */
    public void replaceWith(Neo4jLazyNode target) {
        access.replaceDeclaration(() -> {
            var old = canonical(); var next = target.canonical();
            if (old == next) return;
            next.markShared();
            for (RelationshipWrapper rel : new LinkedHashSet<>(old.allRels))
                ((Neo4jLazyRelationship)rel).replaceEndpoint(old,next);
            old.replacement = next;
            old.owner.deleteNode(old);
        });
    }

    @Override public RelationshipWrapper createRelationshipTo(NodeWrapper end, RelationTypesInterface type) {
        var lock=access.topology.readLock(); lock.lock();
        try {
            var start=canonical(); var target=((Neo4jLazyNode)end).canonical();
            if(start.access!=target.access)throw new IllegalArgumentException("Relationship crosses analysis sessions");
            Neo4jLazyRelationship[] result=new Neo4jLazyRelationship[1];
            withNodes(start,target,()->{
                var rel=new Neo4jLazyRelationship(start,target,type);
                start.addOutgoing(rel); target.addIncoming(rel); result[0]=rel;
            });
            return result[0];
        } finally { lock.unlock(); }
    }
    @Override public long getId() { return canonical().id; }
    @Override public void setId(long id) { canonical().id=id; }
    @Override public List<RelationshipWrapper> getRelationships() { return read(node -> new ArrayList<>(node.allRels)); }
    @Override public List<RelationshipWrapper> getRelationships(Direction direction) {
        return read(node -> {
            List<RelationshipWrapper> result=new ArrayList<>();
            (direction==Direction.INCOMING?node.incomingRels:node.outgoingRels).values().forEach(result::addAll);
            return result;
        });
    }
    @Override public List<RelationshipWrapper> getRelationships(Direction direction, RelationTypesInterface... types) {
        return read(node -> {
            List<RelationshipWrapper> result=new ArrayList<>();
            var map=direction==Direction.INCOMING?node.incomingRels:node.outgoingRels;
            for(var type:types){var list=map.get(type);if(list!=null)result.addAll(list);}
            return result;
        });
    }
    @Override public RelationshipWrapper getSingleRelationship(Direction direction, RelationTypesInterface type) {
        var rels=getRelationships(direction,type);
        if(rels.size()>1)throw new IllegalArgumentException("More than one relationship");
        return rels.isEmpty()?null:rels.getFirst();
    }
    @Override public boolean hasRelationship(RelationTypesInterface type,Direction direction) {
        return read(node -> (direction==Direction.INCOMING?node.incomingRels:node.outgoingRels).containsKey(type));
    }
    @Override public Set<Label> getLabels(){return read(node -> new HashSet<>(node.labels));}
    @Override public boolean hasLabel(Label label){return read(node -> node.labels.contains(label));}
    @Override public void addLabel(Label label){read(node -> node.labels.add(label));}
    @Override public void removeLabel(Label label){read(node -> node.labels.remove(label));}
    @Override public void delete(){owner.deleteNode(this);}
    @Override public String toString(){return NodeUtils.nodeToString(this);}
}
