package org.acme.hilla.test.extension.deployment;

import javax.inject.Singleton;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.hilla.Endpoint;
import dev.hilla.EndpointRegistry;
import dev.hilla.push.PushEndpoint;
import dev.hilla.push.PushMessageHandler;
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
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.undertow.deployment.ServletBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.acme.hilla.test.extension.HillaAtmosphereObjectFactory;
import org.acme.hilla.test.extension.HillaFormAuthenticationMechanism;
import org.acme.hilla.test.extension.HillaSecurityPolicy;
import org.acme.hilla.test.extension.HillaSecurityRecorder;
import org.acme.hilla.test.extension.QuarkusEndpointConfiguration;
import org.acme.hilla.test.extension.QuarkusEndpointController;
import org.acme.hilla.test.extension.QuarkusEndpointProperties;
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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class HillaTestExtensionProcessor {

    private static final String FEATURE = "hilla-test-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerJaxrsApplicationToFixApplicationPath(
            BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        producer.produce(new AdditionalIndexedClassesBuildItem(
                QuarkusEndpointController.class.getName()));
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(
                new AdditionalBeanBuildItem(QuarkusEndpointProperties.class));
        beans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(
                "org.acme.hilla.test.extension.QuarkusEndpointControllerConfiguration")
                .addBeanClasses(QuarkusEndpointConfiguration.class,
                        QuarkusEndpointController.class)
                .setDefaultScope(BuiltinScope.SINGLETON.getName())
                .setUnremovable().build());

        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(PushEndpoint.class, PushMessageHandler.class)
                .setDefaultScope(BuiltinScope.APPLICATION.getName())
                .setUnremovable().build());
    }

    @BuildStep
    void registerEndpoints(
            final BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<BeanDefiningAnnotationBuildItem> additionalBeanDefiningAnnotationRegistry) {
        additionalBeanDefiningAnnotationRegistry
                .produce(new BeanDefiningAnnotationBuildItem(
                        DotName.createSimple(Endpoint.class.getName()),
                        BuiltinScope.SINGLETON.getName()));
    }

    @BuildStep
    void registerHillaPushServlet(
            BuildProducer<ServletBuildItem> servletProducer,
            BuildProducer<GeneratedResourceBuildItem> resourceProducer) {
        servletProducer.produce(ServletBuildItem
                .builder(AtmosphereServlet.class.getName(),
                        AtmosphereServlet.class.getName())
                .addMapping("/HILLA/push").setAsyncSupported(true)
                .addInitParam(ApplicationConfig.JSR356_MAPPING_PATH,
                        "/HILLA/push")
                .addInitParam(ApplicationConfig.BROADCASTER_CLASS,
                        SimpleBroadcaster.class.getName())
                // .addInitParam(ApplicationConfig.ATMOSPHERE_HANDLER,
                // QuarkusPushEndpoint.class.getName())
                .addInitParam(ApplicationConfig.ATMOSPHERE_HANDLER,
                        PushEndpoint.class.getName())
                .addInitParam(ApplicationConfig.OBJECT_FACTORY,
                        HillaAtmosphereObjectFactory.class.getName())
                .addInitParam(ApplicationConfig.ATMOSPHERE_INTERCEPTORS,
                        AtmosphereResourceLifecycleInterceptor.class.getName()
                                + ","
                                + TrackMessageSizeInterceptor.class.getName()
                                + ","
                                + SuspendTrackerInterceptor.class.getName())
                .setLoadOnStartup(1).build());
    }

    @BuildStep
    ExcludedTypeBuildItem excludeEndpointProperties() {
        return new ExcludedTypeBuildItem("dev.hilla.EndpointProperties");
    }

    @BuildStep
    void replaceCallsToSpring(
            BuildProducer<BytecodeTransformerBuildItem> producer,
            final CombinedIndexBuildItem index) {
        producer.produce(new BytecodeTransformerBuildItem(
                EndpointRegistry.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(
                        classVisitor, "registerEndpoint")));
        producer.produce(
                new BytecodeTransformerBuildItem(PushEndpoint.class.getName(),
                        (s, classVisitor) -> new SpringReplacementsClassVisitor(
                                classVisitor, "onMessageRequest")));
    }

    @BuildStep
    void replaceFieldAutowiredAnnotations(
            BuildProducer<AnnotationsTransformerBuildItem> producer) {
        DotName autowiredAnnotation = DotName.createSimple(
                "org.springframework.beans.factory.annotation.Autowired");
        Predicate<AnnotationInstance> isAutowiredAnnotation = ann -> ann.name()
                .equals(autowiredAnnotation);
        Set<DotName> classesToTransform = Set.of(
                DotName.createSimple("dev.hilla.push.PushEndpoint"),
                DotName.createSimple("dev.hilla.push.PushMessageHandler"));
        producer.produce(new AnnotationsTransformerBuildItem(
                new AnnotationsTransformer() {

                    @Override
                    public boolean appliesTo(AnnotationTarget.Kind kind) {
                        return AnnotationTarget.Kind.FIELD == kind;
                    }

                    @Override
                    public void transform(TransformationContext ctx) {
                        FieldInfo fieldInfo = ctx.getTarget().asField();
                        if (classesToTransform
                                .contains(fieldInfo.declaringClass().name())
                                && ctx.getAnnotations().stream()
                                        .anyMatch(isAutowiredAnnotation)) {
                            ctx.transform().remove(isAutowiredAnnotation)
                                    .add(DotNames.INJECT).done();
                        }
                    }
                }));
    }

    @BuildStep
    void registerHillaSecurityPolicy(HttpBuildTimeConfig buildTimeConfig,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        if (buildTimeConfig.auth.form.enabled) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(HillaSecurityPolicy.class)
                    .setDefaultScope(DotNames.SINGLETON).setUnremovable()
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHillaFormAuthenticationMechanism(
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        producer.produce(SyntheticBeanBuildItem
                .configure(HillaFormAuthenticationMechanism.class)
                .types(HttpAuthenticationMechanism.class).setRuntimeInit()
                .scope(Singleton.class).alternativePriority(1)
                .supplier(recorder.setupFormAuthenticationMechanism()).done());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void configureHillaSecurityComponents(HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        recorder.configureHttpSecurityPolicy(beanContainer.getValue());
    }

    static class SpringReplacementsClassVisitor extends ClassVisitor {

        private final String methodName;

        public SpringReplacementsClassVisitor(ClassVisitor classVisitor,
                String methodName) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
            this.methodName = methodName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name,
                String descriptor, String signature, String[] exceptions) {
            if (methodName.equals(name)) {
                MethodVisitor superVisitor = super.visitMethod(access, name,
                        descriptor, signature, exceptions);
                return new SpringReplacementsRedirectMethodVisitor(
                        superVisitor);
            }
            return super.visitMethod(access, name, descriptor, signature,
                    exceptions);
        }
    }

    static class SpringReplacementsRedirectMethodVisitor extends MethodVisitor {

        protected SpringReplacementsRedirectMethodVisitor(MethodVisitor mv) {
            super(Gizmo.ASM_API_VERSION, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.CHECKCAST
                    && "org/springframework/security/core/Authentication"
                            .equals(type)) {
                // Hack: drop explicit cast to Authentication to prevent runtime
                // error
                return;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                String descriptor, boolean isInterface) {
            if (Opcodes.INVOKESTATIC == opcode
                    && "org/springframework/security/core/context/SecurityContextHolder"
                            .equals(owner)) {
                if ("setContext".equals(name)) {
                    // Drop calls to SecurityContextHolder.setContext
                    System.out.println(
                            "HACK: drop call to SecurityContextHolder.setContext");
                    // Take the SecurityContextImpl from the stack
                    super.visitInsn(Opcodes.POP);
                    return;
                }
                if ("clearContext".equals(name)) {
                    // Drop calls to SecurityContextHolder.clearContext
                    System.out.println(
                            "HACK: drop call to SecurityContextHolder.clearContext");
                    return;
                }
            }
            if (Opcodes.INVOKESTATIC == opcode
                    && "org/springframework/util/ClassUtils".equals(owner)
                    && "getUserClass".equals(name)) {
                System.out.println(
                        "HACK: replace call to ClassUtils.getUserClass");
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "org/acme/hilla/test/extension/SpringReplacements",
                        "classUtils_getUserClass", descriptor, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
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
