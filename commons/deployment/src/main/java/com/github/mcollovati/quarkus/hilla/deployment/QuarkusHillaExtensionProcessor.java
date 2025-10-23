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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.DefaultMenuAccessControl;
import com.vaadin.flow.server.startup.ServletDeployer;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.push.PushEndpoint;
import com.vaadin.hilla.push.PushMessageHandler;
import com.vaadin.hilla.signals.handler.SignalsHandler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExcludeDependencyBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.undertow.deployment.IgnoredServletContainerInitializerBuildItem;
import io.quarkus.undertow.deployment.ServletBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import com.github.mcollovati.quarkus.hilla.BodyHandlerRecorder;
import com.github.mcollovati.quarkus.hilla.HillaAtmosphereObjectFactory;
import com.github.mcollovati.quarkus.hilla.HillaConfiguration;
import com.github.mcollovati.quarkus.hilla.NonNullApi;
import com.github.mcollovati.quarkus.hilla.QuarkusAtmosphereServlet;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointController;
import com.github.mcollovati.quarkus.hilla.QuarkusEndpointProperties;
import com.github.mcollovati.quarkus.hilla.QuarkusVaadinServiceListenerPropagator;
import com.github.mcollovati.quarkus.hilla.crud.FilterableRepositorySupport;
import com.github.mcollovati.quarkus.hilla.deployment.asm.OffendingMethodCallsReplacer;
import com.github.mcollovati.quarkus.hilla.graal.DelayedInitBroadcaster;
import com.github.mcollovati.quarkus.hilla.reload.HillaLiveReloadRecorder;

import static com.github.mcollovati.quarkus.hilla.deployment.DataRepositorySupportBuiltItem.Provider.PANACHE;
import static com.github.mcollovati.quarkus.hilla.deployment.DataRepositorySupportBuiltItem.Provider.SPRING_DATA;

class QuarkusHillaExtensionProcessor {

    private static final String FEATURE = "quarkus-hilla";
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
    void publishRepositorySupport(
            BuildProducer<DataRepositorySupportBuiltItem> supportedProviders, CurateOutcomeBuildItem outcomeBuildItem) {
        DataRepositorySupportBuiltItem builtItem = new DataRepositorySupportBuiltItem();
        boolean springDataJpaPresent = outcomeBuildItem.getApplicationModel().getDependencies().stream()
                .anyMatch(dep -> "io.quarkus".equals(dep.getGroupId())
                        && dep.getArtifactId().startsWith("quarkus-spring-data-jpa"));
        if (springDataJpaPresent) {
            builtItem.addProvider(SPRING_DATA);
        }
        boolean panachePresent = outcomeBuildItem.getApplicationModel().getDependencies().stream()
                .anyMatch(dep -> "io.quarkus".equals(dep.getGroupId())
                        && dep.getArtifactId().startsWith("quarkus-hibernate-orm-panache"));
        if (panachePresent) {
            builtItem.addProvider(PANACHE);
        }
        supportedProviders.produce(builtItem);
    }

    @BuildStep
    void setupCrudAndListServiceSupport(
            DataRepositorySupportBuiltItem supportedProviders,
            BuildProducer<ExcludeDependencyBuildItem> producer,
            BuildProducer<AdditionalIndexedClassesBuildItem> additionalClasses) {
        if (supportedProviders.isPresent(SPRING_DATA)) {
            producer.produce(new ExcludeDependencyBuildItem("com.github.mcollovati", "hilla-shaded-deps"));
            additionalClasses.produce(new AdditionalIndexedClassesBuildItem(
                    SPRING_FILTERABLE_REPOSITORY.toString(), FilterableRepositorySupport.class.getName()));
        }
        if (supportedProviders.isPresent(PANACHE)) {
            additionalClasses.produce(new AdditionalIndexedClassesBuildItem(
                    PANACHE_FILTERABLE_REPOSITORY.toString(), FilterableRepositorySupport.class.getName()));
        }
    }

