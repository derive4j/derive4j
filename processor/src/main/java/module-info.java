module derive4j.processor {
    requires static auto.service.annotations;
    requires com.squareup.javapoet;
    requires java.compiler;
    requires derive4j.processor.api;
    requires derive4j.annotation;

    provides javax.annotation.processing.Processor
        with org.derive4j.processor.DerivingProcessor;

    uses org.derive4j.processor.api.ExtensionFactory;
    uses org.derive4j.processor.api.DerivatorFactory;
}
