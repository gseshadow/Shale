package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.PracticeAreaCard;
import com.shale.ui.util.ColorUtil;

public class PracticeAreaCardFactory {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    public record PracticeAreaCardModel(
            Integer practiceAreaId,
            String name,
            String colorCss
    ) {
    }

    private final Consumer<Integer> onOpenPracticeArea;

    public PracticeAreaCardFactory(Consumer<Integer> onOpenPracticeArea) {
        this.onOpenPracticeArea = onOpenPracticeArea;
    }

    public PracticeAreaCard create(PracticeAreaCardModel model, Variant variant) {
        Objects.requireNonNull(model, "model");

        PracticeAreaCard card = new PracticeAreaCard();
        card.setPracticeAreaId(model.practiceAreaId());
        card.setOnOpen(onOpenPracticeArea);

        card.setName(model.name());

        // DB colors: nvarchar hex (often "#RRGGBB" or "0xRRGGBBAA")
        String css = ColorUtil.toCssBackgroundColor(model.colorCss());

        // Dot uses the practice-area color
        card.setDotCssColor(css);

        // Background: subtle for readability (same approach as StatusCardFactory)
        card.setBackgroundCssColor("rgba(0,0,0,0.06)");

        switch (variant) {
        case FULL -> card.applyFull();
        case COMPACT -> card.applyCompact();
        case MINI -> card.applyMini();
        }

        return card;
    }
}