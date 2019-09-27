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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.*;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.testing.compile.CompilationSubject.assertThat;

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
    final Compilation compilation = Compiler
        .compiler(new ModulePathCompiler())
        .withOptions("--release", "9")
        .withProcessors(new DerivingProcessor())
        .compile(Stream
            .concat(Stream.of(getJavaFileObject(Paths.get("../examples/src/main/java/module-info.java")))
                , Arrays
                    .stream(exampleFiles)
                    .map(file -> getJavaFileObject(Paths.get("../examples/src/main/java/org/derive4j/example/" + file))))
            .collect(Collectors.toList()));

    assertThat(compilation).succeeded();
  }

  private static JavaFileObject getJavaFileObject(Path path) {
    try {
      return JavaFileObjects.forResource(path.toUri().toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

}

final class ModulePathCompiler implements JavaCompiler {
  private final JavaCompiler compiler;
  private final List<File> modulesFiles;

  ModulePathCompiler() {
    this.compiler = ToolProvider.getSystemJavaCompiler();

    final Module module = CompileExamplesTest.class.getModule();
    final Set<ResolvedModule> modules = module.getLayer().configuration().modules();
    this.modulesFiles = modules
        .stream()
        .map(rm -> rm.reference().location())
        .filter(Optional::isPresent)
        .filter(ouri -> ouri.get().getScheme().startsWith("file"))
        .map(ouri -> {
          final String path = Paths.get(ouri.get()).toString();
          return new File(path);
        })
        .collect(Collectors.toList());
  }

  @Override
  public CompilationTask getTask(Writer writer, JavaFileManager javaFileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> iterable, Iterable<String> iterable1, Iterable<? extends JavaFileObject> iterable2) {
    return compiler.getTask(writer, javaFileManager, diagnosticListener, iterable, iterable1, iterable2);
  }

  @Override
  public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> diagnosticListener, Locale locale, Charset charset) {
    final StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticListener, locale, charset);

    try {
      fileManager.setLocation(StandardLocation.MODULE_PATH, modulesFiles);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return fileManager;
  }

  @Override
  public int isSupportedOption(String s) {
    return compiler.isSupportedOption(s);
  }

  @Override
  public int run(InputStream inputStream, OutputStream outputStream, OutputStream outputStream1, String... strings) {
    return compiler.run(inputStream, outputStream, outputStream1, strings);
  }

  @Override
  public Set<SourceVersion> getSourceVersions() {
    return compiler.getSourceVersions();
  }
}
