/**
 * Copyright (c) 2015, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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
package org.derive4j.processor.derivator.patternmatching;

import com.squareup.javapoet.*;
import org.derive4j.processor.api.DeriveUtils;
import org.derive4j.processor.api.model.*;
import org.derive4j.processor.derivator.Flavours;
import org.derive4j.processor.derivator.MapperDerivator;
import org.derive4j.processor.Utils;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.derive4j.processor.derivator.MapperDerivator.mapperFieldName;
import static org.derive4j.processor.derivator.MapperDerivator.mapperTypeName;
import static org.derive4j.processor.Utils.getAbstractMethods;
import static org.derive4j.processor.Utils.joinStringsAsArguments;

public class OtherwiseMatchingStepDerivator {


  static TypeSpec otherwiseMatchingStepTypeSpec(AlgebraicDataType adt,
                                                DeriveContext deriveContext, DeriveUtils deriveUtils) {

    TypeSpec.Builder otherwiseMatchBuilder = TypeSpec.classBuilder(otherwiseBuilderClassName())
        .addTypeVariables(PatternMatchingDerivator.matcherVariables(adt).map(TypeVariableName::get).collect(Collectors.toList()))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addFields(adt.dataConstruction().constructors().stream()
            .map(dc -> FieldSpec.builder(mapperTypeName(adt, dc, deriveContext), mapperFieldName(dc))
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .collect(Collectors.toList()));

    MethodSpec.Builder otherwiseMatchConstructorBuilder = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .addParameters(adt.dataConstruction().constructors().stream()
            .map(dc -> ParameterSpec.builder(mapperTypeName(adt, dc, deriveContext), mapperFieldName(dc)).build())
            .collect(Collectors.toList()));

    for (DataConstructor dc : adt.dataConstruction().constructors()) {
      otherwiseMatchConstructorBuilder.addStatement("this.$L = $L", mapperFieldName(dc), mapperFieldName(dc));
    }

    TypeElement f0 = Flavours.findF0(deriveContext.flavour(), deriveUtils.elements());

    return otherwiseMatchBuilder
        .addMethod(otherwiseMatchConstructorBuilder.build())
        .addMethod(MethodSpec.methodBuilder("otherwise")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addParameter(ParameterSpec.builder(
                TypeName.get(deriveUtils.types().getDeclaredType(f0, adt.matchMethod().returnTypeVariable())),
                "otherwise").build()
            )
            .returns(TypeName.get(deriveUtils.types().getDeclaredType(Flavours.findF1(deriveContext.flavour(), deriveUtils.elements()),
                adt.typeConstructor().declaredType(), adt.matchMethod().returnTypeVariable())))
            .addCode(adt.dataConstruction().match(new DataConstruction.Cases<CodeBlock>() {
              @Override
              public CodeBlock multipleConstructors(DataConstructors constructors) {
                return constructors.match(new DataConstructors.Cases<CodeBlock>() {
                  @Override
                  public CodeBlock visitorDispatch(VariableElement visitorParam, DeclaredType visitorType, List<DataConstructor> constructors) {

                    String visitorVarName = visitorParam.getSimpleName().toString();
                    String adtLambdaParam = Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

                    CodeBlock lambdaArgs = adt.dataConstruction().constructors().stream().map(dc -> {
                      NameAllocator nameAllocator = new NameAllocator();
                      nameAllocator.newName("otherwise", "otherwise arg");
                      nameAllocator.newName(adtLambdaParam, "adt var");
                      nameAllocator.newName(visitorVarName, "visitor var");
                      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::dataArgument))
                          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

                      return CodeBlock.builder().add("this.$1L != null ? this.$1L : (" +
                          joinStringsAsArguments(IntStream.range(3, 3 + dc.arguments().size() + dc.typeRestrictions().size())
                              .mapToObj(i -> "$" + i + "L"))
                          + ") -> otherwise.$2L()", (Object[])
                          Stream.concat(Stream.of(
                              mapperFieldName(dc),
                              getAbstractMethods(f0.getEnclosedElements()).get(0).getSimpleName().toString()),
                              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::dataArgument))
                                  .map(DataArgument::fieldName)
                                  .map(daName -> nameAllocator.get(daName))
                          ).toArray(String[]::new)).build();
                    }).reduce((cb1, cb2) -> CodeBlock.builder().add(cb1).add(",\n").add(cb2).build()).orElse(CodeBlock.builder().build());


                    NameAllocator nameAllocator = new NameAllocator();
                    nameAllocator.newName("otherwise", "otherwise arg");
                    nameAllocator.newName(adtLambdaParam, "adt var");
                    nameAllocator.newName(visitorVarName, "visitor var");

                    return CodeBlock.builder()
                        .addStatement("$T $L = $T.$L($L)",
                            TypeName.get(visitorType),
                            nameAllocator.get("visitor var"),
                            ClassName.get(deriveContext.targetPackage(), deriveContext.targetClassName()),
                            MapperDerivator.visitorLambdaFactoryName(adt),
                            lambdaArgs)
                        .addStatement("return $1L -> $1L.$2L($3L)",
                            nameAllocator.get("adt var"),
                            adt.matchMethod().element().getSimpleName(),
                            nameAllocator.get("visitor var")
                        )
                        .build();
                  }

                  @Override
                  public CodeBlock functionsDispatch(List<DataConstructor> constructors) {

                    CodeBlock.Builder codeBlock = CodeBlock.builder();

                    for (DataConstructor dc : constructors) {
                      NameAllocator nameAllocator = new NameAllocator();
                      nameAllocator.newName("otherwise", "otherwise arg");
                      nameAllocator.newName(mapperFieldName(dc), "case var");
                      Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::dataArgument))
                          .forEach(da -> nameAllocator.newName(da.fieldName(), da.fieldName()));

                      codeBlock.addStatement("$1T $2L = (this.$3L != null) ? this.$3L : (" +
                              joinStringsAsArguments(IntStream.range(5, 5 + dc.arguments().size() + dc.typeRestrictions().size())
                                  .mapToObj(i -> "$" + i + "L"))
                              + ") -> otherwise.$4L()",
                          Stream.concat(
                              Stream.of(
                                  mapperTypeName(adt, dc, deriveContext),
                                  nameAllocator.get("case var"),
                                  mapperFieldName(dc),
                                  getAbstractMethods(f0.getEnclosedElements()).get(0).getSimpleName().toString()),
                              Stream.concat(dc.arguments().stream(), dc.typeRestrictions().stream().map(TypeRestriction::dataArgument))
                                  .map(DataArgument::fieldName)
                                  .map(daName -> nameAllocator.get(daName))
                          ).toArray(Object[]::new)
                      );
                    }

                    String adtLambdaParam = Utils.uncapitalize(adt.typeConstructor().declaredType().asElement().getSimpleName());

                    return codeBlock
                        .addStatement("return $1L -> $1L.$2L($3L)",
                            adtLambdaParam,
                            adt.matchMethod().element().getSimpleName(),
                            joinStringsAsArguments(constructors.stream().map(dc -> mapperFieldName(dc))))
                        .build();
                  }
                });
              }

              @Override
              public CodeBlock oneConstructor(DataConstructor constructor) {
                throw new IllegalStateException();
              }

              @Override
              public CodeBlock noConstructor() {
                throw new IllegalStateException();
              }
            })).build())
        .build();

  }

  static String otherwiseBuilderClassName() {
    return "PartialMatchBuilder";
  }

}
