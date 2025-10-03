package es.uniovi.reflection.progquery.database.nodes;

public enum NestingTypes implements NodeLabel {
    TOP_LEVEL_TYPE("Top Level Type"), NESTED_TYPE("Nested Type"), MEMBER_TYPE("Member Type"), LOCAL_TYPE("Local Type"),
    ANONYMOUS_CLASS("Anonymous Class");


    private final String name;

    NestingTypes(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
