package org.derive4j.example;

import fj.Show;
import java.util.Arrays;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Instances;

@Data(@Derive(@Instances(Show.class)))
public interface ArrayWrapper {
  interface Cases<R> {
    R Wrapper(int[] array1, String... array2);
  }

  <R> R match(Cases<R> cases);

  Show<int[]> intsShow = Show.showS(Arrays::toString);

  Show<String[]> stringsShow = Show.showS(Arrays::toString);

  Show<ArrayWrapper> wrapperShow = ArrayWrappers.arrayWrapperShow(intsShow, stringsShow);

}
