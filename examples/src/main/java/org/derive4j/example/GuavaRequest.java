package org.derive4j.example;

import org.derive4j.Data;

import static org.derive4j.Flavour.Guava;

@Data(flavour = Guava) public abstract class GuavaRequest {

  interface Cases<R> {
    R GET(String path);
    R DELETE(String path);
    R PUT(String path, String body);
    R POST(String path, String body);
  }

  public abstract <R> R match(Cases<R> cases);

}
