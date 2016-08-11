package org.derive4j.example;

import java.util.function.IntFunction;
import org.derive4j.Data;
import org.derive4j.FieldNames;

@Data
public abstract class IntNewType {
  public abstract <X> X match(@FieldNames("intW") IntFunction<X> intW);

  @Override
  public abstract String toString();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
