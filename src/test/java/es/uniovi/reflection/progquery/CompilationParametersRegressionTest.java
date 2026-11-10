package es.uniovi.reflection.progquery;

/** Executable regression for compiler limits, aliases and constructor defaults. */
public final class CompilationParametersRegressionTest {
    public static void main(String[] args) {
        ProgQueryParameters defaults = new ProgQueryParameters();
        check("8".equals(defaults.max_concurrent_compilations), "Default must be eight");
        defaults.parseParameter("-max_concurrent_compilations=3");
        check("3".equals(defaults.max_concurrent_compilations), "Long option was ignored");
        defaults.parseParameter("-mcc=1");
        defaults.parseParameter("-mcwt=6");
        check("1".equals(defaults.max_concurrent_compilations), "Short option was ignored");
        check("6".equals(defaults.max_concurrent_write_transactions), "Write concurrency changed");
        check(defaults.javac_options.isEmpty(), "Compiler limit became a javac argument");

        ProgQuery legacy = new ProgQuery("host", "7687", "neo4j", "unused", "db", "80000", "4",
                "user", "program", true);
        check(legacy.compilationWorkers(14) == 8, "Existing constructor must default to eight");
        check(legacy.compilationWorkers(2) == 2, "Pool exceeds task count");
        check(server("1").compilationWorkers(14) == 1, "Sequential override was ignored");
        check(server("3").compilationWorkers(14) == 3, "Explicit limit was ignored");
        ProgQuery embedded = new ProgQuery("unused", "db", "user", "program", true);
        check(embedded.compilationWorkers(14) == 1, "Embedded mode must remain sequential");

        for (String invalid : new String[]{"0", "-1", "abc", "", "2147483648"}) {
            try {
                defaults.parseParameter("-mcc=" + invalid);
                throw new AssertionError("Invalid command-line limit accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Expected before a compiler or database is opened.
            }
            try {
                server(invalid);
                throw new AssertionError("Invalid constructor limit accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Programmatic callers use the same validation.
            }
        }
        System.out.println("Passed compilation limit regressions: defaults, aliases, bounds, constructors and embedded mode.");
    }

    private static ProgQuery server(String workers) {
        return new ProgQuery("host", "7687", "neo4j", "unused", "db", "80000", "4", workers,
                "user", "program", true);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
