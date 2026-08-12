package com.shale.core.dto;

import com.shale.core.model.MigratedCaseDateKey;
import java.time.LocalDateTime;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MigratedCaseDateProjectionDtoTest {
    @Test void emptyProjectionRepresentsAllNineMeaningsAsAbsent() {
        var projection = MigratedCaseDateProjectionDto.empty(42);
        assertEquals(9, projection.dates().size());
        for (var key : MigratedCaseDateKey.values()) {
            var slot = projection.date(key);
            assertFalse(slot.present());
            assertNull(slot.startsAt());
        }
    }

    @Test void intakeRetainsTimeWhileTheOtherEightRequireAllDayValues() {
        LocalDateTime timed = LocalDateTime.of(2026, 8, 10, 9, 37);
        var intake = MigratedCaseDateProjectionDto.Slot.present(MigratedCaseDateKey.CALLER_DATE, timed, null, false);
        assertEquals(timed, intake.startsAt());
        assertFalse(intake.allDay());

        for (var key : MigratedCaseDateKey.values()) {
            if (key == MigratedCaseDateKey.CALLER_DATE) continue;
            assertDoesNotThrow(() -> MigratedCaseDateProjectionDto.Slot.present(key, timed.toLocalDate().atStartOfDay(), null, true));
            assertThrows(IllegalArgumentException.class,
                    () -> MigratedCaseDateProjectionDto.Slot.present(key, timed, null, false));
        }
    }

    @Test void contractRequiresExactlyOneSlotForEveryStableMeaning() {
        var slots = new EnumMap<MigratedCaseDateKey, MigratedCaseDateProjectionDto.Slot>(MigratedCaseDateKey.class);
        for (var key : MigratedCaseDateKey.values()) slots.put(key, MigratedCaseDateProjectionDto.Slot.absent(key));
        slots.put(MigratedCaseDateKey.DATE_OF_INJURY,
                MigratedCaseDateProjectionDto.Slot.present(MigratedCaseDateKey.DATE_OF_INJURY,
                        LocalDateTime.of(2026, 1, 2, 0, 0), null, true));
        var projection = new MigratedCaseDateProjectionDto(7, slots);
        assertTrue(projection.date(MigratedCaseDateKey.DATE_OF_INJURY).present());
        slots.clear();
        assertEquals(9, projection.dates().size(), "the DTO must be immutable and independent of callers");
        assertThrows(IllegalArgumentException.class, () -> new MigratedCaseDateProjectionDto(7, slots));
    }
}
