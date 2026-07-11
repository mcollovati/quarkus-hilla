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
package com.github.mcollovati.quarkus.hilla.deployment.copilot;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

import dev.codex.quarkushilla.copilot.app.CopilotTestBeans;

import com.github.mcollovati.quarkus.hilla.CopilotQuarkusIntegration;

@WebServlet(urlPatterns = "/copilot-dev-mode/*")
class CopilotDevModeProbeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        if ("/application-class".equals(path)) {
            response.getWriter()
                    .write(CopilotQuarkusIntegration.getApplicationClass(null).getName());
        } else if ("/application-scoped-flow-service-bean".equals(path)) {
            response.getWriter().write(applicationScopedFlowServiceBean());
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private String applicationScopedFlowServiceBean() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(CopilotTestBeans.ApplicationScopedFlowService.class);
        return Boolean.toString(!beans.isEmpty());
    }
}
