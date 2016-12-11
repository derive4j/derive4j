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

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.derive4j.Flavour;
import org.derive4j.processor.api.model.TypeRestriction;

public interface DeriveUtils {

  Types types();

  Elements elements();

  TypeName resolveToTypeName(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeName>> typeArgs);

  Function<TypeVariable, Optional<TypeMirror>> typeRestrictions(List<TypeRestriction> typeRestrictions);

  TypeMirror resolve(TypeMirror typeMirror, Function<TypeVariable, Optional<TypeMirror>> typeArgs);

  DeclaredType resolve(DeclaredType declaredType, Function<TypeVariable, Optional<TypeMirror>> typeArgs);

  MethodSpec.Builder overrideMethodBuilder(final ExecutableElement abstractMethod, DeclaredType declaredType);

  List<TypeVariable> typeVariablesIn(TypeMirror typeMirror);

  List<ExecutableElement> allAbstractMethods(DeclaredType declaredType);

  List<ExecutableElement> allAbstractMethods(TypeElement typeElement);

  ObjectModel object();

  Optional<SamInterface> samInterface(String qualifiedClassName);

  SamInterface function0Model(Flavour flavour);

  SamInterface function1Model(Flavour flavour);

  OptionModel optionModel(Flavour flavour);

  Optional<EitherModel> eitherModel(Flavour flavour);
}
