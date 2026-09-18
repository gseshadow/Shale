package com.shale.ui.component;

import java.util.List;
import java.util.Objects;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;

/** Contact-typed adapter over the shared wrapping classification chip primitive. */
public final class ContactClassificationChipGroup extends ClassificationChipGroup {
    public enum Size { COMPACT, STANDARD }

    /** Presentation input used where assignment lifecycle must remain visible. */
    public record Item(String label, String color, String categoryLabel, long definitionId, boolean historical) { }

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

    public static ContactClassificationChipGroup withLifecycle(List<Item> values, Size size) {
        return new ContactClassificationChipGroup(values, size, true);
    }

    private ContactClassificationChipGroup(List<Item> values, Size size, boolean lifecycleAware) {
        super(Objects.requireNonNull(values, "values").stream().map(value -> new Chip(
                value.label(), value.color(), value.categoryLabel(), value.definitionId(), false, value.historical())).toList(),
                Objects.requireNonNull(size, "size") == Size.COMPACT
                        ? ClassificationChipGroup.Size.COMPACT : ClassificationChipGroup.Size.STANDARD);
    }
}
