package es.uniovi.reflection.progquery.utils;

import com.sun.source.util.JavacTask;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.*;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executable regression test; run main with the same javac exports as ProgQuery. */
public final class JavacInfoRegressionTest {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager firstFiles = compiler.getStandardFileManager(null, null, null);
             StandardJavaFileManager secondFiles = compiler.getStandardFileManager(null, null, null)) {
            JavacTask first = task(compiler, firstFiles, "First", """
                    void object(Object value);
                    void arity();
                    void narrow(String value);
                    <T> void renamed(T value);
                    <T extends Comparable<T>> void recursive(T value);
                    <T extends Number> void differentBound(T value);
                    void parameterized(java.util.List<String> value);
                    void erased(java.util.List value);
                    <T> void reverseErasure(java.util.List<T> value);
                    void array(Object[] value);
                    String result(Object value) throws java.io.IOException;
                    void primitive(int value);
                    void inner(Outer<String>.Inner value);
                    void classVariable(C value);
                    """);
            JavacTask second = task(compiler, secondFiles, "Second", """
                    boolean object(Object value);
                    void arity(Object value);
                    void narrow(Object value);
                    <U> void renamed(U value);
                    <U extends Comparable<U>> void recursive(U value);
                    <U extends CharSequence> void differentBound(U value);
                    void parameterized(java.util.List<Integer> value);
                    <U> void erased(java.util.List<U> value);
                    void reverseErasure(java.util.List value);
                    void array(Object[] value);
                    Object result(Object value);
                    void primitive(long value);
                    void inner(Outer<Integer>.Inner value);
                    void classVariable(C value);
                    """);
            Map<String, ExecutableType> sub = signatures(first, "First");
            Map<String, ExecutableType> sup = signatures(second, "Second");
            JavacInfo info = new JavacInfo(second);
            JavacInfo.setJavacInfo(info);
            try {
                // Prove the fixture exercises different compiler symbol universes.
                var subObject = (com.sun.tools.javac.code.Type) sub.get("object").getParameterTypes().getFirst();
                var supObject = (com.sun.tools.javac.code.Type) sup.get("object").getParameterTypes().getFirst();
                if (subObject.tsym == supObject.tsym)
                    throw new AssertionError("Expected independent javac symbols");

                check(info, sub, sup, "object", true);
                check(info, sub, sup, "arity", false);
                check(info, sub, sup, "narrow", false);
                check(info, sub, sup, "renamed", true);
                check(info, sub, sup, "recursive", true);
                check(info, sub, sup, "differentBound", false);
                check(info, sub, sup, "parameterized", false);
                check(info, sub, sup, "erased", true);
                check(info, sub, sup, "reverseErasure", false);
                check(info, sub, sup, "array", true);
                check(info, sub, sup, "result", true);
                check(info, sub, sup, "primitive", false);
                check(info, sub, sup, "inner", false);
                check(info, sub, sup, "classVariable", false);
                if (!info.isSubSignatureOwn(sub.get("classVariable"), sub.get("classVariable")))
                    throw new AssertionError("Class type variable must match itself without recursing");

                Map<String, JavacInfo.MethodSignature> savedSub = snapshots(sub);
                Map<String, JavacInfo.MethodSignature> savedSup = snapshots(sup);
                // Match CompilationTask.call() cleanup with -XDuseUnsharedTable=true.
                com.sun.tools.javac.util.Names.instance(
                        ((com.sun.tools.javac.api.BasicJavacTask) first).getContext()).dispose();
                com.sun.tools.javac.util.Names.instance(
                        ((com.sun.tools.javac.api.BasicJavacTask) second).getContext()).dispose();
                for (String name : sub.keySet()) {
                    boolean expected = List.of("object", "renamed", "recursive", "erased", "array", "result")
                            .contains(name);
                    if (info.isSubSignatureOwn(savedSub.get(name), savedSup.get(name)) != expected)
                        throw new AssertionError("Saved signature after javac cleanup: " + name);
                }
                System.out.println("Passed 29 signature regression cases, including disposed javac name tables.");
            } finally {
                JavacInfo.currentJavacInfo.remove();
            }
        }
    }

    private static JavacTask task(JavaCompiler compiler, JavaFileManager files, String name, String methods)
            throws Exception {
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///test/" + name + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "package test; interface " + name + "<C> {" + methods + "} class Outer<T> { class Inner {} }";
            }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                List.of("-proc:none", "-XDuseUnsharedTable=true"), null,
                List.of(source));
        task.analyze();
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
            throw new AssertionError(diagnostics.getDiagnostics());
        return task;
    }

    private static Map<String, ExecutableType> signatures(JavacTask task, String name) {
        Map<String, ExecutableType> signatures = new HashMap<>();
        task.getElements().getTypeElement("test." + name).getEnclosedElements().forEach(element -> {
            if (element instanceof ExecutableElement method)
                signatures.put(method.getSimpleName().toString(), (ExecutableType) method.asType());
        });
        return signatures;
    }

    private static void check(JavacInfo info, Map<String, ExecutableType> sub, Map<String, ExecutableType> sup,
                              String name, boolean expected) {
        boolean actual = info.isSubSignatureOwn(sub.get(name), sup.get(name));
        if (actual != expected)
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    private static Map<String, JavacInfo.MethodSignature> snapshots(Map<String, ExecutableType> signatures) {
        Map<String, JavacInfo.MethodSignature> saved = new HashMap<>();
        signatures.forEach((name, type) -> saved.put(name, JavacInfo.signatureOf(type)));
        return saved;
    }
}
