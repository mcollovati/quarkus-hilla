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
package com.github.mcollovati.quarkus.hilla.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.Principal;
import java.util.Set;
import java.util.function.Function;

import io.quarkus.security.test.utils.AuthData;
import io.quarkus.security.test.utils.IdentityMock;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.SpringReplacements;

import static org.assertj.core.api.Assertions.assertThat;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.ADMIN;
import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.USER;

class SpringReplacementsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource(testResource("test-application.properties"))
            .overrideRuntimeConfigKey("quarkus.http.auth.basic", "true")
            .overrideRuntimeConfigKey("quarkus.http.auth.proactive", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            IdentityMock.class,
                            AuthData.class,
                            TestIdentityProvider.class,
                            TestIdentityController.class,
                            TestUtils.class));

    @Inject
    MyBean bean;

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add(ADMIN.username, ADMIN.pwd, "ADMIN")
                .add(USER.username, USER.pwd, "USER");
    }

    @Test
    void authenticationUtil_getSecurityHolderAuthentication_anonymous_returnsNull() {
        IdentityMock.setUpAuth(IdentityMock.ANONYMOUS);
        Principal principal = SpringReplacements.authenticationUtil_getSecurityHolderAuthentication();
        assertThat(principal).isNull();
    }

    @Test
    void authenticationUtil_getSecurityHolderAuthentication_authenticated_returnsPrincipal() {
        IdentityMock.setUpAuth(IdentityMock.ADMIN);
        Principal principal = SpringReplacements.authenticationUtil_getSecurityHolderAuthentication();
        assertThat(principal).isNotNull().extracting(Principal::getName).isEqualTo("admin");
    }

    @Test
    void authenticationUtil_getSecurityHolderRoleChecker_authenticated_checksRoles() {
        IdentityMock.setUpAuth(new AuthData(Set.of("ADMIN", "SUPERUSER"), false, "admin"));
        Function<String, Boolean> checker = SpringReplacements.authenticationUtil_getSecurityHolderRoleChecker();
        assertThat(checker).isNotNull();
        assertThat(checker.apply("ADMIN")).as("Check for ADMIN role").isTrue();
        assertThat(checker.apply("SUPERUSER")).as("Check for SUPERUSER role").isTrue();
        assertThat(checker.apply("GUEST")).as("Check for GUEST role").isFalse();
        assertThat(checker.apply("")).as("Check for blank role").isFalse();
        assertThat(checker.apply(null)).as("Check for null role").isFalse();
    }

    @Test
    void authenticationUtil_getSecurityHolderRoleChecker_anonymous_checkIsAlwaysFalse() {
        IdentityMock.setUpAuth(IdentityMock.ANONYMOUS);
        Function<String, Boolean> checker = SpringReplacements.authenticationUtil_getSecurityHolderRoleChecker();
        assertThat(checker).isNotNull();
        assertThat(checker.apply("ADMIN")).as("Check for ADMIN role").isFalse();
        assertThat(checker.apply("SUPERUSER")).as("Check for SUPERUSER role").isFalse();
        assertThat(checker.apply("GUEST")).as("Check for GUEST role").isFalse();
        assertThat(checker.apply("ANONYMOUS")).as("Check for ANONYMOUS role").isFalse();
        assertThat(checker.apply("")).as("Check for blank role").isFalse();
        assertThat(checker.apply(null)).as("Check for null role").isFalse();
    }

    @Test
    void classUtils_getUserClass_proxiedObject_returnRawClass() {
        Class<?> userClass = SpringReplacements.classUtils_getUserClass(bean.getClass());
        assertThat(userClass).isEqualTo(MyBean.class);

        userClass = SpringReplacements.classUtils_getUserClass(new MyBean());
        assertThat(userClass).isEqualTo(MyBean.class);
    }

    @ApplicationScoped
    public static class MyBean {}

    private static String testResource(String name) {
        return SpringReplacementsTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
