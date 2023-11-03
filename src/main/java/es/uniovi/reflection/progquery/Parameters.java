package es.uniovi.reflection.progquery;

import java.util.ArrayList;
import java.util.List;

public class Parameters {
    public String userId = "";
    public String programId = "";
    public String neo4j_database = "";
    public String neo4j_mode = "";
    public String neo4j_database_path = "";
    public String neo4j_user = "";
    public String neo4j_password = "";
    public String neo4j_host = "";
    public String neo4j_port_number = "";
    public String max_operations_transaction = "";
    public boolean verbose;
    public List<String> javac_options = new ArrayList<>();
}
