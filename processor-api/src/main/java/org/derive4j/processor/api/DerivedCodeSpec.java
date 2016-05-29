/*
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.derive4j.Data;
import org.derive4j.Derive;

import static java.util.Collections.unmodifiableList;
import static org.derive4j.Visibility.Smart;
import static org.derive4j.processor.api.DerivedCodeSpecs.getClasses;
import static org.derive4j.processor.api.DerivedCodeSpecs.getFields;
import static org.derive4j.processor.api.DerivedCodeSpecs.getInfos;
import static org.derive4j.processor.api.DerivedCodeSpecs.getMethods;
import static org.derive4j.processor.api.DerivedCodeSpecs.getWarnings;

@Data(@Derive(withVisibility = Smart)) public abstract class DerivedCodeSpec {

  DerivedCodeSpec() {

  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, List<FieldSpec> fields, List<MethodSpec> methods, List<DeriveMessage> infos,
      List<DeriveMessage> warnings) {

    return DerivedCodeSpecs.codeSpec(unmodifiableList(new ArrayList<>(classes)), unmodifiableList(new ArrayList<>(fields)),
        unmodifiableList(new ArrayList<>(methods)), unmodifiableList(new ArrayList<>(infos)), unmodifiableList(new ArrayList<>(warnings)));
  }

  public static DerivedCodeSpec codeSpec(TypeSpec classes, FieldSpec field, MethodSpec method) {

    return codeSpec(Collections.singletonList(classes), Collections.singletonList(field), Collections.singletonList(method), Collections.emptyList(),
        Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(TypeSpec clazz, MethodSpec method) {

    return codeSpec(Collections.singletonList(clazz), Collections.emptyList(), Collections.singletonList(method), Collections.emptyList(),
        Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(FieldSpec field, MethodSpec method) {

    return codeSpec(Collections.emptyList(), Collections.singletonList(field), Collections.singletonList(method), Collections.emptyList(),
        Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, MethodSpec method) {

    return codeSpec(classes, Collections.emptyList(), Collections.singletonList(method), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec codeSpec(List<TypeSpec> classes, FieldSpec field, MethodSpec method) {

    return codeSpec(classes, Collections.singletonList(field), Collections.singletonList(method), Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec methodSpecs(List<MethodSpec> methods) {

    return codeSpec(Collections.emptyList(), Collections.emptyList(), methods, Collections.emptyList(), Collections.emptyList());
  }

  public static DerivedCodeSpec methodSpec(MethodSpec method) {

    return methodSpecs(Collections.singletonList(method));
  }

  public static DerivedCodeSpec none() {

    return methodSpecs(Collections.emptyList());
  }

  private static <A> List<A> concat(List<A> as1, List<A> as2) {

    return Stream.concat(as1.stream(), as2.stream()).collect(Collectors.toList());
  }

  public abstract <R> R match(Cases<R> cases);

  public final DerivedCodeSpec append(DerivedCodeSpec cs) {

    return codeSpec(concat(classes(), cs.classes()), concat(fields(), cs.fields()), concat(methods(), cs.methods()), concat(infos(), cs.infos()),
        concat(warnings(), cs.warnings()));
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

  public final List<DeriveMessage> infos() {

    return getInfos(this);
  }

  public final List<DeriveMessage> warnings() {

    return getWarnings(this);
  }

  public interface Cases<R> {
    R codeSpec(List<TypeSpec> classes, List<FieldSpec> fields, List<MethodSpec> methods, List<DeriveMessage> infos, List<DeriveMessage> warnings);
  }

}
