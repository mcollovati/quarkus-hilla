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
package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import org.springframework.web.multipart.MultipartFile;

@BrowserCallable
@AnonymousAllowed
public class UploadEndpoint {

    public Object upload(Info info, MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("upload", "test");
            file.transferTo(tempFile);
            return new Result(info, tempFile.toUri().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Info(String id, @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date) {}

    public record Result(Info info, String uri) {}
}
