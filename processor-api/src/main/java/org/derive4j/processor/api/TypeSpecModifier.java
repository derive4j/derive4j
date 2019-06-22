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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Kind;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

public final class TypeSpecModifier {
  public TypeSpecModifier(TypeSpec typeSpec) {
    kind = typeSpec.kind;
    name = typeSpec.name;
    javadoc = typeSpec.javadoc;
    annotations = typeSpec.annotations;
    modifiers = typeSpec.modifiers;
    typeVariables = typeSpec.typeVariables;
    superclass = typeSpec.superclass;
    superinterfaces = typeSpec.superinterfaces;
    enumConstants = typeSpec.enumConstants;
    fieldSpecs = typeSpec.fieldSpecs;
    methodSpecs = typeSpec.methodSpecs;
    typeSpecs = typeSpec.typeSpecs;
    originatingElements = typeSpec.originatingElements;
  }

  private final Kind             kind;
  private final String           name;
  private CodeBlock              javadoc;
  private List<AnnotationSpec>   annotations;
  private Set<Modifier>          modifiers;
  private List<TypeVariableName> typeVariables;
  private TypeName               superclass;
  private List<TypeName>         superinterfaces;
  private Map<String, TypeSpec>  enumConstants;
  private List<FieldSpec>        fieldSpecs;
  private List<MethodSpec>       methodSpecs;
  private List<TypeSpec>         typeSpecs;
  private final List<Element>    originatingElements;

  public TypeSpec build() {

    TypeSpec.Builder builder;
    switch (kind) {
      case ANNOTATION:
        builder = TypeSpec.annotationBuilder(name);
        break;
      case CLASS:
        builder = TypeSpec.classBuilder(name);
        break;
      case ENUM:
        builder = TypeSpec.enumBuilder(name);
        break;
      case INTERFACE:
        builder = TypeSpec.interfaceBuilder(name);
        break;
      default:
        throw new UnsupportedOperationException("Unknown kind: " + kind);
    }
    builder.addAnnotations(annotations)
        .addModifiers(modifiers.toArray(new Modifier[0]))
        .addTypeVariables(typeVariables)
        .superclass(superclass)
        .addSuperinterfaces(superinterfaces);
    enumConstants.forEach(builder::addEnumConstant);
    builder.addFields(fieldSpecs).addMethods(methodSpecs).addTypes(typeSpecs);
    originatingElements.forEach(builder::addOriginatingElement);
    return builder.build();
  }

  public TypeSpecModifier modJavadoc(UnaryOperator<CodeBlock> modJavadoc) {
    javadoc = modJavadoc.apply(javadoc);
    return this;
  }

  public TypeSpecModifier modAnnotations(UnaryOperator<List<AnnotationSpec>> modAnnotations) {
    annotations = modAnnotations.apply(annotations);
    return this;
  }

  public TypeSpecModifier modModifiers(UnaryOperator<Set<Modifier>> modModifiers) {
    modifiers = modModifiers.apply(modifiers);
    return this;
  }

  public TypeSpecModifier modTypeVariables(UnaryOperator<List<TypeVariableName>> modTypeVariables) {
    typeVariables = modTypeVariables.apply(typeVariables);
    return this;
  }

  public TypeSpecModifier modSuperclass(TypeName superclass) {
    this.superclass = superclass;
    return this;
  }

  public TypeSpecModifier modSuperinterfaces(UnaryOperator<List<TypeName>> modSuperinterfaces) {
    superinterfaces = modSuperinterfaces.apply(superinterfaces);
    return this;
  }

  public TypeSpecModifier modEnumConstants(UnaryOperator<Map<String, TypeSpec>> modEnumConstants) {
    enumConstants = modEnumConstants.apply(enumConstants);
    return this;
  }

  public TypeSpecModifier modFields(UnaryOperator<List<FieldSpec>> modFields) {
    fieldSpecs = modFields.apply(fieldSpecs);
    return this;
  }

  public TypeSpecModifier modMethods(UnaryOperator<List<MethodSpec>> modMethods) {
    methodSpecs = modMethods.apply(methodSpecs);
    return this;
  }

  public TypeSpecModifier modTypes(UnaryOperator<List<TypeSpec>> modTypes) {
    typeSpecs = modTypes.apply(typeSpecs);
    return this;
  }

}
