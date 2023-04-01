package org.acme.hilla.test.extension.deployment;

import dev.hilla.Endpoint;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import org.acme.hilla.test.extension.QuarkusEndpointConfiguration;
import org.acme.hilla.test.extension.QuarkusEndpointController;
import org.acme.hilla.test.extension.QuarkusEndpointProperties;
import org.acme.hilla.test.extension.QuarkusPushEndpoint;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;
import org.jboss.jandex.DotName;

class HillaTestExtensionProcessor {

    private static final String FEATURE = "hilla-test-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerJaxrsApplicationToFixApplicationPath(BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        producer.produce(new AdditionalIndexedClassesBuildItem("org.acme.hilla.test.extension.HillaApplication"));
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(QuarkusEndpointProperties.class));
        beans.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClasses("org.acme.hilla.test.extension.QuarkusEndpointControllerConfiguration")
                        .addBeanClasses(
                                QuarkusEndpointConfiguration.class,
                                QuarkusEndpointController.class
                        )
                        .setDefaultScope(BuiltinScope.SINGLETON.getName())
                        .setUnremovable()
                        .build());
    }


    @BuildStep
    void registerEndpoints(final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
                           BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(Endpoint.class.getName()),
                BuiltinScope.SINGLETON.getName()
        ));
    }

    @BuildStep
    void registerHillaPushServlet(BuildProducer<ServletBuildItem> servletProducer) {
        servletProducer.produce(ServletBuildItem
                .builder(AtmosphereServlet.class.getName(),
                        AtmosphereServlet.class.getName())
                .addMapping("/HILLA/push")
                .setAsyncSupported(true)
                .addInitParam(ApplicationConfig.JSR356_MAPPING_PATH, "/HILLA/push")
                .addInitParam(ApplicationConfig.BROADCASTER_CLASS, SimpleBroadcaster.class.getName())
                //.addInitParam(ApplicationConfig.JSR356_PATH_MAPPING_LENGTH, "0")
                .addInitParam(ApplicationConfig.ATMOSPHERE_HANDLER, QuarkusPushEndpoint.class.getName())
                .addInitParam(ApplicationConfig.ATMOSPHERE_INTERCEPTORS, AtmosphereResourceLifecycleInterceptor.class.getName()
                        + "," + TrackMessageSizeInterceptor.class.getName()
                        + "," + SuspendTrackerInterceptor.class.getName())
                .setLoadOnStartup(1).build());
    }


    @BuildStep
    ExcludedTypeBuildItem excludeEndpointProperties() {
        return new ExcludedTypeBuildItem("dev.hilla.EndpointProperties");
    }


    private String getMappingPath(String path) {
        String mappingPath;
        if (path.endsWith("/*")) {
            return path;
        }
        if (path.endsWith("/")) {
            mappingPath = path + "*";
        } else {
            mappingPath = path + "/*";
        }
        return mappingPath;
    }
}
