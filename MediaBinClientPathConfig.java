package com.eu.interflow.livesite.mediabin;

import java.io.File;

import com.interwoven.livesite.runtime.RequestContext;

/**
 * One instance of a MediaBinClientPathConfig class exists and is declared as a bean with the id
 * "com.eu.interflow.livesite.mediabin.mediaBinClientPathConfigPath" 
 *   
 * @author brobertson
 *
 */
abstract public class MediaBinClientPathConfig {

	protected RequestContext context;
	
	public void setContext(RequestContext context) {
		this.context = context;
	}
	public RequestContext getContext() {
		return this.context;
	}
	
	/**
	 * Take a relative file path and return the appropriate file object based on the current context
	 * @param relativePath
	 * @return the File object represented by the path;
	 */
	abstract public File getFile(String relativePath);
	
	/**
	 * Take the same relativePath as getFile() and return a URL that the file is accessible over.
	 * 
	 * @param relativePath
	 * @return the url
	 */
	abstract public String getFileURL(String relativePath);
	
}
