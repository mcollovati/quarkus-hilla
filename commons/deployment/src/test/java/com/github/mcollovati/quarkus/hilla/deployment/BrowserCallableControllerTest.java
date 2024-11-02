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

import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.endpoints.Pojo;
import com.github.mcollovati.quarkus.hilla.deployment.endpoints.TestBrowserCallable;

class BrowserCallableControllerTest extends AbstractEndpointControllerTest {

    private static final String ENDPOINT_NAME = TestBrowserCallable.class.getSimpleName();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource(testResource("test-application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestUtils.class, Pojo.class, TestBrowserCallable.class));

    @Override
    protected String getEndpointName() {
        return ENDPOINT_NAME;
    }

    private static String testResource(String name) {
        return BrowserCallableControllerTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
