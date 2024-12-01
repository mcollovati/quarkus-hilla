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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.quarkus.arc.deployment.devui.Name;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

public record AccessAnnotationInfo(Name name, List<String> roles) {

    public static DotName ROLES_ALLOWED_ANNOTATION = DotName.createSimple("jakarta.annotation.security.RolesAllowed");

    public static AccessAnnotationInfo from(AnnotationInstance annotation) {
        if (annotation.name().equals(ROLES_ALLOWED_ANNOTATION)) {
            return new AccessAnnotationInfo(
                    Name.from(annotation.name()),
                    Arrays.asList(annotation.value("value").asStringArray()));
        }
        return new AccessAnnotationInfo(Name.from(annotation.name()), Collections.emptyList());
    }
}
