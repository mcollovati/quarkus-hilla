package org.acme.hilla.test.extension.deployment;

import dev.hilla.Endpoint;
import dev.hilla.EndpointRegistry;
import dev.hilla.push.PushEndpoint;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.Gizmo;
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
    void registerJaxrsApplicationToFixApplicationPath(BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        producer.produce(new AdditionalIndexedClassesBuildItem(
                QuarkusEndpointController.class.getName()
        ));
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

    @BuildStep
    void replaceCallsToSpring(BuildProducer<BytecodeTransformerBuildItem> producer, final CombinedIndexBuildItem index) {
        producer.produce(new BytecodeTransformerBuildItem(EndpointRegistry.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(classVisitor, "registerEndpoint")
        ));
        producer.produce(new BytecodeTransformerBuildItem(PushEndpoint.class.getName(),
                (s, classVisitor) -> new SpringReplacementsClassVisitor(classVisitor, "onMessageRequest")
        ));
    }

    static class SpringReplacementsClassVisitor extends ClassVisitor {

        private final String methodName;

        public SpringReplacementsClassVisitor(ClassVisitor classVisitor, String methodName) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
            this.methodName = methodName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (methodName.equals(name)) {
                MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new SpringReplacementsRedirectMethodVisitor(superVisitor);
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    static class SpringReplacementsRedirectMethodVisitor extends MethodVisitor {

        protected SpringReplacementsRedirectMethodVisitor(MethodVisitor mv) {
            super(Gizmo.ASM_API_VERSION, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.CHECKCAST && "org/springframework/security/core/Authentication".equals(type)) {
                // Hack: change explicit cast to Authentication to Object to prevent runtime error
                super.visitTypeInsn(opcode, "java/lang/Object");
                return;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (Opcodes.INVOKESTATIC == opcode && "org/springframework/security/core/context/SecurityContextHolder".equals(owner)
                    && ("setContext".equals(name) || "clearContext".equals(name))) {
                // Replace calls to SecurityContextHolder methods with noop
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/acme/hilla/test/extension/SpringReplacements",
                        "securityContextHolder_" + name, descriptor, false);
                return;
            }
            if (Opcodes.INVOKESTATIC == opcode && "org/springframework/util/ClassUtils".equals(owner) &&
                    "getUserClass".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/acme/hilla/test/extension/SpringReplacements",
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
