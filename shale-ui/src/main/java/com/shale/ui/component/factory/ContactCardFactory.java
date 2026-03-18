package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.ContactCard;

public class ContactCardFactory {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    public record ContactCardModel(
            Integer contactId,
            String displayName,
            String email,
            String phone
    ) {
    }

    private final Consumer<Integer> onOpenContact;

    public ContactCardFactory(Consumer<Integer> onOpenContact) {
        this.onOpenContact = onOpenContact;
    }

    public ContactCard create(ContactCardModel model, Variant variant) {
        Objects.requireNonNull(model, "model");

        ContactCard card = new ContactCard();
        card.setContactId(model.contactId());
        card.setOnOpen(onOpenContact);
        card.setName(model.displayName());
        card.setEmail(model.email());
        card.setPhone(model.phone());
        card.setBackgroundCssColor(null);

        switch (variant) {
        case FULL -> card.applyFull();
        case COMPACT -> card.applyCompact();
        case MINI -> card.applyMini();
        }

        return card;
    }

    public ContactCard createMini(Integer contactId, String displayName) {
        return create(new ContactCardModel(contactId, displayName, null, null), Variant.MINI);
    }
}
