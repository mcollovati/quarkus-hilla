/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment.vaadinplugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import com.vaadin.flow.plugin.base.BuildFrontendUtil;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.Config;

import static com.vaadin.flow.server.frontend.FrontendUtils.TOKEN_FILE;

public class VaadinPrepareFrontendTask implements CodeGenProvider {

    private Path baseDir;

    @Override
    public String providerId() {
        return "quarkus-hilla-vaadin-prepare-frontend";
    }

    @Override
    public String inputDirectory() {
        return "";
    }

    @Override
    public void init(ApplicationModel model, Map<String, String> properties) {
        System.out.println("VaadinPrepareFrontendTask.init");
        if (model.getApplicationModule() == null) {
            System.out.println("=============== Application Module is null");
        } else {
            baseDir = model.getApplicationModule().getModuleDir().toPath();
            System.out.println("=============== Application Module is not null, path is: " + baseDir);
        }
    }

    @Override
    public Path getInputDirectory() {
        return baseDir;
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        if (context.test()) {
            return false;
        }
        System.out.println("VaadinPrepareFrontendTask.trigger");
        SmallRyeConfig config = context.config().unwrap(SmallRyeConfig.class);
        VaadinBuildTimeConfig vaadinConfig = config.getConfigMapping(VaadinBuildTimeConfig.class);
        QuarkusPluginAdapter vaadinPlugin = new QuarkusPluginAdapter(vaadinConfig, context.applicationModel());
        File tokenFile = new File(vaadinPlugin.servletResourceOutputDirectory(), TOKEN_FILE);
        long lastModified = tokenFile.lastModified();

        // propagate info via System properties and token file
        tokenFile = BuildFrontendUtil.propagateBuildInfo(vaadinPlugin);

        try {
            BuildFrontendUtil.prepareFrontend(vaadinPlugin);
        } catch (Exception exception) {
            throw new CodeGenException("Could not execute prepare-frontend goal.", exception);
        }
        return lastModified == 0 || tokenFile.lastModified() > lastModified;
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {
        System.out.println("VaadinPrepareFrontendTask.shouldRun");
        return baseDir != null;
    }
}
