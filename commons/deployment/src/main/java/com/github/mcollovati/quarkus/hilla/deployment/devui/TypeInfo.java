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
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.Type;

public record TypeInfo(
        List<String> annotations, String type, @Nullable List<TypeInfo> generics) {

    public static TypeInfo from(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            return new TypeInfo(
                    annotationsSimple(type.annotations()),
                    type.name().withoutPackagePrefix(),
                    type.asParameterizedType().arguments().stream()
                            .map(TypeInfo::from)
                            .toList());
        }
        return new TypeInfo(annotationsSimple(type.annotations()), type.name().withoutPackagePrefix(), null);
    }

    private static List<String> annotationsSimple(List<AnnotationInstance> annotations) {
        return annotations.stream().map(a -> a.toString(true)).toList();
    }
}
