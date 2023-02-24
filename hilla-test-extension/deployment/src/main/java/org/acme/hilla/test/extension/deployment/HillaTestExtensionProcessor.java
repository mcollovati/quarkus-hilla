package org.acme.hilla.test.extension.deployment;

import dev.hilla.Endpoint;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.DotName;

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
    /*
    @BuildStep
    RemovedResourceBuildItem removeHillaEndpointsValidator() {
        System.out.println("============================================ YEP!!!");
        return new RemovedResourceBuildItem(ArtifactKey.fromString("dev.hilla:endpoint"),
                Set.of("dev/hilla/startup/EndpointsValidator.class"));
    }

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
