/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.derive4j.Data;

import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;

@Data
public abstract class DerivedCodeSpec {

  public interface Cases<R> {
    R codeSpec(List<TypeSpec> classes, List<FieldSpec> fields, List<MethodSpec> methods);
  }

  public static DerivedCodeSpec codeSpec(TypeSpec classes, FieldSpec field, MethodSpec method) {

    return DerivedCodeSpecs.codeSpec(Collections.singletonList(classes), Collections.singletonList(field),
        Collections.singletonList(method));
  }

  public static DerivedCodeSpec codeSpec(TypeSpec clazz, MethodSpec method) {

    return DerivedCodeSpecs.codeSpec(Collections.singletonList(clazz), Collections.emptyList(),
        Collections.singletonList(method));
  }

  public static DerivedCodeSpec codeSpec(TypeSpec clazz, List<MethodSpec> methods) {

    return DerivedCodeSpecs.codeSpec(Collections.singletonList(clazz), Collections.emptyList(), methods);
  }

  public static DerivedCodeSpec codeSpec(FieldSpec field, MethodSpec method) {

    return DerivedCodeSpecs.codeSpec(Collections.emptyList(), Collections.singletonList(field),
        Collections.singletonList(method));
  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, MethodSpec method) {

    return DerivedCodeSpecs.codeSpec(classes, Collections.emptyList(), Collections.singletonList(method));
  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, FieldSpec field, MethodSpec method) {

    return DerivedCodeSpecs.codeSpec(classes, Collections.singletonList(field), Collections.singletonList(method));
  }

  public static DerivedCodeSpec methodSpecs(List<MethodSpec> methods) {

    return DerivedCodeSpecs.codeSpec(Collections.emptyList(), Collections.emptyList(), methods);
  }

  public static DerivedCodeSpec methodSpec(MethodSpec method) {

    return methodSpecs(Collections.singletonList(method));
  }

  public static DerivedCodeSpec none() {

    return methodSpecs(Collections.emptyList());
  }

  DerivedCodeSpec() {

  }

  public abstract <R> R match(Cases<R> cases);

  public final DerivedCodeSpec append(DerivedCodeSpec cs) {

    return DerivedCodeSpecs.codeSpec(concat(classes(), cs.classes()), concat(fields(), cs.fields()),
        concat(methods(), cs.methods()));
  }

  public final List<TypeSpec> classes() {

    return getClasses(this);
  }

  public final List<FieldSpec> fields() {

    return getFields(this);
  }

  public final List<MethodSpec> methods() {

    return getMethods(this);
  }

  private static <A> List<A> concat(List<A> as1, List<A> as2) {

    return Stream.concat(as1.stream(), as2.stream()).collect(Collectors.toList());
  }

}
