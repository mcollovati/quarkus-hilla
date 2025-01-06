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

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.plugin.base.BuildFrontendUtil;
import com.vaadin.flow.plugin.base.PluginAdapterBase;
import com.vaadin.flow.server.ExecutionFailedException;
import com.vaadin.flow.server.frontend.BundleValidationUtil;
import com.vaadin.flow.server.frontend.FrontendTools;
import com.vaadin.flow.server.frontend.FrontendToolsSettings;
import com.vaadin.flow.server.frontend.FrontendUtils;
import com.vaadin.hilla.engine.EngineConfiguration;
import com.vaadin.pro.licensechecker.LicenseChecker;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.builder.BuildException;
import org.jboss.jandex.IndexView;

public final class VaadinPlugin {

    private final QuarkusPluginAdapter pluginAdapter;

    public VaadinPlugin(VaadinBuildTimeConfig vaadinConfig, ApplicationModel applicationModel) {
        this.pluginAdapter = new QuarkusPluginAdapter(vaadinConfig, applicationModel);
    }

    public void prepareFrontend() throws BuildException {
        // propagate info via System properties and token file
        BuildFrontendUtil.propagateBuildInfo(pluginAdapter);

        try {
            BuildFrontendUtil.prepareFrontend(pluginAdapter);
        } catch (Exception exception) {
            throw new BuildException("Could not execute prepare-frontend goal.", exception, List.of());
        }
    }

    public void buildFrontend(IndexView index) throws BuildException {
        long start = System.nanoTime();

        try {
            configureHilla(index);
            BuildFrontendUtil.runNodeUpdater(pluginAdapter);
        } catch (ExecutionFailedException | URISyntaxException exception) {
            throw new BuildException("Could not execute build-frontend goal", exception, List.of());
        }

        if (pluginAdapter.generateBundle()
                && BundleValidationUtil.needsBundleBuild(pluginAdapter.servletResourceOutputDirectory())) {
            try {
                BuildFrontendUtil.runFrontendBuild(pluginAdapter);
            } catch (URISyntaxException | TimeoutException exception) {
                throw new BuildException(exception.getMessage(), exception, List.of());
            }
        }
        LicenseChecker.setStrictOffline(true);
        boolean licenseRequired = BuildFrontendUtil.validateLicenses(pluginAdapter);

        BuildFrontendUtil.updateBuildFile(pluginAdapter, licenseRequired);

        long ms = (System.nanoTime() - start) / 1000000;
        pluginAdapter.logInfo("Build frontend completed in " + ms + " ms.");
    }

    private void configureHilla(IndexView index) throws URISyntaxException {
        EngineConfiguration.BrowserCallableFinder browserCallableFinder;
        Lookup lookup = pluginAdapter.createLookup(pluginAdapter.getClassFinder());
        var finders = lookup.lookupAll(EngineConfiguration.BrowserCallableFinder.class);
        if (finders.size() > 1) {
            throw new IllegalStateException("Found more than one BrowserCallableFinder implementation: "
                    + finders.stream().map(obj -> obj.getClass().getName()).collect(Collectors.joining(", ")));
        } else if (!finders.isEmpty()) {
            browserCallableFinder = finders.iterator().next();
        } else {
            browserCallableFinder = new QuarkusHillaBrowserCallableFinder(index);
        }

        FrontendTools tools = new FrontendTools(getFrontendToolsSettings(pluginAdapter));
        ApplicationModel applicationModel = pluginAdapter.getApplicationModel();
        WorkspaceModule module = applicationModel.getApplicationModule();
        var conf = new EngineConfiguration.Builder()
                .baseDir(module.getModuleDir().toPath())
                .buildDir(module.getBuildDir().toPath())
                .outputDir(pluginAdapter.generatedTsFolder().toPath())
                .groupId(applicationModel.getAppArtifact().getGroupId())
                .artifactId(applicationModel.getAppArtifact().getArtifactId())
                .classpath(buildClasspath(applicationModel))
                .withDefaultAnnotations()
                .browserCallableFinder(browserCallableFinder)
                .nodeCommand(tools.getNodeBinary())
                .productionMode(true)
                .build();
        EngineConfiguration.setDefault(conf);
    }

    private static Collection<String> buildClasspath(ApplicationModel model) {
        return QuarkusPluginAdapter.buildClasspath(model)
                .map(path -> path.toAbsolutePath().toString())
                .toList();
    }

    private static FrontendToolsSettings getFrontendToolsSettings(PluginAdapterBase adapter) throws URISyntaxException {
        FrontendToolsSettings settings = new FrontendToolsSettings(
                adapter.npmFolder().getAbsolutePath(),
                () -> FrontendUtils.getVaadinHomeDirectory().getAbsolutePath());
        settings.setNodeDownloadRoot(adapter.nodeDownloadRoot());
        settings.setNodeVersion(adapter.nodeVersion());
        settings.setAutoUpdate(adapter.nodeAutoUpdate());
        settings.setUseGlobalPnpm(adapter.useGlobalPnpm());
        settings.setForceAlternativeNode(adapter.requireHomeNodeExec());
        return settings;
    }
}
