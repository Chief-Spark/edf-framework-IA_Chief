package com.experian.edf.oxygen.curation.framework.infotype;


import com.experian.edf.oxygen.curation.framework.InformationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Address implements InformationType {
    private String country;

    @Override
    public String curationType() {
        return Address.class.getCanonicalName();
    }

}
