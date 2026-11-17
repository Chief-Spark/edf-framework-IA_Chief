package com.experian.edf.oxygen.curation.framework;

import com.experian.edf.oxygen.curation.framework.actions.AddressCuration;
import com.experian.edf.oxygen.curation.framework.actions.NameCuration;
import com.experian.edf.oxygen.curation.framework.infotype.Address;
import com.experian.edf.oxygen.curation.framework.infotype.OxygenEpochDate;
import com.experian.edf.oxygen.curation.framework.infotype.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class CurationService implements Serializable {

    private Map<String, Class<? extends CurationAction>> map = new HashMap<>();

    private Logger log = LoggerFactory.getLogger(CurationService.class);

    public CurationService() {

        registerCurationAction(Name.class.getCanonicalName(), NameCuration.class);
        registerCurationAction(Address.class.getCanonicalName(), AddressCuration.class);
    }

    public void registerCurationAction(Class<? extends InformationType> type, Class<? extends CurationAction> cls) {
        try {
            log.debug("Registering action for type: " + type.getCanonicalName());
            map.put(type.newInstance().curationType(), cls);
        } catch (Exception e) {
            log.error("Error in registering action for type: " + type.getCanonicalName() + e.getMessage());
        }

    }

    private void registerCurationAction(String canonicalClassName, Class<? extends CurationAction> actionCls) {
        try {
            map.put(canonicalClassName, actionCls);
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }

    private Optional<CurationAction> getCurationAction(InformationType type) {
        try {
            return Optional.of(map.get(type.curationType()).newInstance());
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        return Optional.empty();

    }

    public <T extends InformationType,V> Optional<T> curate(Class<T> t, V value) {
        Optional<CurationAction> curationAction;
        try {

            curationAction = getCurationAction(t.newInstance());
            if (curationAction.isPresent()) {
                return Optional.of((T) curationAction.get().performAction(value));
            }
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
        return Optional.empty();
    }


    public Name curateName(String value) {
        return curate(Name.class, value).get();
    }

}
