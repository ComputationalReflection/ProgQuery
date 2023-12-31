package es.uniovi.reflection.progquery.database;

import es.uniovi.reflection.progquery.database.embedded.EmbeddedDBBuilder;
import es.uniovi.reflection.progquery.database.manager.EmbeddedManager;
import es.uniovi.reflection.progquery.database.manager.NEO4JManager;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.node_wrappers.Neo4jEmbeddedWrapperNode;

import java.io.File;

public class EmbeddedInsertion implements InsertionStrategy {
    private GraphDatabaseService gDBService;
    private Transaction currentTransaction;
    private EmbeddedDBBuilder dbBuilder;

    public EmbeddedInsertion() {
        dbBuilder = new EmbeddedDBBuilder();
        gDBService = dbBuilder.getNewEmbeddedDBService();
    }

    public EmbeddedInsertion(String database_path, String database) {
        dbBuilder = new EmbeddedDBBuilder(database_path, database);
        gDBService = dbBuilder.getNewEmbeddedDBService();
    }

    @Override
    public Neo4jEmbeddedWrapperNode createNode() {
        return new Neo4jEmbeddedWrapperNode(currentTransaction.createNode());
    }

    @Override
    public Neo4jEmbeddedWrapperNode createNode(NodeTypes label) {
        Neo4jEmbeddedWrapperNode wrapper = createNode();
        wrapper.getNode().addLabel(label);
        return wrapper;
    }

    @Override
    public Neo4jEmbeddedWrapperNode createNode(NodeTypes label, Object[] props) {
        Neo4jEmbeddedWrapperNode wrapper = createNode(label);
        addProps(wrapper, props);
        return wrapper;
    }


    @Override
    public Neo4jEmbeddedWrapperNode createNode(Object[] props) {
        Neo4jEmbeddedWrapperNode wrapper = createNode();
        addProps(wrapper, props);
        return wrapper;
    }

    private void addProps(Neo4jEmbeddedWrapperNode wrapper, Object[] props) {

        Node n = wrapper.getNode();
        for (int i = 0; i < props.length; i = i + 2)
            n.setProperty(props[i].toString(), props[i + 1]);
    }

    @Override
    public void startAnalysis() {
        currentTransaction = gDBService.beginTx();
    }

    @Override
    public void endAnalysis() {
        currentTransaction.commit();
        currentTransaction.close();
        dbBuilder.shutdownManager();
    }

    @Override
    public NEO4JManager getManager() {
        return new EmbeddedManager(currentTransaction);
    }
}
