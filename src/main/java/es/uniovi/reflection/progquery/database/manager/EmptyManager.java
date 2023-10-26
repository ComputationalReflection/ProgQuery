package es.uniovi.reflection.progquery.database.manager;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

public class EmptyManager implements NEO4JManager {
    @Override
    public NodeWrapper getProgramFromDB(String programId, String userId) {
        return null;
    }

    @Override
    public void close() {  }
}
