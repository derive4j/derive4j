/**
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Processor API".
 *
 * "Derive4J - Processor API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Processor API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Processor API".  If not, see <http://www.gnu.org/licenses/>.
 */

package org.derive4j.processor.api;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.util.Collections;
import java.util.List;

public abstract class DerivedCodeSpec {

  private DerivedCodeSpec() {
  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, List<FieldSpec> fields, List<MethodSpec> methods, List<DeriveMessage> infos, List<DeriveMessage> warnings) {
    return new DerivedCodeSpec() {

      @Override
      public <R> R match(Cases<R> cases) {
        return cases.spec(classes, fields, methods, infos, warnings);
      }
    };
  }

  public static DerivedCodeSpec codeSpec(TypeSpec classe, FieldSpec field, MethodSpec method) {
    return codeSpec(Collections.singletonList(classe), Collections.singletonList(field), Collections.singletonList(method), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(TypeSpec classe, MethodSpec method) {
    return codeSpec(Collections.singletonList(classe), Collections.<FieldSpec>emptyList(), Collections.singletonList(method), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(FieldSpec field, MethodSpec method) {
    return codeSpec(Collections.emptyList(), Collections.singletonList(field), Collections.singletonList(method), Collections.<DeriveMessage>emptyList(), Collections.<DeriveMessage>emptyList());
  }


  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, MethodSpec method) {
    return codeSpec(classes, Collections.emptyList(), Collections.singletonList(method), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec none() {
    return methodSpecs(Collections.emptyList());
  }

  public static DerivedCodeSpec methodSpecs(List<MethodSpec> methods) {
    return codeSpec(Collections.emptyList(), Collections.emptyList(), methods, Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec typeSpecs(List<TypeSpec> typeSpecs) {
    return codeSpec(typeSpecs, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec methodSpec(MethodSpec method) {
    return methodSpecs(Collections.singletonList(method));
  }

  public abstract <R> R match(Cases<R> cases);

  public List<TypeSpec> classes() {
    return match((classes, fields, methods, infos, warnings) -> classes);
  }

  public List<FieldSpec> fields() {
    return match((classes, fields, methods, infos, warnings) -> fields);
  }

  public List<MethodSpec> methods() {
    return match((classes, fields, methods, infos, warnings) -> methods);
  }

  public List<DeriveMessage> infos() {
    return match((classes, fields, methods, infos, warnings) -> infos);
  }

  public List<DeriveMessage> warnings() {
    return match((classes, fields, methods, infos, warnings) -> warnings);
  }

  public interface Cases<R> {
    R spec(List<TypeSpec> classes, List<FieldSpec> fields, List<MethodSpec> methods, List<DeriveMessage> infos, List<DeriveMessage> warnings);
  }

}
