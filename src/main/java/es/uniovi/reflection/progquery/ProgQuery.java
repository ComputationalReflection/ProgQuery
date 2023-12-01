package es.uniovi.reflection.progquery;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.typeInfo.PackageInfo;
import es.uniovi.reflection.progquery.utils.JavacInfo;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgQuery {
    public final static Logger LOGGER = Logger.getLogger(ProgQuery.class.getName());
    private CompilationScheduler compilationScheduler;
    private String programId;
    private String userId;

    public ProgQuery(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password, String neo4j_database, String max_operations_transaction, String userId, String programId, boolean verbose) {
        this.userId = userId;
        this.programId = programId;
        if(!verbose) LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
        compilationScheduler = new CompilationScheduler(neo4j_host,neo4j_port,neo4j_user,neo4j_password,neo4j_database,max_operations_transaction,programId,userId);
    }

    public ProgQuery(String neo4j_database_path, String neo4j_database, String userId, String programId, boolean verbose) {
        this.userId = userId;
        this.programId = programId;
        if(!verbose) LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
        compilationScheduler = new CompilationScheduler(neo4j_database_path,neo4j_database,programId,userId);
    }

    public List<String> insert(List<String> javac_options_list) {
        List<String> errors = new ArrayList<>();
        ProgQuery.LOGGER.info("Insertion started ...");
        for (String javac_options:javac_options_list)
            errors.addAll(compilationScheduler.newCompilationTask(javac_options));
        compilationScheduler.finalizeInsertion();
        if(errors.size() > 0)
            ProgQuery.LOGGER.info("Insertion completed with " + errors.size() + " errors.");
        else
            ProgQuery.LOGGER.info("Insertion completed without errors.");
        return errors;
    }

    public static void resetCaches(){
        DefinitionCache.TYPE_CACHE = new DefinitionCache<>();
        DefinitionCache.METHOD_DEF_CACHE = new DefinitionCache<>();
        PackageInfo.PACKAGE_INFO = new PackageInfo();
        InfoToInsert.INFO_TO_INSERT = new InfoToInsert();
        JavacInfo.setJavacInfo(null);
    }
}
