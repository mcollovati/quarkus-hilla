/*
 * Copyright 2023 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.deployment;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.push.PushEndpoint;
import com.vaadin.hilla.push.PushMessageHandler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExcludeDependencyBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.undertow.deployment.IgnoredServletContainerInitializerBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;

import com.github.mcollovati.quarkus.hilla.BodyHandlerRecorder;
import com.github.mcollovati.quarkus.hilla.HillaAtmosphereObjectFactory;
import com.github.mcollovati.quarkus.hilla.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.NonNullApi;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointController;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointProperties;
import com.github.mcollovati.quarkus.hilla.QuarkusNavigationAccessControl;
import com.github.mcollovati.quarkus.hilla.QuarkusVaadinServiceListenerPropagator;
import com.github.mcollovati.quarkus.hilla.crud.FilterableRepositorySupport;
import com.github.mcollovati.quarkus.hilla.deployment.asm.SpringReplacer;

class QuarkusHillaExtensionProcessor {

    private static final String FEATURE = "quarkus-hilla";
    public static final String SPRING_DATA_SUPPORT = "com.github.mcollovati.quarkus.hilla.spring-data-jpa-support";
    public static final String PANACHE_SUPPORT = "com.github.mcollovati.quarkus.hilla.panache-support";
    public static final DotName SPRING_FILTERABLE_REPOSITORY =
            DotName.createSimple("com.github.mcollovati.quarkus.hilla.crud.spring.FilterableRepository");
    public static final DotName PANACHE_FILTERABLE_REPOSITORY =
            DotName.createSimple("com.github.mcollovati.quarkus.hilla.crud.panache.FilterableRepository");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    QuarkusHillaEnvironmentBuildItem detectQuarkusHillaMode(CurateOutcomeBuildItem outcomeBuildItem) {
        boolean quarkusVaadinPresent = outcomeBuildItem.getApplicationModel().getDependencies().stream()
                .anyMatch(dep -> "com.vaadin".equals(dep.getGroupId())
                        && dep.getArtifactId().startsWith("vaadin-quarkus"));
        return new QuarkusHillaEnvironmentBuildItem(quarkusVaadinPresent);
    }

    @BuildStep
    void publishCapabilities(
            BuildProducer<CapabilityBuildItem> capabilityProducer, CurateOutcomeBuildItem outcomeBuildItem) {
        boolean springDataJpaPresent = outcomeBuildItem.getApplicationModel().getDependencies().stream()
                .anyMatch(dep -> "io.quarkus".equals(dep.getGroupId())
                        && dep.getArtifactId().startsWith("quarkus-spring-data-jpa"));
        if (springDataJpaPresent) {
            capabilityProducer.produce(new CapabilityBuildItem(SPRING_DATA_SUPPORT, "quarkus-hilla"));
        }
        boolean panachePresent = outcomeBuildItem.getApplicationModel().getDependencies().stream()
                .anyMatch(dep -> "io.quarkus".equals(dep.getGroupId())
                        && dep.getArtifactId().startsWith("quarkus-hibernate-orm-panache"));
        if (panachePresent) {
            capabilityProducer.produce(new CapabilityBuildItem(PANACHE_SUPPORT, "quarkus-hilla"));
        }
    }

    @BuildStep
    void setupCrudAndListServiceSupport(
            Capabilities capabilities,
            BuildProducer<ExcludeDependencyBuildItem> producer,
            BuildProducer<AdditionalIndexedClassesBuildItem> additionalClasses) {
        if (capabilities.isPresent(SPRING_DATA_SUPPORT)) {
            producer.produce(new ExcludeDependencyBuildItem("com.github.mcollovati", "hilla-shaded-deps"));
            additionalClasses.produce(new AdditionalIndexedClassesBuildItem(
                    SPRING_FILTERABLE_REPOSITORY.toString(), FilterableRepositorySupport.class.getName()));
        }
        if (capabilities.isPresent(PANACHE_SUPPORT)) {
            additionalClasses.produce(new AdditionalIndexedClassesBuildItem(
                    PANACHE_FILTERABLE_REPOSITORY.toString(), FilterableRepositorySupport.class.getName()));
        }
    }

    @BuildStep
    void detectFilterableRepositoryImplementors(
            Capabilities capabilities,
            CombinedIndexBuildItem index,
            BuildProducer<FilterableRepositoryImplementorBuildItem> producer) {
        IndexView indexView = index.getComputingIndex();
        if (capabilities.isPresent(SPRING_DATA_SUPPORT)) {
            indexView
                    .getKnownDirectImplementors(SPRING_FILTERABLE_REPOSITORY)
                    .forEach(ci -> producer.produce(
                            new FilterableRepositoryImplementorBuildItem(SPRING_FILTERABLE_REPOSITORY, ci.name())));
        }
        if (capabilities.isPresent(PANACHE_SUPPORT)) {
            indexView
                    .getKnownDirectImplementors(PANACHE_FILTERABLE_REPOSITORY)
                    .forEach(ci -> producer.produce(
                            new FilterableRepositoryImplementorBuildItem(PANACHE_FILTERABLE_REPOSITORY, ci.name())));
        }
    }

    @BuildStep
    void implementFilterableRepositories(
            CombinedIndexBuildItem index,
            BuildProducer<BytecodeTransformerBuildItem> producer,
            List<FilterableRepositoryImplementorBuildItem> filterableRepoImplementors) {
        IndexView indexView = index.getComputingIndex();
        filterableRepoImplementors.forEach(item -> {
            FilterableRepositoryImplementor visitorFunction =
                    new FilterableRepositoryImplementor(indexView, item.getFilterableInterface());
            producer.produce(new BytecodeTransformerBuildItem.Builder()
                    .setClassToTransform(item.getImplementor().toString())
                    .setVisitorFunction(visitorFunction)
                    .build());
        });
    }

    // In hybrid environment sometimes the requests hangs while reading body, causing the UI to freeze until read
    // timeout is reached.
    // Requiring the installation of vert.x body handler seems to fix the issue.
    // See https://github.com/mcollovati/quarkus-hilla/issues/182
    // See https://github.com/mcollovati/quarkus-hilla/issues/490
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void installRequestBodyHandler(
            BodyHandlerRecorder recorder,
            QuarkusHillaEnvironmentBuildItem quarkusHillaEnv,
            BodyHandlerBuildItem bodyHandlerBuildItem,
            BuildProducer<FilterBuildItem> producer) {
        if (quarkusHillaEnv.isHybrid()) {
            producer.produce(new FilterBuildItem(recorder.installBodyHandler(bodyHandlerBuildItem.getHandler()), 120));
        }
    }

    // EndpointsValidator checks for the presence of Spring, so it should be
    // ignored
    @BuildStep
    IgnoredServletContainerInitializerBuildItem ignoreEndpointsValidator() {
        return new IgnoredServletContainerInitializerBuildItem("com.vaadin.hilla.startup.EndpointsValidator");
    }

    // Configuring removed resources causes the index to be rebuilt, but the
    // hilla-jandex artifact does not contain any classes.
    // Adding a marker forces indexes to be build against Hilla artifacts.
    // Removed resources should also be configured for the endpoint artifact.
    @BuildStep
    void addMarkersForHillaJars(BuildProducer<AdditionalApplicationArchiveMarkerBuildItem> producer) {
        producer.produce(new AdditionalApplicationArchiveMarkerBuildItem("com/vaadin/hilla"));
    }

    @BuildStep
    void registerJaxrsApplicationToFixApplicationPath(BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        producer.produce(new AdditionalIndexedClassesBuildItem(QuarkusEndpointController.class.getName()));
    }

    @BuildStep
    AuthFormBuildItem authFormEnabledBuildItem() {
        boolean authFormEnabled = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.auth.form.enabled", Boolean.class)
                .orElse(false);
        return new AuthFormBuildItem(authFormEnabled);
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
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable()
                .build());
    }

    @BuildStep
    void registerEndpoints(
            final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(Endpoint.class.getName()), BuiltinScope.SINGLETON.getName()));
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(BrowserCallable.class.getName()), BuiltinScope.SINGLETON.getName()));
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
        SpringReplacer.addClassVisitors(producer);
    }

    @BuildStep
    void replaceFieldAutowiredAnnotations(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName autowiredAnnotation = DotName.createSimple("org.springframework.beans.factory.annotation.Autowired");
        Predicate<AnnotationInstance> isAutowiredAnnotation = ann -> ann.name().equals(autowiredAnnotation);
        Set<DotName> classesToTransform = Set.of(
                DotName.createSimple("com.vaadin.hilla.push.PushEndpoint"),
                DotName.createSimple("com.vaadin.hilla.push.PushMessageHandler"));
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
    void replacePackageNonNullApiAnnotations(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName sourceAnnotation = DotName.createSimple("org.springframework.lang.NonNullApi");
        DotName targetAnnotation = DotName.createSimple(NonNullApi.class);
        Predicate<AnnotationInstance> isAnnotatedPredicate = ann -> ann.name().equals(sourceAnnotation);
        producer.produce(new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {

            @Override
            public boolean appliesTo(AnnotationTarget.Kind kind) {
                return AnnotationTarget.Kind.CLASS == kind;
            }

            @Override
            public void transform(TransformationContext ctx) {
                if (ctx.getAnnotations().stream().anyMatch(isAnnotatedPredicate)) {
                    ctx.transform()
                            .remove(isAnnotatedPredicate)
                            .add(targetAnnotation)
                            .done();
                }
            }
        }));
    }

    @BuildStep
    void registerHillaSecurityPolicy(AuthFormBuildItem authFormEnabled, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (authFormEnabled.isEnabled()) {
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
            AuthFormBuildItem authFormBuildItem,
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        if (authFormBuildItem.isEnabled()) {
            producer.produce(SyntheticBeanBuildItem.configure(HillaFormAuthenticationMechanism.class)
                    .types(HttpAuthenticationMechanism.class)
                    .setRuntimeInit()
                    .scope(Singleton.class)
                    .alternative(true)
                    .priority(1)
                    .supplier(recorder.setupFormAuthenticationMechanism())
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void configureHillaSecurityComponents(
            AuthFormBuildItem authFormBuildItem, HillaSecurityRecorder recorder, BeanContainerBuildItem beanContainer) {
        if (authFormBuildItem.isEnabled()) {
            recorder.configureHttpSecurityPolicy(beanContainer.getValue());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureNavigationAccessControl(
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer,
            Optional<NavigationAccessControlBuildItem> navigationAccessControlBuildItem) {
        navigationAccessControlBuildItem
                .map(NavigationAccessControlBuildItem::getLoginPath)
                .ifPresent(loginPath -> recorder.configureNavigationAccessControl(beanContainer.getValue(), loginPath));
    }

    @BuildStep
    void configureNavigationControlAccessCheckers(
            List<NavigationAccessCheckerBuildItem> accessCheckers, BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(accessCheckers.stream()
                        .map(item -> item.getAccessChecker().toString())
                        .toList())
                .setUnremovable()
                .setDefaultScope(DotNames.SINGLETON)
                .build());
    }

    @BuildStep
    void registerNavigationAccessControl(
            AuthFormBuildItem authFormBuildItem,
            CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<NavigationAccessControlBuildItem> accessControlProducer,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {

        Set<DotName> securityAnnotations = Set.of(
                DotName.createSimple(DenyAll.class.getName()),
                DotName.createSimple(AnonymousAllowed.class.getName()),
                DotName.createSimple(RolesAllowed.class.getName()),
                DotName.createSimple(PermitAll.class.getName()));
        boolean hasSecuredRoutes =
                index.getComputingIndex().getAnnotations(DotName.createSimple(Route.class.getName())).stream()
                        .flatMap(route -> route.target().annotations().stream().map(AnnotationInstance::name))
                        .anyMatch(securityAnnotations::contains);
        if (authFormBuildItem.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            QuarkusNavigationAccessControl.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            DefaultAccessCheckDecisionResolver.class)
                    .setUnremovable()
                    .build());
            if (hasSecuredRoutes) {
                accessCheckerProducer.produce(
                        new NavigationAccessCheckerBuildItem(DotName.createSimple(AnnotatedViewAccessChecker.class)));
            }

            ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.http.auth.form.login-page", String.class)
                    .map(NavigationAccessControlBuildItem::new)
                    .ifPresent(accessControlProducer::produce);
        }
    }

    @BuildStep
    void registerServiceInitEventPropagator(
            QuarkusHillaEnvironmentBuildItem quarkusHillaEnv,
            BuildProducer<GeneratedResourceBuildItem> resourceProducer,
            BuildProducer<ServiceProviderBuildItem> serviceProviderProducer) {
        String descriptor = QuarkusVaadinServiceListenerPropagator.class.getName() + System.lineSeparator();
        resourceProducer.produce(new GeneratedResourceBuildItem(
                "META-INF/services/" + VaadinServiceInitListener.class.getName(),
                descriptor.getBytes(StandardCharsets.UTF_8)));
        serviceProviderProducer.produce(new ServiceProviderBuildItem(
                VaadinServiceInitListener.class.getName(), QuarkusVaadinServiceListenerPropagator.class.getName()));
    }

    // @BuildStep
    void fullstackSignalsSupport(BuildProducer<AdditionalBeanBuildItem> producer) {
        producer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses("com.vaadin.hilla.signals.core.SignalsRegistry")
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable()
                .build());
    }

    @BuildStep
    void preventHillaSpringBeansDetection(BuildProducer<ExcludedTypeBuildItem> producer) {
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.crud.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.startup.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.signals.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.route.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.push.PushConfigurer"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointCodeGenerator"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointControllerConfiguration"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointProperties"));
    }

    public static final class NavigationAccessControlBuildItem extends SimpleBuildItem {

        private final String loginPath;

        public NavigationAccessControlBuildItem(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginPath() {
            return loginPath;
        }
    }

    public static final class NavigationAccessCheckerBuildItem extends MultiBuildItem {

        private final DotName accessChecker;

        public NavigationAccessCheckerBuildItem(DotName accessChecker) {
            this.accessChecker = accessChecker;
        }

        public DotName getAccessChecker() {
            return accessChecker;
        }
    }
}
