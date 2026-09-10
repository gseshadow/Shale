package com.shale.ui.component;

import java.util.List;
import java.util.Objects;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;

/** Contact-typed adapter over the shared wrapping classification chip primitive. */
public final class ContactClassificationChipGroup extends ClassificationChipGroup {
    public enum Size { COMPACT, STANDARD }

    public ContactClassificationChipGroup(List<ClassificationPresentation> values, Size size) {
        super(Objects.requireNonNull(values,"values").stream().map(value -> new Chip(
                value.label(), value.color(), switch(value.category()) {
                    case CONTACT_TYPE -> "Contact Type";
                    case SPECIALTY -> "Specialty";
                    case CREDENTIAL -> "Credential";
                }, value.definitionId(), false)).toList(),
                Objects.requireNonNull(size,"size") == Size.COMPACT
                        ? ClassificationChipGroup.Size.COMPACT : ClassificationChipGroup.Size.STANDARD);
    }
}
