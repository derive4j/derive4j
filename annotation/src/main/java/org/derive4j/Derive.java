/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * This file is part of "Derive4J - Annotations API".
 *
 * "Derive4J - Annotations API" is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * "Derive4J - Annotations API" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with "Derive4J - Annotations API".  If not, see <http://www.gnu.org/licenses/>.
 */
package org.derive4j;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import static org.derive4j.Make.caseOfMatching;
import static org.derive4j.Make.casesMatching;
import static org.derive4j.Make.catamorphism;
import static org.derive4j.Make.constructors;
import static org.derive4j.Make.factory;
import static org.derive4j.Make.getters;
import static org.derive4j.Make.lambdaVisitor;
import static org.derive4j.Make.lazyConstructor;
import static org.derive4j.Make.modifiers;
import static org.derive4j.Visibility.Same;

@Target(ElementType.TYPE)
@Documented
public @interface Derive {

  String inClass() default ":auto";

  Visibility withVisibility() default Same;

  Class<?> extend() default Class.class;

  Make[] make() default { lambdaVisitor, constructors, getters, modifiers, lazyConstructor, caseOfMatching,
      casesMatching, catamorphism, factory };

  Instances[] value() default {};

}
