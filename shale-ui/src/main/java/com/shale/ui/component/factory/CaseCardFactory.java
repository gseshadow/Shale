package com.shale.ui.component.factory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.CaseCard;
import javafx.scene.Node;

public final class CaseCardFactory {
	private static final String STATUS_FALLBACK_CSS = "#F1F5F9";
	private static final String ATTORNEY_FALLBACK_CSS = "#94A3B8";
	private static final String PRACTICE_AREA_FALLBACK_CSS = "#CBD5E1";

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

		card.setStatus(vm.primaryStatusName());
		card.setStatusCssColor(CaseCard.normalizeColor(vm.primaryStatusColor(), STATUS_FALLBACK_CSS));
		card.setAttorneyDotCssColor(CaseCard.normalizeColor(vm.responsibleAttorneyColor(), ATTORNEY_FALLBACK_CSS));
		card.setPracticeAreaCssColor(CaseCard.normalizeColor(vm.practiceAreaColor(), PRACTICE_AREA_FALLBACK_CSS));

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
			Boolean nonEngagementLetterSent,
			String primaryStatusName,
			String primaryStatusColor,
			String practiceAreaColor
	) {
		public CaseCardModel(long id, String name, LocalDate intakeDate, LocalDate solDate, String responsibleAttorney,
				String responsibleAttorneyColor, Boolean nonEngagementLetterSent) {
			this(id, name, intakeDate, solDate, responsibleAttorney, responsibleAttorneyColor, nonEngagementLetterSent, "", "", "");
		}

		public CaseCardModel(long id, String name, LocalDate intakeDate, LocalDate solDate, String responsibleAttorney,
				String responsibleAttorneyColor, Boolean nonEngagementLetterSent, String primaryStatusName, String primaryStatusColor) {
			this(id, name, intakeDate, solDate, responsibleAttorney, responsibleAttorneyColor, nonEngagementLetterSent,
					primaryStatusName, primaryStatusColor, "");
		}

		public CaseCardModel {
			name = Objects.requireNonNullElse(name, "");
			responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			nonEngagementLetterSent = Boolean.TRUE.equals(nonEngagementLetterSent);
			primaryStatusName = Objects.requireNonNullElse(primaryStatusName, "");
			primaryStatusColor = Objects.requireNonNullElse(primaryStatusColor, "");
			practiceAreaColor = Objects.requireNonNullElse(practiceAreaColor, "");
		}
	}
}
