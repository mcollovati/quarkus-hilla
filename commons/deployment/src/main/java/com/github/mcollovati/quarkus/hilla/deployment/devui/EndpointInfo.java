/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment.devui;

import jakarta.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.quarkus.arc.deployment.devui.Name;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

public record EndpointInfo(
        AnnotationTarget.Kind kind,
        Name declaringClass,
        @Nullable MethodDescriptor methodDescriptor,
        @Nullable String endpointAnnotation,
        AccessAnnotationInfo accessAnnotation,
        List<EndpointInfo> children) {

    public static DotName ENDPOINT_ANNOTATION = DotName.createSimple("com.vaadin.hilla.Endpoint");
    public static DotName BROWSER_CALLABLE_ANNOTATION = DotName.createSimple("com.vaadin.hilla.BrowserCallable");
    public static DotName ENDPOINT_EXPOSED_ANNOTATION = DotName.createSimple("com.vaadin.hilla.EndpointExposed");
    public static DotName DENY_ALL_ANNOTATION = DotName.createSimple("jakarta.annotation.security.DenyAll");
    public static DotName ROLES_ALLOWED_ANNOTATION = DotName.createSimple("jakarta.annotation.security.RolesAllowed");
    public static DotName ANONYMOUS_ALLOWED_ANNOTATION =
            DotName.createSimple("com.vaadin.flow.server.auth.AnonymousAllowed");
    private static final AccessAnnotationInfo DENY_ALL_ACCESS_ANNOTATION_INFO =
            new AccessAnnotationInfo(Name.from(DENY_ALL_ANNOTATION), Collections.emptyList());

    /**
     * Creates an {@link EndpointInfo} from the given {@link ClassInfo}.
     *
     * @param classInfo the class info to create the endpoint info from
     * @param computingIndex the computing index, not the normal index
     * @return the created endpoint info
     */
    public static EndpointInfo from(ClassInfo classInfo, IndexView computingIndex) {
        final var accessAnnotation =
                getEffectiveAccessAnnotation(classInfo.declaredAnnotations()).orElse(DENY_ALL_ACCESS_ANNOTATION_INFO);
        return new EndpointInfo(
                classInfo.kind(),
                Name.from(classInfo.name()),
                null,
                classInfo.declaredAnnotations().stream()
                        .map(AnnotationInstance::name)
                        .filter(a -> a.equals(ENDPOINT_ANNOTATION) || a.equals(BROWSER_CALLABLE_ANNOTATION))
                        .findFirst()
                        .map(DotName::withoutPackagePrefix)
                        .orElse(null),
                accessAnnotation,
                retrieveExposedMethods(computingIndex, classInfo, accessAnnotation));
    }

    private static List<EndpointInfo> retrieveExposedMethods(
            IndexView index, ClassInfo classInfo, AccessAnnotationInfo accessAnnotation) {
        return Stream.concat(Stream.of(classInfo), getExposedSuperClasses(classInfo, index).stream())
                .flatMap(c -> c.methodsInDeclarationOrder().stream())
                .filter(m -> Modifier.isPublic(m.flags()) && !m.isConstructor() && !Modifier.isStatic(m.flags()))
                .map(m -> from(m, accessAnnotation))
                .toList();
    }

    private static List<ClassInfo> getExposedSuperClasses(ClassInfo classInfo, IndexView index) {
        List<ClassInfo> superClasses = new ArrayList<>();
        Type superClassType = classInfo.superClassType();
        do {
            final var superClass = index.getClassByName(superClassType.name());
            if (superClass == null) break;
            if (superClass.hasAnnotation(ENDPOINT_EXPOSED_ANNOTATION)) {
                superClasses.add(superClass);
            }
            superClassType = superClass.superClassType();
        } while (superClassType != null);

        return superClasses;
    }

    public static EndpointInfo from(MethodInfo methodInfo, AccessAnnotationInfo classAccessAnnotation) {
        return new EndpointInfo(
                methodInfo.kind(),
                Name.from(methodInfo.declaringClass().name()),
                MethodDescriptor.from(methodInfo),
                null,
                getEffectiveAccessAnnotation(methodInfo.declaredAnnotations()).orElse(classAccessAnnotation),
                null);
    }

    /**
     * Returns the effective access annotation.
     * Based on the
     * <a href="https://vaadin.com/docs/latest/hilla/guides/security/configuring#security-options">security rules</a>.
     * @param annotations the annotations
     * @return the effective access annotation, if any
     */
    private static Optional<AccessAnnotationInfo> getEffectiveAccessAnnotation(List<AnnotationInstance> annotations) {
        return annotations.stream()
                .filter(EndpointInfo::isAccessAnnotation)
                .reduce((a1, a2) -> {
                    if (a1.name().equals(DENY_ALL_ANNOTATION)) {
                        return a1;
                    } else if (a2.name().equals(DENY_ALL_ANNOTATION)) {
                        return a2;
                    } else if (a1.name().equals(ANONYMOUS_ALLOWED_ANNOTATION)) {
                        return a1;
                    } else if (a2.name().equals(ANONYMOUS_ALLOWED_ANNOTATION)) {
                        return a2;
                    } else if (a1.name().equals(ROLES_ALLOWED_ANNOTATION)) {
                        return a1;
                    } else if (a2.name().equals(ROLES_ALLOWED_ANNOTATION)) {
                        return a2;
                    } else {
                        // PermitAll
                        return a1;
                    }
                })
                .map(AccessAnnotationInfo::from);
    }

    private static boolean isAccessAnnotation(AnnotationInstance annotation) {
        return annotation.name().packagePrefix().equals("jakarta.annotation.security")
                || annotation.name().packagePrefix().equals("com.vaadin.flow.server.auth");
    }
}
