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

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.vaadin.hilla.crud.filter.Filter;
import io.quarkus.deployment.bean.JavaBeanUtil;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.BranchResult;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;
import org.objectweb.asm.ClassVisitor;

import com.github.mcollovati.quarkus.hilla.crud.FilterableRepositorySupport;

import static io.quarkus.gizmo.FieldDescriptor.of;

/**
 * Generates runtime implementation of FilterableRepository methods.
 *
 * The generated {@code list} and {@code count} methods delegate the call to {@link FilterableRepositorySupport},
 * with the addition of the Class of the entity that the repository is supposed to handle.
 *
 * For Panache, an addition {@code isNew} method is generated. This method reads the value of the entity {@code @Id}
 * to determine if the entity is new or not.
 * {@code isNew} is used by the CrudRepositoryService implementation for Panache, to chose if {@code save} should
 * call {@code persist} or {@code merge} to save the entity.
 *
 * The byte code instrumentation for {@code isNew} is mostly taken by the spring-data-jpa Quarkus extension.
 * Credits goes to the original authors of the code.
 *
 * See https://github.com/quarkusio/quarkus/blob/main/extensions/spring-data-jpa/deployment/src/main/java/io/quarkus/spring/data/deployment/generate/StockMethodsAdder.java
 * for additional information.
 */
