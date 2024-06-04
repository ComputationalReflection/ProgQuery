package es.uniovi.reflection.progquery.database.relations;

public enum CFGRelationTypes implements RelationTypesInterface {
	//CFG_FINALLY_TO_LAST_STMT, No represents control flow, just links a finally block with its special node

	/*Callable definitions with CFG Nodes*/
	CFG_ENTRIES,
	CFG_END_OF,
	/*Unconditional Control flow*/
	CFG_NEXT,
	CFG_THROWS,

	CFG_TRUE_CONDITION,
	CFG_FALSE_CONDITION,
	CFG_FOR_EACH_HAS_NEXT,
	CFG_FOR_EACH_NO_NEXT,
	CFG_SWITCH_MATCHES_CASE,
	CFG_SWITCH_DEFAULT_CASE,
	CFG_IF_PREVIOUS_BREAK,
	CFG_IF_PREVIOUS_CONTINUE,
	CFG_IF_NO_EXCEPTION,
	CFG_IF_UNCAUGHT_EXCEPTION,
	CFG_IF_CAUGHT_EXCEPTION,
	CFG_MAY_THROW;

	public static String getCFGRelations() {
		String ret = "";
		for (CFGRelationTypes cfgRel : CFGRelationTypes.values())
			ret += cfgRel.name() + " | ";
		return ret.substring(0, ret.length() - 3);
	}

}
