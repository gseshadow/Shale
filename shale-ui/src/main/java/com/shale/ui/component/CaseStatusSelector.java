package com.shale.ui.component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import javafx.stage.Window;

/** Case Status presentation preserves the existing colored MINI status-pill factory. */
public final class CaseStatusSelector<T> extends MiniCardSelector<T> {
    public CaseStatusSelector(List<T> options, T currentValue, Function<T, Integer> identity,
            Function<T, String> name, Function<T, String> color) {
        super("Case Status", options, currentValue, identity, name,
                value -> new StatusCardFactory(ignored -> { }).create(
                        new StatusCardModel(identity.apply(value), name.apply(value), null, color.apply(value)),
                        StatusCardFactory.Variant.MINI));
        getStyleClass().add("case-status-selector");
    }

    public static <T> Optional<T> showPicker(Window owner, List<T> options, T currentValue,
            Function<T, Integer> identity, Function<T, String> name, Function<T, String> color) {
        return showPicker(owner, "Case Status", new CaseStatusSelector<>(options, currentValue, identity, name, color));
    }
}
