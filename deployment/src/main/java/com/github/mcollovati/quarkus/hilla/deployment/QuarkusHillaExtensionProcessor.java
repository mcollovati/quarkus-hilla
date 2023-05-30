package com.github.mcollovati.quarkus.hilla.deployment;

import com.github.mcollovati.quarkus.hilla.HillaAtmosphereObjectFactory;
import com.github.mcollovati.quarkus.hilla.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointController;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointProperties;
import com.github.mcollovati.quarkus.hilla.QuarkusViewAccessChecker;
import com.github.mcollovati.quarkus.hilla.deployment.asm.EndpointTransferMapperClassVisitor;
import com.github.mcollovati.quarkus.hilla.deployment.asm.PushEndpointClassVisitor;
import com.github.mcollovati.quarkus.hilla.deployment.asm.SpringReplacementsClassVisitor;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.EndpointInvoker;
import dev.hilla.EndpointRegistry;
import dev.hilla.endpointransfermapper.EndpointTransferMapper;
import dev.hilla.push.PushEndpoint;
import dev.hilla.push.PushMessageHandler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.undertow.deployment.IgnoredServletContainerInitializerBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;

class QuarkusHillaExtensionProcessor {

    private static final String FEATURE = "quarkus-hilla";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // EndpointsValidator checks for the presence of Spring, so it should be
    // ignored
    @BuildStep
    IgnoredServletContainerInitializerBuildItem ignoreEndpointsValidator() {
        return new IgnoredServletContainerInitializerBuildItem("dev.hilla.startup.EndpointsValidator");
    }

    // Configuring removed resources causes the index to be rebuilt, but the
    // hilla-jandex artifact does not contain any classes.
    // Adding a marker forces indexes to be build against Hilla artifacts.
    // Removed resources should also be configured for the endpoint artifact.
    @BuildStep
    void addMarkersForHillaJars(BuildProducer<AdditionalApplicationArchiveMarkerBuildItem> producer) {
        producer.produce(new AdditionalApplicationArchiveMarkerBuildItem("dev/hilla"));
    }

