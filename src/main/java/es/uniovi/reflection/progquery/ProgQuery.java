package es.uniovi.reflection.progquery;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgQuery {
    public final static Logger LOGGER = Logger.getLogger(ProgQuery.class.getName());
    private CompilationScheduler compilationScheduler;
    private String programId;
    private String userId;

    public ProgQuery(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password, String neo4j_database, String userId, String programId, boolean verbose) {
        this.userId = userId;
        this.programId = programId;
        if(!verbose) LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
        compilationScheduler = new CompilationScheduler(neo4j_host,neo4j_port,neo4j_user,neo4j_password,neo4j_database,programId,userId);
    }

    public ProgQuery(String neo4j_database_path, String userId, String programId, boolean verbose) {
        this.userId = userId;
        this.programId = programId;
        if(!verbose) LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
        compilationScheduler = new CompilationScheduler(neo4j_database_path,programId,userId);
    }

    public void analyze(List<String> javac_options_list) {
        for (String javac_options:javac_options_list)
            compilationScheduler.newCompilationTask(javac_options);
        compilationScheduler.endAnalysis();
    }
}
