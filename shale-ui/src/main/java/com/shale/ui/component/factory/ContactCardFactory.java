package com.shale.ui.component.factory;

import java.util.function.Consumer;

import com.shale.ui.component.ContactCard;

public class ContactCardFactory {

	private final Consumer<Integer> onOpenContact;

	public ContactCardFactory(Consumer<Integer> onOpenContact) {
		this.onOpenContact = onOpenContact;
	}

	public ContactCard createMini(Integer contactId, String displayName) {
		return create(contactId, displayName, ContactCard.Variant.MINI);
	}

	public ContactCard createCompact(Integer contactId, String displayName) {
		return create(contactId, displayName, ContactCard.Variant.COMPACT);
	}

	private ContactCard create(Integer contactId, String displayName, ContactCard.Variant variant) {
		return new ContactCard(contactId, displayName, variant, onOpenContact);
	}
}
