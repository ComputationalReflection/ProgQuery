package es.uniovi.reflection.progquery.database.nodes;

import es.uniovi.reflection.progquery.CompilationScheduler;
import org.neo4j.graphdb.Label;

import java.util.HashSet;
import java.util.Set;

public enum NodeCategory implements NodeLabel{
	PQ_NODE("PQ Node"), //TOP Category
	AST_NODE("AST Node"),
	CFG_NODE("CFG Node"),
	PDG_NODE("PDG Node"),
	TYPE_NODE("Type Node"),
	PACKAGE_NODE("Package Node"),

	AST_TYPE("AST Type", AST_NODE),

	COMPILATION_UNIT("Compilation Unit", AST_NODE), //SUPERCLASS FOR FILES

	DECLARATION("Declaration"),    //SUPERCLASS FOR DECLARATION,TYPE_DECLARATION,CALLABLE_DEC

	TYPE_DEC("Type Declaration", DECLARATION, TYPE_NODE), //SUPERCLASS FOR CLASS INTERFACE ENUM

	CALLABLE_DEC("Callable Declaration", DECLARATION), // SUPERCLASS FOR CONSTRUCTOR AND METHODS

	VARIABLE_DEC("Variable Declaration", DECLARATION), //SUPERCLASS FOR PARAM_DEC, VAR_DEC, ATTR_DEC
	LOCAL_DEC("Local Declaration", AST_NODE, VARIABLE_DEC), //SUPERCLASS FOR PARAM_DEC, VAR_DEC,
	//Currently there is no rel between a non-declared method and its parameters
	//So, LOCAL_DEC -> AST_NODE

	STATEMENT("Statement", AST_NODE),
	LOOP("Loop", STATEMENT),

	CASE("Case", AST_NODE),

	PATTERN("Pattern", AST_NODE),

	EXPRESSION("Expression", AST_NODE),
	CALL("Call", EXPRESSION), //SUPERCLASS FOR METHOD_INV AND NEW_
	LVALUE("L-Value", EXPRESSION),
	ANY_ASSIGNMENT("Any Assignment", AST_NODE),
	UNARY_EXPRESSION("Unary Operation", NodeCategory.EXPRESSION),
	SWITCH("Switch", AST_NODE),

	IDENTIFIER("Identifier", AST_NODE),
	IDENTIFIER_SELECTION("Identifier Selection", AST_NODE);


	private final String name;
	Set<NodeCategory> hypernyms = new HashSet<>();

	NodeCategory(String name, NodeCategory... hypernyms){
		this.name = name;
		for(NodeCategory n: hypernyms)
			this.hypernyms.add(n);
	}

	public String getName() {
		return name;
	}
}