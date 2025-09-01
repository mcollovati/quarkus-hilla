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
package com.github.mcollovati.quarkus.hilla.deployment.asm;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vaadin.hilla.ApplicationContextProvider;
import com.vaadin.hilla.AuthenticationUtil;
import com.vaadin.hilla.EndpointCodeGenerator;
import com.vaadin.hilla.EndpointInvoker;
import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.EndpointUtil;
import com.vaadin.hilla.Hotswapper;
import com.vaadin.hilla.engine.EngineAutoConfiguration;
import com.vaadin.hilla.push.PushEndpoint;
import com.vaadin.hilla.push.PushMessageHandler;
import com.vaadin.hilla.signals.internal.SecureSignalsRegistry;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import org.objectweb.asm.Opcodes;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;

import com.github.mcollovati.quarkus.hilla.HillaReplacements;
import com.github.mcollovati.quarkus.hilla.SpringReplacements;

public class OffendingMethodCallsReplacer {

    private static final Map.Entry<MethodSignature, MethodSignature> ClassUtils_getUserClass = Map.entry(
            MethodSignature.of("org/springframework/util/ClassUtils", "getUserClass"),
            MethodSignature.of(SpringReplacements.class, "classUtils_getUserClass"));
    private static final Map.Entry<MethodSignature, MethodSignature> Class_forName = Map.entry(
            MethodSignature.of(Class.class, "forName", "(Ljava/lang/String;)Ljava/lang/Class;"),
            MethodSignature.of(SpringReplacements.class, "class_forName"));

