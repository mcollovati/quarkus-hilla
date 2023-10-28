/*
 * Copyright 2023 Marco Collovati, Dario Götze
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

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

public class MyTest {

    @Test
    void testMe() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexWithSummary(MyTest.class.getResourceAsStream("package-info.class"));
        Index index = indexer.complete();
        index.getClassesInPackage("com.example.application").stream()
                // .flatMap(ci -> ci.annotations().stream())
                .forEach(ci -> System.out.println(ci.kind()));
    }
}
