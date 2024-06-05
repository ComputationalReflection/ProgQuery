package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

import java.util.HashSet;
import java.util.Set;

public enum NodeCategory implements Label {
	PQ_NODE, //TOP Category
	AST_NODE, CFG_NODE, PDG_NODE, TYPE_NODE, PACKAGE_NODE,

	AST_TYPE(AST_NODE),

	DEFINITION,	//SUPERCLASS FOR DECLARATION,TYPE_DEFINITION,CALLABLE_DEF

	TYPE_DEF(DEFINITION, TYPE_NODE), //SUPERCLASS FOR CLASS INTERFACE ENUM

	CALLABLE_DEF(DEFINITION), // SUPERCLASS FOR CONSTRUCTOR AND METHODS

	VARIABLE_DEC(DEFINITION), //SUPERCLASS FOR PARAM_DEC, VAR_DEC, ATTR_DEC
	LOCAL_DEC(AST_NODE, VARIABLE_DEC), //SUPERCLASS FOR PARAM_DEC, VAR_DEC,
	//Currently there is no rel between a non-declared method and its parameters
	//So, LOCAL_DEF -> AST_NODE

	STATEMENT(AST_NODE), STATEMENT_PART(AST_NODE),
	LOOP(STATEMENT),

	EXPRESSION(AST_NODE),
	CALL(EXPRESSION), //SUPERCLASS FOR METHOD_INV AND NEW_
	LVALUE(EXPRESSION),
	ANY_ASSIGNMENT(AST_NODE),

	IDENTIFIER(AST_NODE),
	IDENTIFIER_SELECTION(AST_NODE);

	Set<NodeCategory> hypernyms = new HashSet<>();

	NodeCategory(NodeCategory... hypernyms){
		for(NodeCategory n: hypernyms)
			this.hypernyms.add(n);
	}
}