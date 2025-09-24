package es.uniovi.reflection.progquery;

import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.pg.PackageManager;
import es.uniovi.reflection.progquery.utils.JavacInfo;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgQuery {
    public final static Logger LOGGER = Logger.getLogger(ProgQuery.class.getName());
    private final ASTAuxiliarStorage ast = new ASTAuxiliarStorage();
    private String programId;
    private String userId;
    private String neo4j_host;
    private String neo4j_port;
    private String neo4j_user;
    private String neo4j_password;
    private String neo4j_database;
    private String max_operations_transaction;
    private String neo4j_database_path;
    private boolean neo4j_server_mode;


    public ProgQuery(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password,
                     String neo4j_database, String max_operations_transaction, String userId, String programId,
                     boolean verbose) {
        this.userId = userId;
        this.programId = programId;

        this.neo4j_host = neo4j_host;
        this.neo4j_port = neo4j_port;
        this.neo4j_user = neo4j_user;
        this.neo4j_password = neo4j_password;
        this.neo4j_database = neo4j_database;
        this.max_operations_transaction = max_operations_transaction;
        this.neo4j_server_mode = true;

        if (!verbose)
            LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
    }

    public ProgQuery(String neo4j_database_path, String neo4j_database, String userId, String programId,
                     boolean verbose) {
        this.userId = userId;
        this.programId = programId;

        this.neo4j_database = neo4j_database;
        this.neo4j_database_path = neo4j_database_path;
        this.neo4j_server_mode = false;

        if (!verbose)
            LOGGER.setLevel(Level.OFF);
        org.neo4j.internal.unsafe.IllegalAccessLoggerSuppressor.suppress();
    }

    public List<String> insert(List<String> javac_options_list) {
        createCaches(ast);
        CompilationScheduler compilationScheduler;
        if (neo4j_server_mode)
            compilationScheduler =
                    new CompilationScheduler(ast, neo4j_host, neo4j_port, neo4j_user, neo4j_password, neo4j_database,
                            max_operations_transaction, programId, userId);
        else
            compilationScheduler =
                    new CompilationScheduler(ast, neo4j_database_path, neo4j_database, programId, userId);
        List<String> errors = new ArrayList<>();
        ProgQuery.LOGGER.info("Insertion started ...");
        for (String javac_options : javac_options_list)
            errors.addAll(compilationScheduler.newCompilationTask(javac_options));
        if (errors.stream().filter(error -> error.startsWith(Diagnostic.Kind.ERROR.toString())).count() == 0) {
            compilationScheduler.finalizeInsertion();
            if (errors.isEmpty())
                ProgQuery.LOGGER.info("Insertion completed without errors.");
            else
                ProgQuery.LOGGER.info("Insertion completed with warnings.");
        } else
            ProgQuery.LOGGER.info(errors.size() + " errors found, insertion aborted.");
        resetCaches();
        return errors;
    }

    public static void resetCaches() {
        ProgQuery.LOGGER.info("Resetting caches ...");
        DefinitionCache.TYPE_CACHE.remove();
        DefinitionCache.CALLABLE_DEC_CACHE.remove();
        DefinitionCache.COMPONENT_CACHE.remove();
        PackageManager.PACKAGE_MANAGER.remove();
        InfoToInsert.INFO_TO_INSERT.remove();
        JavacInfo.setJavacInfo(null);
    }

    public static void createCaches(ASTAuxiliarStorage ast) {
        ProgQuery.LOGGER.info("Creating caches ...");
        DefinitionCache.TYPE_CACHE.set(new DefinitionCache<>());
        DefinitionCache.CALLABLE_DEC_CACHE.set(new DefinitionCache<>());
        DefinitionCache.COMPONENT_CACHE.set(new DefinitionCache<>());
        PackageManager.PACKAGE_MANAGER.set(new PackageManager(ast));
        InfoToInsert.INFO_TO_INSERT.set(new InfoToInsert());
        JavacInfo.setJavacInfo(null);
    }
}
