package com.shale.core.dto;

import com.shale.core.model.CaseDatePresentationPurpose;
import java.util.Arrays;
import java.util.List;

/** Tenant default for one presentation purpose. An empty selections list is authoritative. */
public record CaseDatePresentationConfigurationDto(long configurationId, int shaleClientId,
        CaseDatePresentationPurpose purpose, List<CaseDatePresentationSelectionDto> selections, byte[] rowVer) {
    public CaseDatePresentationConfigurationDto {
        selections=selections==null?List.of():List.copyOf(selections); rowVer=copy(rowVer);
    }
    @Override public byte[] rowVer(){return copy(rowVer);}
    private static byte[] copy(byte[] value){return value==null?null:Arrays.copyOf(value,value.length);}
}
