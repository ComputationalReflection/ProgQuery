package es.uniovi.reflection.progquery;

import java.util.*;

public class Main {
    //Examples
    //Local
    //-user=progquery -neo4j_mode=local -program=ExampleClasses -neo4j_database_path="C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\neo4j" "-d C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes -classpath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes; -sourcepath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\src\main\java; -g -nowarn -target 8 -source 8"
    //Remote
    //-user=progquery -program=ExampleClasses -neo4j_mode=server -neo4j_host=192.168.137.100 -neo4j_password=secreto "-d C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes -classpath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes; -sourcepath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\src\main\java; -g -nowarn -target 8 -source 8"

    public static ProgQueryParameters parameters = new ProgQueryParameters();

    public static void main(String[] args) {
        parseArguments(args);
        ProgQuery progquery = parameters.neo4j_mode.equals(ProgQueryParameters.NEO4J_MODE_SERVER) ?
                new ProgQuery(parameters.neo4j_host,parameters.neo4j_port_number,parameters.neo4j_user,parameters.neo4j_password,parameters.neo4j_database,parameters.max_operations_transaction,parameters.userId,parameters.programId, parameters.verbose):
                new ProgQuery(parameters.neo4j_database_path,parameters.userId,parameters.programId,parameters.verbose);
        progquery.analyze(parameters.javac_options);
    }

    private static void parseArguments(String[] args) {
        for (String parameter : args) {
            parameters.parseParameter(parameter);
        }
        if (parameters.javac_options.isEmpty()) {
            System.out.println(parameters.noJavacOptionsMessage);
            System.exit(2);
            return;
        }
        if (parameters.userId.isEmpty()) {
            System.out.println(parameters.noUserMessage);
            System.exit(2);
            return;
        }
        if (parameters.programId.isEmpty()) {
            System.out.println(parameters.noProgramMessage);
            System.exit(2);
            return;
        }
        if (parameters.neo4j_mode.equals(ProgQueryParameters.NEO4J_MODE_SERVER)) {
            if (parameters.neo4j_host.isEmpty()) {
                System.out.println(parameters.noHostMessage);
                System.exit(2);
                return;
            }
            if (parameters.neo4j_password.isEmpty()) {
                System.out.println(parameters.noPasswordMessage);
                System.exit(2);
                return;
            }
            if (parameters.neo4j_database.isEmpty()) {
                parameters.neo4j_database = parameters.userId;
            }
        }
        else if (parameters.neo4j_mode.equals(ProgQueryParameters.NEO4J_MODE_LOCAL)) {
            if (parameters.neo4j_database_path.isEmpty()) {
                System.out.println(parameters.noDataBasePathMessage);
                System.exit(2);
            }
        }
    }
}