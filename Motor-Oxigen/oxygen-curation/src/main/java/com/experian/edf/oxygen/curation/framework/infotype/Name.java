package com.experian.edf.oxygen.curation.framework.infotype;


import com.experian.edf.oxygen.curation.framework.InformationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Name implements InformationType {
    private String firstName;
    private String lastName;

    public String getFullName() {
        return firstName + " " + lastName;
    }


    @Override
    public String curationType() {
        return Name.class.getCanonicalName();
    }

}