public class FilterableRepositoryImplementor implements BiFunction<String, ClassVisitor, ClassVisitor> {

    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());
    public static final DotName PRIMITIVE_LONG = DotName.createSimple(long.class.getName());
    public static final DotName PRIMITIVE_INTEGER = DotName.createSimple(int.class.getName());
    public static final DotName JPA_ID = DotName.createSimple("jakarta.persistence.Id");
    public static final DotName JPA_EMBEDDED_ID = DotName.createSimple("jakarta.persistence.EmbeddedId");
    public static final DotName SPRING_PAGEABLE = DotName.createSimple("org.springframework.data.domain.Pageable");

    private final IndexView index;
    private final DotName filterableRepositoryInterface;

    public FilterableRepositoryImplementor(IndexView index, DotName filterableRepositoryInterface) {
        this.index = index;
        this.filterableRepositoryInterface = filterableRepositoryInterface;
    }

    @Override
    public ClassVisitor apply(String className, ClassVisitor classVisitor) {
        ClassInfo repository = index.getClassByName(className);
        List<Type> types = JandexUtil.resolveTypeParameters(repository.name(), filterableRepositoryInterface, index);
        if (!(types.get(0) instanceof ClassType)) {
            throw new IllegalArgumentException("Cannot determine the type of the JPA entity that " + className
                    + " is supposed to handle by implementing FilterableRepository<ENTITY, ID>.");
        }
        DotName entityType = types.get(0).name();
        ClassTransformer transformer = new ClassTransformer(className);

        MethodInfo countMethod =
                repository.method("count", Type.create(DotName.createSimple(Filter.class), Type.Kind.CLASS));
        if (countMethod == null) {
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
        }

        MethodInfo listMethod = repository.method(
                "list",
                Type.create(SPRING_PAGEABLE, Type.Kind.CLASS),
                Type.create(DotName.createSimple(Filter.class), Type.Kind.CLASS));
        if (listMethod == null) {
            MethodCreator listCreator =
                    transformer.addMethod("list", List.class, "L" + SPRING_PAGEABLE.toString('/') + ";", Filter.class);
            listCreator.returnValue(listCreator.invokeStaticMethod(
                    MethodDescriptor.ofMethod(
                            FilterableRepositorySupport.class.getName(),
                            "list",
                            List.class.getName(),
                            SPRING_PAGEABLE.toString(),
                            Filter.class.getName(),
                            Class.class.getName()),
                    listCreator.getMethodParam(0),
                    listCreator.getMethodParam(1),
                    listCreator.loadClassFromTCCL(entityType.toString())));
        }

        implementIsNew(index, repository, entityType, transformer);

        return transformer.applyTo(classVisitor);
    }

    private void implementIsNew(
            IndexView index, ClassInfo repository, DotName entityType, ClassTransformer transformer) {
        ClassInfo filterableRepository = index.getClassByName(filterableRepositoryInterface);
        MethodInfo isNewMethod = filterableRepository.method("isNew", TypeVariable.create("T"));
        if (isNewMethod == null) {
            return;
        }

        MethodInfo isNewMethodImplementation = repository.method("isNew", Type.create(entityType, Type.Kind.CLASS));
        if (isNewMethodImplementation == null) {
            MethodCreator isNewCreator = transformer.addMethod(
                    isNewMethod.name(),
                    isNewMethod.returnType().toString(),
                    isNewMethod.parameterType(0).name().toString());
            ResultHandle entity = isNewCreator.getMethodParam(0);
            AnnotationTarget idAnnotationTarget = getIdAnnotationTarget(entityType, index);
            Type idType = getTypeOfTarget(idAnnotationTarget);
            ResultHandle idValue = generateObtainValue(isNewCreator, entityType, entity, idAnnotationTarget);
            if (idType instanceof PrimitiveType) {
                if (!idType.name().equals(PRIMITIVE_LONG) && !idType.name().equals(PRIMITIVE_INTEGER)) {
                    throw new IllegalArgumentException("Id type of '" + entityType + "' is invalid.");
                }
                if (idType.name().equals(PRIMITIVE_LONG)) {
                    ResultHandle longObject = isNewCreator.invokeStaticMethod(
                            MethodDescriptor.ofMethod(Long.class, "valueOf", Long.class, long.class), idValue);
                    idValue = isNewCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(Long.class, "intValue", int.class), longObject);
                }

                BranchResult idValueNonZeroBranch = isNewCreator.ifNonZero(idValue);
                idValueNonZeroBranch.trueBranch().returnBoolean(false);
                idValueNonZeroBranch.falseBranch().returnBoolean(true);
            } else {
                BranchResult idValueNullBranch = isNewCreator.ifNull(idValue);
                idValueNullBranch.falseBranch().returnBoolean(false);
                idValueNullBranch.trueBranch().returnBoolean(true);
            }
        }
    }

    // --- Code from spring-data-jpa Quarkus extension
    private AnnotationTarget getIdAnnotationTarget(DotName entityDotName, IndexView index) {
        return getIdAnnotationTargetRec(entityDotName, index, entityDotName);
    }

    private AnnotationTarget getIdAnnotationTargetRec(
            DotName currentDotName, IndexView index, DotName originalEntityDotName) {
        ClassInfo classInfo = index.getClassByName(currentDotName);
        if (classInfo == null) {
            throw new IllegalStateException("Entity " + originalEntityDotName + " was not part of the Quarkus index");
        }

        List<AnnotationInstance> annotationInstances = Stream.of(JPA_ID, JPA_EMBEDDED_ID)
                .map(classInfo.annotationsMap()::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
        if (annotationInstances.isEmpty()) {
            if (OBJECT.equals(classInfo.superName())) {
                throw new IllegalArgumentException(
                        "Currently only Entities with the @Id or @EmbeddedId annotation are supported. Offending class is "
                                + originalEntityDotName);
            }
            return getIdAnnotationTargetRec(classInfo.superName(), index, originalEntityDotName);
        }

        if (annotationInstances.size() > 1) {
            throw new IllegalArgumentException(
                    "Currently the @Id or @EmbeddedId annotation can only be placed on a single field or method. "
                            + "Offending class is " + originalEntityDotName);
        }

        return annotationInstances.get(0).target();
    }

    private ResultHandle generateObtainValue(
            MethodCreator methodCreator,
            DotName entityDotName,
            ResultHandle entity,
            AnnotationTarget annotationTarget) {
        if (annotationTarget instanceof FieldInfo) {
            FieldInfo fieldInfo = annotationTarget.asField();
            if (Modifier.isPublic(fieldInfo.flags())) {
                return methodCreator.readInstanceField(of(fieldInfo), entity);
            }

            String getterMethodName = JavaBeanUtil.getGetterName(
                    fieldInfo.name(), fieldInfo.type().name());
            return methodCreator.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(
                            entityDotName.toString(),
                            getterMethodName,
                            fieldInfo.type().name().toString()),
                    entity);
        }
        MethodInfo methodInfo = annotationTarget.asMethod();
        return methodCreator.invokeVirtualMethod(
                MethodDescriptor.ofMethod(
                        entityDotName.toString(),
                        methodInfo.name(),
                        methodInfo.returnType().name().toString()),
                entity);
    }

    private Type getTypeOfTarget(AnnotationTarget idAnnotationTarget) {
        if (idAnnotationTarget instanceof FieldInfo) {
            return idAnnotationTarget.asField().type();
        }
        return idAnnotationTarget.asMethod().returnType();
    }
}
