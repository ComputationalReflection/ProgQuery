package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum NodeTypes implements NodeLabel {

    /*Package nodes*/
    PROGRAM("Program", NodeCategory.PACKAGE_NODE),
    PACKAGE("Package", NodeCategory.PACKAGE_NODE),
    MODULE("Module", NodeCategory.PACKAGE_NODE),
    EXPORTS_DIRECTIVE("Exports Directive", NodeCategory.PACKAGE_NODE),
    PROVIDES_DIRECTIVE("Provides Directive", NodeCategory.PACKAGE_NODE),
    OPENS_DIRECTIVE("Opens Directive", NodeCategory.PACKAGE_NODE),

    /*Type nodes*/
    ERROR_TYPE("Error Type", NodeCategory.TYPE_NODE),
    CALLABLE_TYPE("Callable Type", NodeCategory.TYPE_NODE),
    NULL_TYPE("Null Type", NodeCategory.TYPE_NODE),
    TYPE_VARIABLE("Type Variable", NodeCategory.TYPE_NODE),
    UNKNOWN_TYPE("Unknown Type", NodeCategory.TYPE_NODE),

    /*Polymorphic AST Types OR Type Nodes*/
    ARRAY_TYPE("Array Type"),
    INTERSECTION_TYPE("Intersection Type"),
    PARAMETERIZED_TYPE("Parameterized Type"),
    PRIMITIVE_TYPE("Primitive Type"),
    UNION_TYPE("Union Type"),
    WILDCARD_TYPE("Wildcard Type"),
    VOID_TYPE("Void Type"),

    /* Program Dependency Graph Nodes*/
    THIS_REFERENCE("This Reference", NodeCategory.PDG_NODE),

    /*Control Flow Graph Nodes*/
    CFG_NORMAL_END("CFG Normal End", NodeCategory.CFG_NODE),
    CFG_START("CFG Start", NodeCategory.CFG_NODE),
    CFG_EXCEPTIONAL_END("CFG Exceptional End", NodeCategory.CFG_NODE),
    CFG_FINALLY_END("CFG Finally End", NodeCategory.CFG_NODE),

    /*Polymorphic Declarations/Definitions AST Nodes or not */
    GENERIC_TYPE_DEC("Generic Type Declaration"),
    ANNOTATION_DEC("Annotation Declaration", NodeCategory.TYPE_DEC),
    ATTR_DEC("Attribute Declaration", NodeCategory.VARIABLE_DEC),
    CLASS_DEC("Class Declaration", NodeCategory.TYPE_DEC),
    CONSTRUCTOR_DEC("Constructor Declaration", NodeCategory.CALLABLE_DEC),
    ENUM_DEC("Enum Declaration", NodeCategory.TYPE_DEC),
    ENUM_ELEMENT("Enum Element", NodeCategory.VARIABLE_DEC),
    INTERFACE_DEC("Interface Declaration", NodeCategory.TYPE_DEC),
    METHOD_DEC("Method Declaration", NodeCategory.CALLABLE_DEC),
    RECORD_COMPONENT("Record Component", NodeCategory.DECLARATION),
    RECORD_DEC("Record Declaration", NodeCategory.TYPE_DEC),

    /*AST nodes*/
    ANNOTATION("Annotation", NodeCategory.AST_NODE),
    ORDINARY_CU("Ordinary Compilation Unit", NodeCategory.COMPILATION_UNIT),
    CU_SKIPPED_DEC("Skipped Compilation Unit Declaration", NodeCategory.AST_NODE),
    MODULAR_CU("Modular Compilation Unit", NodeCategory.COMPILATION_UNIT),
    ERRONEOUS_NODE("Erroneous Node", NodeCategory.AST_NODE),
    IMPORT("Import", NodeCategory.AST_NODE),
    INITIALIZATION("Initialization", NodeCategory.ANY_ASSIGNMENT),
    METHOD_IDENTIFIER("Method Identifier", NodeCategory.IDENTIFIER),
    METHOD_SELECTION("Method Selection", NodeCategory.IDENTIFIER_SELECTION),
    PACKAGE_IDENTIFIER("Package Identifier", NodeCategory.IDENTIFIER),
    PACKAGE_SELECTION("Package Selection", NodeCategory.IDENTIFIER_SELECTION),
    PARAMETER_DEC("Parameter Declaration", NodeCategory.LOCAL_DEC),
    UNKNOWN_IDENTIFIER("Unknown Identifier", NodeCategory.IDENTIFIER),

    /*AST Types*/
    ANNOTATED_TYPE("Annotated Type", NodeCategory.AST_TYPE),
    TYPE_IDENTIFIER("Type Identifier", NodeCategory.IDENTIFIER, NodeCategory.AST_TYPE),
    TYPE_SELECTION("Type Selection", NodeCategory.IDENTIFIER_SELECTION, NodeCategory.AST_TYPE),
    TYPE_PARAM("Type Parameter", NodeCategory.DECLARATION),
    VAR_TYPE("Var Type", NodeCategory.AST_TYPE),

    /* AST Patterns */
    TYPE_PATTERN("Type Pattern", NodeCategory.PATTERN, NodeCategory.LOCAL_DEC),
    DISCARD_PATTERN("Discard Pattern", NodeCategory.PATTERN),
    RECORD_PATTERN("Record Pattern", NodeCategory.PATTERN),

    /*AST Statements OR Statement Sections*/
    ASSERT_STATEMENT("Assert Statement", NodeCategory.STATEMENT),
    BLOCK("Block", NodeCategory.STATEMENT),
    BREAK_STATEMENT("Break Statement", NodeCategory.STATEMENT),
    CASE_COLON("Case Colon", NodeCategory.CASE),
    CASE_ARROW("Case Arrow", NodeCategory.CASE),
    CATCH_BLOCK("Catch Block", NodeCategory.AST_NODE),
    CONTINUE_STATEMENT("Continue Statement", NodeCategory.STATEMENT),
    DO_WHILE_LOOP("Do-While Loop", NodeCategory.LOOP),
    EMPTY_STATEMENT("Empty Statement", NodeCategory.STATEMENT),
    FOR_EACH_LOOP("For-Each Loop", NodeCategory.LOOP),
    EXPRESSION_STATEMENT("Expression Statement", NodeCategory.STATEMENT),
    FINALLY_BLOCK("Finally Block", NodeCategory.AST_NODE),
    FOR_LOOP("For Loop", NodeCategory.LOOP),
    IF_STATEMENT("If Statement", NodeCategory.STATEMENT),
    LABELED_STATEMENT("Labeled Statement", NodeCategory.STATEMENT),
    LOCAL_VAR_DEC("Local Variable Declaration", NodeCategory.LOCAL_DEC, NodeCategory.STATEMENT),
    RETURN_STATEMENT("Return Statement", NodeCategory.STATEMENT),
    SWITCH_STATEMENT("Switch Statement", NodeCategory.STATEMENT, NodeCategory.SWITCH),
    SYNCHRONIZED_BLOCK("Synchronized Block", NodeCategory.STATEMENT),
    THROW_STATEMENT("Throw Statement", NodeCategory.STATEMENT),
    TRY_STATEMENT("Try Statement", NodeCategory.STATEMENT),
    WHILE_LOOP("While Loop", NodeCategory.LOOP),
    YIELD_STATEMENT("Yield Statement", NodeCategory.STATEMENT),

    /*AST Expressions*/
    ARRAY_ACCESS("Array Access", NodeCategory.LVALUE),
    ASSIGNMENT("Assignment", NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    ATTR_SELECTION("Attribute Selection", NodeCategory.IDENTIFIER_SELECTION, NodeCategory.LVALUE),
    BINARY_OPERATION("Binary Operation", NodeCategory.EXPRESSION),
    CALLABLE_REFERENCE("Callable Reference", NodeCategory.EXPRESSION),
    COMPOUND_ASSIGNMENT("Compound Assignment", NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    CONDITIONAL_EXPRESSION("Conditional Expression", NodeCategory.EXPRESSION),
    INSTANCE_OF("Instance Of", NodeCategory.EXPRESSION),
    LAMBDA_EXPRESSION("Lambda Expression", NodeCategory.EXPRESSION),
    LITERAL("Literal", NodeCategory.EXPRESSION),
    METHOD_INVOCATION("Method Invocation", NodeCategory.CALL),
    RESERVED_INVOCATION("Reserved Invocation", NodeCategory.CALL),
    NEW_ARRAY("New Array", NodeCategory.EXPRESSION),
    NEW_INSTANCE("New Instance", NodeCategory.CALL),
    RESERVED_IDENTIFIER("Reserved Identifier", NodeCategory.IDENTIFIER, NodeCategory.EXPRESSION),
    RESERVED_SELECTION("Reserved Selection", NodeCategory.IDENTIFIER_SELECTION, NodeCategory.EXPRESSION),
    SWITCH_EXPRESSION("Switch Expression", NodeCategory.EXPRESSION, NodeCategory.SWITCH),
    TYPE_CAST("Type Cast", NodeCategory.EXPRESSION),
    UNARY_OPERATION("Unary Operation", NodeCategory.EXPRESSION),
    VARIABLE("Variable", NodeCategory.IDENTIFIER, NodeCategory.LVALUE),
    ;



    NodeTypes(String name, NodeCategory... hypernyms) {
        this(name);
        List<NodeCategory> hyperList = new ArrayList<>(Arrays.asList(hypernyms));
        for (int i = 0; i < hyperList.size(); i++) {
            NodeCategory superCategory = hyperList.get(i);
            if (!this.hypernyms.contains(superCategory)){
                this.hypernyms.add(superCategory);
                hyperList.addAll(superCategory.hypernyms);
            }
        }
    }


    NodeTypes(String name) {
        this.name = name;
        hypernyms = new ArrayList<>();
        hypernyms.add(NodeCategory.PQ_NODE);
    }
    private final String name;
    public String getName() {
        return name;
    }
    public final List<NodeCategory> hypernyms;
}
