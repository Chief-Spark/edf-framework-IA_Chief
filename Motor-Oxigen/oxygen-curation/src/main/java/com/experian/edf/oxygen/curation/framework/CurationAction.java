package com.experian.edf.oxygen.curation.framework;

/**
 * Curation Action
 *
 * @param <IT> Information Type Implementation
 * @param <VT> Type of Data send as input for Perform Action
 */
public interface CurationAction<IT extends InformationType, VT> {
    IT performAction(VT value);
}
