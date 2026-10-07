package com.shale.ui.component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import javafx.stage.Window;

/** Practice Area presentation; selection mechanics and dialog shell live in MiniCardSelector. */
public final class PracticeAreaSelector<T> extends MiniCardSelector<T> {
    public PracticeAreaSelector(List<T> options, T currentValue, Function<T, Integer> identity,
            Function<T, String> name, Function<T, String> color) {
        super("Practice Area", options, currentValue, identity, name,
                value -> new PracticeAreaCardFactory(ignored -> { }).create(
                        new PracticeAreaCardModel(identity.apply(value), name.apply(value), color.apply(value)),
                        PracticeAreaCardFactory.Variant.MINI));
        getStyleClass().add("practice-area-selector");
    }

    public static <T> Optional<T> showPicker(Window owner, List<T> options, T currentValue,
            Function<T, Integer> identity, Function<T, String> name, Function<T, String> color) {
        return showPicker(owner, "Practice Area", new PracticeAreaSelector<>(options, currentValue, identity, name, color));
    }
}
