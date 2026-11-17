package oxygen.stage.synthesis.consistencycheck;

import com.experian.edf.oxygen.domain.SyntheticEvent;
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationChecker;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import scala.collection.mutable.ListBuffer;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 8/11/20 at 9:04 PM
 */
public class RefCacheManagerUtil implements Serializable {
    public static void removeFromCache(scala.collection.immutable.List<SyntheticEvent> syntheticEvent, Map<String, Int2ObjectOpenHashMap<ListBuffer<SyntheticEvent>>> refCacheMap) {
        refCacheMap.forEach((key, value) -> value.forEach((key1, value1) -> value1 = RelationshipMigrationChecker.syntheticEventsDiff(value1, syntheticEvent)));
    }
}
