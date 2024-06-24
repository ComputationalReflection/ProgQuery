package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

import java.util.HashSet;
import java.util.Set;

public enum NodeCategory implements Label {
	PQ_NODE, //TOP Category
	AST_NODE, CFG_NODE, PDG_NODE, TYPE_NODE, PACKAGE_NODE,

	AST_TYPE(AST_NODE),

	DECLARATION,	//SUPERCLASS FOR DECLARATION,TYPE_DECLARATION,CALLABLE_DEC

	TYPE_DEC(DECLARATION, TYPE_NODE), //SUPERCLASS FOR CLASS INTERFACE ENUM

	CALLABLE_DEC(DECLARATION), // SUPERCLASS FOR CONSTRUCTOR AND METHODS

	VARIABLE_DEC(DECLARATION), //SUPERCLASS FOR PARAM_DEC, VAR_DEC, ATTR_DEC
	LOCAL_DEC(AST_NODE, VARIABLE_DEC), //SUPERCLASS FOR PARAM_DEC, VAR_DEC,
	//Currently there is no rel between a non-declared method and its parameters
	//So, LOCAL_DEC -> AST_NODE

	STATEMENT(AST_NODE),
	LOOP(STATEMENT),

	CASE_SECTION(AST_NODE),
	EXPRESSION(AST_NODE),
	CALL(EXPRESSION), //SUPERCLASS FOR METHOD_INV AND NEW_
	LVALUE(EXPRESSION),
	ANY_ASSIGNMENT(AST_NODE),
	SWITCH(AST_NODE),

	IDENTIFIER(AST_NODE),
	IDENTIFIER_SELECTION(AST_NODE);

	Set<NodeCategory> hypernyms = new HashSet<>();

	NodeCategory(NodeCategory... hypernyms){
		for(NodeCategory n: hypernyms)
			this.hypernyms.add(n);
	}
}