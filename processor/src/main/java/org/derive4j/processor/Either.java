package org.derive4j.processor;

import java.util.function.Function;
import org.derive4j.Data;

@Data
abstract class Either<A, B> {
  abstract <X> X fold(Function<A, X> left, Function<B, X> right);
}
