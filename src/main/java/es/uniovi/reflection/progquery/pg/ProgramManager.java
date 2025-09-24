package es.uniovi.reflection.progquery.pg;

import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

import java.time.ZonedDateTime;

public class ProgramManager {
    private NodeWrapper currentProgram;

    NodeWrapper getCurrentProgram() {
        return currentProgram;
    }

    public void setCurrentProgram(NodeWrapper currentProgram) {
        this.currentProgram = currentProgram;
    }

    public void createCurrentProgram(String programID, String userID) {
        currentProgram = DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.PROGRAM);
        currentProgram.setProperty("ID", programID);
        currentProgram.setProperty("USER_ID", userID);
        currentProgram.setProperty("timestamp", ZonedDateTime.now().toString());
    }

}
