package org.acme.hilla.test.extension.deployment;

import java.lang.reflect.Modifier;
import java.util.Set;

import dev.hilla.Endpoint;
import dev.hilla.EndpointRegistry;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDefiningAnnotationBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.processor.AsmUtilCopy;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.deployment.util.AsmUtil;
import io.quarkus.deployment.util.IoUtil;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.maven.dependency.ArtifactKey;
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
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

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
    void replaceCallToSpringClassUtils(BuildProducer<BytecodeTransformerBuildItem> producer, final CombinedIndexBuildItem index) {
        producer.produce(new BytecodeTransformerBuildItem(EndpointRegistry.class.getName(),
                (s, classVisitor) -> new ClassUtilsClassVisitor(classVisitor)
        ));
    }

    static class ClassUtilsClassVisitor extends ClassVisitor {
        public ClassUtilsClassVisitor(ClassVisitor classVisitor) {
            super(Gizmo.ASM_API_VERSION, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("registerEndpoint".equals(name)) {
                MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new ClassUtilsGetUserClassRedirectMethodVisitor(superVisitor);
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    static class ClassUtilsGetUserClassRedirectMethodVisitor extends MethodVisitor {

        protected ClassUtilsGetUserClassRedirectMethodVisitor(MethodVisitor mv) {
            super(Gizmo.ASM_API_VERSION, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (Opcodes.INVOKESTATIC == opcode && "org/springframework/util/ClassUtils".equals(owner) &&
                    "getUserClass".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/acme/hilla/test/extension/ClassUtils",
                        name, descriptor, false);
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
