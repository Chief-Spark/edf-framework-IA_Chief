package oxygen.consistencycheck.dd;

import java.io.Serializable;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 8/24/20 at 2:10 PM
 */
public enum DomainDictionaryFieldHandler implements Serializable {
    LFP("LFP", "Latest For Period"),
    LAP("LAP", "Latest Across Period"),
    EUC("EUC", "Earliest Until Corrected");

    private final String identifier;
    private final String description;

    DomainDictionaryFieldHandler(String identifier, String description) {
        this.identifier = identifier;
        this.description = description;
    }

    public final String identifier() {
        return identifier;
    }

    public String description() {
        return description;
    }
}
