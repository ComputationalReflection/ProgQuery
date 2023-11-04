package es.uniovi.reflection.progquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProgQueryParameters {
    private static final String COPYRIGHT_MESSAGE =  "ProgQuery 3.0.0 - Computational Reflection Research Group (University of Oviedo)\n";

    private static final String HELP_MESSAGE = COPYRIGHT_MESSAGE + "\nOptions:\n" +
            "\t-help\n\t\tDisplays this usage message (Short form: -?).\n" +
            "\t-user=<user_id>\n\t\tUser id. (Short form:-u=<user_id>)\n" +
            "\t-program=<program_id>\n\t\tProgram identifier. (Short form:-p=<program_id>)\n" +
            "\t-neo4j_mode={local,server}\n\t\tNEO4J mode: local or server. (Default value is server, short form:-nm={local,server})\n" +
            "\t-neo4j_user=<user_name>\n\t\tNEO4J User name. (Default value is neo4j, short form:-nu=<user_name>)\n" +
            "\t-neo4j_password=<user_password>\n\t\tNEO4J User password. (Short form:-np=<user_pasword>)\n" +
            "\t-neo4j_host=<host>\n\t\tNEO4J Host address. (Short form:-nh=<host>)\n" +
            "\t-neo4j_port_number=<port_number>\n\t\tNEO4J Port number. (Default value is 7687, short form:-npn=<port_number>)\n" +
            "\t-neo4j_database=<database_name>\n\t\tNEO4J Database name. (Default value is the -user parameter value, short form:-ndb=<database_name>)\n" +
            "\t-neo4j_database_path=<database_path>\n\t\tNEO4J Database path, when Local mode is used. (Short form:-ndbp=<database_path>)\n" +
            "\t-max_operations_transaction=<number>\n\t\tMaximum number of operations per transaction. (Default value is 80000, short form:-mot=<number>)\n" +
            "\t\"javac_options_1\" ... \"javac_options_n\"\n\t\tA list of sets of options between \" separated by white spaces used to run the Java compiler several times.\n" +
            "\t-verbose\n\t\tShows log info (Default value is false).\n" +
            "\n";

    public String copyrightMessage;
    public String helpMessage;
    public String errorMessage;
    public String noUserMessage;
    public String noProgramMessage;
    public String noHostMessage;
    public String noJavacOptionsMessage;
    public String noPasswordMessage;
    public String noDataBasePathMessage;
    public String unknownNEO4JModeMessage;
    public String userId = "";
    public String programId = "";
    public String neo4j_database = "";
    public String neo4j_mode = DEFAULT_NEO4J_MODE;
    public String neo4j_database_path = "";
    public String neo4j_user = DEFAULT_NEO4J_USER;
    public String neo4j_password = "";
    public String neo4j_host = "";
    public String neo4j_port_number = DEFAULT_NEO4J_PORT_NUMBER;
    public String max_operations_transaction = DEFAULT_MAX_OPERATIONS_TRANSACTION;
    public boolean verbose = DEFAULT_VERBOSE;
    public List<String> javac_options = new ArrayList<>();

    public ProgQueryParameters(){
        this.copyrightMessage = COPYRIGHT_MESSAGE;
        this.helpMessage = HELP_MESSAGE;
        this.errorMessage = copyrightMessage + "\nSome error in the input parameters. Type -help for help.\n";
        this.noUserMessage = copyrightMessage + "\nNo user specified. Type -help for help.\n";
        this.noProgramMessage = copyrightMessage + "\nNo program specified. Type -help for help.\n";
        this.noHostMessage = copyrightMessage + "\nNo NEO4J host specified. Type -help for help.\n";
        this.noJavacOptionsMessage = copyrightMessage + "\nNo javac options specified. Type -help for help.\n";
        this.noPasswordMessage = copyrightMessage + "\nNo NEO4J user password specified. Type -help for help.\n";
        this.noDataBasePathMessage = copyrightMessage + "\nNo database path specified using NEO4J local mode. Type -help for help.\n";
        this.unknownNEO4JModeMessage = copyrightMessage + "\nUnknown neo4j mode option. Type -help for help.\n";
    }
    public static final String NEO4J_MODE_SERVER = "server";
    public static final String NEO4J_MODE_LOCAL = "local";
    public static final boolean DEFAULT_VERBOSE = false;
    public static final String DEFAULT_NEO4J_MODE = NEO4J_MODE_SERVER;
    public static final String DEFAULT_NEO4J_PORT_NUMBER = "7687";
    public static final String DEFAULT_NEO4J_USER = "neo4j";
    public static final String DEFAULT_MAX_OPERATIONS_TRANSACTION = "80000";
    protected static final String[] HELP_OPTIONS = { "help" , "?" };
    protected static final String[] USER_OPTIONS = { "user","u" };
    protected static final String[] PROGRAM_OPTIONS = { "program","p" };
    protected static final String[] NEO4J_DATABASE_OPTIONS = { "neo4j_database","ndb" };
    protected static final String[] NEO4J_DATABASE_PATH_OPTIONS = { "neo4j_database_path","ndbp" };
    protected static final String[] NEO4J_USER_OPTIONS = { "neo4j_user","nu" };
    protected static final String[] NEO4J_MODE_OPTIONS = { "neo4j_mode","nm" };
    protected static final String[] NEO4J_MODE_NAMES = { NEO4J_MODE_LOCAL, NEO4J_MODE_SERVER, "no" };
    protected static final String[] NEO4J_PASSWORD_OPTIONS = { "neo4j_password","np" };
    protected static final String[] NEO4J_HOST_OPTIONS = { "neo4j_host","nh" };
    protected static final String[] NEO4J_PORT_NUMBER_OPTIONS = { "neo4j_port_number","npn" };
    protected static final String[] MAX_OPERATIONS_TRANSACTION_OPTIONS = { "max_operations_transaction","mot" };
    protected static final String[] VERBOSE_OPTIONS = { "verbose" };
    protected static final String[] OPTIONS_PREFIX = { "-" };
    protected static final String[] OPTIONS_ASSIGNMENT = { "=" };

    public void parseParameter(String parameter) {
        for (String parameterPrefix : ProgQueryParameters.OPTIONS_PREFIX) {
            if (parameter.startsWith(parameterPrefix)) {
                if(this.parseOption(parameter.substring(parameterPrefix.length()).toLowerCase()))
                    return;
            }
        }
        this.javac_options.add(parameter);
    }
    protected boolean parseOption(String option) {
        for (String opString : ProgQueryParameters.HELP_OPTIONS) {
            if (option.equals(opString)) {
                System.out.println(this.helpMessage);
                System.exit(0);
            }
        }
        for (String opString : ProgQueryParameters.USER_OPTIONS) {
            if (option.startsWith(opString)) {
                this.userId = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.PROGRAM_OPTIONS) {
            if (option.startsWith(opString)) {
                this.programId = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_DATABASE_PATH_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_database_path = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_DATABASE_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_database = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_USER_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_user = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_PASSWORD_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_password = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_HOST_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_host = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_PORT_NUMBER_OPTIONS) {
            if (option.startsWith(opString)) {
                this.neo4j_port_number = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.NEO4J_MODE_OPTIONS) {
            if (option.startsWith(opString)) {
                String modeOption = parseValue(option.substring(opString.length()));
                if (!Arrays.asList(ProgQueryParameters.NEO4J_MODE_NAMES).contains(modeOption)) {
                    System.err.println(this.unknownNEO4JModeMessage);
                    System.exit(2);
                }
                this.neo4j_mode = modeOption;
                return true;
            }
        }
        for (String opString : ProgQueryParameters.MAX_OPERATIONS_TRANSACTION_OPTIONS) {
            if (option.startsWith(opString)) {
                this.max_operations_transaction = parseValue(option.substring(opString.length()));
                return true;
            }
        }
        for (String opString : ProgQueryParameters.VERBOSE_OPTIONS) {
            if (option.startsWith(opString)) {
                this.verbose = true;
                return true;
            }
        }
        return false;
    }

    protected String parseValue(String value) {
        for (String opAssignment : ProgQueryParameters.OPTIONS_ASSIGNMENT)
            if (value.startsWith(opAssignment))
                return value.substring(opAssignment.length());
        System.err.println(this.errorMessage);
        System.exit(2);  // 2 == Bad option assignment
        return null;
    }
}
