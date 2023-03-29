package org.acme.hilla.test.extension.deployment;

import dev.hilla.Endpoint;
import dev.hilla.push.PushEndpoint;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import org.acme.hilla.test.extension.push.QuarkusPushEndpoint;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;
import org.jboss.jandex.DotName;
import org.springframework.web.context.support.AppCtxRecorder;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

class HillaTestExtensionProcessor {

    private static final String FEATURE = "hilla-test-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void addMissingSpringClasses(BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        System.out.println("============================================ YEP!!!");
        producer.produce(new AdditionalIndexedClassesBuildItem("org.springframework.core.env.EnvironmentCapable"));
        producer.produce(new AdditionalIndexedClassesBuildItem("org.springframework.beans.factory.ListableBeanFactory"));
    }

    @BuildStep
    void registerEndpoints(final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
                           BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        System.out.println("============================================ YEP 2!!!");
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf("io.quarkus.undertow.runtime.ServletProducer"));
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName
                        .createSimple(Endpoint.class.getName())
        ));
    }

    @BuildStep
    void registerHillaPushServlet(BuildProducer<ServletBuildItem> servletProducer) {
        System.out.println("============================================ registerHillaPushServlet!!!");
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

    @Record(RUNTIME_INIT)
    @BuildStep
    public void helloBuildStep(AppCtxRecorder recorder, BeanContainerBuildItem beanContainer) {
        //recorder.setAppCtx(beanContainer.getValue());
    }

    /*
    @BuildStep
    RemovedResourceBuildItem removeHillaEndpointsValidator() {
        System.out.println("============================================ YEP!!!");
        return new RemovedResourceBuildItem(ArtifactKey.fromString("dev.hilla:endpoint"),
                Set.of("dev/hilla/startup/EndpointsValidator.class"));
    }
     */

    /*
    @BuildStep
    BytecodeTransformerBuildItem boh() {
        System.out.println("============================================ YEP!!!");
        return new BytecodeTransformerBuildItem("dev.hilla.startup.EndpointsValidator", )
    }
     */

    @BuildStep
    ExcludedTypeBuildItem excludeEndpointProperties() {
        return new ExcludedTypeBuildItem("dev.hilla.EndpointProperties");
    }


}
