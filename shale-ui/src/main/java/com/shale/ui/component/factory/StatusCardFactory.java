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

        card.setBackgroundCssColor(css);
        card.setTextCssColor(ColorUtil.readableTextColor(model.colorCss()));

        switch (variant) {
        case FULL -> card.applyFull();
        case COMPACT -> card.applyCompact();
        case MINI -> card.applyMini();
        }

        return card;
    }
}
