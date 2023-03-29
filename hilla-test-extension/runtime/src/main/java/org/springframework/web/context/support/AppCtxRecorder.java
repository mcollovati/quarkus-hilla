package org.springframework.web.context.support;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.springframework.context.ApplicationContext;

@Recorder
public class AppCtxRecorder {

    public AppCtxRecorder() {
        System.out.println("AppCtxRecorder ctor :: ");
    }

    public void setAppCtx(BeanContainer beanContainer) {
        ApplicationContext appCtx = beanContainer.beanInstance(ApplicationContext.class);
        //System.out.println("AppCtxRecorder ctor :: " + appCtx);
        WebApplicationContextUtils.initWebApplicationContext(appCtx);
    }
}
