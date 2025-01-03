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

import java.util.Map;

import com.vaadin.hilla.AuthenticationUtil;
import com.vaadin.hilla.EndpointInvoker;
import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.EndpointUtil;
import com.vaadin.hilla.Hotswapper;
import com.vaadin.hilla.parser.utils.ConfigList;
import com.vaadin.hilla.push.PushEndpoint;
import com.vaadin.hilla.push.PushMessageHandler;
import com.vaadin.hilla.signals.core.registry.SecureSignalsRegistry;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodDescriptor;
import org.springframework.data.domain.Sort;

import com.github.mcollovati.quarkus.hilla.SpringReplacements;

public class OffendingMethodCallsReplacer {

    private static Map.Entry<MethodSignature, MethodSignature> ClassUtils_getUserClass = Map.entry(
            MethodSignature.of("org/springframework/util/ClassUtils", "getUserClass"),
            MethodSignature.of(SpringReplacements.class, "classUtils_getUserClass"));
    private static Map.Entry<MethodSignature, MethodSignature> Class_forName = Map.entry(
            MethodSignature.of(Class.class, "forName", "(Ljava/lang/String;)Ljava/lang/Class;"),
            MethodSignature.of(SpringReplacements.class, "class_forName"));

    private static Map.Entry<MethodSignature, MethodSignature> AuthenticationUtil_getSecurityHolderAuthentication =
            Map.entry(
                    MethodSignature.of(
                            AuthenticationUtil.class,
                            "getSecurityHolderAuthentication",
                            "()Lorg/springframework/security/core/Authentication;"),
                    MethodSignature.of(
                            SpringReplacements.class,
                            "authenticationUtil_getSecurityHolderAuthentication",
                            "()Ljava/security/Principal;"));
    private static Map.Entry<MethodSignature, MethodSignature> AuthenticationUtil_getSecurityHolderRoleChecker =
            Map.entry(
                    MethodSignature.of(AuthenticationUtil.class, "getSecurityHolderRoleChecker"),
                    MethodSignature.of(SpringReplacements.class, "authenticationUtil_getSecurityHolderRoleChecker"));
    private static Map.Entry<MethodSignature, MethodSignature> SecurityContextHolder_setContext = Map.entry(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "setContext"),
            MethodSignature.DROP_METHOD);
    private static Map.Entry<MethodSignature, MethodSignature> SecurityContextHolder_clearContext = Map.entry(
            MethodSignature.of("org/springframework/security/core/context/SecurityContextHolder", "clearContext"),
            MethodSignature.DROP_METHOD);

    private static Map.Entry<MethodSignature, MethodSignature> EndpointInvoker_createDefaultEndpointMapper = Map.entry(
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
                ConfigList.Processor.class.getName(),
                (s, classVisitor) -> new NonnullPluginConfigProcessorClassVisitor(classVisitor)));
        // Remove sort method that references a type that is not in the shaded deps jar
        producer.produce(new BytecodeTransformerBuildItem(Sort.class.getName(), (className, classVisitor) -> {
            ClassTransformer transformer = new ClassTransformer(className);
            MethodDescriptor sortMethod =
                    MethodDescriptor.ofMethod(className, "sort", className + "$TypedSort", "java.lang.Class");
            transformer.removeMethod(sortMethod);
            return transformer.applyTo(classVisitor);
        }));
    }

    @SafeVarargs
    private static BytecodeTransformerBuildItem transform(
            Class<?> clazz, String method, Map.Entry<MethodSignature, MethodSignature>... replacements) {
        return new BytecodeTransformerBuildItem(
                clazz.getName(),
                (s, classVisitor) ->
                        new MethodReplacementClassVisitor(classVisitor, method, Map.ofEntries(replacements)));
    }
}
