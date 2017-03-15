package com.eu.interflow.livesite.mediabin;

import java.util.Calendar;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MediaBinRefreshCache {
	
	protected static HashMap<String, CacheItem> cache;

	protected static int checkInterval;
	
	private static Log mLogger = LogFactory.getLog(MediaBinRefreshCache.class);

	static {
		mLogger.info("CREATING MediaBinRefreshCache");
		cache = new HashMap<String, CacheItem>();
		checkInterval = Settings.getUpdateCheckInterval();
	}
	
	/**
	 * Returns the cached value of the last modified time of the passed asset id, if it is in the cache and has not expired
	 * 
	 * @param assetId
	 * @return	time since epoch in milliseconds if the time is available, zero if entry is not in the cache or cache entry has expired
	 */
	public static long getModifiedTime(String assetId) {
		CacheItem c = cache.get(assetId);
		if (c == null) {
			return 0;
		}
		else {
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(
						"AssetId : " + assetId 
						+ " lastMod = " + c.lastModifiedTime
						+ " ; " + (c.lastCheckTime + checkInterval) 
						+ " < " + Calendar.getInstance().getTimeInMillis()
				);
			}
			if (c.lastCheckTime + checkInterval < Calendar.getInstance().getTimeInMillis()) {
				mLogger.debug("AssetId : " + assetId + " >>>> CACHE EXPIRED");
				return 0;
			}
			else {
				return c.lastModifiedTime;
			}
		}
	}
	
	/**
	 * Set the last modified time for the passed assetid in the cache 
	 * @param assetId
	 * @param lastModifiedTime
	 */
	public static void setModifiedTime(String assetId, long lastModifiedTime) {
		
		synchronized (cache) {
			CacheItem c = cache.get(assetId);
			if (c == null) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("SET NEW AssetId : " + assetId + " : " + lastModifiedTime);
				}
				cache.put(assetId, new CacheItem(lastModifiedTime));
			}
			else {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("UPDATE AssetId : " + assetId  + " : " + lastModifiedTime + " > " + Calendar.getInstance().getTimeInMillis());
				}
				c.update(lastModifiedTime);
			}
		}

	}
	
	/**
	 * Single cache item in the cache, just holds the last check time and the last modified time
	 * @author brobertson
	 */
	private static class CacheItem {
		public long lastCheckTime;
		public long lastModifiedTime;
		
		public CacheItem(long lastModifiedTime) {
			update(lastModifiedTime);
		}
		
		public void update(long lastModifiedTime) {
			this.lastCheckTime    = Calendar.getInstance().getTimeInMillis();
			this.lastModifiedTime = lastModifiedTime;
		}
		
	}

}
