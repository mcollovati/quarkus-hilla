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
package com.github.mcollovati.quarkus.hilla;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.AnnotationLiteral;

/**
 * Literal for the {@link jakarta.enterprise.inject.Any} annotation.
 *
 * NOTE: this code has been copy/pasted from vaadin-quarkus extension, credits goes to Vaadin Ltd.
 * https://github.com/vaadin/quarkus/blob/master/runtime/src/main/java/com/vaadin/quarkus/AnyLiteral.java
 */
public class AnyLiteral extends AnnotationLiteral<Any> implements Any {}
