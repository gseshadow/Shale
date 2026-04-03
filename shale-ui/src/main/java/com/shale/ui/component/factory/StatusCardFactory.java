package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.StatusCard;
import com.shale.ui.util.ColorUtil;

public class StatusCardFactory {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    public record StatusCardModel(
            Integer statusId,
            String name,
            Integer sortOrder,
            String colorCss
    ) {
    }

    private final Consumer<Integer> onOpenStatus;

    public StatusCardFactory(Consumer<Integer> onOpenStatus) {
        this.onOpenStatus = onOpenStatus;
    }

    public StatusCard create(StatusCardModel model, Variant variant) {
        Objects.requireNonNull(model, "model");

        StatusCard card = new StatusCard();
        card.setStatusId(model.statusId());
        card.setOnOpen(onOpenStatus);

        card.setName(model.name());

        // If your ColorUtil already accepts "0xRRGGBBAA", keep using it.
        String css = ColorUtil.toCssBackgroundColor(model.colorCss());

        // Dot always uses the status color (so white still shows due to stroke).
        card.setDotCssColor(css);

        // Background: keep subtle so readability is consistent (recommended).
        // If you'd rather fill the pill with the status color, replace with `css`.
        card.setBackgroundCssColor("rgba(0,0,0,0.06)");

        switch (variant) {
        case FULL -> card.applyFull();
        case COMPACT -> card.applyCompact();
        case MINI -> card.applyMini();
        }

        return card;
    }
}
