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
package com.github.mcollovati.quarkus.testing;

import com.codeborne.selenide.Screenshots;
import com.codeborne.selenide.ex.UIAssertionError;
import io.quarkus.test.common.TestStatus;
import io.quarkus.test.junit.callback.QuarkusTestAfterTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenshotOnFailure
        implements QuarkusTestBeforeTestExecutionCallback, QuarkusTestAfterTestExecutionCallback {

    private static final Logger log = LoggerFactory.getLogger(ScreenShotOnFailure.class);

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        final String className = context.getTestInstance().getClass().getName();
        final String methodName = context.getTestMethod().getName();
        Screenshots.startContext(className, methodName);
    }

    @Override
    public void afterTestExecution(QuarkusTestMethodContext context) {
        TestStatus testStatus = context.getTestStatus();
        if (testStatus.isTestFailed() && !(testStatus.getTestErrorCause() instanceof UIAssertionError)) {
            log.info(Screenshots.saveScreenshotAndPageSource());
        }
        Screenshots.finishContext();
    }
}
