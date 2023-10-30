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
package com.github.mcollovati.quarkus.hilla.deployment;

import java.util.List;
import java.util.function.BiFunction;

import dev.hilla.crud.filter.Filter;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.objectweb.asm.ClassVisitor;
import org.springframework.data.domain.Pageable;

import com.github.mcollovati.quarkus.hilla.crud.FilterableRepositorySupport;

public class FilterableRepositoryImplementor implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private final IndexView index;
    private final DotName filterableRepositoryInterface;

    public FilterableRepositoryImplementor(IndexView index, DotName filterableRepositoryInterface) {
        this.index = index;
        this.filterableRepositoryInterface = filterableRepositoryInterface;
    }

    @Override
    public ClassVisitor apply(String className, ClassVisitor classVisitor) {
        ClassInfo repositoryInterface = index.getClassByName(className);
        List<Type> types =
                JandexUtil.resolveTypeParameters(repositoryInterface.name(), filterableRepositoryInterface, index);
        if (!(types.get(0) instanceof ClassType)) {
            throw new IllegalArgumentException(
                    "Entity generic argument of " + className + " is not a regular class type");
        }
        DotName entityType = types.get(0).name();
        ClassTransformer transformer = new ClassTransformer(className);

        MethodCreator countCreator = transformer.addMethod("count", long.class, Filter.class);
        countCreator.returnValue(countCreator.invokeStaticMethod(
                MethodDescriptor.ofMethod(
                        FilterableRepositorySupport.class.getName(),
                        "count",
                        long.class.getName(),
                        Filter.class.getName(),
                        Class.class.getName()),
                countCreator.getMethodParam(0),
                countCreator.loadClass(entityType.toString())));

        MethodCreator listCreator = transformer.addMethod("list", List.class, Pageable.class, Filter.class);
        listCreator.returnValue(listCreator.invokeStaticMethod(
                MethodDescriptor.ofMethod(
                        FilterableRepositorySupport.class.getName(),
                        "list",
                        List.class.getName(),
                        Pageable.class.getName(),
                        Filter.class.getName(),
                        Class.class.getName()),
                listCreator.getMethodParam(0),
                listCreator.getMethodParam(1),
                listCreator.loadClassFromTCCL(entityType.toString())));

        return transformer.applyTo(classVisitor);
    }
}
