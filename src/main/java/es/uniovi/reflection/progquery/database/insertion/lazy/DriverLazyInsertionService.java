package es.uniovi.reflection.progquery.database.insertion.lazy;

import es.uniovi.reflection.progquery.ProgQuery;
import es.uniovi.reflection.progquery.database.manager.NEO4JServerManager;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.graphdb.Label;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String NODES_QUERY =
            "UNWIND $instances AS item\n" + "CREATE (n:$(item.labels))\n" + "SET n = item.props\n" +
                    "RETURN ID(n)";
    private static final String RELS_QUERY = "UNWIND $rels AS rel\n" + "MATCH (a), (b)\n" +
            "WHERE ID(a) = rel.source_id AND ID(b) = rel.target_id\n" +
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
        for (int i = 0; i < REPETITIONS; i++) {
            actionByPartsInParallel("node", info.nodeSet.size(), maxOperationsPerTransaction,
                    maxConcurrentWriteTransactions, false, sessionSupplier, (session, start, end) ->
                            executeNodeListOneQuery(session, info.nodeSet, start, end));
            actionByPartsInParallel("relationship", info.relSet.size(), maxOperationsPerTransaction,
                    maxConcurrentWriteTransactions, true, sessionSupplier, (session, start, end) ->
                            executeRelListOneQuery(session, info.relSet, start, end));
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

    private static void actionByPartsInParallel(String elementType, int listSize, int numberPerPart,
                                                int maxConcurrentWriteTransactions,
                                                boolean replayDeadlockedBatchesSequentially,
                                                Supplier<Session> sessionSupplier, SessionBatchAction action) {
        if (numberPerPart <= 0)
            throw new IllegalArgumentException("numberPerPart must be positive.");
        if (maxConcurrentWriteTransactions <= 0)
            throw new IllegalArgumentException("maxConcurrentWriteTransactions must be positive.");
        if (listSize == 0) {
            ProgQuery.LOGGER.info("No " + elementType + " write transaction batches to execute.");
            try (Session session = sessionSupplier.get()) {
                action.accept(session, 0, 0);
            }
            return;
        }

        int batchCount = (listSize + numberPerPart - 1) / numberPerPart;
        if (batchCount == 1) {
            executeBatch(elementType, new Batch(1, 0, listSize), batchCount, "calling thread", sessionSupplier, action);
            return;
        }

        int workerCount = Math.min(maxConcurrentWriteTransactions, batchCount);
        ProgQuery.LOGGER.info("Executing " + batchCount + " " + elementType + " write transaction batches with " +
                workerCount + " concurrent worker threads.");
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<BatchFuture> futures = new java.util.ArrayList<>(batchCount);
        List<Batch> deadlockedBatches = new java.util.ArrayList<>();
        boolean completed = false;
        try {
            int batchNumber = 1;
            for (int start = 0; start < listSize; start += numberPerPart) {
                Batch batch = new Batch(batchNumber++, start, Math.min(start + numberPerPart, listSize));
                futures.add(new BatchFuture(batch, executor.submit(() ->
                        executeBatch(elementType, batch, batchCount, "parallel worker", sessionSupplier, action))));
            }
            for (BatchFuture batchFuture : futures) {
                try {
                    batchFuture.future().get();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (replayDeadlockedBatchesSequentially && isDeadlock(cause)) {
                        deadlockedBatches.add(batchFuture.batch());
                        ProgQuery.LOGGER.warning("Relationship batch " + batchFuture.batch().number() + "/" + batchCount +
                                " deadlocked and will be replayed sequentially after parallel batches complete.");
                    } else {
                        throw propagateBatchFailure(cause);
                    }
                }
            }
            for (Batch batch : deadlockedBatches) {
                ProgQuery.LOGGER.info("Replaying deadlocked relationship batch " + batch.number() + "/" + batchCount +
                        " sequentially.");
                executeBatch(elementType, batch, batchCount, "sequential retry", sessionSupplier, action);
            }
            completed = true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Insertion interrupted while waiting for transaction batches.", exception);
        } finally {
            if (completed)
                executor.shutdown();
            else {
                futures.forEach(batchFuture -> batchFuture.future().cancel(true));
                executor.shutdownNow();
            }
        }
    }

    private static void executeBatch(String elementType, Batch batch, int batchCount, String executionMode,
                                     Supplier<Session> sessionSupplier, SessionBatchAction action) {
        ProgQuery.LOGGER.info("Starting " + elementType + " batch " + batch.number() + "/" + batchCount + " [" +
                batch.start() + ", " + batch.end() + ") on " + executionMode + ".");
        try (Session session = sessionSupplier.get()) {
            action.accept(session, batch.start(), batch.end());
        }
        ProgQuery.LOGGER.info("Completed " + elementType + " batch " + batch.number() + "/" + batchCount + " [" +
                batch.start() + ", " + batch.end() + ") on " + executionMode + ".");
    }

    private static boolean isDeadlock(Throwable throwable) {
        return throwable instanceof TransientException exception &&
                "Neo.TransientError.Transaction.DeadlockDetected".equals(exception.code());
    }

    private static RuntimeException propagateBatchFailure(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException)
            return runtimeException;
        return new IllegalStateException("A transaction batch failed.", cause);
    }

    private static Void executeNodeListOneQuery(Session session, List<NodeWrapper> nodes, int start, int end) {
        return session.writeTransaction(tx -> {
            List<NodeWrapper> subList = nodes.subList(start, end);
            List<Map<String, Object>> labelsAndPropsList = new java.util.ArrayList<>(subList.size());
            for (int i = 0; i < subList.size(); i++) {
                NodeWrapper n = subList.get(i);
                HashMap<String, Object> map = new HashMap<>();
                map.put("props", n.getAllProperties().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                map.put("labels", n.getLabels().stream().map(Label::name).collect(Collectors.toList()));
                labelsAndPropsList.add(map);
            }
            Result result = tx.run(NODES_QUERY, parameters("instances", labelsAndPropsList));
            for (int i = 0; i < subList.size(); i++) {
                subList.get(i).setId(result.next().values().get(0).asLong());
            }
            if (result.hasNext()) {
                throw new IllegalStateException("Not all nodes were created successfully.");
            }
            return null;
        });
    }

    private static Void executeRelListOneQuery(Session session, List<RelationshipWrapper> relationships,
                                               int start, int end) {
        return session.writeTransaction(tx -> {
            List<RelationshipWrapper> subList = relationships.subList(start, end);
            List<Map<String, Object>> relsDataList = subList.stream().map(rel -> {
                HashMap<String, Object> map = new HashMap<>();
                map.put("source_id", rel.getStartNode().getId());
                map.put("target_id", rel.getEndNode().getId());
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

    private static void logPendingElements(InfoToInsert info) {
        info.prepareInsertion();
        ProgQuery.LOGGER.info("Preparing insertion of " + info.nodeSet.size() + " nodes and " +
                info.relSet.size() + " relationships.");
    }

    @FunctionalInterface
    private interface SessionBatchAction {
        void accept(Session session, int start, int end);
    }

    private record Batch(int number, int start, int end) {}

    private record BatchFuture(Batch batch, Future<?> future) {}

}
