/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotation Processor".
 *
 * "Derive4J - Annotation Processor" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotation Processor" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "Derive4J - Annotation Processor".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import org.derive4j.ExportAsPublic;
import org.derive4j.processor.api.Derivator;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.model.AlgebraicDataType;

import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.DerivedCodeSpec.methodSpec;
import static org.derive4j.processor.api.DerivedCodeSpec.none;

final class ExportDerivator implements Derivator {

  ExportDerivator(DeriveUtils utils) {
    this.utils = utils;
    exportAsPublicAnnotation = utils.elements().getTypeElement(ExportAsPublic.class.getName());
  }

  private final DeriveUtils utils;
  private final TypeElement exportAsPublicAnnotation;

  @Override
  public DeriveResult<DerivedCodeSpec> derive(AlgebraicDataType adt) {

    return result(ElementFilter.methodsIn(adt.typeConstructor().typeElement().getEnclosedElements())
        .stream()
        .filter(executableElement -> isStaticPackage(executableElement.getModifiers()))
        .filter(executableElement -> hasExportAsPublicAnnotation(executableElement))
        .map(this::exportAsPublic)
        .reduce(none(), DerivedCodeSpec::append));

  }

  private DerivedCodeSpec exportAsPublic(ExecutableElement executableElement) {
    MethodSpec.Builder methodBuilder = replicate(executableElement).addModifiers(Modifier.PUBLIC);

    Name adtClassName = executableElement.getEnclosingElement().getSimpleName();
    String methodName = executableElement.getSimpleName().toString();
    String parameters = executableElement.getParameters()
        .stream()
        .map(ve -> ve.getSimpleName().toString())
        .collect(Collectors.joining(", "));

    DerivedCodeSpec result;

    if (executableElement.getParameters().isEmpty()) {

      FieldSpec.Builder singleton = FieldSpec.builder(ClassName.get(executableElement.getReturnType()), methodName,
          Modifier.PRIVATE, Modifier.STATIC);

      if (!executableElement.getTypeParameters().isEmpty()) {
        singleton.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build());
        methodBuilder.addAnnotation(
            AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "rawtypes", "unchecked").build());
      }

      result = DerivedCodeSpec.codeSpec(singleton.build(),
          methodBuilder.addStatement("$1T _$2L = $2L", TypeName.get(executableElement.getReturnType()), methodName)
              .beginControlFlow("if (_$L == null)", methodName)
              .addStatement("$1L = _$1L = $2L.$1L($3L)", methodName, adtClassName, parameters)
              .endControlFlow()
              .addStatement("return _$L", methodName)
              .build());
    } else {
      result = methodSpec(replicate(executableElement).addModifiers(Modifier.PUBLIC)
          .addStatement("return $L.$L($L)", adtClassName, methodName, parameters)
          .build());
    }

    return result;
  }

  private MethodSpec.Builder replicate(ExecutableElement method) {

    String methodName = method.getSimpleName().toString();
    MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
    methodBuilder.addModifiers(method.getModifiers());

    for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
      TypeVariable var = (TypeVariable) typeParameterElement.asType();
      methodBuilder.addTypeVariable(TypeVariableName.get(var));
    }

    methodBuilder.returns(TypeName.get(method.getReturnType()));

    List<? extends VariableElement> parameters = method.getParameters();
    for (VariableElement parameter : parameters) {
      TypeName type = TypeName.get(parameter.asType());
      String name = parameter.getSimpleName().toString();
      Set<Modifier> parameterModifiers = parameter.getModifiers();
      ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(type, name)
          .addModifiers(parameterModifiers.toArray(new Modifier[parameterModifiers.size()]));
      for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
        parameterBuilder.addAnnotation(AnnotationSpec.get(mirror));
      }
      methodBuilder.addParameter(parameterBuilder.build());
    }
    methodBuilder.varargs(method.isVarArgs());

    for (TypeMirror thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }
    String docComment = utils.elements().getDocComment(method);
    if (docComment != null) {
      methodBuilder.addJavadoc(docComment);
    }

    return methodBuilder;
  }

  private boolean hasExportAsPublicAnnotation(ExecutableElement executableElement) {
    return executableElement.getAnnotationMirrors()
        .stream()
        .anyMatch(am -> exportAsPublicAnnotation.equals(am.getAnnotationType().asElement()));
  }

  private static boolean isStaticPackage(Set<Modifier> modifiers) {
    return modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.PRIVATE);
  }

}
