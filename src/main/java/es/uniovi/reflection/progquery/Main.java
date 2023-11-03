package es.uniovi.reflection.progquery;

import java.util.*;

public class Main {
    //Example
    //-user=progquery -program=ExampleClasses -neo4j_database_path="C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\neo4j" -neo4j_mode=local "-d C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes -classpath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\target\classes; -sourcepath C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses\src\main\java; -g -nowarn -target 8 -source 8"
    public static Parameters parameters = new Parameters();

    public static void main(String[] args) {
        parseArguments(args);
        ProgQuery progquery = parameters.neo4j_mode.equals(OptionsConfiguration.NEO4J_MODE_SERVER) ?
                new ProgQuery(parameters.neo4j_host,parameters.neo4j_port_number,parameters.neo4j_user,parameters.neo4j_password,parameters.neo4j_database,parameters.userId,parameters.programId, parameters.verbose):
                new ProgQuery(parameters.neo4j_database_path,parameters.userId,parameters.programId,parameters.verbose);
        progquery.analyze(parameters.javac_options);
    }

    private static void parseArguments(String[] args) {
        setDefaultParameters();
        for (String parameter : args) {
            parseParameter(parameter);
        }
        if (parameters.javac_options.isEmpty()) {
            System.out.println(OptionsConfiguration.noJavacOptions);
            System.exit(2);
            return;
        }
        if (parameters.userId.isEmpty()) {
            System.out.println(OptionsConfiguration.noUser);
            System.exit(2);
            return;
        }
        if (parameters.programId.isEmpty()) {
            System.out.println(OptionsConfiguration.noProgram);
            System.exit(2);
            return;
        }
        if (Objects.equals(parameters.neo4j_mode, OptionsConfiguration.NEO4J_MODE_SERVER)) {
            if (parameters.neo4j_host.isEmpty()) {
                System.out.println(OptionsConfiguration.noHost);
                System.exit(2);
                return;
            }
            if (parameters.neo4j_password.isEmpty()) {
                System.out.println(OptionsConfiguration.noPassword);
                System.exit(2);
                return;
            }
            if (parameters.neo4j_database.isEmpty()) {
                parameters.neo4j_database = parameters.userId;
            }
        }
        else if (Objects.equals(parameters.neo4j_mode, OptionsConfiguration.NEO4J_MODE_LOCAL)) {
            if (parameters.neo4j_database_path.isEmpty()) {
                System.out.println(OptionsConfiguration.noDataBasePath);
                System.exit(2);
                return;
            }
        }
    }

    private static void setDefaultParameters() {
        parameters.neo4j_user = OptionsConfiguration.DEFAULT_NEO4J_USER;
        parameters.neo4j_port_number = OptionsConfiguration.DEFAULT_NEO4J_PORT_NUMBER;
        parameters.neo4j_mode = OptionsConfiguration.DEFAULT_NEO4J_MODE;
        parameters.max_operations_transaction = OptionsConfiguration.DEFAULT_MAX_OPERATIONS_TRANSACTION;
        parameters.verbose = OptionsConfiguration.DEFAULT_VERBOSE;
    }

    private static void parseParameter(String parameter) {
        for (String parameterPrefix : OptionsConfiguration.optionsPrefix) {
            if (parameter.startsWith(parameterPrefix)) {
                if(parseOption(parameter.substring(parameterPrefix.length()).toLowerCase()))
                    return;
            }
        }
        parameters.javac_options.add(parameter);
    }

    private static boolean parseOption(String option) {
        for (String opString : OptionsConfiguration.helpOptions) {
            if (option.equals(opString)) {
                System.out.println(OptionsConfiguration.helpMessage);
                System.exit(0);
            }
        }
        for (String opString : OptionsConfiguration.userOptions) {
            if (option.startsWith(opString)) {
                parameters.userId = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.programOptions) {
            if (option.startsWith(opString)) {
                parameters.programId = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_database_pathOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_database_path = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_databaseOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_database = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_userOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_user = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_passwordOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_password = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_hostOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_host = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_port_numberOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_port_number = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_modeOptions) {
            if (option.startsWith(opString)) {
                String modeOption = parseValue(option.substring(opString.length()));
                if (!Arrays.asList(OptionsConfiguration.neo4j_modeNames).contains(modeOption)) {
                    System.err.println(OptionsConfiguration.unknownNEO4JMode);
                    System.exit(2);
                }
                parameters.neo4j_mode = modeOption;
                return true;
            }
        }
        for (String opString : OptionsConfiguration.max_operations_transactionOptions) {
            if (option.startsWith(opString)) {
                parameters.max_operations_transaction = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : OptionsConfiguration.verboseOptions) {
            if (option.startsWith(opString)) {
                parameters.verbose = true;
                return true;
            }
        }
        return false;
    }

    private static String parseValue(String value) {
        for (String opAssignment : OptionsConfiguration.optionsAssignment)
            if (value.startsWith(opAssignment))
                return value.substring(opAssignment.length());
        System.err.println(OptionsConfiguration.errorMessage);
        System.exit(2);  // 2 == Bad option assignment
        return null;
    }
}