package com.experian.edf.oxygen.curation.framework.actions;


import com.experian.edf.oxygen.curation.framework.CurationAction;
import com.experian.edf.oxygen.curation.framework.infotype.Name;

public class NameCuration implements CurationAction<Name, String> {
    @Override
    public Name performAction(String value) {
        return new Name();
    }
}
