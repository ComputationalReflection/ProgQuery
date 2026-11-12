package es.uniovi.reflection.progquery;

import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.ASTRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.*;
import org.neo4j.graphdb.Direction;
import java.util.*;

/** Executable regression for endpoint reuse and deferred deletions; no database needed. */
public final class LazyStorageRegressionTest {
    public static void main(String[] args) {
        checkIdentityAndDeletion();
        for (int seed = 0; seed < 50; seed++) {
            List<String> original = exercise(seed, false);
            check(original.equals(exercise(seed, true)), "Graph differs from original replacement protocol: " + seed);
        }
        InfoToInsert.INFO_TO_INSERT.remove();
        System.out.println("Passed relationship identity/properties, self-loops, adjacency, deferred deletion and 50 differential graph cases.");
    }

    private static void checkIdentityAndDeletion() {
        var info = new InfoToInsert();
        InfoToInsert.INFO_TO_INSERT.set(info);
        var old = node(0);
        var replacement = node(1);
        var neighbor = node(2);
        var cache = new DefinitionCache<String>();
        cache.put("class", old);
        var edges = new ArrayList<RelationshipWrapper>();
        edges.add(edge(old, old, 0));
        edges.add(edge(old, neighbor, 1));
        edges.add(edge(neighbor, old, 2));
        edges.add(edge(neighbor, old, 3));
        edges.add(edge(old, replacement, 4));
        edges.add(edge(replacement, old, 5));
        cache.putDefinition("class", replacement);
        check(cache.get("class") == replacement, "Declaration cache was not updated");
        check(info.getNodeSet().size() == 2 && info.getRelSet().size() == 6, "Replacement changed graph size");
        for (int i = 0; i < edges.size(); i++) {
            var rel = edges.get(i);
            check(info.getRelSet().get(i) == rel, "Relationship was recreated");
            check(rel.getProperty("edge").equals(i), "Lost edge property");
            check(rel.getStartNode() != old && rel.getEndNode() != old, "Stale endpoint");
        }
        check(old.getRelationships().isEmpty(), "Old adjacency was not cleared");
        checkAdjacency(info);
        edges.get(0).delete(); // Self-loop: remove both adjacency occurrences.
        edges.get(0).delete(); // Repeated deletion remains harmless.
        check(info.getRelSet().size() == 5, "Deferred edge deletion was not filtered");
        checkAdjacency(info);
        var extra = node(3);
        extra.delete();
        extra.delete();
        check(info.getNodeSet().size() == 2, "Deferred node deletion was not filtered");
        edge(neighbor, replacement, 6);
        info.prepareInsertion();
        info.prepareInsertion();
        check(info.getRelQueriesInfo().size() == 6 && info.getNodeQueriesInfo().size() == 2,
                "Compaction/query preparation was not repeatable");
    }

    private static List<String> exercise(int seed, boolean optimized) {
        var info = new InfoToInsert();
        InfoToInsert.INFO_TO_INSERT.set(info);
        var random = new Random(seed);
        var nodes = new ArrayList<Neo4jLazyNode>();
        var cache = new DefinitionCache<Integer>();
        for (int i = 0; i < 20; i++) {
            var node = node(i);
            nodes.add(node);
            cache.put(i, node);
        }
        for (int i = 0; i < 200; i++) edge(nodes.get(random.nextInt(20)), nodes.get(random.nextInt(20)), i);
        for (int i = 0; i < 15; i++) {
            var old = nodes.get(i);
            var replacement = node(i);
            if (optimized) cache.putDefinition(i, replacement);
            else {
                for (var rel : old.getRelationships(Direction.INCOMING)) {
                    var next = rel.getStartNode().createRelationshipTo(replacement, rel.getType());
                    rel.getAllProperties().forEach(p -> next.setProperty(p.getKey(), p.getValue()));
                    rel.delete();
                }
                for (var rel : old.getRelationships(Direction.OUTGOING)) {
                    var next = replacement.createRelationshipTo(rel.getEndNode(), rel.getType());
                    rel.getAllProperties().forEach(p -> next.setProperty(p.getKey(), p.getValue()));
                    rel.delete();
                }
                old.delete();
            }
            nodes.set(i, replacement);
        }
        for (var rel : new ArrayList<>(info.getRelSet()))
            if ((int) rel.getProperty("edge") % 7 == 0) rel.delete();
        checkAdjacency(info);
        var graph = new ArrayList<String>();
        for (var node : info.getNodeSet()) graph.add("node:" + node.getProperty("testId"));
        for (var rel : info.getRelSet()) graph.add(rel.getStartNode().getProperty("testId") + ":" + rel.getTypeString()
                + ":" + rel.getProperty("edge") + ":" + rel.getEndNode().getProperty("testId"));
        Collections.sort(graph);
        return graph;
    }

    private static Neo4jLazyNode node(int id) {
        var result = new Neo4jLazyNode(NodeTypes.CLASS_DEC);
        result.setProperty("testId", id);
        return result;
    }
    private static RelationshipWrapper edge(NodeWrapper a, NodeWrapper b, int id) {
        var result = a.createRelationshipTo(b, id % 2 == 0 ? ASTRelationTypes.DECLARES_TYPE : ASTRelationTypes.DECLARES_FIELD);
        result.setProperty("edge", id);
        return result;
    }
    private static void checkAdjacency(InfoToInsert info) {
        var active = new HashSet<>(info.getRelSet());
        var nodes = new HashSet<>(info.getNodeSet());
        for (var rel : active) {
            check(nodes.contains(rel.getStartNode()) && nodes.contains(rel.getEndNode()), "Edge leaves active graph");
            check(rel.getStartNode().getRelationships(Direction.OUTGOING).contains(rel), "Missing outgoing adjacency");
            check(rel.getEndNode().getRelationships(Direction.INCOMING).contains(rel), "Missing incoming adjacency");
        }
        for (var node : nodes) for (var rel : node.getRelationships()) check(active.contains(rel), "Deleted adjacency remains");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
