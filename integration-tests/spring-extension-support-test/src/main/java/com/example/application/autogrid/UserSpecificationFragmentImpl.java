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
package com.example.application.autogrid;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

import dev.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class UserSpecificationFragmentImpl implements UserSpecificationFragment {

    private final EntityManager entityManager;
    private final JpaFilterConverter jpaFilterConverter;

    public UserSpecificationFragmentImpl(EntityManager entityManager, JpaFilterConverter jpaFilterConverter) {
        this.entityManager = entityManager;
        this.jpaFilterConverter = jpaFilterConverter;
    }

    @Override
    public long count(Filter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = builder.createQuery(User.class);
        Root<User> root = query.from(User.class);
        Predicate predicate = jpaFilterConverter.toPredicate(filter, User.class, builder, query, root);

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        countQuery.select(builder.count(root));
        if (predicate != null) {
            countQuery.where(predicate);
        }
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    @Override
    public List<User> list(Pageable pageable, Filter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = builder.createQuery(User.class);
        Root<User> root = query.from(User.class);
        Predicate predicate = jpaFilterConverter.toPredicate(filter, User.class, builder, query, root);
        if (predicate != null) {
            query.where(predicate);
        }
        if (pageable != null && pageable.getSortOr(Sort.unsorted()).isSorted()) {
            query.orderBy(jpaFilterConverter.toOrder(pageable.getSort(), builder, root));
        }
        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        return typedQuery.getResultList();
    }
}
