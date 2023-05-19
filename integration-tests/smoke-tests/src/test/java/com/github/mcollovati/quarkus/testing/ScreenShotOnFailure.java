package com.github.mcollovati.quarkus.testing;

import com.codeborne.selenide.Screenshots;
import com.codeborne.selenide.ex.UIAssertionError;
import io.quarkus.test.common.TestStatus;
import io.quarkus.test.junit.callback.QuarkusTestAfterTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenShotOnFailure implements QuarkusTestBeforeTestExecutionCallback, QuarkusTestAfterTestExecutionCallback {

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