    @BuildStep
    void registerJaxrsApplicationToFixApplicationPath(BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        producer.produce(new AdditionalIndexedClassesBuildItem(QuarkusEndpointController.class.getName()));
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(QuarkusEndpointProperties.class));
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses("com.github.mcollovati.quarkus.hilla.QuarkusEndpointControllerConfiguration")
                .addBeanClasses(QuarkusEndpointConfiguration.class, QuarkusEndpointController.class)
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable()
                .build());

        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(PushEndpoint.class, PushMessageHandler.class)
                .setDefaultScope(BuiltinScope.APPLICATION.getName())
                .setUnremovable()
                .build());
    }

    @BuildStep
    void registerEndpoints(
            final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(Endpoint.class.getName()), BuiltinScope.SINGLETON.getName()));
    }

    @BuildStep
    void registerHillaPushServlet(
            BuildProducer<ServletBuildItem> servletProducer,
            BuildProducer<GeneratedResourceBuildItem> resourceProducer) {
        servletProducer.produce(
                ServletBuildItem.builder(AtmosphereServlet.class.getName(), AtmosphereServlet.class.getName())
                        .addMapping("/HILLA/push")
                        .setAsyncSupported(true)
                        .addInitParam(ApplicationConfig.JSR356_MAPPING_PATH, "/HILLA/push")
                        .addInitParam(ApplicationConfig.BROADCASTER_CLASS, SimpleBroadcaster.class.getName())
                        .addInitParam(ApplicationConfig.ATMOSPHERE_HANDLER, PushEndpoint.class.getName())
                        .addInitParam(ApplicationConfig.OBJECT_FACTORY, HillaAtmosphereObjectFactory.class.getName())
                        .addInitParam(
                                ApplicationConfig.ATMOSPHERE_INTERCEPTORS,
                                AtmosphereResourceLifecycleInterceptor.class.getName()
                                        + ","
                                        + TrackMessageSizeInterceptor.class.getName()
                                        + ","
                                        + SuspendTrackerInterceptor.class.getName())
                        .setLoadOnStartup(1)
                        .build());
    }

    @BuildStep
    void replaceCallsToSpring(
            BuildProducer<BytecodeTransformerBuildItem> producer, final CombinedIndexBuildItem index) {
        producer.produce(new BytecodeTransformerBuildItem(
                EndpointRegistry.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(classVisitor, "registerEndpoint")));
        producer.produce(new BytecodeTransformerBuildItem(
                PushEndpoint.class.getName(), (s, classVisitor) -> new PushEndpointClassVisitor(classVisitor)));
        producer.produce(new BytecodeTransformerBuildItem(
                EndpointInvoker.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(classVisitor, "invokeVaadinEndpointMethod")));
        producer.produce(new BytecodeTransformerBuildItem(
                PushMessageHandler.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(classVisitor, "handleBrowserSubscribe")));
        producer.produce(new BytecodeTransformerBuildItem(
                EndpointTransferMapper.class.getName(),
                (s, classVisitor) -> new EndpointTransferMapperClassVisitor(classVisitor)));
    }

    @BuildStep
    void replaceFieldAutowiredAnnotations(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName autowiredAnnotation = DotName.createSimple("org.springframework.beans.factory.annotation.Autowired");
        Predicate<AnnotationInstance> isAutowiredAnnotation = ann -> ann.name().equals(autowiredAnnotation);
        Set<DotName> classesToTransform = Set.of(
                DotName.createSimple("dev.hilla.push.PushEndpoint"),
                DotName.createSimple("dev.hilla.push.PushMessageHandler"));
        producer.produce(new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {

            @Override
            public boolean appliesTo(AnnotationTarget.Kind kind) {
                return AnnotationTarget.Kind.FIELD == kind;
            }

            @Override
            public void transform(TransformationContext ctx) {
                FieldInfo fieldInfo = ctx.getTarget().asField();
                if (classesToTransform.contains(fieldInfo.declaringClass().name())
                        && ctx.getAnnotations().stream().anyMatch(isAutowiredAnnotation)) {
                    ctx.transform()
                            .remove(isAutowiredAnnotation)
                            .add(DotNames.INJECT)
                            .done();
                }
            }
        }));
    }

    @BuildStep
    void registerHillaSecurityPolicy(
            HttpBuildTimeConfig buildTimeConfig, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (buildTimeConfig.auth.form.enabled) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(HillaSecurityPolicy.class)
                    .setDefaultScope(DotNames.SINGLETON)
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHillaFormAuthenticationMechanism(
            HttpBuildTimeConfig httpBuildTimeConfig,
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        if (httpBuildTimeConfig.auth.form.enabled) {
            producer.produce(SyntheticBeanBuildItem.configure(HillaFormAuthenticationMechanism.class)
                    .types(HttpAuthenticationMechanism.class)
                    .setRuntimeInit()
                    .scope(Singleton.class)
                    .alternativePriority(1)
                    .supplier(recorder.setupFormAuthenticationMechanism())
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void configureHillaSecurityComponents(HillaSecurityRecorder recorder, BeanContainerBuildItem beanContainer) {
        recorder.configureHttpSecurityPolicy(beanContainer.getValue());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureFlowViewAccessChecker(
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer,
            Optional<FlowViewAccessCheckerBuildItem> viewAccessCheckerBuildItem) {
        viewAccessCheckerBuildItem
                .map(FlowViewAccessCheckerBuildItem::getLoginPath)
                .ifPresent(loginPath -> recorder.configureFlowViewAccessChecker(beanContainer.getValue(), loginPath));
    }

    @BuildStep
    void registerViewAccessChecker(
            HttpBuildTimeConfig buildTimeConfig,
            CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<FlowViewAccessCheckerBuildItem> loginProducer) {

        Set<DotName> securityAnnotations = Set.of(
                DotName.createSimple(DenyAll.class.getName()),
                DotName.createSimple(AnonymousAllowed.class.getName()),
                DotName.createSimple(RolesAllowed.class.getName()),
                DotName.createSimple(PermitAll.class.getName()));
        boolean hasSecuredRoutes =
                index.getComputingIndex().getAnnotations(DotName.createSimple(Route.class.getName())).stream()
                        .flatMap(route -> route.target().annotations().stream().map(AnnotationInstance::name))
                        .anyMatch(securityAnnotations::contains);

        if (buildTimeConfig.auth.form.enabled) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(HillaSecurityPolicy.class)
                    .setDefaultScope(DotNames.SINGLETON)
                    .setUnremovable()
                    .build());

            if (hasSecuredRoutes) {
                beans.produce(AdditionalBeanBuildItem.builder()
                        .addBeanClasses(QuarkusViewAccessChecker.class, QuarkusViewAccessChecker.Installer.class)
                        .setUnremovable()
                        .build());
                buildTimeConfig
                        .auth
                        .form
                        .loginPage
                        .map(FlowViewAccessCheckerBuildItem::new)
                        .ifPresent(loginProducer::produce);
            }
        }
    }

    public static final class FlowViewAccessCheckerBuildItem extends SimpleBuildItem {

        private final String loginPath;

        public FlowViewAccessCheckerBuildItem(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginPath() {
            return loginPath;
        }
    }
}