    @BuildStep
    void detectFilterableRepositoryImplementors(
            DataRepositorySupportBuiltItem supportedProviders,
            CombinedIndexBuildItem index,
            BuildProducer<FilterableRepositoryImplementorBuildItem> producer) {
        IndexView indexView = index.getComputingIndex();

        Consumer<DotName> registrar = interfaceName -> Stream.concat(
                        indexView.getKnownDirectImplementations(interfaceName).stream(),
                        indexView.getKnownDirectSubinterfaces(interfaceName).stream())
                .map(ClassInfo::name)
                .distinct()
                .forEach(clazzName ->
                        producer.produce(new FilterableRepositoryImplementorBuildItem(interfaceName, clazzName)));

        if (supportedProviders.isPresent(SPRING_DATA)) {
            registrar.accept(SPRING_FILTERABLE_REPOSITORY);
        }
        if (supportedProviders.isPresent(PANACHE)) {
            registrar.accept(PANACHE_FILTERABLE_REPOSITORY);
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

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(value = ExecutionTime.RUNTIME_INIT)
    void setupEndpointLiveReload(
            LiveReloadBuildItem liveReloadBuildItem,
            HillaConfiguration hillaConfiguration,
            HillaLiveReloadRecorder recorder) {
        if (hillaConfiguration.liveReload().enable()) {
            recorder.startEndpointWatcher(liveReloadBuildItem.isLiveReload(), hillaConfiguration);
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
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(new AdditionalBeanBuildItem(QuarkusEndpointProperties.class));
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses("com.github.mcollovati.quarkus.hilla.QuarkusEndpointControllerConfiguration")
                .setDefaultScope(BuiltinScope.APPLICATION.getName())
                .setUnremovable()
                .build());

        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(PushEndpoint.class, PushMessageHandler.class)
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable()
                .build());

        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(QuarkusEndpointConfiguration.class, QuarkusEndpointController.class)
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable()
                .build());
    }

