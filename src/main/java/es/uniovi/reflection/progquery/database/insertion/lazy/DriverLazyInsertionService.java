package es.uniovi.reflection.progquery.database.insertion.lazy;

import es.uniovi.reflection.progquery.ProgQuery;
import es.uniovi.reflection.progquery.database.manager.NEO4JServerManager;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import org.neo4j.driver.*;
import org.neo4j.graphdb.Label;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

public class DriverLazyInsertionService {
    private static final int REPETITIONS = 1;
    private static final String IMPORT_NODE_LABEL = "PQImportNode";
    private static final String IMPORT_ID_PROPERTY = "__pqImportId";
    private static final String IMPORT_NODE_ID_CONSTRAINT = "pq_import_node_id_unique";
    private static final String CREATE_IMPORT_NODE_ID_CONSTRAINT =
            "CREATE CONSTRAINT " + IMPORT_NODE_ID_CONSTRAINT + " IF NOT EXISTS " +
                    "FOR (n:" + IMPORT_NODE_LABEL + ") REQUIRE n." + IMPORT_ID_PROPERTY + " IS UNIQUE";
    private static final String NODES_QUERY =
            "UNWIND $instances AS item\n" + "CREATE (n:" + IMPORT_NODE_LABEL + ":$(item.labels))\n" +
                    "SET n = item.props\n" + "SET n." + IMPORT_ID_PROPERTY + " = item.import_id\n" +
                    "RETURN elementId(n)";
    private static final String RELS_QUERY = "UNWIND $rels AS rel\n" +
            "MATCH (a:" + IMPORT_NODE_LABEL + " {" + IMPORT_ID_PROPERTY + ": rel.source_id})\n" +
            "MATCH (b:" + IMPORT_NODE_LABEL + " {" + IMPORT_ID_PROPERTY + ": rel.target_id})\n" +
            "CREATE (a)-[r:$(rel.type)]->(b)\n" + "SET r = rel.properties"
            + "\nRETURN COUNT(r) AS created";

    public static void defaultDBInsertion(InfoToInsert info, String server_address, final String USER,
                                          final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION,
                                          final int MAX_CONCURRENT_WRITE_TRANSACTIONS) {
        try (final Driver driver = GraphDatabase.driver(NEO4JServerManager.NEO4J_PROTOCOL + server_address,
                AuthTokens.basic(USER, PASS))) {
            insertByPartsInParallel(info, MAX_OPERATIONS_PER_TRANSACTION, MAX_CONCURRENT_WRITE_TRANSACTIONS,
                    driver::session);
        }
    }

    public static void insertToSpecificDB(InfoToInsert info, String server_address, final String USER,
                                          final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION,
                                          final int MAX_CONCURRENT_WRITE_TRANSACTIONS, String DB_NAME) {
        SessionConfig configForDB = SessionConfig.forDatabase(DB_NAME);
        try (final Driver driver = GraphDatabase.driver(NEO4JServerManager.NEO4J_PROTOCOL + server_address,
                AuthTokens.basic(USER, PASS))) {
            insertByPartsInParallel(info, MAX_OPERATIONS_PER_TRANSACTION, MAX_CONCURRENT_WRITE_TRANSACTIONS,
                    () -> driver.session(configForDB));
        }
    }

    private static void insertByPartsInParallel(InfoToInsert info, int maxOperationsPerTransaction,
                                                int maxConcurrentWriteTransactions, Supplier<Session> sessionSupplier) {
        logPendingElements(info);
        Map<NodeWrapper, String> importIds = createImportIds(info.nodeSet);
        try (Session session = sessionSupplier.get()) {
            createImportNodeIdConstraint(session);
        }
        for (int i = 0; i < REPETITIONS; i++) {
            actionByPartsInParallel(info.nodeSet.size(), maxOperationsPerTransaction, maxConcurrentWriteTransactions,
                    sessionSupplier, (session, start, end) ->
                            executeNodeListOneQuery(session, info.nodeSet, importIds, start, end));
            actionByPartsInParallel(info.relSet.size(), maxOperationsPerTransaction, maxConcurrentWriteTransactions,
                    sessionSupplier, (session, start, end) ->
                            executeRelListOneQuery(session, info.relSet, importIds, start, end));
        }
    }

    public static <T> void actionByParts(int listSize, int numberPerPart, BiConsumer<Integer, Integer> action) {
        if (numberPerPart <= 0)
            throw new IllegalArgumentException("numberPerPart must be positive.");
        int i = 0;
        while ((i + 1) * numberPerPart < listSize)
            action.accept(i++ * numberPerPart, i * numberPerPart);
        action.accept(i * numberPerPart, listSize);
    }

