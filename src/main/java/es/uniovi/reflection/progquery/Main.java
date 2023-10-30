package es.uniovi.reflection.progquery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    //-user=progquery -program=ExampleClasses -neo4j_host=156.35.94.130 -neo4j_database=debug -neo4j_password=secreto -src=C:\Users\VirtualUser\Source\Repos\StaticCodeAnalysis\Programs\ExampleClasses
    //-user=progquery -program=ExampleClasses -neo4j_database=debug -neo4j_mode=local -src=C:\Users\Miguel\Source\codeanalysis\codeanalysis-tool\Programs\ExampleClasses

    public static Parameters parameters = new Parameters();

    public static void main(String[] args) {
        parseArguments(args);
        String[] javac_options = new String[]
                {
                        "-nowarn",
                        "-d",
                        Paths.get(parameters.sourceFolder, "target", "classes").toAbsolutePath().toString(),
                        "-g",
                        "-target",
                        "15",
                        "-source",
                        "15",
                        "-classpath",
                        parameters.class_path,
                        "sourcepath",
                        Paths.get(parameters.sourceFolder, "src").toAbsolutePath().toString()
                };
        ProgQuery progquery = parameters.neo4j_mode.equals(OptionsConfiguration.DEFAULT_NEO4J_MODE)?
                new ProgQuery(parameters.neo4j_host,parameters.neo4j_port_number,parameters.neo4j_user,parameters.neo4j_password,parameters.neo4j_database,parameters.programId,parameters.userId, parameters.verbose):
                new ProgQuery(parameters.neo4j_database_path,parameters.programId,parameters.userId, parameters.verbose);
        progquery.analyze(Collections.singletonList(String.join(" ", javac_options)));
    }


    private static void parseArguments(String[] args) {
        setDefaultParameters();
        List<String> inputFileNames = new ArrayList<String>();
        for (String parameter : args) {
            parseParameter(parameter, inputFileNames);
        }

        if (parameters.userId.isEmpty()) {
            System.out.println(OptionsConfiguration.noUser);
            System.exit(0);
            return;
        }
        if (parameters.programId.isEmpty()) {
            System.out.println(OptionsConfiguration.noProgram);
            System.exit(0);
            return;
        }

        if (parameters.neo4j_mode == OptionsConfiguration.DEFAULT_NEO4J_MODE) { //server

            if (parameters.neo4j_host.isEmpty()) {
                System.out.println(OptionsConfiguration.noHost);
                System.exit(0);
                return;
            }
            if (parameters.neo4j_password.isEmpty()) {
                System.out.println(OptionsConfiguration.noPassword);
                System.exit(0);
                return;
            }
            if (parameters.neo4j_database.isEmpty()) {
                parameters.neo4j_database = parameters.userId;
            }
        }
        if (parameters.sourceFolder.isEmpty()) {
            System.out.println(OptionsConfiguration.noInputMessage);
            System.exit(0);
            return;
        }
    }

    private static void setDefaultParameters() {
        parameters.neo4j_user = OptionsConfiguration.DEFAULT_NEO4J_USER;
        parameters.neo4j_port_number = OptionsConfiguration.DEFAULT_NEO4J_PORT;
        parameters.neo4j_mode = OptionsConfiguration.DEFAULT_NEO4J_MODE;
        parameters.max_operations_transaction = OptionsConfiguration.DEFAULT_MAX_OPERATIONS_TRANSACTION;
        parameters.verbose = OptionsConfiguration.DEFAULT_VERBOSE;
    }

    private static void parseParameter(String parameter, List<String> inputFiles) {
        for (String parameterPrefix : OptionsConfiguration.optionsPrefix) {
            if (parameter.startsWith(parameterPrefix)) {
                parseOption(parameter.substring(parameterPrefix.length(), parameter.length()).toLowerCase());
                return;
            }
        }
        inputFiles.add(parameter);
    }

    private static void parseOption(String option) {
        for (String opString : OptionsConfiguration.helpOptions) {
            if (option.equals(opString)) {
                System.out.println(OptionsConfiguration.helpMessage);
                System.exit(0);
            }
        }
        for (String opString : OptionsConfiguration.userOptions) {
            if (option.startsWith(opString)) {
                parameters.userId = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.programOptions) {
            if (option.startsWith(opString)) {
                parameters.programId = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_databaseOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_database = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_database_pathOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_database_path = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_userOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_user = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_passwordOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_password = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.sourceFolderOptions) {
            if (option.startsWith(opString)) {
                parameters.sourceFolder = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_hostOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_host = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_port_numberOptions) {
            if (option.startsWith(opString)) {
                parameters.neo4j_port_number = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.neo4j_modeOptions) {
            if (option.startsWith(opString)) {
                String modeOption = parseValue(option.substring(opString.length(), option.length()));
                if (Arrays.asList(OptionsConfiguration.neo4j_modeNames).indexOf(modeOption) == -1) {
                    System.err.println(OptionsConfiguration.unknownNEO4JMode);
                    System.exit(1);
                }
                parameters.neo4j_mode = modeOption;
                return;
            }
        }
        for (String opString : OptionsConfiguration.max_operations_transactionOptions) {
            if (option.startsWith(opString)) {
                parameters.max_operations_transaction = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.classPathOptions) {
            if (option.startsWith(opString)) {
                parameters.class_path = parseValue(option.substring(opString.length(), option.length()));
                return;
            }
        }
        for (String opString : OptionsConfiguration.verboseOptions) {
            if (option.startsWith(opString)) {
                parameters.verbose = true;
                return;
            }
        }
        System.err.println(OptionsConfiguration.errorMessage);
        System.exit(1);  // 1 == Unknown option
    }

    private static String parseValue(String value) {
        for (String opAssignment : OptionsConfiguration.optionsAssignment)
            if (value.startsWith(opAssignment))
                return value.substring(opAssignment.length(), value.length());
        System.err.println(OptionsConfiguration.errorMessage);
        System.exit(2);  // 2 == Bad option assignment
        return null;
    }

    public static List<File> listFiles(String path) {
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {
            // We want to find only regular files
            return walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .map(f -> f.toAbsolutePath().toFile())
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<File>();
        }
    }

}