    @BuildStep
    void registerEndpoints(
            final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        additionalBeanProducer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(SignalsHandler.class)
                .setUnremovable()
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .build());
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(Endpoint.class.getName()), BuiltinScope.APPLICATION.getName()));
        additionalBeanDefiningAnnotationRegistry.produce(new BeanDefiningAnnotationBuildItem(
                DotName.createSimple(BrowserCallable.class.getName()), BuiltinScope.APPLICATION.getName()));
    }

    @BuildStep
    void registerHillaPushServlet(
            BuildProducer<ServletBuildItem> servletProducer,
            QuarkusEndpointConfiguration endpointConfiguration,
            NativeConfig nativeConfig) {
        ServletBuildItem.Builder builder = ServletBuildItem.builder(
                QuarkusAtmosphereServlet.class.getName(), QuarkusAtmosphereServlet.class.getName());
        String prefix = endpointConfiguration.isDefaultEndpointPrefix()
                ? ""
                : endpointConfiguration.getNormalizedEndpointPrefix();
        String hillaPushMapping = prefix + "/HILLA/push";

        builder.addMapping(hillaPushMapping)
                .setAsyncSupported(true)
                .addInitParam(ApplicationConfig.JSR356_MAPPING_PATH, hillaPushMapping)
                .addInitParam(ApplicationConfig.ATMOSPHERE_HANDLER, PushEndpoint.class.getName())
                .addInitParam(ApplicationConfig.OBJECT_FACTORY, HillaAtmosphereObjectFactory.class.getName())
                .addInitParam(ApplicationConfig.ANALYTICS, "false")
                .addInitParam(
                        ApplicationConfig.ATMOSPHERE_INTERCEPTORS,
                        AtmosphereResourceLifecycleInterceptor.class.getName()
                                + ","
                                + TrackMessageSizeInterceptor.class.getName()
                                + ","
                                + SuspendTrackerInterceptor.class.getName())
                .setLoadOnStartup(1);
        if (nativeConfig.enabled()) {
            builder.addInitParam(ApplicationConfig.BROADCASTER_CLASS, DelayedInitBroadcaster.class.getName());
        }
        servletProducer.produce(builder.build());
    }

    @BuildStep
    void replaceOffendingMethodCalls(BuildProducer<BytecodeTransformerBuildItem> producer) {
        OffendingMethodCallsReplacer.addClassVisitors(producer);
    }

    @BuildStep
    void replaceFieldAutowiredAnnotations(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName autowiredAnnotation = DotName.createSimple("org.springframework.beans.factory.annotation.Autowired");
        Predicate<AnnotationInstance> isAutowiredAnnotation = ann -> ann.name().equals(autowiredAnnotation);
        Set<DotName> classesToTransform = Set.of(
                DotName.createSimple("com.vaadin.hilla.push.PushEndpoint"),
                DotName.createSimple("com.vaadin.hilla.push.PushMessageHandler"));
        AnnotationTransformation transformation = AnnotationTransformation.forFields()
                .whenField(fieldInfo ->
                        classesToTransform.contains(fieldInfo.declaringClass().name()))
                .when(ctx -> ctx.hasAnnotation(isAutowiredAnnotation))
                .transform(ctx -> {
                    ctx.remove(isAutowiredAnnotation);
                    ctx.add(AnnotationInstance.builder(DotNames.INJECT).buildWithTarget(ctx.declaration()));
                });
        producer.produce(new AnnotationsTransformerBuildItem(transformation));
    }

    @BuildStep
    void replacePackageNonNullApiAnnotations(BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName sourceAnnotation = DotName.createSimple("org.springframework.lang.NonNullApi");
        DotName targetAnnotation = DotName.createSimple(NonNullApi.class);
        Predicate<AnnotationInstance> isAnnotatedPredicate = ann -> ann.name().equals(sourceAnnotation);
        AnnotationTransformation transformation = AnnotationTransformation.forClasses()
                .when(ctx -> ctx.hasAnnotation(isAnnotatedPredicate))
                .transform(ctx -> {
                    ctx.remove(isAnnotatedPredicate);
                    ctx.add(AnnotationInstance.builder(targetAnnotation).buildWithTarget(ctx.declaration()));
                });
        producer.produce(new AnnotationsTransformerBuildItem(transformation));
    }

    @BuildStep
    void registerServiceInitEventPropagator(
            BuildProducer<GeneratedResourceBuildItem> resourceProducer,
            BuildProducer<ServiceProviderBuildItem> serviceProviderProducer) {
        String descriptor = QuarkusVaadinServiceListenerPropagator.class.getName() + System.lineSeparator();
        resourceProducer.produce(new GeneratedResourceBuildItem(
                "META-INF/services/" + VaadinServiceInitListener.class.getName(),
                descriptor.getBytes(StandardCharsets.UTF_8)));
        serviceProviderProducer.produce(new ServiceProviderBuildItem(
                VaadinServiceInitListener.class.getName(), QuarkusVaadinServiceListenerPropagator.class.getName()));
    }

    @BuildStep
    void preventHillaSpringBeansDetection(BuildProducer<ExcludedTypeBuildItem> producer) {
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.crud.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.startup.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.signals.config.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.signals.core.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.route.**"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.push.PushConfigurer"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointCodeGenerator"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointControllerConfiguration"));
        producer.produce(new ExcludedTypeBuildItem("com.vaadin.hilla.EndpointProperties"));

        producer.produce(new ExcludedTypeBuildItem("org.atmosphere.cpr.ContainerInitializer"));
        producer.produce(new ExcludedTypeBuildItem("org.atmosphere.cpr.AnnotationScanningServletContainerInitializer"));
        producer.produce(new ExcludedTypeBuildItem(ServletDeployer.class.getName()));
    }

    /*
     * Temporary step to register missing beans
     * See https://github.com/vaadin/quarkus/issues/175
     */
    @BuildStep
    void registerVaadinQuarkusServices(BuildProducer<AdditionalBeanBuildItem> producer) {
        producer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(DefaultMenuAccessControl.class)
                .setDefaultScope(DotNames.SINGLETON)
                .setUnremovable()
                .build());
    }
}
