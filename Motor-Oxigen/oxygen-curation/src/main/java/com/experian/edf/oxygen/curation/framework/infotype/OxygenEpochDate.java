package com.experian.edf.oxygen.curation.framework.infotype;


import com.experian.edf.oxygen.curation.framework.InformationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OxygenEpochDate implements InformationType {
    private Long date;

    public OxygenEpochDate date(Long date) {
        this.date = date;
        return this;
    }

    @Override
    public String curationType() {
        return OxygenEpochDate.class.getCanonicalName();
    }

}
