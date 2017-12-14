/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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

import com.squareup.javapoet.CodeBlock;
import java.util.function.Function;
import org.derive4j.processor.api.model.DataArgument;
import org.derive4j.processor.api.model.DataConstructor;

public interface InstanceUtils {

  FieldsTypeClassInstanceBindingMap bindings();

  DerivedCodeSpec generateInstanceFactory(CodeBlock statement, CodeBlock... statements);

  CodeBlock matchImpl(Function<DataConstructor, CodeBlock> lambdaImpl);

  CodeBlock instanceFor(DataArgument da);

  String adtVariableName();

}
