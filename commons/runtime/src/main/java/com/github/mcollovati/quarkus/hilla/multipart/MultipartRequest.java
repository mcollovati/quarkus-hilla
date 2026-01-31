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
package com.github.mcollovati.quarkus.hilla.multipart;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;

/**
 * A {@link HttpServletRequest} wrapper that supports a better integration with
 * Hilla multipart form data handling.
 */
public final class MultipartRequest extends HttpServletRequestWrapper {

    private final Map<String, Collection<FormValue>> formData;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request the {@link HttpServletRequest} to be wrapped.
     * @param formData the {@link MultipartFormDataInput} containing form data
     * @throws IllegalArgumentException if the request is null
     */
    public MultipartRequest(HttpServletRequest request, MultipartFormDataInput formData) {
        super(request);
        this.formData = formData.getValues();
    }

    @Override
    public String getParameter(String name) {
        Collection<FormValue> values = formData.get(name);
        return Optional.ofNullable(values).stream()
                .flatMap(Collection::stream)
                .filter(fv -> !fv.isFileItem())
                .findFirst()
                .map(FormValue::getValue)
                .orElseGet(() -> super.getParameter(name));
    }

    /**
     * Return a {@link java.util.Map} of the multipart files contained in this request.
     *
     * @return a map containing the parameter names as keys, and the
     * {@link MultipartFile} objects as values
     */
    public Map<String, MultipartFile> getFileMap() {
        return formData.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().iterator().next()))
                .filter(e -> e.getValue().isFileItem())
                .map(e -> new MultipartFileImpl(e.getKey(), e.getValue()))
                .collect(Collectors.toMap(MultipartFileImpl::getName, Function.identity()));
    }

    private static class MultipartFileImpl implements MultipartFile, Serializable {

        private final String name;
        private final FormValue formValue;

        public MultipartFileImpl(String name, FormValue formValue) {
            this.name = name;
            this.formValue = formValue;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return formValue.getFileName();
        }

        @Override
        public String getContentType() {
            return formValue.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        }

        @Override
        public boolean isEmpty() {
            return getSize() <= 0L;
        }

        @Override
        public long getSize() {
            try {
                return formValue.getFileItem().getFileSize();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return formValue.getFileItem().getInputStream().readAllBytes();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return formValue.getFileItem().getInputStream();
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            transferTo(dest.toPath());
        }

        @Override
        public void transferTo(Path dest) throws IOException, IllegalStateException {
            Files.deleteIfExists(dest);
            formValue.getFileItem().write(dest);
        }
    }
}
