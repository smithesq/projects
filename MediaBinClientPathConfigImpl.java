package com.eu.interflow.livesite.mediabin;

import java.io.File;

import com.interwoven.livesite.file.FileDal;

public class MediaBinClientPathConfigImpl extends MediaBinClientPathConfig {

	private String importPath = "/assets/mb-imported";
	
	/**
	 * @return the importPath
	 */
	public String getImportPath() {
		return importPath;
	}

	/**
	 * @param importPath the importPath to set
	 */
	public void setImportPath(String importPath) {
		this.importPath = importPath;
	}

	@SuppressWarnings("deprecation")
	@Override
	public File getFile(String relativePath) {
		FileDal fd = context.getFileDal();
		return new File(fd.getRoot() + fd.getSeparator() + importPath + fd.getSeparator() + relativePath);
	}

	@Override
	public String getFileURL(String relativePath) {
		return importPath + "/" + relativePath;
	}

}
