/*
 * Copyright (c) 2019, Jean-Baptiste Giraudeau <jb@giraudeau.info>
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

import com.google.common.truth.Truth;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.Test;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public class CompileExamplesTest {

  @Test
  public void compile_Either_Option_List_Tree() {
    checkCompileOf("Either.java", "Option.java", "List.java", "Tree.java", "ListMethods.java");
  }

  @Test
  public void compile_Address_Contact_Person() {
    checkCompileOf("Address.java", "Contact.java", "Person.java", "PersonName.java");
  }

  @Test
  public void compile_Amount_Country() {
    checkCompileOf("Amount.java", "Country.java");
  }

  @Test
  public void compile_data_annotation_PhoneAndPers() {
    checkCompileOf("PhoneAndPers.java", "Event.java", "data.java");
  }

  @Test
  public void compile_Day_enum() {
    checkCompileOf("Day.java");
  }

  @Test
  public void compile_Expression() {
    checkCompileOf("Expr.java", "Expression.java");
  }

  @Test
  public void compile_Events() {
    checkCompileOf("Event.java", "ExtendedEvent.java", "data.java");
  }

  @Test
  public void compile_InfiniteStream() {
    checkCompileOf("Stream.java");
  }

  @Test
  public void compile_Int_newType() {
    checkCompileOf("IntNewType.java");
  }

  @Test
  public void compile_Property() {
    checkCompileOf("Property.java");
  }

  @Test
  public void compile_Request() {
    checkCompileOf("Request.java");
  }

  @Test
  public void compile_Term() {
    checkCompileOf("Term.java");
  }

  @Test
  public void compile_extensible_algebras() {
    checkCompileOf("algebras/ObjectAlgebras.java");
  }

  private static void checkCompileOf(String... exampleFiles) {
    Truth.assert_()
        .about(javaSources())
        .that(Arrays.asList(exampleFiles)
            .stream()
            .map(file -> JavaFileObjects.forResource("org/derive4j/example/" + file))
            .collect(Collectors.toList()))
        .processedWith(new DerivingProcessor())
        .compilesWithoutError();
  }

}
