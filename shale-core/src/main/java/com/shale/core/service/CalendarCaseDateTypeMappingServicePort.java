package com.shale.core.service;

import com.shale.core.model.CalendarCaseDateTypeMapping;
import com.shale.core.model.CreateCalendarCaseDateTypeMappingCommand;
import com.shale.core.model.DeleteCalendarCaseDateTypeMappingCommand;
import com.shale.core.model.SetCalendarCaseDateTypeMappingActiveCommand;
import com.shale.core.model.UpdateCalendarCaseDateTypeMappingCommand;
import java.util.List;

/** Application boundary; tenant and actor are deliberately absent and come from the DB session. */
public interface CalendarCaseDateTypeMappingServicePort {
    List<CalendarCaseDateTypeMapping> listMappings();
    CalendarCaseDateTypeMapping createMapping(CreateCalendarCaseDateTypeMappingCommand command);
    CalendarCaseDateTypeMapping updateMapping(UpdateCalendarCaseDateTypeMappingCommand command);
    CalendarCaseDateTypeMapping setMappingActive(SetCalendarCaseDateTypeMappingActiveCommand command);
    void deleteMapping(DeleteCalendarCaseDateTypeMappingCommand command);
}
