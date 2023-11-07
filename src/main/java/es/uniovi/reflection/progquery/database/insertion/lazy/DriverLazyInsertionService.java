package es.uniovi.reflection.progquery.database.insertion.lazy;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

import static org.neo4j.driver.Values.parameters;

public class DriverLazyInsertionService {
	private static final int REPETITIONS =1 ;

	public static void defaultDBInsertion(InfoToInsert info, String server_address, final String USER,
			final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION) {
		final String PROTOCOL = "neo4j://";
		try (final Driver driver = GraphDatabase.driver(PROTOCOL + server_address, AuthTokens.basic(USER, PASS));
				Session session = driver.session()) {
			final List<Pair<String, Object[]>> nodeInfo = info.getNodeQueriesInfo();
			for (int i = 0; i < REPETITIONS; i++) {
				actionByParts(info.nodeSet.size(), MAX_OPERATIONS_PER_TRANSACTION, (start, end) -> executeNodesQuery(session,
						info.nodeSet, nodeInfo, r -> r.list().get(0).values().get(0).asLong(), start, end));
				final List<Pair<String, Object[]>> relInfo = info.getRelQueriesInfo();
				actionByParts(info.relSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
						(start, end) -> executeRelsQuery(session, relInfo, start, end));
			}
		}
	}
	public static void insertToSpecificDB(InfoToInsert info, String server_address, final String USER,
			final String PASS, final int MAX_OPERATIONS_PER_TRANSACTION, String DB_NAME) {
		final String PROTOCOL = "neo4j://";
		SessionConfig configForDB=SessionConfig.forDatabase(DB_NAME);
		try (final Driver driver = GraphDatabase.driver(PROTOCOL + server_address, AuthTokens.basic(USER, PASS));
				Session session = driver.session(configForDB)) {

			final List<Pair<String, Object[]>> nodeInfo = info.getNodeQueriesInfo();
			for (int i = 0; i < REPETITIONS; i++) {
				actionByParts(info.nodeSet.size(), MAX_OPERATIONS_PER_TRANSACTION, (start, end) -> executeNodesQuery(session,
						info.nodeSet, nodeInfo, r -> r.list().get(0).values().get(0).asLong(), start, end));

				final List<Pair<String, Object[]>> relInfo = info.getRelQueriesInfo();
				actionByParts(info.relSet.size(), MAX_OPERATIONS_PER_TRANSACTION,
						(start, end) -> executeRelsQuery(session, relInfo, start, end));
			}
		}
	}

	public static <T> void actionByParts(int listSize, int numberPerPart, BiConsumer<Integer, Integer> action) {
		int i = 0;
		while ((i + 1) * numberPerPart < listSize)
			action.accept(i++ * numberPerPart, i * numberPerPart);
		action.accept(i * numberPerPart, listSize);
	}

	private static Void executeNodesQuery(Session session, List<NodeWrapper> nodes,
			List<Pair<String, Object[]>> nodeQueries, Function<Result, Long> resultF, int start, int end) {
		return session.writeTransaction(new TransactionWork<Void>() {

			@Override
			public Void execute(Transaction tx) {
				for (int i = start; i < end; i++) {
					NodeWrapper n = nodes.get(i);
					Pair<String, Object[]> queryAndParams = nodeQueries.get(i);
					n.setId(resultF.apply(tx.run(queryAndParams.getFirst(), parameters(queryAndParams.getSecond()))));
				}
				return null;
			}

		});
	}

	private static Void executeRelsQuery(Session session, List<Pair<String, Object[]>> relsQueries, int start,
			int end) {
		return session.writeTransaction(new TransactionWork<Void>() {
			@Override
			public Void execute(Transaction tx) {
				for (int i = start; i < end; i++) {
					Pair<String, Object[]> pair = relsQueries.get(i);
					tx.run(pair.getFirst(), parameters(pair.getSecond()));
				}
				return null;
			}
		});
	}
}
