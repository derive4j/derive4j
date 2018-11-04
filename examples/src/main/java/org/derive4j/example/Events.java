package org.derive4j.example;

import org.derive4j.*;

public class Events {

  @Data(flavour = Flavour.Cyclops)
  interface EventV1 {

    interface Cases<R> {
      R newItem(Long ref, String itemName);

      R itemRemoved(Long ref);
    }

    <R> R match(Cases<R> cases);
  }

  @Data(flavour = Flavour.Cyclops)
  interface EventV2 {

    interface Cases<R> extends EventV1.Cases<R> { // extends V1 with:

      // new `initialStock` field in `newItem` event:
      R newItem(Long ref, String itemName, int initialStock);

      // default to 0 for old events:
      @Override
      default R newItem(Long ref, String itemName) {
        return newItem(ref, itemName, 0);
      }

      // new event:
      R itemRenamed(Long ref, String newName);
    }

    <R> R match(Cases<R> cases);

    static EventV2 fromV1(EventV1 v1Event) {
      // Events are functions!
      return v1Event::match;
    }
  }

}
