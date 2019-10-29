module derive4j.processor {
    requires static auto.service.annotations;
    requires derive4j.processor.api;

    provides javax.annotation.processing.Processor
        with org.derive4j.processor.DerivingProcessor;

    uses org.derive4j.processor.api.ExtensionFactory;
    uses org.derive4j.processor.api.DerivatorFactory;
}