    private static void actionByPartsInParallel(int listSize, int numberPerPart, int maxConcurrentWriteTransactions,
                                                Supplier<Session> sessionSupplier, SessionBatchAction action) {
        if (numberPerPart <= 0)
            throw new IllegalArgumentException("numberPerPart must be positive.");
        if (maxConcurrentWriteTransactions <= 0)
            throw new IllegalArgumentException("maxConcurrentWriteTransactions must be positive.");
        if (listSize == 0) {
            try (Session session = sessionSupplier.get()) {
                action.accept(session, 0, 0);
            }
            return;
        }

        int batchCount = (listSize + numberPerPart - 1) / numberPerPart;
        if (batchCount == 1) {
            ProgQuery.LOGGER.info("Executing one write transaction batch on the calling thread.");
            try (Session session = sessionSupplier.get()) {
                action.accept(session, 0, listSize);
            }
            return;
        }

        int workerCount = Math.min(maxConcurrentWriteTransactions, batchCount);
        ProgQuery.LOGGER.info("Executing " + batchCount + " write transaction batches with " + workerCount +
                " concurrent worker threads.");
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<?>> futures = new java.util.ArrayList<>(batchCount);
        boolean completed = false;
        try {
            for (int start = 0; start < listSize; start += numberPerPart) {
                int batchStart = start;
                int batchEnd = Math.min(start + numberPerPart, listSize);
                futures.add(executor.submit(() -> {
                    try (Session session = sessionSupplier.get()) {
                        action.accept(session, batchStart, batchEnd);
                    }
                }));
            }
            for (Future<?> future : futures)
                future.get();
            completed = true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Insertion interrupted while waiting for transaction batches.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw new IllegalStateException("A transaction batch failed.", cause);
        } finally {
            if (completed)
                executor.shutdown();
            else {
                futures.forEach(future -> future.cancel(true));
                executor.shutdownNow();
            }
        }
    }

    private static Void executeNodeListOneQuery(Session session, List<NodeWrapper> nodes,
                                                Map<NodeWrapper, String> importIds, int start, int end) {
        return session.writeTransaction(tx -> {
            List<NodeWrapper> subList = nodes.subList(start, end);
            List<Map<String, Object>> labelsAndPropsList = new java.util.ArrayList<>(subList.size());
            for (int i = 0; i < subList.size(); i++) {
                NodeWrapper n = subList.get(i);
                HashMap<String, Object> map = new HashMap<>();
                map.put("props", n.getAllProperties().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                map.put("labels", n.getLabels().stream().map(Label::name).collect(Collectors.toList()));
                map.put("import_id", getImportId(importIds, n, "node"));
                labelsAndPropsList.add(map);
            }
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

    private static Void executeRelListOneQuery(Session session, List<RelationshipWrapper> relationships,
                                               Map<NodeWrapper, String> importIds, int start, int end) {
        return session.writeTransaction(tx -> {
            List<RelationshipWrapper> subList = relationships.subList(start, end);
            List<Map<String, Object>> relsDataList = subList.stream().map(rel -> {
                HashMap<String, Object> map = new HashMap<>();
                map.put("source_id", getImportId(importIds, rel.getStartNode(), "source"));
                map.put("target_id", getImportId(importIds, rel.getEndNode(), "target"));
                map.put("type", rel.getTypeString());
                map.put("properties", rel.getAllProperties().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                return map;
            }).collect(Collectors.toList());
            Result result = tx.run(RELS_QUERY, parameters("rels", relsDataList));
            int relsCreated = result.single().get("created").asInt();
            if (relsCreated != subList.size()) {
                throw new IllegalStateException(
                        "Not all relationships were created successfully, created " + relsCreated + " vs expected " +
                                subList.size());
            }
            return null;
        });
    }

    private static Map<NodeWrapper, String> createImportIds(List<NodeWrapper> nodes) {
        String importPrefix = UUID.randomUUID().toString();
        Map<NodeWrapper, String> importIds = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++)
            importIds.put(nodes.get(i), importPrefix + ':' + i);
        return importIds;
    }

    private static String getImportId(Map<NodeWrapper, String> importIds, NodeWrapper node, String endpoint) {
        String importId = importIds.get(node);
        if (importId == null)
            throw new IllegalStateException("Relationship " + endpoint + " node is not part of this import.");
        return importId;
    }

    private static void createImportNodeIdConstraint(Session session) {
        session.run(CREATE_IMPORT_NODE_ID_CONSTRAINT).consume();
    }

    private static void logPendingElements(InfoToInsert info) {
        ProgQuery.LOGGER.info("Preparing insertion of " + info.nodeSet.size() + " nodes and " +
                info.relSet.size() + " relationships.");
    }

    @FunctionalInterface
    private interface SessionBatchAction {
        void accept(Session session, int start, int end);
    }

}
