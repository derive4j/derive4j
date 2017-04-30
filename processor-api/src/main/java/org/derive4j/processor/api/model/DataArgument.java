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
package org.derive4j.processor.api.model;

import java.util.function.BiFunction;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.ExportAsPublic;
import org.derive4j.FieldNames;
import org.derive4j.Visibility;

import static org.derive4j.processor.api.model.DataArguments.getFieldName;
import static org.derive4j.processor.api.model.DataArguments.getType;

@Data(value = @Derive(withVisibility = Visibility.Smart))
public abstract class DataArgument {

  DataArgument() {}

  public abstract <R> R match(@FieldNames({ "fieldName", "type" }) BiFunction<String, TypeMirror, R> dataArgument);

  public String fieldName() {

    return getFieldName(this);
  }

  public TypeMirror type() {

    return getType(this);
  }

  @ExportAsPublic
  static DataArgument dataArgument(String fieldName, TypeMirror type) {
    if (type instanceof ErrorType) {
      throw new IllegalArgumentException("Type of " + fieldName + " is not valid: " + type);
    }
    return DataArguments.dataArgument0(fieldName, type);
  }
}
