/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package io.quarkus.vertx.http.runtime.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.BlockingSecurityExecutor;
import io.vertx.ext.web.RoutingContext;

/**
 * Narrow compatibility bridge to Quarkus' authoritative HTTP security model.
 * Package-private Quarkus contracts are intentionally isolated here so an
 * incompatible Quarkus upgrade fails at compile time instead of silently
 * changing authorization semantics.
 */
public final class QuarkusHillaSecurityBridge {

    private static final String PATH_POLICY_FOUND =
            AbstractPathMatchingHttpSecurityPolicy.class.getName() + ".POLICY_FOUND";
    private static final String SELECTED_AUTH_MECHANISMS =
            HttpAuthenticator.class.getName() + "#selected-auth-mechanisms";
    private static final String SELECTED_AUTH_MECHANISM_INSTANCES =
            HttpAuthenticator.class.getName() + "#selected-auth-mechanism-instances";
    private static final String ATTEMPT_AUTH_INVOKED = HttpAuthenticator.class.getName() + "#attemptAuthentication";

    private QuarkusHillaSecurityBridge() {}

    public static Set<String> requiredAuthenticationMechanisms(
            PathMatchingHttpSecurityPolicy policy, RoutingContext context) {
        HttpSecurityConfiguration.AuthenticationMechanisms mechanisms = policy.getAuthMechanisms(context);
        return mechanisms == null ? Set.of() : Set.copyOf(mechanisms.names());
    }

    public static boolean pathPolicyApplied(RoutingContext context) {
        return AbstractPathMatchingHttpSecurityPolicy.policyApplied(context);
    }

    public static HttpSecurityPolicy.AuthorizationRequestContext authorizationRequestContext(
            BlockingSecurityExecutor blockingExecutor) {
        return new HttpSecurityPolicy.DefaultAuthorizationRequestContext(blockingExecutor);
    }

    public static SecurityIdentity applyGlobalRolesMapping(RoutingContext routingContext, SecurityIdentity identity) {
        if (routingContext == null || identity == null) {
            return identity;
        }
        RolesMapping rolesMapping = routingContext.get(RolesMapping.ROLES_MAPPING_KEY);
        return rolesMapping == null ? identity : rolesMapping.apply(identity);
    }

    public static Map<String, Object> targetData(RoutingContext transportContext) {
        Map<String, Object> data = new HashMap<>();
        if (transportContext != null) {
            Object rolesMapping = transportContext.get(RolesMapping.ROLES_MAPPING_KEY);
            if (rolesMapping != null) {
                data.put(RolesMapping.ROLES_MAPPING_KEY, rolesMapping);
            }
        }
        return data;
    }

    public static void prepareTargetAuthentication(
            RoutingContext targetContext, PathMatchingHttpSecurityPolicy pathMatchingPolicy) {
        targetContext.setUser(null);
        targetContext.put(AbstractPathMatchingHttpSecurityPolicy.class.getName(), pathMatchingPolicy);
        targetContext.remove(PATH_POLICY_FOUND);
        targetContext.remove(HttpAuthenticationMechanism.class.getName());
        targetContext.remove(HttpSecurityUtils.SECURITY_IDENTITIES_ATTRIBUTE);
        targetContext.remove(QuarkusHttpUser.DEFERRED_IDENTITY_KEY);
        targetContext.remove(SELECTED_AUTH_MECHANISMS);
        targetContext.remove(SELECTED_AUTH_MECHANISM_INSTANCES);
        targetContext.remove(ATTEMPT_AUTH_INVOKED);
    }
}
