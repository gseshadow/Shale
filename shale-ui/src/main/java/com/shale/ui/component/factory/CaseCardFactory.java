package com.shale.ui.component.factory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.function.Consumer;

import com.shale.ui.component.CaseCard;
import com.shale.core.dto.SelectedCaseDateOccurrenceDto;
import javafx.scene.Node;

public final class CaseCardFactory {
	private static final String STATUS_FALLBACK_CSS = "#F1F5F9";
	private static final String PRACTICE_AREA_FALLBACK_CSS = "#CBD5E1";

	public enum Variant {
		FULL, COMPACT, MINI, EMBEDDED, TASK_PREVIEW
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
		card.setPresentationDates(vm.presentationDates());
		card.setNonEngagementLetterSent(vm.nonEngagementLetterSent());

		card.setStatus(vm.primaryStatusName());
		card.setStatusCssColor(CaseCard.normalizeColor(vm.primaryStatusColor(), STATUS_FALLBACK_CSS));
		card.setAttorneyDotCssColor(vm.responsibleAttorneyColor());
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
		case EMBEDDED -> card.applyEmbeddedMini();
		case TASK_PREVIEW -> card.applyTaskPreview();
		}

		return card;
	}

	// Matches your existing CaseCardVm fields closely
	public record CaseCardModel(
			long id,
			String name,
			String responsibleAttorney,
			String responsibleAttorneyColor,
			Boolean nonEngagementLetterSent,
			String primaryStatusName,
			String primaryStatusColor,
			String practiceAreaColor,
			List<PresentationDate> presentationDates
	) {
		public CaseCardModel {
			name = Objects.requireNonNullElse(name, "");
			responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
			nonEngagementLetterSent = Boolean.TRUE.equals(nonEngagementLetterSent);
			primaryStatusName = Objects.requireNonNullElse(primaryStatusName, "");
			primaryStatusColor = Objects.requireNonNullElse(primaryStatusColor, "");
			practiceAreaColor = Objects.requireNonNullElse(practiceAreaColor, "");
			presentationDates = List.copyOf(Objects.requireNonNull(presentationDates, "presentationDates"));
		}
	}

	public record PresentationDate(String selectionIdentity,String systemKey,String displayName,
			LocalDate date,boolean pendingConfirmation) {
		public PresentationDate { displayName=Objects.requireNonNullElse(displayName,""); }
	}

	public static List<PresentationDate> toPresentationDates(List<SelectedCaseDateOccurrenceDto> values) {
		return Objects.requireNonNullElse(values, List.<SelectedCaseDateOccurrenceDto>of()).stream()
				.filter(value -> value.caseDateId() != null && value.startsAt() != null)
				.map(value -> new PresentationDate(value.selectionIdentity(), value.displaySystemKey(),
						value.displayName(), value.startsAt().toLocalDate(), value.pendingConfirmation()))
				.toList();
	}
}
