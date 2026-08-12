package com.shale.data.service.adapter;

import com.shale.core.dto.FormConfigurationDto;
import com.shale.core.service.FormConfigurationServicePort;
import com.shale.data.dao.FormConfigurationDao;
import java.util.Objects;

public final class FormConfigurationServiceAdapter implements FormConfigurationServicePort {
    private final FormConfigurationDao dao;
    public FormConfigurationServiceAdapter(FormConfigurationDao dao){this.dao=Objects.requireNonNull(dao,"dao");}
    @Override public FormConfigurationDto load(int tenant,int actor,String formKey){return dao.load(tenant,actor,formKey);}
    @Override public FormConfigurationDto replace(ReplaceCommand command){return dao.replace(command);}
}
