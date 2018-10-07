package org.derive4j.example;

import java.util.function.BiFunction;
import org.derive4j.Data;
import org.derive4j.FieldNames;

@Data
interface InfiniteStream<A> {
  <R> R match(@FieldNames({ "head", "tail" }) BiFunction<A, InfiniteStream<A>, R> cons);
}
