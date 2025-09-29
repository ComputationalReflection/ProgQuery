package es.uniovi.reflection.progquery;

import javax.tools.Diagnostic;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Main {
    //Examples
    //Local
    //-user=progquery -program=ExampleClasses -neo4j_mode=local -neo4j_database_path=".\codeanalysis-tool\Programs\ExampleClasses\target\neo4j" "-d .\codeanalysis-tool\Programs\ExampleClasses\target\classes -classpath .\codeanalysis-tool\Programs\ExampleClasses\target\classes; -sourcepath .\codeanalysis-tool\Programs\ExampleClasses\src\main\java; -g -nowarn -target 8 -source 8"
    //Remote
    //-user=progquery -program=ExampleClasses -neo4j_mode=server -neo4j_host=192.168.137.100 -neo4j_password=secreto "-d .\codeanalysis-tool\Programs\ExampleClasses\target\classes -classpath .\codeanalysis-tool\Programs\ExampleClasses\target\classes; -sourcepath .\codeanalysis-tool\Programs\ExampleClasses\src\main\java; -g -nowarn -target 8 -source 8"
    public static void main(String[] args) {
        ProgQueryParameters parameters = ProgQueryParameters.parseArguments(args);
        ProgQuery progquery = parameters.neo4j_mode.equals(ProgQueryParameters.NEO4J_MODE_SERVER) ?
                new ProgQuery(parameters.neo4j_host,parameters.neo4j_port_number,parameters.neo4j_user,parameters.neo4j_password,parameters.neo4j_database,parameters.max_operations_transaction,parameters.userId,parameters.programId, parameters.verbose):
                new ProgQuery(parameters.neo4j_database_path, parameters.neo4j_database, parameters.userId,parameters.programId,parameters.verbose);

        System.out.print("\nBuilding " + parameters.userId + ":" + parameters.programId + "... ");
        Instant buildStart = Instant.now();
        List<String> insertResult = progquery.insert(parameters.javac_options);
        if(insertResult.stream().filter(error -> error.startsWith(Diagnostic.Kind.ERROR.toString())).count() == 0) {
            Instant buildFinish = Instant.now();
            System.out.println("completed [" + (Duration.between(buildStart, buildFinish).toMillis() / 1000)  + " s]");
        } else {
            System.err.println("Build failed! Use -verbose option for details.");
            System.err.println("Program '" + parameters.userId + ":" + parameters.programId + "' insertion failed.");
            String errors = String.join(System.getProperty("line.separator"), insertResult);
            System.err.println(errors);
            System.exit(1);
        }
    }
}