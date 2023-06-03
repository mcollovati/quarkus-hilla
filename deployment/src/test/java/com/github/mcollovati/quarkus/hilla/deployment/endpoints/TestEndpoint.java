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
package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.hilla.Endpoint;

import com.vaadin.flow.server.auth.AnonymousAllowed;

@Endpoint
@AnonymousAllowed
public class TestEndpoint {

    public String echo(String message) {
        return message;
    }

    public int calculate(String operator, int a, int b) {
        int result;
        switch (operator) {
        case "+":
            result = a + b;
            break;
        case "*":
            result = a * b;
            break;
        default:
            throw new IllegalArgumentException("Invalid operation");
        }
        return result;
    }

    public Pojo pojo(Pojo pojo) {
        return new Pojo(pojo.number * 10, pojo.text + pojo.text);
    }

    public static class Pojo {
        @JsonProperty
        private final int number;

        @JsonProperty
        private final String text;

        @JsonCreator
        public Pojo(@JsonProperty int number, @JsonProperty String text) {
            this.number = number;
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Pojo pojo = (Pojo) o;
            return number == pojo.number && Objects.equals(text, pojo.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, text);
        }
    }
}
