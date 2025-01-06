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
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.server.Constants;
import com.vaadin.flow.server.frontend.FrontendTools;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import static com.vaadin.flow.server.Constants.VAADIN_SERVLET_RESOURCES;
import static com.vaadin.flow.server.frontend.FrontendUtils.FRONTEND;

@ConfigMapping(prefix = "vaadin.build")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface VaadinBuildTimeConfig {

    /**
     * Gets if Vaadin Quarkus Plugins is enabled.
     * @return
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Defines the project frontend directory from where resources should be copied
     * from for use with the frontend build tool.
     *
     * @return
     */
    @WithDefault(Constants.LOCAL_FRONTEND_RESOURCES_PATH)
    File frontendResourcesDirectory();

    /**
     * Whether to generate a bundle from the project frontend sources or not.
     *
     * @return
     */
    @WithDefault("true")
    boolean generateBundle();

    /**
     * Whether to generate embeddable web components from WebComponentExporter inheritors.
     *
     * @return
     */
    @WithDefault("true")
    boolean generateEmbeddableWebComponents();

    /**
     * Whether to use byte code scanner strategy to discover frontend components.
     *
     * @return
     */
    @WithDefault("true")
    boolean optimizeBundle();

    /**
     * Whether to run npm install after updating dependencies.
     *
     * @return
     */
    @WithDefault("true")
    boolean runNpmInstall();

    /**
     * Setting this to true will run npm ci instead of npm install when using npm.
     * If using pnpm, the install will be run with --frozen-lockfile parameter.
     * This makes sure that the versions in package lock file will not be overwritten
     * and production builds are reproducible.
     *
     * @return
     */
    @WithDefault("false")
    boolean ciBuild();

    /**
     * Setting this to true will force a build of the production build even if there
     * is a default production bundle that could be used. Created production bundle
     * optimization is defined by optimizeBundle parameter.
     *
     * @return
     */
    @WithDefault("false")
    boolean forceProductionBuild();

    /**
     * Control cleaning of generated frontend files when executing 'build-frontend'.
     * Mainly this is wanted to be true which it is by default.
     *
     * @return
     */
    @WithDefault("true")
    boolean cleanFrontendFiles();

    /**
     * Application properties file in Quarkus project.
     *
     * @return
     */
    @WithDefault("src/main/resources/application.properties")
    File applicationProperties();

    /**
     * Whether or not insert the initial Uidl object in the bootstrap index.html
     *
     * @return
     */
    @WithDefault("false")
    boolean eagerServerLoad();

    /**
     * A directory with project's frontend source files.
     *
     * @return
     */
    @WithDefault("src/main/" + FRONTEND)
    File frontendDirectory();

    /**
     * The folder where flow will put TS API files for client projects.
     *
     * @return
     */
    Optional<File> generatedTsFolder();

    /**
     * Download node. js from this URL. Handy in heavily firewalled corporate environments
     * where the node.js download can be provided from an intranet mirror.
     * Defaults to null which will cause the downloader to use NodeInstaller.DEFAULT_NODEJS_DOWNLOAD_ROOT.
     *
     * Example: "https:// nodejs. org/ dist/".
     *
     * @return
     */
    Optional<String> nodeDownloadRoot();

    /**
     * Setting defining if the automatically installed node version may be updated to the default Vaadin node version.
     *
     * @return
     */
    @WithDefault("" + Constants.DEFAULT_NODE_AUTO_UPDATE)
    boolean nodeAutoUpdate();

    /**
     * The node. js version to be used when node. js is installed automatically by Vaadin, for example `"v16.0.0"`.
     * Defaults to the Vaadin-default node version - see FrontendTools for details.
     *
     * @return
     */
    @WithDefault(FrontendTools.DEFAULT_NODE_VERSION)
    String nodeVersion();

    /**
     * The folder where `package. json` file is located. Default is project root dir.
     *
     * @return
     */
    Optional<File> npmFolder();

    /**
     * Default generated path of the OpenAPI json.
     *
     * @return
     */
    @WithDefault("generated-resources/openapi.json")
    File openApiJsonFile();

    /**
     * Instructs to use pnpm for installing npm frontend resources.
     *
     * @return
     */
    @WithDefault("" + Constants.ENABLE_PNPM_DEFAULT)
    boolean pnpmEnable();

    /**
     * Instructs to use bun for installing npm frontend resources.
     *
     * @return
     */
    @WithDefault("" + Constants.ENABLE_BUN_DEFAULT)
    boolean bunEnable();

    /**
     * Instructs to use globally installed pnpm tool or the default supported pnpm version.
     *
     * @return
     */
    @WithDefault("" + Constants.GLOBAL_PNPM_DEFAULT)
    boolean useGlobalPnpm();

    /**
     * Whether vaadin home node executable usage is forced.
     * If it's set to true then vaadin home 'node' is checked and installed if it's absent.
     * Then it will be used instead of globally 'node' or locally installed 'node'.
     *
     * @return
     */
    @WithDefault("" + Constants.DEFAULT_REQUIRE_HOME_NODE_EXECUTABLE)
    boolean requireHomeNodeExec();

    /**
     * Defines the output directory for generated non-served resources, such as
     * the token file.
     */
    @WithDefault(VAADIN_SERVLET_RESOURCES)
    File resourceOutputDirectory();

    /**
     * The folder where the frontend build tool should output index. js and other generated files.
     *
     * @return
     */
    @WithDefault(Constants.VAADIN_WEBAPP_RESOURCES)
    File webpackOutputDirectory();

    /**
     * Additional npm packages to run post install scripts for.
     * <p>
     * Post install is automatically run for internal dependencies which rely on
     * post install scripts to work, e.g. esbuild.
     *
     * @return
     */
    Optional<List<String>> postinstallPackages();

    /**
     * Whether to disable dev bundle rebuild.
     *
     * @return
     */
    @WithDefault("false")
    boolean skipDevBundleBuild();

    /**
     * Whether to enable react
     *
     * @return
     */
    Optional<Boolean> reactEnabled();

    /**
     * Identifier for the application.
     * If not specified, defaults to the hashed value of 'groupId:artifactId'.
     *
     * @return
     */
    Optional<String> applicationIdentifier();

    /**
     * Parameter for adding file extensions to handle when generating bundles. Hashes are calculated for these files as part of detecting if a new bundle should be generated.
     * From the commandline use comma separated list -Ddevmode. frontendExtraFileExtensions="svg,ico"
     * In plugin configuration use comma separated values    svg,ico
     *
     * @return
     */
    Optional<List<String>> frontendExtraFileExtensions();

    /**
     * Whether to exclude npm packages for web components.
     *
     * @return
     */
    @WithDefault("false")
    boolean npmExcludeWebComponents();
}
