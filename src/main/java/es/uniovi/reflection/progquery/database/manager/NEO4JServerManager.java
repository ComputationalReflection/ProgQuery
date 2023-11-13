package es.uniovi.reflection.progquery.database.manager;

import es.uniovi.reflection.progquery.ProgQuery;
import es.uniovi.reflection.progquery.node_wrappers.Neo4jLazyNode;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.io.IOException;
import java.util.List;

public class NEO4JServerManager implements NEO4JManager {
    public static final String NEO4J_PROTOCOL = "bolt://";
    public static final String NEO4J_DEFAULT_DB = "neo4j";
    private Driver driver;
    private String db_name;
    @Override
    public NodeWrapper getProgramFromDB(String programId, String userId) {
        try {
            List<Record> programsIfAny = executeQuery(String.format("MATCH (p:PROGRAM) WHERE p.ID='%s' AND p.USER_ID='%s' RETURN ID(p)", programId, userId));
            if (programsIfAny.size() == 0)
                return null;
            return new Neo4jLazyNode(programsIfAny.get(0).get(0).asLong());
        } catch (Exception e) {
            System.err.println("Error getting program " + userId + ":" + programId + " from database.");
            return null;
        }
    }

    public NEO4JServerManager(String address, String user, String password) {
        this(address,user,password, NEO4JServerManager.NEO4J_DEFAULT_DB);
    }

    public NEO4JServerManager(String address, String user, String password, String db_name) {
        ProgQuery.LOGGER.info("Creating driver '" + NEO4J_PROTOCOL + address + "' created'");
        driver = GraphDatabase.driver(NEO4J_PROTOCOL + address,AuthTokens.basic(user, password));
        this.db_name = db_name;
    }

    private List<Record> executeQuery(String query) throws Exception {
        try(Session session = driver.session(SessionConfig.forDatabase(db_name))){
            return session.writeTransaction(tx -> tx.run(query).list());
        }
        catch (Exception e)
        {
            System.err.println("Error executing query '" + query + "'");
            throw new Exception("Error executing query to database " + db_name);
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
