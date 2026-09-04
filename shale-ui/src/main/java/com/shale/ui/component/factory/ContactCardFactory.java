package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.ContactCard;
import com.shale.ui.component.ContactClassificationChipGroup;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;
import java.util.List;

public class ContactCardFactory {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    public record ContactCardModel(
            Integer contactId,
            String displayName,
            String role,
            String email,
            String phone,
            List<ClassificationPresentation> classifications
    ) {
        public ContactCardModel { classifications=List.copyOf(classifications); }
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
        card.setRole(model.role());
        card.setEmail(model.email());
        card.setPhone(model.phone());
        card.setClassifications(model.classifications());
        card.setBackgroundCssColor(null);

        switch (variant) {
        case FULL -> card.applyFull();
        case COMPACT -> card.applyCompact();
        case MINI -> card.applyMini();
        }

        return card;
    }

    public ContactCard createMini(Integer contactId, String displayName) {
        return create(new ContactCardModel(contactId, displayName, null, null, null, List.of()), Variant.MINI);
    }
}
