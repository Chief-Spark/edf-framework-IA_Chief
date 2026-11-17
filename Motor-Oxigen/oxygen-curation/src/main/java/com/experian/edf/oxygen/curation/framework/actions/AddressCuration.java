package com.experian.edf.oxygen.curation.framework.actions;

import com.experian.edf.oxygen.curation.framework.CurationAction;
import com.experian.edf.oxygen.curation.framework.infotype.Address;

public class AddressCuration implements CurationAction<Address, String> {
    @Override
    public Address performAction(String value) {
        Address a = new Address();
        a.setCountry(value);
        return a;
    }
}