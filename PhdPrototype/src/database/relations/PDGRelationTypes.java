package database.relations;

public enum PDGRelationTypes implements RelationTypesInterface {

	MODIFIED_BY, STATE_MODIFIED_BY, USED_BY, STATE_MAY_BE_MODIFIED,
	// For the future
	RETURNS, RETURNS_A_PART_OF, MAY_RETURN, MAY_RETURN_A_PART_OF

	// Aqu� hay que acordarse de que siempre que haya una modificaci�n, en vez
	// de ligar la declaraci�n de la variable con el uso, se ligue la �ltima
	// modificaci�n (OJO problema con el CFG puede ser m�s complicado)
	// int x=2;--->x+2 if(cond){ x=4;-->x+3}
	// v STATE MAY CHANGE ........ | STATE CHANGE
	// x+7<-------------------------
	// Preguntar jose SSA
}
