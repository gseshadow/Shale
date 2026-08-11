package com.shale.data.service.adapter;

import com.shale.core.model.*;
import com.shale.core.service.CalendarCaseDateTypeMappingServicePort;
import com.shale.data.dao.CalendarCaseDateTypeMappingDao;
import java.util.List;
import java.util.Objects;

/** Production application adapter; authorization remains at the DAO transaction boundary. */
public final class CalendarCaseDateTypeMappingServiceAdapter implements CalendarCaseDateTypeMappingServicePort {
    private final CalendarCaseDateTypeMappingDao dao;
    public CalendarCaseDateTypeMappingServiceAdapter(CalendarCaseDateTypeMappingDao dao){this.dao=Objects.requireNonNull(dao,"dao");}
    @Override public List<CalendarCaseDateTypeMapping> listMappings(){return dao.listMappings();}
    @Override public CalendarCaseDateTypeMapping createMapping(CreateCalendarCaseDateTypeMappingCommand command){return dao.createMapping(command);}
    @Override public CalendarCaseDateTypeMapping updateMapping(UpdateCalendarCaseDateTypeMappingCommand command){return dao.updateMapping(command);}
    @Override public CalendarCaseDateTypeMapping setMappingActive(SetCalendarCaseDateTypeMappingActiveCommand command){return dao.setMappingActive(command);}
    @Override public void deleteMapping(DeleteCalendarCaseDateTypeMappingCommand command){dao.deleteMapping(command);}
}
