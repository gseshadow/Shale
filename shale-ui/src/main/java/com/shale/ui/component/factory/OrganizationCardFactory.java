package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.core.model.Organization;
import com.shale.ui.component.OrganizationCard;
import com.shale.ui.util.ColorUtil;

public class OrganizationCardFactory {

	public enum Variant {
		FULL, COMPACT, MINI
	}

	public record OrganizationCardModel(
			Integer organizationId,
			String name,
			Integer organizationTypeId,
			String organizationTypeName,
			String phone,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			String colorCss
	) {
	}

	private final Consumer<Integer> onOpenOrganization;

	public OrganizationCardFactory(Consumer<Integer> onOpenOrganization) {
		this.onOpenOrganization = onOpenOrganization;
	}

	public OrganizationCard create(OrganizationCardModel model, Variant variant) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(variant, "variant");

		OrganizationCard card = new OrganizationCard();
		card.setOrganizationId(model.organizationId());
		card.setOnOpen(onOpenOrganization);

		card.setName(model.name());
		card.setOrganizationType(model.organizationTypeId(), model.organizationTypeName());
		card.setPhone(model.phone());
		card.setEmail(model.email());
		card.setWebsite(model.website());
		card.setAddress(model.address1(), model.address2(), model.city(), model.state(), model.postalCode(), model.country());
		card.setNotesSnippet(model.notes());
		card.setBackgroundCssColor(ColorUtil.toCssBackgroundColor(model.colorCss()));

		switch (variant) {
		case FULL -> card.applyFull();
		case COMPACT -> card.applyCompact();
		case MINI -> card.applyMini();
		}

		return card;
	}

	public OrganizationCard create(Organization organization, Variant variant) {
		Objects.requireNonNull(organization, "organization");
		OrganizationCardModel model = new OrganizationCardModel(
				organization.getId(),
				organization.getName(),
				organization.getOrganizationTypeId(),
				organization.getOrganizationTypeName(),
				organization.getPhone(),
				organization.getEmail(),
				organization.getWebsite(),
				organization.getAddress1(),
				organization.getAddress2(),
				organization.getCity(),
				organization.getState(),
				organization.getPostalCode(),
				organization.getCountry(),
				organization.getNotes(),
				null);
		return create(model, variant);
	}
}
