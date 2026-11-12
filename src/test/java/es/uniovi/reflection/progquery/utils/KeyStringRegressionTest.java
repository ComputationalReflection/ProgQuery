package es.uniovi.reflection.progquery.utils;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.typeInfo.keys.*;
import es.uniovi.reflection.progquery.typeInfo.keys.type.*;
import es.uniovi.reflection.progquery.typeInfo.keys.var.*;
import es.uniovi.reflection.progquery.visitors.KeyTypeVisitor;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Semantic key names, including nested keys, must not expose Object.toString hashes. */
public final class KeyStringRegressionTest {
    private static final Pattern HASH = Pattern.compile("[\\w.$]+@[0-9a-fA-F]+\\b");
    private static final String SOURCE = """
            package fixture;
            class Sample<C extends Comparable<C>> {
                int member;
                int[] array;
                java.util.List<? extends Number> upper;
                java.util.List<? super String> lower;
                java.util.List<?> any;
                <T extends Number & Comparable<T>> T recursive(T value) throws java.io.IOException { return value; }
                String overloaded(String value) { return value; }
                Integer overloaded(Integer value) { return value; }
            }
            """;
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var firstFiles = compiler.getStandardFileManager(null, null, null);
             var secondFiles = compiler.getStandardFileManager(null, null, null)) {
            var first = task(compiler, firstFiles);
            var second = task(compiler, secondFiles);
            check(first.getElements().getTypeElement("fixture.Sample") != second.getElements().getTypeElement("fixture.Sample"),
                    "Expected distinct javac symbol universes");
            var left = keys(first);
            var right = keys(second);
            check(left.keySet().equals(right.keySet()), "Different fixture keys");
            for (var entry : left.entrySet()) {
                Object key = entry.getValue();
                check(key.getClass().getMethod("toString").getDeclaringClass() != Object.class,
                        "Inherited Object.toString: " + entry.getKey());
                int originalHash = key.hashCode();
                String text = key.toString();
                check(!HASH.matcher(text).find(), "Object hash in " + entry.getKey() + ": " + text);
                check(originalHash == key.hashCode(), "Formatting mutated a key");
                check(text.equals(right.get(entry.getKey()).toString()), "Context-dependent name: " + entry.getKey());
            }
            check(!left.get("overload.String").toString().equals(left.get("overload.Integer").toString()), "Overloads collapsed");
            check(!left.get("intersection").toString().equals(left.get("union").toString()), "Compound kinds collapsed");
            check(!left.get("capture.upper").toString().equals(left.get("capture.lower").toString()), "Capture bounds collapsed");
            check(!left.get("method.absentReceiver").toString().equals(left.get("method.nullReceiver").toString()), "Absent/null receiver collapsed");
            check(!left.get("method.absentReceiver").toString().equals(left.get("method.constructor").toString()), "Constructor flag lost");
            check(TypeNames.stable("java.util.List<capture#123 of ? extends Number>")
                    .equals("java.util.List<capture of ? extends Number>"), "Capture hash leaked from type description");
            check(TypeNames.stable("capture#123 of ? super String").equals(TypeNames.stable("capture#987 of ? super String")),
                    "Capture descriptions depend on identity");
            check(TypeNames.stable("capture#1234x").equals("capture#1234x"), "Non-diagnostic text was changed");
            check(TypeNames.stable(null) == null, "Absent type text changed");
            check(TypeNames.stable("@A(\"capture#12\") java.util.List<capture#34 of ?>")
                    .equals("@A(\"capture#12\") java.util.List<capture of ?>"), "Annotation literal was changed");
            check(TypeNames.stable("@A(\"escaped \\\" capture#12\") capture#34 of ?")
                    .equals("@A(\"escaped \\\" capture#12\") capture of ?"), "Escaped annotation literal was changed");
            System.out.println("Passed " + left.size() + " key representations in independent javac contexts, nested captures and recursive bounds.");
        } finally {
            JavacInfo.currentJavacInfo.remove();
        }
    }
    private static JavacTask task(JavaCompiler compiler, JavaFileManager files) throws Exception {
        var source = new SimpleJavaFileObject(URI.create("string:///fixture/Sample.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return SOURCE; }
        };
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, List.of(source));
        task.analyze();
        check(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR), diagnostics.getDiagnostics().toString());
        return task;
    }
    private static Map<String,Object> keys(JavacTask task) {
        JavacInfo.setJavacInfo(new JavacInfo(task));
        var result = new LinkedHashMap<String,Object>();
        var sample = task.getElements().getTypeElement("fixture.Sample");
        var string = task.getElements().getTypeElement("java.lang.String").asType();
        var object = task.getElements().getTypeElement("java.lang.Object").asType();
        result.put("class", sample.asType().accept(KeyTypeVisitor.INSTANCE, null));
        result.put("classVariable", sample.getTypeParameters().getFirst().asType().accept(KeyTypeVisitor.INSTANCE, null));
        result.put("primitive", task.getTypes().getPrimitiveType(TypeKind.INT).accept(KeyTypeVisitor.INSTANCE, null));
        for (var element : sample.getEnclosedElements()) {
            if (element instanceof VariableElement field) {
                result.put("field." + field.getSimpleName(), field.asType().accept(KeyTypeVisitor.INSTANCE, null));
                if (field.asType() instanceof DeclaredType declared) {
                    var captured = (DeclaredType) task.getTypes().capture(declared);
                    result.put("capture." + field.getSimpleName(), captured.getTypeArguments().getFirst().accept(KeyTypeVisitor.INSTANCE, null));
                    check(!TypeNames.stable(captured.toString()).matches(".*capture#\\d+.*"), "Actual javac capture hash remained");
                }
            } else if (element instanceof ExecutableElement method) {
                var symbol = (Symbol.MethodSymbol) method;
                if (method.getSimpleName().contentEquals("overloaded")) {
                    result.put("overload." + (method.getParameters().getFirst().asType().toString().endsWith("String") ? "String" : "Integer"), new CallableKey(symbol));
                } else if (method.getSimpleName().contentEquals("recursive")) {
                    result.put("callable", new CallableKey(symbol));
                    result.put("executable", method.asType().accept(KeyTypeVisitor.INSTANCE, null));
                    var variable = (TypeVariable) method.getTypeParameters().getFirst().asType();
                    result.put("methodVariable", variable.accept(KeyTypeVisitor.INSTANCE, null));
                    result.put("recursiveBound", variable.getUpperBound().accept(KeyTypeVisitor.INSTANCE, null));
                }
            }
        }
        var type = object.accept(KeyTypeVisitor.INSTANCE, null);
        var str = string.accept(KeyTypeVisitor.INSTANCE, null);
        result.put("intersection", new CompoundTypeKey(true, List.of(type, str)));
        result.put("union", new CompoundTypeKey(false, List.of(type, str)));
        result.put("method.absentReceiver", new MethodTypeKey(List.of(type), List.of(), List.of(), str, null, false));
        result.put("method.nullReceiver", new MethodTypeKey(List.of(type), List.of(), List.of(), str, NullTypeKey.NULL_TYPE_KEY, false));
        result.put("method.constructor", new MethodTypeKey(List.of(type), List.of(), List.of(), str, null, true));
        result.put("void", VoidTypeKey.VOID_TYPE_KEY);
        result.put("null", NullTypeKey.NULL_TYPE_KEY);
        result.put("none", NoneTypeKey.NONE_TYPE_KEY);
        result.put("error", ErrorTypeKey.ERROR_TYPE_KEY);
        result.put("unknown", UnknownTypeKey.UNKNOWN_TYPE_KEY);
        result.put("root", NoPackageKey.INSTANCE);
        result.put("parameterized.absent", new ParameterizedTypeKey(null, null));
        result.put("parameterized.absentArgument", new ParameterizedTypeKey(Arrays.asList((TypeKey) null), type));
        var field = (Symbol) sample.getEnclosedElements().stream().filter(e -> e.getSimpleName().contentEquals("member")).findFirst().orElseThrow();
        result.put("component", new ComponentKey(field));
        result.put("fieldKey", new FieldKey(field));
        result.put("localScope", new LocalScopeVarKey(field));
        result.put("this", new ThisKey((Symbol) sample));
        return result;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
