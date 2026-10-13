package es.uniovi.reflection.progquery.database.insertion.lazy;

import es.uniovi.reflection.progquery.database.manager.NEO4JServerManager;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import org.neo4j.driver.*;
import org.neo4j.graphdb.Label;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

public class DriverLazyInsertionService {
    private static final int REPETITIONS = 1;
    private static final String NODES_QUERY =
            "UNWIND $instances AS item\n" + "CREATE (n:$(item.labels))\n" + "SET n = item.props\n" +
                    "RETURN elementId(n)";
    private static final String RELS_QUERY = "UNWIND $rels AS rel\n" + "MATCH (a), (b)\n" +
            "WHERE elementId(a)= rel.source_id AND elementId(b)= rel.target_id\n" +
            "CREATE (a)-[r:$(rel.type)]->(b)\n" + "SET r = rel.properties"
//            + "\nRETURN COUNT(r)"
            ;

    public static void defaultDBInsertion(InfoToInsert info, String server_address, final String USER,
                                          final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION) {
        try (final Driver driver = GraphDatabase.driver(NEO4JServerManager.NEO4J_PROTOCOL + server_address,
                AuthTokens.basic(USER, PASS)); Session session = driver.session()) {
            for (int i = 0; i < REPETITIONS; i++) {
                actionByParts(info.nodeSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
                        (start, end) -> executeNodeListOneQuery(session, info.nodeSet, start, end));
                actionByParts(info.relSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
                        (start, end) -> executeRelListOneQuery(session, info.relSet, start, end));
            }
        }
    }

    public static void insertToSpecificDB(InfoToInsert info, String server_address, final String USER,
                                          final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION, String DB_NAME) {
        SessionConfig configForDB = SessionConfig.forDatabase(DB_NAME);
        try (final Driver driver = GraphDatabase.driver(NEO4JServerManager.NEO4J_PROTOCOL + server_address,
                AuthTokens.basic(USER, PASS)); Session session = driver.session(configForDB)) {
            for (int i = 0; i < REPETITIONS; i++) {
                actionByParts(info.nodeSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
                        (start, end) -> executeNodeListOneQuery(session, info.nodeSet, start, end));
                actionByParts(info.relSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
                        (start, end) -> executeRelListOneQuery(session, info.relSet, start, end));
            }
        }
    }

    public static <T> void actionByParts(int listSize, int numberPerPart, BiConsumer<Integer, Integer> action) {
        int i = 0;
        while ((i + 1) * numberPerPart < listSize)
            action.accept(i++ * numberPerPart, i * numberPerPart);
        action.accept(i * numberPerPart, listSize);
    }

    private static Void executeNodeListOneQuery(Session session, List<NodeWrapper> nodes, int start, int end) {
        return session.writeTransaction(tx -> {
            List<NodeWrapper> subList = nodes.subList(start, end);
            List<Map<String, Object>> labelsAndPropsList = subList.stream().map(n -> {
                HashMap<String, Object> map = new HashMap<>();
                map.put("props", n.getAllProperties().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                map.put("labels", n.getLabels().stream().map(Label::name).collect(Collectors.toList()));
                return map;
            }).collect(Collectors.toList());
            Result result = tx.run(NODES_QUERY, parameters("instances", labelsAndPropsList));
            for (int i = 0; i < subList.size(); i++) {
                subList.get(i).setId(result.next().values().get(0).asString());
            }
            if (result.hasNext()) {
                throw new IllegalStateException("Not all nodes were created successfully.");
            }
            return null;
        });
    }

    private static Void executeRelListOneQuery(Session session, List<RelationshipWrapper> relationships, int start,
                                               int end) {
        return session.writeTransaction(tx -> {
            List<RelationshipWrapper> subList = relationships.subList(start, end);
            List<Map<String, Object>> relsDataList = subList.stream().map(rel -> {
                HashMap<String, Object> map = new HashMap<>();
                map.put("source_id", rel.getStartNode().getAllProperties().stream()
                        .filter(entry -> entry.getKey().equals("externalId")).map(Map.Entry::getValue).findFirst()
                        .orElse(rel.getStartNode().getId()));
                map.put("target_id", rel.getEndNode().getAllProperties().stream()
                        .filter(entry -> entry.getKey().equals("externalId")).map(Map.Entry::getValue).findFirst()
                        .orElse(rel.getEndNode().getId()));
                map.put("type", rel.getTypeString());
                map.put("properties", rel.getAllProperties().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                return map;
            }).collect(Collectors.toList());
//            Result r =
                    tx.run(RELS_QUERY, parameters("rels", relsDataList));
//            int relsCreated = r.list().get(0).values().get(0).asInt();
//            if (relsCreated != subList.size()) {
//                throw new IllegalStateException(
//                        "Not all relationships were created successfully, created " + relsCreated + " vs expected" +
//                                subList.size());
//            }
            return null;
        });
    }

}
