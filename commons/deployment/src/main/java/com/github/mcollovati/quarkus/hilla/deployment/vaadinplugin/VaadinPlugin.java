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

import com.vaadin.experimental.FeatureFlags;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.plugin.base.BuildFrontendUtil;
import com.vaadin.flow.plugin.base.PluginAdapterBase;
import com.vaadin.flow.server.Constants;
import com.vaadin.flow.server.ExecutionFailedException;
import com.vaadin.flow.server.frontend.BundleValidationUtil;
import com.vaadin.flow.server.frontend.FrontendTools;
import com.vaadin.flow.server.frontend.FrontendToolsSettings;
import com.vaadin.flow.server.frontend.FrontendUtils;
import com.vaadin.flow.server.frontend.scanner.ClassFinder;
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner;
import com.vaadin.flow.theme.Theme;
import com.vaadin.hilla.engine.BrowserCallableFinder;
import com.vaadin.hilla.engine.EngineAutoConfiguration;
import com.vaadin.pro.licensechecker.LicenseChecker;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.builder.BuildException;
import org.jboss.jandex.IndexView;

import com.github.mcollovati.quarkus.hilla.build.TransferTypesPluginPatch;

/**
 * Implementation of the Vaadin plugin.
 * <p></p>
 * This class is a porting of Vaadin Maven prepare-frontend and build-frontend
 * mojos.
 */
public final class VaadinPlugin {

    private final QuarkusPluginAdapter pluginAdapter;

    /**
     * Creates a new instance of Quarkus Vaadin plugin for the given build configuration and application.
     *
     * @param vaadinConfig     the Vaadin build configuration.
     * @param applicationModel the application model.
     */
    public VaadinPlugin(VaadinBuildTimeConfig vaadinConfig, ApplicationModel applicationModel) {
        this.pluginAdapter = new QuarkusPluginAdapter(vaadinConfig, applicationModel);
        TransferTypesPluginPatch.addMutinySupport(this.pluginAdapter.getClassFinder());
    }

    /**
     * Checks that node and npm tools are installed and creates or updates
     * `package.json` and the frontend build tool configuration files.
     * <p></p>
     * Copies frontend resources available inside `.jar` dependencies to
     * `node_modules` when building a jar package.
     *
     * @throws BuildException if any error occurs.
     */
    public void prepareFrontend() throws BuildException {
        // propagate info via System properties and token file
        BuildFrontendUtil.propagateBuildInfo(pluginAdapter);

        try {
            BuildFrontendUtil.prepareFrontend(pluginAdapter);
        } catch (Exception exception) {
            throw new BuildException("Could not execute prepare-frontend goal.", exception, List.of());
        }
    }

    /**
     * Builds the frontend bundle.
     * <p></p>
     * It performs the following actions when creating a package:
     * <ul>
     * <li>Update {@link Constants#PACKAGE_JSON} file with the {@link NpmPackage}
     * annotations defined in the classpath,</li>
     * <li>Copy resource files used by flow from `.jar` files to the `node_modules`
     * folder</li>
     * <li>Install dependencies by running <code>npm install</code></li>
     * <li>Update the {@link FrontendUtils#IMPORTS_NAME} file imports with the
     * {@link JsModule} {@link Theme} and {@link JavaScript} annotations defined in
     * the classpath,</li>
     * <li>Update {@link FrontendUtils#VITE_CONFIG} file.</li>
     * </ul>
     *
     * @param index the Jandex index of the application.
     * @throws BuildException if any error occurs.
     */
    public void buildFrontend(IndexView index) throws BuildException {
        long start = System.nanoTime();

        FrontendDependenciesScanner frontendDependencies = createFrontendScanner();
        try {
            configureHilla(index);
            BuildFrontendUtil.runNodeUpdater(pluginAdapter, frontendDependencies);
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
        boolean licenseRequired = BuildFrontendUtil.validateLicenses(pluginAdapter, frontendDependencies);

        BuildFrontendUtil.updateBuildFile(pluginAdapter, licenseRequired);

        long ms = (System.nanoTime() - start) / 1000000;
        pluginAdapter.logInfo("Build frontend completed in " + ms + " ms.");
    }

    private void configureHilla(IndexView index) throws URISyntaxException {
        BrowserCallableFinder browserCallableFinder = new QuarkusHillaBrowserCallableFinder(index);
        FrontendTools tools = new FrontendTools(getFrontendToolsSettings(pluginAdapter));
        ApplicationModel applicationModel = pluginAdapter.getApplicationModel();
        WorkspaceModule module = applicationModel.getApplicationModule();
        var conf = new EngineAutoConfiguration.Builder()
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
        EngineAutoConfiguration.setDefault(conf);
    }

    private FrontendDependenciesScanner createFrontendScanner() {
        boolean reactEnabled = pluginAdapter.isReactEnabled()
                && FrontendUtils.isReactRouterRequired(BuildFrontendUtil.getFrontendDirectory(pluginAdapter));
        ClassFinder classFinder = pluginAdapter.getClassFinder();
        FeatureFlags featureFlags = new FeatureFlags(pluginAdapter.createLookup(classFinder));
        if (pluginAdapter.javaResourceFolder() != null) {
            featureFlags.setPropertiesLocation(pluginAdapter.javaResourceFolder());
        }
        return new FrontendDependenciesScanner.FrontendDependenciesScannerFactory()
                .createScanner(
                        !pluginAdapter.optimizeBundle(),
                        classFinder,
                        pluginAdapter.generateEmbeddableWebComponents(),
                        featureFlags,
                        reactEnabled);
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
