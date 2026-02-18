package com.shale.ui.component.factory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.CaseCard;
import com.shale.ui.util.ColorUtil;

import javafx.scene.Node;

public final class CaseCardFactory {

	private final Consumer<Integer> onOpenCase;

	public CaseCardFactory(Consumer<Integer> onOpenCase) {
		this.onOpenCase = onOpenCase;
	}

	public Node create(CaseCardModel vm) {
		CaseCard card = new CaseCard();

		card.setCaseId((int) vm.id()); // keep your current int wiring
		card.setTitle(vm.name().isBlank() ? "(no name)" : vm.name());
		card.setResponsibleAttorney(vm.responsibleAttorney());
		card.setIntakeDate(vm.intakeDate());
		card.setSolDate(vm.solDate());

		card.setBackgroundCssColor(ColorUtil.toCssBackgroundColor(vm.responsibleAttorneyColor()));

		card.setOnOpen(id ->
		{
			if (onOpenCase != null)
				onOpenCase.accept(id);
		});

		return card;
	}

	// Matches your existing CaseCardVm fields closely
	public record CaseCardModel(
			long id,
			String name,
			LocalDate intakeDate,
			LocalDate solDate,
			String responsibleAttorney,
			String responsibleAttorneyColor
	) {
		public CaseCardModel {
			name = Objects.requireNonNullElse(name, "");
			responsibleAttorney = Objects.requireNonNullElse(responsibleAttorney, "");
			responsibleAttorneyColor = Objects.requireNonNullElse(responsibleAttorneyColor, "");
		}
	}
}
