/*
* Copyright (C) 2016-2018 ActionTech.
* based on code by MyCATCopyrightHolder Copyright (c) 2013, OpenCloudDB/MyCAT.
* License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
*/
package com.actiontech.dble.cache;

import com.actiontech.dble.cache.impl.EnchachePooFactory;
import com.actiontech.dble.cache.impl.LevelDBCachePooFactory;
import com.actiontech.dble.cache.impl.MapDBCachePooFactory;
import com.actiontech.dble.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * cache service for other component default using memory cache encache
 *
 * @author wuzhih
 */
public class CacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheService.class);

    private final Map<String, CachePoolFactory> poolFactories = new HashMap<>();
    private final ConcurrentMap<String, CachePool> allPools = new ConcurrentHashMap<>();

    public CacheService(boolean isLowerCaseTableNames) {
        // load cache pool defined
        try {
            init(isLowerCaseTableNames);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public Map<String, CachePool> getAllCachePools() {
        return this.allPools;
    }

    private void init(boolean isLowerCaseTableNames) throws Exception {
        Properties props = new Properties();
        try (InputStream stream = ResourceUtil.getResourceAsStream("/cacheservice.properties")) {
            if (stream == null) {
                LOGGER.info("cache don't be used currently! if use, please configure cacheservice.properties");
                return;
            }
            props.load(stream);
        }
        boolean on = isSwitchOn(props);
        if (on) {
            createRootLayedCachePool(props);
            createSpecificPool(props, isLowerCaseTableNames);
        } else {
            LOGGER.info("cache don't be used currently! if use, please switch on options in cheservice.properties");
        }
    }

    private boolean isSwitchOn(Properties props) throws Exception {
        final String poolFactoryPref = "factory.";
        boolean use = false;

        String[] keys = props.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for (String key : keys) {
            if (key.startsWith(poolFactoryPref)) {
                createPoolFactory(key.substring(poolFactoryPref.length()), (String) props.get(key));
                use = true;
            }
        }
        return use;
    }

    private void createRootLayedCachePool(Properties props) throws Exception {
        String layedCacheType = props.getProperty("layedpool.TableID2DataNodeCacheType");
        String cacheDefault = props.getProperty("layedpool.TableID2DataNodeCache");
        if (cacheDefault != null && layedCacheType != null) {
            throw new java.lang.IllegalArgumentException(
                    "invalid cache config, layedpool.TableID2DataNodeCacheType and " +
                            "layedpool.TableID2DataNodeCache don't coexist");
        } else if (cacheDefault == null && layedCacheType == null) {
            return;
        }

        final String rootlayedCacheName = "TableID2DataNodeCache";
        int size = 0;
        int timeOut = 0;
        if (layedCacheType != null) {
            props.remove("layedpool.TableID2DataNodeCacheType");
        } else {
            String value = (String) props.get("layedpool.TableID2DataNodeCache");
            props.remove("layedpool.TableID2DataNodeCache");

            String[] valueItems = value.split(",");
            layedCacheType = valueItems[0];
            size = Integer.parseInt(valueItems[1]);
            timeOut = Integer.parseInt(valueItems[2]);
        }
        createLayeredPool(rootlayedCacheName, layedCacheType, size, timeOut);
    }

    private void createSpecificPool(Properties props, boolean isLowerCaseTableNames) throws Exception {
        final String poolKeyPref = "pool.";
        final String layedPoolKeyPref = "layedpool.";

        String[] keys = props.keySet().toArray(new String[0]);
        Arrays.sort(keys);

        for (String key : keys) {
            if (key.startsWith(poolKeyPref)) {
                String cacheName = key.substring(poolKeyPref.length());
                String value = (String) props.get(key);
                String[] valueItems = value.split(",");
                if (valueItems.length < 3) {
                    throw new java.lang.IllegalArgumentException("invalid cache config, key:" + key + " value:" + value);
                }
                String type = valueItems[0];
                int size = Integer.parseInt(valueItems[1]);
                int timeOut = Integer.parseInt(valueItems[2]);
                createPool(cacheName, type, size, timeOut);
            } else if (key.startsWith(layedPoolKeyPref)) {
                String cacheName = key.substring(layedPoolKeyPref.length());
                int index = cacheName.indexOf(".");
                String parent = cacheName.substring(0, index);
                String child = cacheName.substring(index + 1);
                CachePool pool = this.allPools.get(parent);

                if (isLowerCaseTableNames) {
                    child = child.toLowerCase();
                }

                String value = (String) props.get(key);
                String[] valueItems = value.split(",");
                if (valueItems.length != 2) {
                    throw new java.lang.IllegalArgumentException("invalid primary cache config, key:" + key + " value:" + value + "too more values");
                }

                if ((pool == null) || !(pool instanceof LayerCachePool)) {
                    throw new java.lang.IllegalArgumentException("parent pool not exists or not layered cache pool:" +
                            parent + " the child cache is:" + child);
                }

                int size = Integer.parseInt(valueItems[0]);
                int timeOut = Integer.parseInt(valueItems[1]);
                ((DefaultLayedCachePool) pool).createChildCache(child, size, timeOut);
            }
        }
    }

    private void createPoolFactory(String factoryType, String factryClassName) throws Exception {
        String lowerClass = factryClassName.toLowerCase();
        switch (lowerClass) {
            case "ehcache":
                poolFactories.put(factoryType, new EnchachePooFactory());
                break;
            case "leveldb":
                poolFactories.put(factoryType, new LevelDBCachePooFactory());
                break;
            case "mapdb":
                poolFactories.put(factoryType, new MapDBCachePooFactory());
                break;
            default:
                CachePoolFactory factry = (CachePoolFactory) Class.forName(factryClassName).newInstance();
                poolFactories.put(factoryType, factry);
        }
    }


    private void checkExists(String poolName) {
        if (allPools.containsKey(poolName)) {
            throw new java.lang.IllegalArgumentException("duplicate cache pool name: " + poolName);
        }
    }

    private CachePoolFactory getCacheFact(String type) {
        CachePoolFactory facty = this.poolFactories.get(type);
        if (facty == null) {
            throw new RuntimeException("CachePoolFactory not defined for type:" + type);
        }
        return facty;
    }

    private void createPool(String poolName, String type, int cacheSize, int expireSeconds) {
        checkExists(poolName);
        CachePoolFactory cacheFact = getCacheFact(type);
        CachePool cachePool = cacheFact.createCachePool(poolName, cacheSize, expireSeconds);
        allPools.put(poolName, cachePool);
    }

    private void createLayeredPool(String cacheName, String type, int size, int expireSeconds) {
        checkExists(cacheName);
        LOGGER.info("create layer cache pool " + cacheName + " of type " + type + " ,default cache size " +
                size + " ,default expire seconds" + expireSeconds);
        DefaultLayedCachePool layerdPool = new DefaultLayedCachePool(cacheName, this.getCacheFact(type), size, expireSeconds);
        this.allPools.put(cacheName, layerdPool);
    }

    /**
     * get cache pool by name, caller should cache result
     *
     * @param poolName poolName
     * @return CachePool
     */
    public CachePool getCachePool(String poolName) {
        return allPools.get(poolName);
    }

    public void clearCache() {
        LOGGER.info("ready all cache pool ");
        for (CachePool pool : allPools.values()) {
            pool.clearCache();
        }
    }

    public void reloadCache(boolean isLowerCaseTableNames) {
        LOGGER.info("reloadCache cache pool ");
        for (CachePool pool : allPools.values()) {
            pool.clearCache();
        }
        allPools.clear();
        try {
            init(isLowerCaseTableNames);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