    private static final Map.Entry<MethodSignature, MethodSignature>
            AuthenticationUtil_getSecurityHolderAuthentication = Map.entry(
                    MethodSignature.of(
                            AuthenticationUtil.class,
                            "getSecurityHolderAuthentication",
                            "()Lorg/springframework/security/core/Authentication;"),
                    MethodSignature.of(
                            SpringReplacements.class,
                            "authenticationUtil_getSecurityHolderAuthentication",
                            "()Ljava/security/Principal;"));
    private static final Map.Entry<MethodSignature, MethodSignature> AuthenticationUtil_getSecurityHolderRoleChecker =
            Map.entry(
                    MethodSignature.of(AuthenticationUtil.class, "getSecurityHolderRoleChecker"),
                    MethodSignature.of(SpringReplacements.class, "authenticationUtil_getSecurityHolderRoleChecker"));
    private static final Map.Entry<MethodSignature, MethodSignature> SecurityContextHolder_setContext = Map.entry(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "setContext"),
            MethodSignature.DROP_METHOD);
    private static final Map.Entry<MethodSignature, MethodSignature> SecurityContextHolder_clearContext = Map.entry(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "clearContext"),
            MethodSignature.DROP_METHOD);

    private static final Map.Entry<MethodSignature, MethodSignature> EndpointInvoker_createDefaultEndpointMapper =
            Map.entry(
                    MethodSignature.of(EndpointInvoker.class, "createDefaultEndpointMapper"),
                    MethodSignature.of(SpringReplacements.class, "endpointInvoker_createDefaultEndpointMapper"));

    public static void addClassVisitors(BuildProducer<BytecodeTransformerBuildItem> producer) {
        producer.produce(transform(Hotswapper.class, "affectsEndpoints", Class_forName));
        producer.produce(transform(EndpointRegistry.class, "registerEndpoint", ClassUtils_getUserClass));
        producer.produce(transform(EndpointUtil.class, "isAnonymousEndpoint", ClassUtils_getUserClass));
        producer.produce(transform(EndpointInvoker.class, "checkAccess", ClassUtils_getUserClass));
        producer.produce(transform(EndpointInvoker.class, "invokeVaadinEndpointMethod", ClassUtils_getUserClass));
        producer.produce(transform(EndpointInvoker.class, "<init>", EndpointInvoker_createDefaultEndpointMapper));
        producer.produce(transform(
                SecureSignalsRegistry.class,
                "register",
                AuthenticationUtil_getSecurityHolderAuthentication,
                AuthenticationUtil_getSecurityHolderRoleChecker));
        producer.produce(transform(
                SecureSignalsRegistry.class,
                "checkAccess",
                AuthenticationUtil_getSecurityHolderAuthentication,
                AuthenticationUtil_getSecurityHolderRoleChecker));
        producer.produce(transform(
                PushMessageHandler.class,
                "handleBrowserSubscribe",
                AuthenticationUtil_getSecurityHolderAuthentication,
                AuthenticationUtil_getSecurityHolderRoleChecker));
        producer.produce(transform(
                PushEndpoint.class,
                "onMessageRequest",
                SecurityContextHolder_setContext,
                SecurityContextHolder_clearContext));
        producer.produce(new BytecodeTransformerBuildItem(
                "com.vaadin.hilla.parser.plugins.nonnull.NonnullPluginConfig$Processor",
                (s, classVisitor) -> new NonnullPluginConfigProcessorClassVisitor(classVisitor)));
        producer.produce(new BytecodeTransformerBuildItem(
                "com.vaadin.hilla.parser.plugins.transfertypes.TransferTypesPlugin",
                (s, classVisitor) -> new TransferTypesPluginClassVisitor(classVisitor)));
        // Remove sort method that references a type that is not in the shaded deps jar
        producer.produce(new BytecodeTransformerBuildItem(Sort.class.getName(), (className, classVisitor) -> {
            ClassTransformer transformer = new ClassTransformer(className);
            MethodDescriptor sortMethod =
                    MethodDescriptor.ofMethod(className, "sort", className + "$TypedSort", "java.lang.Class");
            transformer.removeMethod(sortMethod);
            return transformer.applyTo(classVisitor);
        }));
        producer.produce(applicationContextProvider_runOnContext_patch());
        producer.produce(endpointCodeGenerator_findBrowserCallables_replacement());
        producer.produce(new BytecodeTransformerBuildItem(
                "com.vaadin.hilla.EndpointController",
                (className, classVisitor) -> new EndpointControllerVisitor(classVisitor)));
    }

    @SafeVarargs
    private static BytecodeTransformerBuildItem transform(
            Class<?> clazz, String method, Map.Entry<MethodSignature, MethodSignature>... replacements) {
        return new BytecodeTransformerBuildItem(
                clazz.getName(),
                (s, classVisitor) ->
                        new MethodReplacementClassVisitor(classVisitor, method, Map.ofEntries(replacements)));
    }

    private static BytecodeTransformerBuildItem applicationContextProvider_runOnContext_patch() {
        return new BytecodeTransformerBuildItem(
                ApplicationContextProvider.class.getName(), (className, classVisitor) -> {
                    ClassTransformer transformer = new ClassTransformer(className);
                    MethodDescriptor runOnContextMethod = MethodDescriptor.ofMethod(
                            ApplicationContextProvider.class, "runOnContext", void.class, Consumer.class);
                    transformer.removeMethod(runOnContextMethod);
                    try (MethodCreator creator = transformer.addMethod(runOnContextMethod)) {
                        creator.setModifiers(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC);
                        ResultHandle appCtxField = creator.readStaticField(FieldDescriptor.of(
                                ApplicationContextProvider.class, "applicationContext", ApplicationContext.class));
                        ResultHandle pendingActionsField = creator.readStaticField(
                                FieldDescriptor.of(ApplicationContextProvider.class, "pendingActions", List.class));
                        BranchResult ifNullAppCtx = creator.ifNull(appCtxField);
                        try (BytecodeCreator trueBranch = ifNullAppCtx.trueBranch()) {
                            trueBranch.invokeInterfaceMethod(
                                    MethodDescriptor.ofMethod(List.class, "add", boolean.class, Object.class),
                                    pendingActionsField,
                                    creator.getMethodParam(0));
                        }
                        try (BytecodeCreator falseBranch = ifNullAppCtx.falseBranch()) {
                            falseBranch.invokeInterfaceMethod(
                                    MethodDescriptor.ofMethod(Consumer.class, "accept", void.class, Object.class),
                                    creator.getMethodParam(0),
                                    appCtxField);
                        }
                        creator.returnVoid();
                    }
                    return transformer.applyTo(classVisitor);
                });
    }

    private static BytecodeTransformerBuildItem endpointCodeGenerator_findBrowserCallables_replacement() {
        return new BytecodeTransformerBuildItem(EndpointCodeGenerator.class.getName(), (className, classVisitor) -> {
            ClassTransformer transformer = new ClassTransformer(className);
            MethodDescriptor findBrowserCallablesMethod = MethodDescriptor.ofMethod(
                    className,
                    "findBrowserCallables",
                    List.class,
                    EngineAutoConfiguration.class,
                    ApplicationContext.class);
            transformer.removeMethod(findBrowserCallablesMethod);
            try (MethodCreator creator = transformer.addMethod(findBrowserCallablesMethod)) {
                creator.setModifiers(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC);
                creator.returnValue(creator.invokeStaticMethod(
                        MethodDescriptor.ofMethod(
                                HillaReplacements.class,
                                "findBrowserCallables",
                                List.class,
                                EngineAutoConfiguration.class,
                                ApplicationContext.class),
                        creator.getMethodParam(0),
                        creator.getMethodParam(1)));
            }
            return transformer.applyTo(classVisitor);
        });
    }
}
