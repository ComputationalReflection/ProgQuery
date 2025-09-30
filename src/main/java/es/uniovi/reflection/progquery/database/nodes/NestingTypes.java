package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

public enum NestingTypes implements NodeLabel {
    TOP_LEVEL_TYPE, NESTED_TYPE, MEMBER_TYPE, LOCAL_TYPE, ANONYMOUS_CLASS;

}
