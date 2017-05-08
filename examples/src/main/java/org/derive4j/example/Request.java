/*
 * Copyright (c) 2017, Jean-Baptiste Giraudeau <jb@giraudeau.info>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  * Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.derive4j.example;

import fj.F0;
import fj.P;
import fj.P2;
import fj.Show;
import fj.data.Option;
import fj.data.optic.Lens;
import fj.data.optic.Optional;
import fj.data.optic.Prism;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Instances;

import static fj.data.optic.Lens.lens;
import static fj.data.optic.Optional.optional;
import static fj.data.optic.Prism.prism;
import static org.derive4j.ArgOption.checkedNotNull;

/**
 * A data type to modelize an http request. Abstract because concrete implementation will be generated,
 * by Derive4J (annotation processor for the @Data annotation).
 * Default @Data flavour is JDK, here we specify FJ (Functional Java), also available is Fugue and Fugue2.
 * The flavour is used to determine which implementation of 'Option' or 'Function' will be used by generated code.
 */
@Data(flavour = Flavour.FJ, arguments = checkedNotNull, value = @Derive(@Instances(Show.class)))
public abstract class Request {

  /**
   * Lenses: optics focused on a field present for all datatype contructors (getter cannot 'failed'):
   */
  public static final Lens<Request, String> _path = lens(Requests::getPath, Requests::setPath);
  // which is Equivalent to:
  public static final Lens<Request, String> _pathPatternMatch = lens(
      // getter function:
      Requests.cases().GET(path -> path).DELETE(path -> path).PUT((path, body) -> path).POST((path, body) -> path),
      // setter function:
      newPath -> Requests.cases()
          .GET(path -> Requests.GET(newPath))
          .DELETE(path -> Requests.DELETE(newPath))
          .PUT((path, body) -> Requests.PUT(newPath, body))
          .POST((path, body) -> Requests.POST(newPath, body)));

  /**
   * Alternatively, if you prefer a more FP style, you can define a catamorphism instead
   * (equivalent to the visitor above, most useful for standard data type like Option, Either, List...):
   */
  //  public abstract <X> X match(@FieldNames("path") F<String, X> GET,
  //                              @FieldNames("path") F<String, X> DELETE,
  //                              @FieldNames({"path", "body"}) F2<String, String, X> PUT,
  //                              @FieldNames({"path", "body"}) F2<String, String, X> POST);

  /**
   * Now run compilation and a 'Requests' classe will be generated, by default with the same visibility as 'Request'.
   * If You want you can specify the visibility Package in the @Data annotation and expose only public methods here,
   * and delegate to the generated Requests class. eg. for constructors:
   */
  public static Request GET(String path) {

    return Requests.GET(path);
  }

  public static Request PUT(String path, String body) {

    return Requests.PUT(path, body);
  }

  /**
   * Derive4J provides first class support for lazy construction of data types:
   */
  public static Request lazy(F0<Request> requestExpression) {
    // the requestExpression will be lazy-evaluated on the first call
    // to the 'match' method of the returned instance:
    return Requests.lazy(requestExpression);
  }

  /**
   * Optional: optics focused on a field that may not be present for all contructors (getter return an 'Option'):
   */
  private static final Optional<Request, String> _body = optional(Requests::getBody, Requests::setBody);
  // Equivalent to:
  private static final Optional<Request, String> _bodyPatternMatch = optional(
      // getter function:
      Requests.cases().PUT((path, body) -> body).POST((path, body) -> body).otherwiseNone(),
      // setter function:
      newBody -> Requests.cases()
          .GET(Requests::GET) // or with method reference:
          .DELETE(Requests::DELETE)
          .PUT((path, body) -> Requests.PUT(path, newBody))
          .POST((path, body) -> Requests.POST(path, newBody)));
  /**
   * Prism: optics focused on a specific constructor:
   */
  private static final Prism<Request, String> _GET = prism(
      // Getter function
      Requests.cases().GET(fj.data.Option::some).otherwise(Option::none),
      // Reverse Get function (aka constructor)
      Requests::GET);

  /**
   * Then you can enrich your class with whatever methods you like,
   * using generated static methods to trivialize your implementation:
   */
  // If there more than one field, we use a tuple as the prism target:
  private static final Prism<Request, P2<String, String>> _POST = prism(
      // Getter:
      Requests.cases().POST(P::p).otherwiseNone(),
      // reverse get (construct a POST request given a P2<String, String>):
      p2 -> Requests.POST(p2._1(), p2._2()));

  // the 'accept' method:
  public abstract <X> X match(Cases<X> cases);

  /**
   * Derive4J philosophy is to be as safe and consistent as possible. That is why Object.{equals, hashCode, toString}
   * are not implemented by generated classes by default. Nonetheless, as a concession to legacy, it is possible to force
   * Derive4J to implement them, by declaring them abstract:
   */
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /**
   * FP style Optics (in the future, will be generated by a derive4j 'plugin'):
   */

  @Override
  public abstract String toString();

  /**
   * OOP style 'getters':
   */

  public final String path() {

    return Requests.getPath(this);
  }

  public final Option<String> body() {

    return Requests.getBody(this);
  }

  /**
   * OOP style 'withers':
   */

  public final Request withPath(String newPath) {

    return Requests.setPath(newPath).f(this);
  }

  public final Request withBody(String newBody) {
    // if there is no body field (eg. GET, DELETE) then the original request is returned (no modification):
    return Requests.setBody(newBody).f(this);
  }

  /**
   * First we start by defining a 'visitor' for our datatype:
   */

  // the 'visitor' interface:
  interface Cases<X> {
    X GET(String path);

    X DELETE(String path);

    X PUT(String path, String body);

    X POST(String path, String body);
  }

}
