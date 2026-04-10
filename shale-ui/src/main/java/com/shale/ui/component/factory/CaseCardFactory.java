package com.shale.ui.component.factory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.CaseCard;
import com.shale.ui.util.ColorUtil;

import javafx.scene.Node;

public final class CaseCardFactory {
	private static final String NON_ENGAGEMENT_OVERRIDE_BACKGROUND_CSS = "#D4D4DFF";

	public enum Variant {
		FULL, COMPACT, MINI
	}

	private final Consumer<Integer> onOpenCase;

	public CaseCardFactory(Consumer<Integer> onOpenCase) {
		this.onOpenCase = onOpenCase;
	}

	public Node create(CaseCardModel vm) {
		return create(vm, Variant.COMPACT);
	}

	public Node create(CaseCardModel vm, Variant variant) {
		CaseCard card = new CaseCard();

		card.setCaseId((int) vm.id()); // keep your current int wiring
		card.setTitle(vm.name().isBlank() ? "(no name)" : vm.name());
		card.setResponsibleAttorney(vm.responsibleAttorney());
		card.setIntakeDate(vm.intakeDate());
		card.setSolDate(vm.solDate());

		String backgroundCss = Boolean.TRUE.equals(vm.nonEngagementLetterSent())
				? NON_ENGAGEMENT_OVERRIDE_BACKGROUND_CSS
				: ColorUtil.toCssBackgroundColorOrNull(vm.responsibleAttorneyColor());
		card.setBackgroundCssColor(backgroundCss);

		card.setOnOpen(id ->
		{
			if (onOpenCase != null)
				onOpenCase.accept(id);
		});

		switch (variant) {
		case FULL -> card.applyFull();
		case COMPACT -> card.applyCompact();
		case MINI -> card.applyMini();
		}

		return card;
	}

	// Matches your existing CaseCardVm fields closely
	public record CaseCardModel(
			long id,
			String name,
			LocalDate intakeDate,
			LocalDate solDate,
			String responsibleAttorney,
			String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent
	) {
		public CaseCardModel {
			name = Objects.requireNonNullElse(name, "");
			responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			nonEngagementLetterSent = Boolean.TRUE.equals(nonEngagementLetterSent);
		}
	}
}
