package lucee.runtime.cache.tag.soft;

import static org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength.HARD;
import static org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength.SOFT;

import java.util.Collections;
import java.util.Map;

import lucee.commons.collection.HashMapPro;
import lucee.runtime.cache.tag.CacheItem;
import lucee.runtime.cache.tag.MapCacheHandler;
import lucee.runtime.op.Caster;

import org.apache.commons.collections4.map.ReferenceMap;

public class SoftReferenceCacheHandler extends MapCacheHandler {

    private static Map<String, CacheItem> map = Collections.synchronizedMap(new ReferenceMap<String, CacheItem>(HARD, SOFT, HashMapPro.DEFAULT_INITIAL_CAPACITY, 0.75f));

    @Override
    protected Map<String, CacheItem> map() {
	return map;
    }

    @Override
    public boolean acceptCachedWithin(Object cachedWithin) {
	String str = Caster.toString(cachedWithin, "").trim();
	return str.equalsIgnoreCase("soft");
    }

    @Override
    public String pattern() {
	return "soft";
    }

}
