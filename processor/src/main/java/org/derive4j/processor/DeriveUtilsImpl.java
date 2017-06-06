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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.derive4j.ExportAsPublic;
import org.derive4j.Flavour;
import org.derive4j.Flavours;
import org.derive4j.processor.api.Binding;
import org.derive4j.processor.api.BoundExpression;
import org.derive4j.processor.api.DeriveResult;
import org.derive4j.processor.api.DeriveResults;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.DerivedCodeSpec;
import org.derive4j.processor.api.DerivedCodeSpecs;
import org.derive4j.processor.api.EitherModel;
import org.derive4j.processor.api.EitherModels;
import org.derive4j.processor.api.FieldsTypeClassInstanceBindingMap;
import org.derive4j.processor.api.FreeVariable;
import org.derive4j.processor.api.FreeVariables;
import org.derive4j.processor.api.InstanceLocation;
import org.derive4j.processor.api.InstanceLocations;
import org.derive4j.processor.api.InstanceUtils;
import org.derive4j.processor.api.ObjectModel;
import org.derive4j.processor.api.OptionModel;
import org.derive4j.processor.api.OptionModels;
import org.derive4j.processor.api.SamInterface;
import org.derive4j.processor.api.SamInterfaces;
import org.derive4j.processor.api.model.AlgebraicDataType;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataArguments;
import org.derive4j.processor.api.model.DataConstructor;
import org.derive4j.processor.api.model.DeriveConfig;
import org.derive4j.processor.api.model.DeriveConfigs;
import org.derive4j.processor.api.model.DeriveTargetClass;
import org.derive4j.processor.api.model.DerivedInstanceConfigs;
import org.derive4j.processor.api.model.Expression;
import org.derive4j.processor.api.model.Expressions;
import org.derive4j.processor.api.model.TypeRestriction;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.derive4j.processor.P2.p2;
import static org.derive4j.processor.Unit.unit;
import static org.derive4j.processor.Utils.asBoxedType;
import static org.derive4j.processor.Utils.asDeclaredType;
import static org.derive4j.processor.Utils.asExecutableElement;
import static org.derive4j.processor.Utils.asTypeElement;
import static org.derive4j.processor.Utils.asTypeVariable;
import static org.derive4j.processor.Utils.findOnlyOne;
import static org.derive4j.processor.Utils.fold;
import static org.derive4j.processor.Utils.get;
import static org.derive4j.processor.Utils.getFields;
import static org.derive4j.processor.Utils.getMethods;
import static org.derive4j.processor.Utils.joinStringsAsArguments;
import static org.derive4j.processor.Utils.optionalAsStream;
import static org.derive4j.processor.api.Bindings.binding;
import static org.derive4j.processor.api.BoundExpressions.expression;
import static org.derive4j.processor.api.BoundExpressions.getExpression;
import static org.derive4j.processor.api.BoundExpressions.getFreeVariables;
import static org.derive4j.processor.api.BoundExpressions.modExpression;
import static org.derive4j.processor.api.DeriveMessages.message;
import static org.derive4j.processor.api.DeriveResult.error;
import static org.derive4j.processor.api.DeriveResult.result;
import static org.derive4j.processor.api.EitherModels.EitherModel;
import static org.derive4j.processor.api.FieldsTypeClassInstanceBindingMaps.bindingMap;
import static org.derive4j.processor.api.FieldsTypeClassInstanceBindingMaps.getBindingsByFieldName;
import static org.derive4j.processor.api.FieldsTypeClassInstanceBindingMaps.getFreeVariables;
import static org.derive4j.processor.api.FreeVariables.getName;
import static org.derive4j.processor.api.FreeVariables.getType;
import static org.derive4j.processor.api.FreeVariables.variable;
import static org.derive4j.processor.api.InstanceLocations.caseOf;
import static org.derive4j.processor.api.InstanceLocations.method;
import static org.derive4j.processor.api.InstanceLocations.value;
import static org.derive4j.processor.api.ObjectModels.ObjectModel;
import static org.derive4j.processor.api.SamInterfaces.SamInterface;
import static org.derive4j.processor.api.model.Expressions.baseExpression;
import static org.derive4j.processor.api.model.Expressions.caseOf;
import static org.derive4j.processor.api.model.Expressions.recursiveExpression;

final class DeriveUtilsImpl implements DeriveUtils {

  private final Elements Elements;
  private final Types Types;
  private final DeriveConfigBuilder deriveConfigBuilder;
  private final ObjectModel objectModel;

  private final Function<Flavour, SamInterface> function0Model;
  private final Function<Flavour, SamInterface> function1Model;
  private final Function<Flavour, OptionModel> optionModel;
  private final Function<Flavour, Optional<EitherModel>> eitherModel;

