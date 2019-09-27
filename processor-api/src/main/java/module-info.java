module derive4j.processor.api {
    requires transitive derive4j.annotation;
    requires transitive java.compiler;
    requires transitive com.squareup.javapoet;

    exports org.derive4j.processor.api;
    exports org.derive4j.processor.api.model;
}
