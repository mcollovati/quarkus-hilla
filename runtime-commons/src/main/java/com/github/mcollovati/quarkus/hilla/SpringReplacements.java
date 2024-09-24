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
package com.github.mcollovati.quarkus.hilla;

import java.security.Principal;
import java.util.function.Function;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;

public class SpringReplacements {

    public static Class<?> classUtils_getUserClass(Object object) {
        return classUtils_getUserClass(object.getClass());
    }

    public static Class<?> classUtils_getUserClass(Class<?> clazz) {
        if (clazz.isSynthetic()) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return superclass;
            }
        }
        return clazz;
    }

    public static Principal authenticationUtil_getSecurityHolderAuthentication() {
        SecurityIdentity identity = currentIdentity();
        if (identity != null && !identity.isAnonymous()) {
            return identity.getPrincipal();
        }
        return null;
    }

    public static Function<String, Boolean> authenticationUtil_getSecurityHolderRoleChecker() {
        SecurityIdentity identity = currentIdentity();
        if (identity == null || identity.isAnonymous()) {
            return role -> false;
        }
        return role -> role != null && identity.hasRole(role);
    }

    private static SecurityIdentity currentIdentity() {
        try {
            return CurrentIdentityAssociation.current();
        } catch (Exception e) {
            return null;
        }
    }
}
