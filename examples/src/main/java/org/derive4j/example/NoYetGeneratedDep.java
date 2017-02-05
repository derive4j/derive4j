package org.derive4j.example;

import com.google.auto.value.AutoValue;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.derive4j.Data;
import org.derive4j.example.Amounts.CasesMatchers.TotalMatcher_Amout;

@Data
public abstract class NoYetGeneratedDep {

  public abstract <T> T match(BiFunction<AutoValue_NoYetGeneratedDep_AutoValueClass, TotalMatcher_Amout, T> value);

  @AutoValue
  static abstract class AutoValueClass {
    abstract String test();
  }


}