  DeriveUtilsImpl(Elements Elements, Types Types, DeriveConfigBuilder deriveConfigBuilder) {

    this.Elements = Elements;
    this.Types = Types;
    this.deriveConfigBuilder = deriveConfigBuilder;

    TypeElement object = Elements.getTypeElement(Object.class.getName());
    List<ExecutableElement> objectMethods = ElementFilter.methodsIn(object.getEnclosedElements());

    objectModel = ObjectModel(object,
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("equals")).findAny().get(),
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("hashCode")).findAny().get(),
        objectMethods.stream().filter(e -> e.getSimpleName().contentEquals("toString")).findAny().get());

    SamInterface jdkSupplier = samInterface(Supplier.class.getName()).get();
    SamInterface guavaSupplier = lazySamInterface("com.google.common.base.Supplier");

    function0Model = Flavours.cases()
        .Jdk_(jdkSupplier)
        .Fj_(lazySamInterface("fj.F0"))
        .Fugue_(jdkSupplier)
        .Fugue2_(guavaSupplier)
        .Javaslang_(jdkSupplier)
        .Vavr_(jdkSupplier)
        .HighJ_(jdkSupplier)
        .Guava_(guavaSupplier);

    SamInterface jdkFunction = samInterface(Function.class.getName()).get();
    SamInterface guavaFunction = lazySamInterface("com.google.common.base.Function");

    function1Model = Flavours.cases()
        .Jdk_(jdkFunction)
        .Fj_(lazySamInterface("fj.F"))
        .Fugue_(jdkFunction)
        .Fugue2_(guavaFunction)
        .Javaslang_(lazySamInterface("javaslang.Function1"))
        .Vavr_(lazySamInterface("io.vavr.Function1"))
        .HighJ_(lazySamInterface("org.highj.function.F1"))
        .Guava_(guavaFunction);

    optionModel = Flavours.cases()
        .Jdk_(lazyOptionModel(Optional.class.getName(), "empty", "of"))
        .Fj_(lazyOptionModel("fj.data.Option", "none", "some"))
        .Fugue_(lazyOptionModel("io.atlassian.fugue.Option", "none", "some"))
        .Fugue2_(lazyOptionModel("com.atlassian.fugue.Option", "none", "some"))
        .Javaslang_(lazyOptionModel("javaslang.control.Option", "none", "some"))
        .Vavr_(lazyOptionModel("io.vavr.control.Option", "none", "some"))
        .HighJ_(lazyOptionModel("org.highj.data.Maybe", "Nothing", "Just"))
        .Guava_(lazyOptionModel("com.google.common.base.Optional", "absent", "of"));

    eitherModel = Flavours.cases()
        .Jdk_(Optional.<EitherModel>empty())
        .Fj_(eitherModel("fj.data.Either", "left", "right"))
        .Fugue_(eitherModel("io.atlassian.fugue.Either", "left", "right"))
        .Fugue2_(eitherModel("com.atlassian.fugue.Either", "left", "right"))
        .Javaslang_(eitherModel("javaslang.control.Either", "left", "right"))
        .Vavr_(eitherModel("io.vavr.control.Either", "left", "right"))
        .HighJ_(eitherModel("org.highj.data.Either", "Left", "Right"))
        .Guava_(Optional.empty());
  }

  @Override
  public Types types() {

    return Types;
  }

  @Override
  public Elements elements() {

    return Elements;
  }

  @Override
  public TypeName resolveToTypeName(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeName>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? TypeName.get(dt)
            : ParameterizedTypeName.get(ClassName.get(asTypeElement.visit(dt.asElement()).get()),
                dt.getTypeArguments().stream().map(ta -> resolveToTypeName(ta, typeArgs)).toArray(TypeName[]::new)))
        .orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(TypeName.get(typeMirror)));
  }

  @Override
  public Function<TypeVariable, Optional<TypeMirror>> typeRestrictions(List<TypeRestriction> typeRestrictions) {

    return tv -> typeRestrictions.stream()
        .filter(tr -> Types.isSameType(tr.restrictedTypeVariable(), tv))
        .findFirst()
        .map(TypeRestriction::refinementType);
  }

  @Override
  public TypeMirror resolve(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().isEmpty()
            ? dt
            : Types.getDeclaredType(asTypeElement.visit(dt.asElement()).get(),
                dt.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new))).<TypeMirror>map(
            dt -> dt).orElse(asTypeVariable.visit(typeMirror).flatMap(typeArgs).orElse(typeMirror));
  }

  @Override
  public DeclaredType resolve(DeclaredType declaredType, Function<TypeVariable, Optional<TypeMirror>> typeArgs) {

    return declaredType.getTypeArguments().isEmpty()
        ? declaredType
        : Types.getDeclaredType(asTypeElement.visit(declaredType.asElement()).get(),
            declaredType.getTypeArguments().stream().map(ta -> resolve(ta, typeArgs)).toArray(TypeMirror[]::new));
  }

  @Override
  public MethodSpec.Builder overrideMethodBuilder(ExecutableElement abstractMethod, DeclaredType declaredType) {

    return MethodSpec.overriding(abstractMethod, declaredType, Types);
  }

  @Override
  public List<TypeVariable> typeVariablesIn(TypeMirror typeMirror) {
    List<TypeVariable> typeVariables = new ArrayList<>();

    typeVariablesIn0(typeMirror).forEach(tv -> {
      if (typeVariables.stream().noneMatch(predTv -> Types.isSameType(predTv, tv))) {
        typeVariables.add(tv);
      }
    });
    return typeVariables;
  }

  @Override
  public List<ExecutableElement> allAbstractMethods(DeclaredType declaredType) {

    return asTypeElement.visit(declaredType.asElement()).map(typeElement -> {

      List<P2<ExecutableElement, ExecutableType>> unorderedAbstractMethods = getMethods(
          Elements.getAllMembers(typeElement)).filter(this::abstractMethod)
          .map(e -> p2(e, (ExecutableType) Types.asMemberOf(declaredType, e)))
          .collect(toList());

      Set<ExecutableElement> deduplicatedUnorderedAbstractMethods = IntStream.range(0, unorderedAbstractMethods.size())
          .filter(i -> unorderedAbstractMethods.subList(0, i)
              .stream()
              .noneMatch(m -> m.match((predExecutableElement, predExecutableType) -> unorderedAbstractMethods.get(i)
                  .match((executableElement, executableType) -> predExecutableElement.getSimpleName()
                      .equals(executableElement.getSimpleName()) && Types.isSubsignature(predExecutableType, executableType)))))
          .mapToObj(i -> unorderedAbstractMethods.get(i).match((executableElement, __) -> executableElement))
          .collect(toSet());

      return Stream.concat(getSuperTypeElements(typeElement), Stream.of(typeElement))
          .flatMap(te -> te.getEnclosedElements().stream())
          .map(asExecutableElement::visit)
          .flatMap(Utils::optionalAsStream)
          .filter(deduplicatedUnorderedAbstractMethods::contains)
          .collect(toList());

    }).orElse(emptyList());
  }

  @Override
  public List<ExecutableElement> allAbstractMethods(TypeElement typeElement) {

    return allAbstractMethods((DeclaredType) typeElement.asType());
  }

  @Override
  public Stream<ExecutableElement> allStaticMethods(TypeElement typeElement) {
    return getMethods(Elements.getAllMembers(typeElement)).filter(
        e -> e.getModifiers().contains(Modifier.STATIC) && !e.getModifiers().contains(Modifier.PRIVATE));
  }

  @Override
  public Stream<VariableElement> allStaticFields(TypeElement typeElement) {
    return getFields(Elements.getAllMembers(typeElement)).filter(e -> e.getModifiers().contains(Modifier.STATIC) &&
        e.getModifiers().contains(Modifier.FINAL) &&
        !e.getModifiers().contains(Modifier.PRIVATE));
  }

  @Override
  public Optional<DeclaredType> asDeclaredType(TypeMirror typeMirror) {
    return asDeclaredType.visit(typeMirror);
  }

  @Override
  public Optional<TypeElement> asTypeElement(TypeMirror typeMirror) {
    return asDeclaredType(typeMirror).map(DeclaredType::asElement).flatMap(asTypeElement::visit);
  }

  @Override
  public ObjectModel object() {
    return objectModel;
  }

  @Override
  public Optional<SamInterface> samInterface(String qualifiedClassName) {
    return Optional.ofNullable(Elements.getTypeElement(qualifiedClassName))
        .flatMap(
            typeElement -> findOnlyOne(allAbstractMethods(typeElement)).map(samMethod -> SamInterface(typeElement, samMethod)));
  }

  @Override
  public SamInterface function0Model(Flavour flavour) {
    return function0Model.apply(flavour);
  }

  @Override
  public SamInterface function1Model(Flavour flavour) {
    return function1Model.apply(flavour);
  }

  @Override
  public OptionModel optionModel(Flavour flavour) {
    return optionModel.apply(flavour);
  }

  @Override
  public Optional<EitherModel> eitherModel(Flavour flavour) {
    return eitherModel.apply(flavour);
  }

  @Override
  public String uncapitalize(CharSequence string) {
    return Utils.uncapitalize(string);
  }

  @Override
  public String capitalize(CharSequence string) {
    return Utils.capitalize(string);
  }

  @Override
  public Optional<InstanceLocation> findInstance(TypeElement typeElementContext, ClassName typeClassContext, ClassName typeClass,
      TypeElement typeElement, List<TypeElement> lowPriorityProviders) {

    if (typeElementContext.equals(typeElement) && typeClassContext.equals(typeClass)) {
      return Optional.empty();
    }

    Optional<DeriveConfig> maybeDeriveConfig = deriveConfigBuilder.findDeriveConfig(typeElement).map(P2s::get_2);

    Optional<InstanceLocation> manualInstance = findCompiledInstance(typeElementContext,
        elements().getTypeElement(typeClass.reflectionName()), typeElement, lowPriorityProviders,
        maybeDeriveConfig.map(DeriveConfigs::getTargetClass).map(DeriveTargetClass::className));

    return fold(manualInstance, maybeDeriveConfig.flatMap(deriveConfig -> get(typeClass, deriveConfig.derivedInstances()).map(
        derivedInstanceConfig -> InstanceLocations.generatedIn(
            DerivedInstanceConfigs.getTargetClass(derivedInstanceConfig).orElse(deriveConfig.targetClass().className())))),
        Optional::of);
  }

  @Override
  public DeriveResult<BoundExpression> instanceInitializer(TypeElement typeElementContext, ClassName typeClassContext,
      ClassName typeClass, TypeMirror type, List<TypeElement> lowPriorityProviders) {
    Optional<DeclaredType> maybeDeclaredType = asDeclaredType(asBoxedType.visit(type, types()));
    TypeElement typeClassElement = elements().getTypeElement(typeClass.reflectionName());

    return fold(maybeDeclaredType, DeriveResults.lazy(() -> result(expression(
        singletonList(variable(types().getDeclaredType(typeClassElement, type), instanceVariableName(typeClassElement, type))),
        baseExpression(CodeBlock.of(instanceVariableName(typeClassElement, type)))))), declaredType -> {

      TypeElement typeElement = asTypeElement(declaredType).orElseThrow(RuntimeException::new);

      return fold(findInstance(typeElementContext, typeClassContext, typeClass, typeElement, lowPriorityProviders),
          typeElement.equals(typeElementContext) && typeClassContext.equals(typeClass)
              ? result(expression(emptyList(), recursiveExpression(identity())))
              : DeriveResult.<BoundExpression>error(message("Could not find instance of " + typeClass + " for " + typeElement)),
          instanceLocation -> caseOf(instanceLocation).
              value(ve -> result(expression(emptyList(), baseExpression(
                  CodeBlock.of("$T.$L", ClassName.bestGuess(ve.getEnclosingElement().toString()), ve.getSimpleName())))))
              .generatedIn(instanceClass -> declaredType.getTypeArguments().isEmpty()
                  ? result(expression(emptyList(),
                  baseExpression(CodeBlock.of("$T.$L()", instanceClass, generatedInstanceMethodName(typeClassElement, typeElement)))))
                  : error(message("Please provide static forwarder for generated " + typeClass + " instance for " + typeElement)))
              .method((className, method) -> {

                List<P2<TypeMirror, Integer>> indexedTypeArguments = Utils.zipWithIndex(
                    asDeclaredType(asDeclaredType(method.getReturnType()).get().getTypeArguments().get(0)).get()
                        .getTypeArguments());

                DeriveResult<BoundExpression> args = Utils.zipWithIndex(method.getParameters())
                    .stream()
                    .map(param -> param.match((ve, i) -> {
                      List<TypeVariable> paramTypeVariables = typeVariablesIn(ve.asType());
                      return fold(asTypeElement(ve.asType()).flatMap(paramTypeElement -> indexedTypeArguments.stream()
                              .filter(ta -> paramTypeVariables.stream().anyMatch(tv -> Types.isSameType(tv, P2s.get_1(ta))))
                              .findFirst()
                              .map(P2s::get_2)
                              .map(declaredType.getTypeArguments()::get)
                              .flatMap(tm -> DeriveResults.getResult(
                                  instanceInitializer(typeElementContext, typeClassContext, ClassName.get(paramTypeElement), tm,
                                      lowPriorityProviders)))),
                          DeriveResult.<BoundExpression>error(message("Cannot find type class " + ve.asType())),
                          DeriveResult::result);
                    }))
                    .reduce((dr1, dr2) -> dr1.bind(be1 -> dr2.map(be2 -> join(DeriveUtilsImpl::joinAsArgs, be1, be2))))
                    .orElse(result(expression(emptyList(), baseExpression(CodeBlock.of("")))));

                return args.map(modExpression(Expressions.cases()
                    .baseExpression(cb -> baseExpression(CodeBlock.builder()
                        .add("$T.", className)
                        .add(asTypeArguments(typeVariablesIn(type)))
                        .add("$L(", method.getSimpleName())
                        .add(cb)
                        .add(")")
                        .build()))
                    .recursiveExpression(fromOuterMethod -> recursiveExpression(outterMethod -> CodeBlock.builder()
                        .add("$T.$L(", className, method.getSimpleName())
                        .add(fromOuterMethod.apply(outterMethod))
                        .add(")")
                        .build()))));
              }));
    });
  }

  @Override
  public DeriveResult<FieldsTypeClassInstanceBindingMap> resolveFieldInstances(AlgebraicDataType adt, ClassName typeClass,
      List<TypeElement> lowPriorityProviders) {

    TypeElement typeClassElement = elements().getTypeElement(typeClass.reflectionName());
    return adt.fields()
        .stream()
        .map(da -> instanceInitializer(adt.typeConstructor().typeElement(), typeClass, typeClass, da.type(), lowPriorityProviders)
            .map(e -> bindingMap(getFreeVariables(e), singletonMap(da.fieldName(), binding(
                variable(types().getDeclaredType(typeClassElement, asBoxedType.visit(da.type(), types())),
                    instanceVariableName(typeClassElement, da.type())), getExpression(e))))))
        .reduce((db1, db2) -> db1.bind(b1 -> db2.map(b2 -> join(b1, b2))))
        .orElse(result(bindingMap(emptyList(), emptyMap())));
  }

  @Override
  public CodeBlock lambdaImpl(DataConstructor constructor, CodeBlock impl) {
    return lambdaImpl(constructor, "", impl);
  }

  @Override
  public CodeBlock lambdaImpl(DataConstructor constructor, String suffix, CodeBlock impl) {
    return CodeBlock.builder().add(parameterList(constructor, suffix)).add(" -> ").add(impl).build();
  }

  @Override
  public DeriveResult<DerivedCodeSpec> generateInstance(AlgebraicDataType adt, ClassName typeClass,
      List<TypeElement> lowPriorityProviders, Function<InstanceUtils, DerivedCodeSpec> generateInstance) {

    return resolveFieldInstances(adt, typeClass, lowPriorityProviders).map(

        fieldsTypeClassInstanceBindingMap -> generateInstance.apply(new InstanceUtils() {

          List<FreeVariable> freeVariables = getFreeVariables(fieldsTypeClassInstanceBindingMap);

          final String methodName = generatedInstanceMethodName(elements().getTypeElement(typeClass.reflectionName()),
              adt.typeConstructor().typeElement());

          final Function<DataArgument, CodeBlock> methodRecursiveCall = da ->

              CodeBlock.builder()
                  .add("$T.", adt.deriveConfig()
                      .derivedInstances()
                      .get(typeClass)
                      .targetClass()
                      .orElse(adt.deriveConfig().targetClass().className()))
                  .add(findFirstDeclaredTypeOf(adt.typeConstructor().typeElement(), da.type()).map(
                      dt -> asTypeArguments(dt.getTypeArguments())).orElse(CodeBlock.of("")))
                  .add("$L($L)", methodName, joinStringsAsArguments(freeVariables.stream().map(FreeVariables::getName)))
                  .build();

          @Override
          public FieldsTypeClassInstanceBindingMap bindings() {
            return fieldsTypeClassInstanceBindingMap;
          }

          @Override
          public DerivedCodeSpec generateInstanceFactory(CodeBlock statement, CodeBlock... statements) {

            ParameterizedTypeName returnType = ParameterizedTypeName.get(typeClass,
                TypeName.get(adt.typeConstructor().declaredType()));
            MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(adt.typeConstructor().typeVariables().stream().map(TypeVariableName::get).collect(toList()))
                .returns(returnType)
                .addParameters(freeVariables.stream()
                    .map(fv -> fv.variable((type, name) -> ParameterSpec.builder(TypeName.get(type), name).build()))
                    .collect(toList()));

            List<FieldSpec> fieldSpecs = new ArrayList<>();

            if (freeVariables.isEmpty()) {
              fieldSpecs.add(FieldSpec.builder(typeClass, methodName, Modifier.PRIVATE, Modifier.STATIC)
                  .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build())
                  .build());
              method.addAnnotation(
                  AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "{$S, $S}", "rawtypes", "unchecked").build())
                  .addStatement("$1T _$2L = $2L", returnType, methodName)
                  .beginControlFlow("if (_$L == null)", methodName);
            }

            List<FreeVariable> seenVariable = new ArrayList<>(freeVariables);
            getBindingsByFieldName(fieldsTypeClassInstanceBindingMap).values()
                .forEach(binding -> binding.binding((variable, value) -> {
                  if (isNotIn(seenVariable, variable)) {
                    Expressions.getCodeBlock(value).ifPresent(cb -> {
                      String expr = cb.toString();
                      if (expr.endsWith(")") && !expr.endsWith("()")) {
                        method.addCode("$T $L = ", TypeName.get(getType(variable)), getName(variable)).addCode(cb).addCode(";\n");
                      }
                    });
                    seenVariable.add(variable);
                  }
                  return unit;
                }));

            List<CodeBlock> allCustomStatements = new ArrayList<>();
            allCustomStatements.add(statement);
            allCustomStatements.addAll(Arrays.asList(statements));
            allCustomStatements.subList(0, allCustomStatements.size() - 1)
                .forEach(cb -> method.addCode(cb.toBuilder().add(";").build()));

            if (freeVariables.isEmpty()) {
              method.addCode("$1L = _$1L = ", methodName)
                  .addCode(allCustomStatements.get(allCustomStatements.size() - 1))
                  .addCode(";\n")
                  .endControlFlow()
                  .addStatement("return _$L", methodName);
            } else {
              method.addCode(CodeBlock.builder()
                  .add("return ")
                  .add(allCustomStatements.get(allCustomStatements.size() - 1))
                  .add(";\n")
                  .build());
            }

            return DerivedCodeSpecs.codeSpec(emptyList(), fieldSpecs, singletonList(method.build()));
          }

          @Override
          public CodeBlock matchImpl(Function<DataConstructor, CodeBlock> lambdaImpl) {
            boolean useVisitorFactory = adt.dataConstruction().isVisitorDispatch() &&
                adt.dataConstruction().constructors().size() > 1;
            return CodeBlock.builder()
                .add("$L(\n", adt.matchMethod().element().getSimpleName() +
                    (useVisitorFactory
                         ? "(" + adt.matchMethod().element().getParameters().get(0).getSimpleName()
                         : ""))
                .indent()
                .add(adt.dataConstruction()
                    .constructors()
                    .stream()
                    .map(dc -> lambdaImpl.apply(dc))
                    .reduce((cb1, cb2) -> cb1.toBuilder().add(",\n").add(cb2).build())
                    .orElse(CodeBlock.of("")))
                .add("\n")
                .unindent()
                .add(useVisitorFactory
                    ? "))"
                    : ")")
                .build();
          }

          @Override
          public CodeBlock instanceFor(DataArgument da) {
            return getBindingsByFieldName(fieldsTypeClassInstanceBindingMap).get(da.fieldName())
                .binding((variable, value) -> caseOf(value).baseExpression(cb -> {
                  String expr = cb.toString();
                  return expr.endsWith(")") && !expr.endsWith("()")
                      ? CodeBlock.of(getName(variable))
                      : cb;
                }).recursiveExpression(fromOuter -> fromOuter.apply(methodRecursiveCall.apply(da))));
          }

          @Override
          public String adtVariableName() {
            NameAllocator na = new NameAllocator();
            freeVariables.stream().map(FreeVariables::getName).forEach(na::newName);
            adt.fields().stream().map(DataArguments::getFieldName).forEach(na::newName);
            return na.newName(uncapitalize(adt.typeConstructor().typeElement().getSimpleName().toString()));
          }
        }));
  }

  @Override
  public CodeBlock parameterList(DataConstructor constructor) {
    return CodeBlock.builder()
        .add("(")
        .add(Utils.asLambdaParametersString(constructor.arguments(), constructor.typeRestrictions()))
        .add(")")
        .build();
  }

  @Override
  public CodeBlock parameterList(DataConstructor constructor, String suffix) {
    return CodeBlock.builder()
        .add("(")
        .add(Utils.asLambdaParametersString(constructor.arguments(), constructor.typeRestrictions(), suffix))
        .add(")")
        .build();
  }

  private CodeBlock asTypeArguments(List<? extends TypeMirror> typeVariables) {
    return typeVariables.stream()
        .map(tv -> CodeBlock.of("$T", TypeName.get(tv)))
        .reduce((tv1, tv2) -> tv1.toBuilder().add(", ").add(tv2).build())
        .map(tvs -> CodeBlock.builder().add("<").add(tvs).add(">").build())
        .orElse(CodeBlock.of(""));
  }

  private Optional<DeclaredType> findFirstDeclaredTypeOf(TypeElement typeElement, TypeMirror inType) {
    return asDeclaredType(inType).flatMap(dt -> dt.asElement().equals(typeElement)
        ? Optional.of(dt)
        : dt.getTypeArguments().stream().flatMap(ta -> optionalAsStream(findFirstDeclaredTypeOf(typeElement, ta))).findFirst());
  }

  private String instanceVariableName(TypeElement typeClass, TypeMirror type) {
    return uncapitalize(
        concat(allTypeArgsAsString(type), Stream.of(typeClass.getSimpleName().toString())).collect(Collectors.joining()));
  }

  private String generatedInstanceMethodName(TypeElement typeClass, TypeElement typeElement) {
    return uncapitalize(typeElement.getSimpleName().toString() + typeClass.getSimpleName().toString());
  }

  private Stream<String> allTypeArgsAsString(TypeMirror tm) {
    return asDeclaredType(tm).map(dt -> concat(dt.getTypeArguments().stream().flatMap(this::allTypeArgsAsString),
        Stream.of(dt.asElement().getSimpleName().toString()))).orElseGet(() -> Stream.of(tm.toString()));
  }

  private Optional<InstanceLocation> findCompiledInstance(TypeElement typeElementContext, TypeElement typeClass,
      TypeElement typeElement, List<TypeElement> lowPriorityProviders, Optional<ClassName> deriveTarget) {

    Optional<TypeElement> derivedCompanion = deriveTarget.flatMap(
        cn -> Optional.ofNullable(elements().getTypeElement(cn.reflectionName())));

    Optional<TypeElement> companionClass = Optional.ofNullable(
        elements().getTypeElement(deriveConfigBuilder.deduceDerivedClassName(":auto", typeElement).reflectionName()))
        .filter(te -> !derivedCompanion.filter(te::equals).isPresent());

    List<TypeElement> instancesProviders = concat(concat(Stream.of(typeElementContext, typeClass, typeElement),
        concat(optionalAsStream(derivedCompanion), optionalAsStream(companionClass))), lowPriorityProviders.stream()).collect(
        toList());

    TypeMirror rawShowClass = Types.erasure(typeClass.asType());
    return concat(

        instancesProviders.stream()
            .flatMap(this::allStaticFields)
            .filter(ve -> Types.isSameType(Types.erasure(ve.asType()), rawShowClass))
            .flatMap(ve -> optionalAsStream(
                asDeclaredType(ve.asType()).flatMap(dt -> asTypeElement(dt.getTypeArguments().get(0)).filter(typeElement::equals))
                    .map(te -> value(ve)))),

        instancesProviders.stream()
            .flatMap(this::allStaticMethods)
            .filter(m -> Types.isSameType(Types.erasure(m.getReturnType()), rawShowClass) &&
                asDeclaredType(m.getReturnType()).map(dt -> dt.getTypeArguments().get(0))
                    .flatMap(this::asTypeElement)
                    .filter(typeElement::equals)
                    .isPresent())
            .filter(m -> m.getParameters()
                .stream()
                .allMatch(ve -> asDeclaredType(ve.asType()).filter(
                    dt -> dt.getTypeArguments().size() == 1 && dt.getTypeArguments().get(0).getKind() == TypeKind.TYPEVAR)
                    .isPresent()))
            .map(m -> m.getEnclosingElement().equals(typeElement) &&
                m.getAnnotationMirrors()
                    .stream()
                    .anyMatch(am -> am.getAnnotationType()
                        .asElement()
                        .getSimpleName()
                        .contentEquals(ExportAsPublic.class.getSimpleName()))
                ? method(deriveTarget.orElse(ClassName.get(typeElement)), m)
                : method(ClassName.get(asTypeElement.visit(m.getEnclosingElement()).get()), m)

            )).findFirst();
  }

  private OptionModel lazyOptionModel(String optionClassQualifiedName, String noneConstructor, String someConstructor) {
    return OptionModels.lazy(() -> Optional.ofNullable(Elements.getTypeElement(optionClassQualifiedName))
        .map(typeElement -> OptionModels.optionModel(typeElement, typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> e.getParameters().isEmpty() &&
                    (e.getTypeParameters().size() == 1) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(noneConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + optionClassQualifiedName + '#' + noneConstructor)),

            typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 1) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(someConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + optionClassQualifiedName + '#' + someConstructor))))

        .orElseThrow(() -> new IllegalArgumentException(optionClassQualifiedName + " not found in classpath")));
  }

  private Optional<EitherModel> eitherModel(String eitherClassQualifiedName, String leftConstructor, String rightConstructor) {
    return Optional.ofNullable(Elements.getTypeElement(eitherClassQualifiedName))
        .map(typeElement -> EitherModels.lazy(() -> EitherModel(typeElement, typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 2) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(leftConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + eitherClassQualifiedName + '#' + leftConstructor)),

            typeElement.getEnclosedElements()
                .stream()
                .flatMap(e -> optionalAsStream(asExecutableElement.visit(e)))
                .filter(e -> (e.getParameters().size() == 1) &&
                    (e.getTypeParameters().size() == 2) &&
                    e.getModifiers().contains(Modifier.STATIC) &&
                    e.getModifiers().contains(Modifier.PUBLIC) &&
                    e.getSimpleName().contentEquals(rightConstructor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Constructor not found at " + eitherClassQualifiedName + '#' + rightConstructor)))));
  }

  private SamInterface lazySamInterface(String samInterfaceQualifiedName) {
    return SamInterfaces.lazy(() -> samInterface(samInterfaceQualifiedName).orElseThrow(
        () -> new IllegalArgumentException(samInterfaceQualifiedName + " not found in classpath")));
  }

  private Stream<TypeVariable> typeVariablesIn0(TypeMirror typeMirror) {

    return asDeclaredType.visit(typeMirror)
        .map(dt -> dt.getTypeArguments().stream().flatMap(this::typeVariablesIn0))
        .orElseGet(() -> asTypeVariable.visit(typeMirror).map(Stream::of).orElse(Stream.empty()));
  }

  private boolean abstractMethod(ExecutableElement e) {
    return e.getModifiers().contains(Modifier.ABSTRACT) &&
        !((e.getEnclosingElement().getKind() == ElementKind.INTERFACE) &&
              (Elements.overrides(e, objectModel.equalsMethod(), objectModel.classModel()) ||
                   Elements.overrides(e, objectModel.hashCodeMethod(), objectModel.classModel()) ||
                   Elements.overrides(e, objectModel.toStringMethod(), objectModel.classModel())));
  }

  private FieldsTypeClassInstanceBindingMap join(FieldsTypeClassInstanceBindingMap b1, FieldsTypeClassInstanceBindingMap b2) {
    Map<String, Binding> newBindingsByFieldName = new HashMap<>(getBindingsByFieldName(b1));
    newBindingsByFieldName.putAll(getBindingsByFieldName(b2));
    return bindingMap(merge(getFreeVariables(b1), getFreeVariables(b2)), newBindingsByFieldName);
  }

  private List<FreeVariable> merge(List<FreeVariable> vbs1, List<FreeVariable> vbs2) {
    List<FreeVariable> newBindings = new ArrayList<>(vbs1);
    vbs2.stream().filter(vb -> isNotIn(vbs1, vb)).forEach(newBindings::add);
    return newBindings;
  }

  private Boolean isNotIn(List<FreeVariable> previousVars, FreeVariable var) {
    return var.variable((type, name) -> previousVars.stream()
        .noneMatch(vb1 -> vb1.variable((type1, name1) -> name.equals(name1) && types().isSameType(type, type1))));
  }

  private BoundExpression join(BinaryOperator<Expression> expressionJoiner, BoundExpression b1, BoundExpression b2) {
    return expression(merge(getFreeVariables(b1), getFreeVariables(b2)),
        expressionJoiner.apply(getExpression(b1), getExpression(b2)));
  }

  private static Expression joinAsArgs(Expression e1, Expression e2) {
    return caseOf(e1).baseExpression(
        cb1 -> caseOf(e2).baseExpression(cb2 -> baseExpression(cb1.toBuilder().add(", ").add(cb2).build()))
            .recursiveExpression(fromOuterMethod -> recursiveExpression(
                outer -> cb1.toBuilder().add(", ").add(fromOuterMethod.apply(outer)).build())))
        .recursiveExpression(fromOuterMethod -> caseOf(e2).baseExpression(
            cb2 -> recursiveExpression(outer -> fromOuterMethod.apply(outer).toBuilder().add(", ").add(cb2).build()))
            .recursiveExpression(fromOuterMethod2 -> recursiveExpression(
                outer -> fromOuterMethod.apply(outer).toBuilder().add(", ").add(fromOuterMethod2.apply(outer)).build())));
  }

  private static Stream<TypeElement> getSuperTypeElements(TypeElement e) {

    return concat(Stream.of(e.getSuperclass()), e.getInterfaces().stream()).map(asDeclaredType::visit)
        .flatMap(Utils::optionalAsStream)
        .map(DeclaredType::asElement)
        .map(asTypeElement::visit)
        .flatMap(Utils::optionalAsStream)
        .flatMap(te -> concat(getSuperTypeElements(te), Stream.of(te)));
  }

}

