package org.derive4j.example;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.derive4j.example.GuavaRequests.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class RequestTest {

  @Test
  public void testGuavaFunction() {
    ImmutableList<GuavaRequest> gets = ImmutableList.of(GET("foo"));
    gets = FluentIterable.from(gets).transform(modPath(path -> "/" + path)).toList();
    assertThat(gets.size(), is(1));
    assertThat(getPath(gets.get(0)), is("/foo"));
  }

  @Test
  public void testGuavaOptional() {
    assertTrue(getBody(POST("/foo", "{}")).isPresent());
    assertFalse(getBody(GET("/foo")).isPresent());
  }

}
