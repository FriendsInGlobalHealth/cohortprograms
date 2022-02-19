package org.openmrs.module.esaudefeatures.web;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
public class TimedConcurrentHashMapCache<K, V> extends ConcurrentHashMap<K, V> {
	
	private static final long serialVersionUID = 1L;
	
	private Map<K, Long> timeMap = new ConcurrentHashMap<K, Long>();
	
	// Store each entry for up to 30 minutes
	private static long EXPIRY_IN_MILLS = 1000 * 60 * 30;
	
	public TimedConcurrentHashMapCache() {
		initialize();
	}
	
	void initialize() {
		new CleanerThread().start();
	}
	
	@Override
	public V put(K key, V value) {
		Date date = new Date();
		timeMap.put(key, date.getTime());
		V returnVal = super.put(key, value);
		return returnVal;
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (K key : m.keySet()) {
			put(key, m.get(key));
		}
	}
	
	@Override
	public V putIfAbsent(K key, V value) {
		if (!containsKey(key))
			return put(key, value);
		else
			return get(key);
	}
	
	class CleanerThread extends Thread {
		
		@Override
		public void run() {
			System.out.println("Initiating Cleaner Thread..");
			while (true) {
				cleanMap();
				try {
					Thread.sleep(EXPIRY_IN_MILLS / 2);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private void cleanMap() {
			long currentTime = new Date().getTime();
			for (K key : timeMap.keySet()) {
				if (currentTime > (timeMap.get(key) + EXPIRY_IN_MILLS)) {
					V value = remove(key);
					timeMap.remove(key);
				}
			}
		}
	}
}
