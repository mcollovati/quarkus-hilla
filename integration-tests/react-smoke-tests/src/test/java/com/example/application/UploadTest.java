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
package com.example.application;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.partialText;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;

@QuarkusTest
public class UploadTest extends AbstractTest {

    @Override
    protected String getTestUrl() {
        return super.getBaseURL() + "/upload";
    }

    @Test
    void rootPath_viewDisplayed() throws IOException {
        Path tempFile = Files.createTempFile("upload", "test");
        Files.writeString(tempFile, "hello world");
        openAndWait(() -> $("form#upload"));

        $("input[type=file]").shouldBe(visible).uploadFile(tempFile.toFile());
        $("button[type=submit]").shouldBe(visible).click();

        SelenideElement out = $("output#out").shouldBe(visible);
        out.shouldHave(partialText("File saved to ")).shouldHave(partialText("my-test"));
        String fileURI = out.getText().replaceFirst("^.* saved to \"(.*)\".*$", "$1");
        Path path = Path.of(URI.create(fileURI));
        Assertions.assertThat(path).hasSameTextualContentAs(tempFile);
    }
}
