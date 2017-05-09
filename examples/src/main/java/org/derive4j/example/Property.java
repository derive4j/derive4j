package org.derive4j.example;

import fj.Ord;
import fj.data.Option;
import fj.data.List;
import org.derive4j.Data;
import org.derive4j.Derive;
import org.derive4j.Flavour;
import org.derive4j.Instances;
import org.derive4j.hkt.__;

@Data(flavour = Flavour.FJ)
@Derive(@Instances(Ord.class))
public abstract class Property<T> implements __<Property.µ, T> {
    public enum µ {}
    Property() {}

    interface Cases<T, R> {
        R Simple(String name
            , Option<Integer> value
            , List<String> params);

        R Multi(String name
            , List<Integer> values
            , List<String> params);
    }
    public abstract <R> R match(Cases<T, R> cases);
}