package es.uniovi.reflection.progquery.database.embedded;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;

public class EmbeddedDBBuilder {
    private static final String DEFAULT_DB_DIR = "", DEFAULT_DB_NAME = "neo4j";

    private DatabaseManagementService manager;
    private String database_name;

    public EmbeddedDBBuilder(String database_directory, String database_name) {
        this.database_name = database_name;
        manager = configuration(database_directory, database_name);
        registerShutdownHook(manager);
    }

    public EmbeddedDBBuilder() {
        this(DEFAULT_DB_DIR, DEFAULT_DB_NAME);
    }

    public GraphDatabaseService getNewEmbeddedDBService() { return manager.database(database_name); }

    private static void registerShutdownHook(final DatabaseManagementService managementService) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                managementService.shutdown();
            }
        });
    }

    private boolean existsDatabase(String dbName) {
        for (String existingName : manager.listDatabases())
            if (existingName.contentEquals(dbName))
                return true;
        return false;
    }

    public static DatabaseManagementService configuration(String database_directory, String database_name) {
        Path path = Paths.get(database_directory);
        DatabaseManagementServiceBuilder dbmsBuilder = new DatabaseManagementServiceBuilder(path);
        DatabaseManagementService managementService = dbmsBuilder.loadPropertiesFromFile(createNeo4jConfFile(path, database_name)).build();
        managementService.database(database_name);
        return managementService;
    }

    private static Path createNeo4jConfFile(Path path, String database_name) {
        File confDir = new File(path.toFile(), "conf");
        if (!confDir.exists()) {
            confDir.mkdirs();
        }
        File neo4jConf = new File(confDir, "neo4j.conf");

        try (FileWriter writer = new FileWriter(neo4jConf, false)) {
            writer.write("# Configuration for embedded Neo4j\n");
            writer.write("dbms.default_database="+ database_name);
        } catch (IOException e) {
            throw new RuntimeException("Error creating neo4j.conf file", e);
        }
        return neo4jConf.toPath();
    }

    public void shutdownManager() { manager.shutdown(); }
}
