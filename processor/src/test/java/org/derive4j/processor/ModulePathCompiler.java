package org.derive4j.processor;

import javax.lang.model.SourceVersion;
import javax.tools.*;
import java.io.*;
import java.lang.module.ResolvedModule;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ModulePathCompiler implements JavaCompiler {
  private final JavaCompiler compiler;
  private final List<File>   modulesFiles;

  ModulePathCompiler(Class<?> testClass) {
    this.compiler = ToolProvider.getSystemJavaCompiler();

    final Module module = testClass.getModule();
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
  public CompilationTask getTask(Writer writer, JavaFileManager javaFileManager,
      DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> iterable,
      Iterable<String> iterable1, Iterable<? extends JavaFileObject> iterable2) {
    return compiler.getTask(writer, javaFileManager, diagnosticListener, iterable, iterable1, iterable2);
  }

  @Override
  public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> diagnosticListener,
      Locale locale, Charset charset) {
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
