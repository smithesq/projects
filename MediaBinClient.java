/**
 * 
 */
package com.eu.interflow.livesite.mediabin;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

import com.eu.interflow.livesite.externals.ILocale;
import com.eu.interflow.livesite.mediabin.requests.MediaBinRunnableRequest;
import com.eu.interflow.livesite.mediabin.requests.MediaBinStreamingTransformationRequest;
import com.eu.interflow.livesite.mediabin.requests.MediaBinTransformationRequest;
import com.eu.interflow.livesite.mediabin.requests.MediaBinUpToDateRequest;
import com.eu.interflow.livesite.utils.LSDCRReader;
import com.interwoven.livesite.runtime.RequestContext;
import com.interwoven.livesite.spring.ApplicationContextUtils;
import com.mediabin.www.MBAsset;

/**
 * @author gjones
 * @author brobertson
 *
 */
public class MediaBinClient {

	private static final String PATH_CONFIG_BEAN_ID = "com.eu.interflow.livesite.mediabin.mediaBinClientPathConfig";
	
	/**
	 * Filename prefix used for temporary placeholder files that are used for basic locking
	 */
	private static final String PLACEHOLDER_FILE_PREFIX = ".placeholder.";
	
	/**
	 * Filename used when there is no asset path and a (None) transformation is being used
	 */
	private static final String DEFAULT_TRANSFORMED_FILENAME = "original";

	/**
	 * Attribute of transformed filename elements that indicates whether a download is waiting or not
	 */
	private static final String TRANSFORMATION_READY_ATTRIBUTE = "assetReady";
	
	private static Log mLogger = LogFactory.getLog(MediaBinClient.class);
	
	private static ExecutorService pool = Executors.newFixedThreadPool(Settings.getMaxConnections());
	
	private static LSDCRReader reader = new LSDCRReader();
	
	private static Object mbTransformationsLock = new Object();
	private static Document mbTransformations = null;
	private static long    mbTransformationsLastRefresh = 0;
	
	private static int updateCheckInterval = Settings.getUpdateCheckInterval();
	private static int transformationRefreshInterval = Settings.getTransformationsRefreshInterval();
	
	
	
	/**
	 * Get the MediaBin transformations DCR, this DCR is cached and refreshed once every Settings.getTransformationsRefreshInterval()
	 * @param context
	 * @return
	 * @throws DocumentException
	 */
	private static Document getTransformations(RequestContext context) throws DocumentException {
		mLogger.debug("Getting Transformations DCR");
		if (mbTransformations == null || (mbTransformationsLastRefresh + transformationRefreshInterval < Calendar.getInstance().getTimeInMillis())) {
			synchronized (mbTransformationsLock) {
				mLogger.info("Reading Transformations DCR");
				mbTransformations = reader.readDCR(context, Settings.getTransformationsDCRPath());
				mbTransformationsLastRefresh = Calendar.getInstance().getTimeInMillis();
			}
		}
		return mbTransformations;
	}
	
	
	/**
	 * For a given asset perform all the transformations required by the passed content type, xpath and transformation context combination
	 * Actual requests are delegated off to a ExecutorService, so the transformed files may not exists immediately
	 * 
	 * @param assetPath					The path in MediaBin of an asset taken from a DCR.
	 * @param assetId 					The MediaBin ID of an asset taken from a DCR, e.g. {317136BD-9F9F-4A56-8250-3615646EB735}.
	 * @param contentType				The DCR content type, e.g. content/product-details
	 * @param xpath						An XPath expression of the location of the MediaBin asset within the DCR, e.g. /main/ProductImage.
	 * @param transformationContext		Name the context in which the imported asset is used, e.g. PDF, ProductComponent, etc.
	 * @param context					the request context
	 *  
	 * @return A HashMap containing all the transformations that were executed, or null if there was a problem<br />
	 * 			<ul>
	 * 				<li>Keys of the HashMap are the "TransformedName" attribute in transformations DCR</li>
	 * 				<li>Values of the HashMap is the workspace-relative path to the imported asset for that transformation</li>
	 * 			</ul>
	 */
	@SuppressWarnings("unchecked")
	public static HashMap importAsset(String assetPath, String assetId, String contentType, String xpath, String transformationContext, RequestContext context)
	{
		
		MediaBinClientPathConfig pathConfig = getPathConfig(context);
		
		mLogger.debug("Entering importAsset(assetPath: " + assetPath + ", contentType: " + contentType + ", xpath: " + xpath + ", transformationContext: " + transformationContext + ")");
		
		try {
			
			HashMap transformedFilenames = new HashMap();
			
			Document mbTransformations = getTransformations(context);
			//String searchXPath = "//Transformation[@ContentType='" + contentType + "' and @XPath='" + xpath + "' and @TransformationContext='" + transformationContext + "']";
			String searchXPath = "//Source[@ContentType='" + contentType + "' and @TransformationContext='" + transformationContext + "']/Asset[@XPath='" + xpath + "']/Transformation";
			List settings = mbTransformations.selectNodes(searchXPath);
		
			if (settings.size() == 0) {
				mLogger.info("No transformation found for ContentType(" + contentType + ") XPath(" + xpath + ") TransformationContext(" + transformationContext + ")");
				return null; 
			}
			
			// Some xPath's cannot be used as a folder name, for these a FileSystemFriendlyPath is provided 
			String fileSystemFriendlyPath = ((Element) settings.get(0)).getParent().attributeValue("FileSystemFriendlyPath");
			fileSystemFriendlyPath = (fileSystemFriendlyPath == null) ? xpath.replace('/', '_') : fileSystemFriendlyPath.replace('/', '_');
			
			for(Iterator transformationsIter = settings.listIterator(); transformationsIter.hasNext(); ) {

				Element transform = (Element) transformationsIter.next();
				String transformedName = transform.attributeValue("TransformedName");
				String taskName = transform.attributeValue("TaskName");
				
				mLogger.debug("Found Transformation: " + transformedName + " >> "+ taskName);				
				
				List params = transform.elements("RuntimeParameter");		
				HashMap txParams = new HashMap();
				StringBuffer nameParams = new StringBuffer();
				
				for (Iterator itr = params.iterator(); itr.hasNext(); )
				{
					Element param = (Element) itr.next();
					String name = param.attributeValue("ParameterName");
					String value = param.attributeValue("ParameterValue");
					mLogger.debug(" >> MediaBin Parameter: " + name + ": " + value);
					txParams.put(name, MediaBinRequestUtils.createParameter(name, value));
					nameParams.append("_").append(value);
				}
				
				RequestImportAssetResult result = requestImportAsset(
					assetId, assetPath, contentType, fileSystemFriendlyPath, 
					taskName, nameParams.toString(), txParams, transform.attributeValue("ResultExt"), 
					context, pathConfig
				);
	
				// something went wrong, skip this entry
				if (result == null) continue;

				transformedFilenames.put(transformedName, result.filePath);

			}

			return transformedFilenames;
			
		} catch (Exception e) {
			mLogger.error("Error retrieving file : " + e.getMessage(), e);
		}
		
		return null;
	}

	
	/**
	 * For a given content-type and transformation context, imports all the transformed assets from the passed source DCR document/element
	 * Actual requests are delegated off to a ExecutorService, so the transformed files may not exists immediately
	 * 
	 * The sourceDCR with be updated with all the required transformations within the xpath element, for example:<br />
	 * Original DCR Entry:<pre>
	 * &lt;InUseImage&gt;
	 *   &lt;MediaBinFilePath&gt;Interflow/test/Belgacom/bannerTextePhoto_01d.gif&lt;/MediaBinFilePath&gt;
	 *   &lt;MediaBinAssetId&gt;{012B6D9F-5B91-453C-B2DB-114095B5333F}&lt;/MediaBinAssetId&gt;
	 * &lt;/InUseImage&gt;</pre>
	 * After transformations applied:<pre>
	 * &lt;InUseImage&gt;
	 *   &lt;MediaBinFilePath&gt;Interflow/test/Belgacom/bannerTextePhoto_01d.gif&lt;/MediaBinFilePath&gt;
	 *   &lt;MediaBinAssetId&gt;{012B6D9F-5B91-453C-B2DB-114095B5333F}&lt;/MediaBinAssetId&gt;
	 *   &lt;[TransformedName]&gt;[importedAssetPath]&lt;/[TransformedName]&gt;
	 *   &lt;GalleryImage&gt;/assets/imported/content/product-details/_ProductDetail_{language}_InUseImage/bannerTextePhoto_01d.gif_Interflow - JPG_250_true_012B6D9F5B91453CB2DB114095B5333F.jpg&lt;/[TransformedName]&gt;
	 * &lt;/InUseImage&gt;
	 *	</pre>
	 *
	 * @param contentType				The DCR content type, e.g. content/product-details
	 * @param transformationContext		Name the context in which the imported asset is used, e.g. PDF, ProductComponent, etc.
	 * @param sourceDCRRoot				The root element of the sourceDCR that will be searched for the xpaths found in the TransformationsDCR
	 * @param context					the request context
	 * @param locale					the locale object currently is use, used to replace language/culture keywords in the xpaths (for generic multi-lingual xpaths) 
	 *  
	 * @return true on success, false on any error (the sourceDCR might have been part updated)
	 */
	@SuppressWarnings("unchecked")
	public static boolean importSourceAssets(String contentType, String transformationContext, Element sourceDCRRoot, RequestContext context, ILocale locale)
	{

		MediaBinClientPathConfig pathConfig = getPathConfig(context);

		mLogger.debug("Entering importSourceAssets(contentType: " + contentType + ", transformationContext: " + transformationContext + ")");
		
		try {
			
			Document mbTransformations = getTransformations(context);
			
			String searchXPath = "//Source[@ContentType='" + contentType + "' and @TransformationContext='" + transformationContext + "']/Asset";
			List assets = mbTransformations.selectNodes(searchXPath);
		
			if (assets.size() == 0) {
				mLogger.info("No transformations found for ContentType(" + contentType + ") TransformationContext(" + transformationContext + ")");
				return true;
			}
			mLogger.debug(assets.size() + " Assets xpaths found for ContentType(" + contentType + ") TransformationContext(" + transformationContext + ")");
			
			for(Iterator assetsIter = assets.listIterator(); assetsIter.hasNext(); ) {

				Element asset = (Element) assetsIter.next();
				String xpath = asset.attributeValue("XPath");
				if (xpath == null) continue;

				// get all transformations to be applied to assets for this xpath
				List transformations = asset.elements("Transformation");

				mLogger.debug(" >> " + transformations.size() + " transformations found for xpath " + xpath);
				
				// find all elements on the xpath of the sourceDCR
				String localisedXpath = locale.replaceLocale(xpath);
				List dcrElements = sourceDCRRoot.selectNodes(localisedXpath);
				if (dcrElements.size() == 0) {
					if (mLogger.isDebugEnabled()) {
						mLogger.debug("No elements found in sourceDCR for xpath '" + xpath + "' that was localised to '" + localisedXpath + "'. Source DCR: \n" + sourceDCRRoot.asXML());
					}
					continue;
				}

				// Some xPath's cannot be used as a folder name, for these a FileSystemFriendlyPath is provided 
				String fileSystemFriendlyPath = asset.attributeValue("FileSystemFriendlyPath");
				fileSystemFriendlyPath = (fileSystemFriendlyPath == null) ? xpath.replace('/', '_') : fileSystemFriendlyPath.replace('/', '_');

				String assetIdXPath   = asset.attributeValue("AssetIdXPath");
				String assetPathXPath = asset.attributeValue("AssetPathXPath");
				
				if (assetIdXPath == null || assetIdXPath.length() == 0) {
					mLogger.error("No AssetIdXPath value set in MediaBin transformation Asset element : " + asset.asXML());
					continue;
				}
				if (assetPathXPath != null && assetPathXPath.length() == 0) {
					assetPathXPath = null;
				}
				
				mLogger.debug(" >> " + dcrElements.size() + " dcrElements found for localised xpath " + localisedXpath);
				
				for(Iterator transformationsIter = transformations.listIterator(); transformationsIter.hasNext(); ) {

					Element transform = (Element) transformationsIter.next();
					String transformedName = transform.attributeValue("TransformedName");
					String taskName = transform.attributeValue("TaskName");

					mLogger.debug(" >> Found Transformation: " + transformedName + " >> "+ taskName);				

					List params = transform.elements("RuntimeParameter");		
					HashMap txParams = new HashMap();
					StringBuffer nameParams = new StringBuffer();

					for (Iterator itr = params.iterator(); itr.hasNext(); )
					{
						Element param = (Element) itr.next();
						String name = param.attributeValue("ParameterName");
						String value = param.attributeValue("ParameterValue");
						mLogger.debug(" >> >> MediaBin Parameter: " + name + ": " + value);
						txParams.put(name, MediaBinRequestUtils.createParameter(name, value));
						nameParams.append("_").append(value);
					}

					for(Iterator dcrElemIter = dcrElements.listIterator(); dcrElemIter.hasNext(); ) {
						
						Element dcrElement = (Element) dcrElemIter.next();
						String assetId   = dcrElement.valueOf(assetIdXPath);
						String assetPath = (assetPathXPath == null) ? null : dcrElement.valueOf(assetPathXPath);
						
						if (assetId == null || assetId.length() == 0) {
							if (mLogger.isDebugEnabled()) {
								mLogger.debug("Missing " + assetIdXPath + " from '" + dcrElement.getName() + "' element."); // + ((assetPath == null || assetId == null) ? "SourceDCR : " + sourceDCRRoot.asXML() : ""));
							}
							continue;
						}

						mLogger.debug(" >> >> Found Element: " + assetId+ " >> "+ assetPath);
						
						RequestImportAssetResult result = requestImportAsset(
											assetId, assetPath, contentType, fileSystemFriendlyPath, 
											taskName, nameParams.toString(), txParams, transform.attributeValue("ResultExt"), 
											context, pathConfig
										);
						
						// something went wrong, skip this entry
						if (result == null) continue;
						
						Element transformedElem = dcrElement.addElement(transformedName);
						transformedElem.addAttribute(TRANSFORMATION_READY_ATTRIBUTE, (result.transformRequested) ? "no" : "yes");
						transformedElem.setText(result.filePath);

					}
				}
			}

			return true;
			
		} catch (Exception e) {
			mLogger.error("Error retrieving files : " + e.getMessage(), e);
		}
		
		return false;
	}

	
	/**
	 * Get the path config bean, set the context and return it.
	 * 
	 * @param context
	 * @return
	 */
	public static MediaBinClientPathConfig getPathConfig(RequestContext context) { 
	
	    MediaBinClientPathConfig pathConfig = (MediaBinClientPathConfig) ApplicationContextUtils.getBean(PATH_CONFIG_BEAN_ID);
	    if (pathConfig == null) {
	        throw new RuntimeException("Bean id: " + PATH_CONFIG_BEAN_ID + ", not found in the application context, unable to retrieve MediaBinClientPathConfig object.");
	    }
	    
	    if (mLogger.isDebugEnabled()) {
	    	mLogger.debug("MediaBinClient: successfully retrieved object by bean id: " + PATH_CONFIG_BEAN_ID + ", object type: " + pathConfig.getClass().getName());
	    }
	    
	    pathConfig.setContext(context);
	    
	    return pathConfig;
	}
	
	
	/**
	 * Called by importAsset() and importSourceAssets() to do the actual generation of the mediabin request
	 * 
	 * @param assetId
	 * @param assetPath
	 * @param contentType
	 * @param fileSystemFriendlyPath
	 * @param taskName
	 * @param nameParams
	 * @param txParams
	 * @param resultExt
	 * @param context
	 * @return
	 * @throws MediaBinRequestException
	 * @throws RemoteException
	 */
	@SuppressWarnings("unchecked")
	private static RequestImportAssetResult requestImportAsset(
				String assetId, String assetPath, String contentType, String fileSystemFriendlyPath, 
				String taskName, String nameParams, HashMap txParams, String resultExt, 
				RequestContext context, MediaBinClientPathConfig pathConfig
				) 
				throws MediaBinRequestException, RemoteException {
		
		String cleanAssetId = assetId.replaceAll("[{}-]", "");
	
		String relativeFileDir =
			contentType + "/"
			+ fileSystemFriendlyPath + "/"
			+ cleanAssetId + "/";
		
		String filename = null;
		boolean deriveExt = false;
		if (assetPath == null || assetPath.length() == 0) {
			// if no asset path exists, then we don't know the filename 
			if (taskName.equals("(None)")) {
				// if we are not performing a transformation, then we just use a default filename
				filename = new String(DEFAULT_TRANSFORMED_FILENAME);
				// at this point we don't even know the extension, so we will have to derive it when we download the file
				deriveExt = true;
				
				// attempt to find the local cache file								
				File parentDir = pathConfig.getFile(relativeFileDir);
				if (parentDir.exists()) {
					// find any files that matching "original.*" - these are the local cached files
					String[] files = parentDir.list(new DefaultFilenameFilter());
					if (files.length == 1) {
						// we have a winner! Update the filename and filePath
						filename = files[0];
					}
					else if (files.length >= 2) {
						// we have more than 2 or more matching files!
						// this shouldn't really happen - abort
						mLogger.error("Found " + files.length + " matching default files with different extensions for AssetId " + assetId + " in " + parentDir);
						return null;
					}
				}
			}
			else {
				// if we are performing a transformation, then we make a filename from the transformation details
				// because all transformations will result in a file of a certain type, the config file can specify
				// the result extension
				filename = taskName + nameParams + "." + resultExt;
			}
		}
		else {
			// we have an asset path - so we have a filename we can use
			if (taskName.equals("(None)")) {
				// if we are not performing a transformation, then the filename is the same
				// as the assetPath
				filename = assetPath.substring(assetPath.lastIndexOf('/') + 1);
			}
			else {
				// if we are performing a transformation, then the filename is the 
				// assetPath filename, plus the transformation details
				// again we know the extension from the config file
				filename = assetPath.substring(assetPath.lastIndexOf('/') + 1) + "_" + taskName + nameParams + "." + resultExt;
			}
		}
		
		String filePath = relativeFileDir + filename;
		
		// we create the placeholder file as a prefix of the resultFilename
		// this is so that we can glob for the file when deriveExt == true
		// filename = "original"
		// placeholderFilename = ".placeholder.original"
		// therefore glob of "original.*" should only find one file
		String placeholderFilePath =  relativeFileDir + PLACEHOLDER_FILE_PREFIX + filename;

		File file = pathConfig.getFile(filePath);
		File placeholderFile = pathConfig.getFile(placeholderFilePath);
	
		boolean doRequest = false;
		MediaBinRunnableRequest mbr = null;
		if (file.exists()) {
			// file exists, check when it was last modified
			
			mLogger.debug(" >> >> File exists: " + filePath);
			long lastModifiedInMediaBin = MediaBinRefreshCache.getModifiedTime(assetId);
			if (lastModifiedInMediaBin == 0) {
				// we don't know the last time it was modified in MediaBin (or the cache entry has expired)
				// so re-fetch from MediaBin
				mbr = new MediaBinUpToDateRequest(new MediaBinTransformationRequest(assetId, assetPath, taskName, file, placeholderFile, txParams));
				doRequest = true;
				
				// TODO: if deriveExt == true, check to make sure the extension is the same as the current filename
			}
			else {
				mLogger.debug("Compare File Mod : " + file.lastModified() + " < " + lastModifiedInMediaBin);
				if (file.lastModified() < lastModifiedInMediaBin) {
					mbr = new MediaBinTransformationRequest(assetId, assetPath, taskName, file, placeholderFile, txParams);
					doRequest = true;
				}
			}
		}
		else {
			boolean createPlaceholder = true;
			if (placeholderFile.exists()) {
				// placeholder file exists, this means there is probably another thread downloading the asset currently
				
				mLogger.debug("Compare Placeholder Mod : " + (placeholderFile.lastModified() + updateCheckInterval) + " < " + Calendar.getInstance().getTimeInMillis());
				if (placeholderFile.lastModified() + updateCheckInterval < Calendar.getInstance().getTimeInMillis()) {
					// if the file was last modified longer than we normally do checks, 
					// then lets assume the other thread didn't finish the download (server restart, etc)
					// so schedule another download
					mLogger.info("Placeholder file has existed for too long, scheduling another download : " + filePath);
				}
				else {
					createPlaceholder = false;
				}
			}
			
			if (createPlaceholder && createPlaceholderFile(placeholderFile)) {
				doRequest = true;
				mbr = new MediaBinTransformationRequest(assetId, assetPath, taskName, file, placeholderFile, txParams);
				
				if (deriveExt) {
					// if we need to derive the extension of the file, we need to call MediaBin to get the extension of the file
					try {
						MBAsset mbAsset = mbr.getAsset(assetId, assetPath);
						String ext = mbAsset.getMName().substring(mbAsset.getMName().lastIndexOf('.'));
						// update all of the variables relating to the file
						filename = filename + ext;
						filePath = relativeFileDir + filename;
						file = pathConfig.getFile(filePath);
						((MediaBinTransformationRequest)mbr).setLocalFile(file);
						
					} catch (MediaBinRequestException e) {
						mLogger.error("Error deriving file extension : " + e.getMessage(), e);
						doRequest = false;
					}
				}
			}
		}
		
		if (doRequest && mbr != null) {
			mLogger.debug(" >> >> Starting " + mbr.getClass().getCanonicalName() + " : " + assetPath);
			// Give the request to the thread manager to start, avoiding too many connections to mediabin
			pool.execute(mbr);
		}

		return new RequestImportAssetResult(pathConfig.getFileURL(filePath), doRequest);

	}
	
	private static class RequestImportAssetResult {
		
		public String filePath;
		public boolean transformRequested;

		RequestImportAssetResult(String filePath, boolean transformRequested) {
			this.filePath = filePath;
			this.transformRequested = transformRequested;
		}

	}	
	
	/**
	 * Creates a temporary file that is used as a place holder to prevent multiple 
	 * MediaBinRequests from being fired for the same image transformation
	 * 
	 * @param file the file to create
	 * 
	 * @return true if the file was created successfully, false otherwise
	 */
	protected static boolean createPlaceholderFile(File file) {
	    try {
			File parentDir = file.getParentFile();
			if (!parentDir.exists() && !parentDir.mkdirs()) {
				mLogger.error("Unable to create placeholder file, parent directory could not be created. File: " + file.getAbsolutePath());
			}
			else {
				FileOutputStream file_output = new FileOutputStream(file);
				DataOutputStream data_out = new DataOutputStream(file_output);
				byte[] dummy = {'.'};
				data_out.write(dummy);
				file_output.close();				
				return true;
			}
	    }
	    catch (IOException e) {
	    	mLogger.error("IOException writing placeholder file", e);
	    }
	    return false;
	    
	}
	
	private static class DefaultFilenameFilter implements FilenameFilter {
		
		private static String DEFAULT_TRANSFORMED_FILENAME_DOT = DEFAULT_TRANSFORMED_FILENAME + ".";

		public boolean accept(File dir, String name) {
			return name.startsWith(DEFAULT_TRANSFORMED_FILENAME_DOT);
		}

	}
	
	/**
	 * Returns the passed imported asset path as a File object
	 * 
	 * @param context
	 * @param importedRelativePath
	 * @param onlyIfReady	only return the file object if the asset has been downloaded from mediabin
	 * @return	the file object, if onlyIfReady is true and the file does not exist, or it's placeholder file still exists, 
	 *          then null is returned  
	 */
	public static File getImportedAssetFile(RequestContext context, String importedRelativePath, boolean onlyIfReady) {

		MediaBinClientPathConfig pathConfig = getPathConfig(context);
		File asset = pathConfig.getFile(importedRelativePath);
		
		if (onlyIfReady) {
			
			if (!asset.exists()) return null;
			if (asset.length() <= 1) return null;
					
			String filename = asset.getName();
			String placeholderFilename = PLACEHOLDER_FILE_PREFIX + filename;
			
			File placeholder = new File(asset.getParentFile().getPath() + File.separator + placeholderFilename);

			if (placeholder.exists()) return null;
			
			// If this is an download without a name, then we also need to check for ".placeholder.original" (no extension)
			if (filename.startsWith(DEFAULT_TRANSFORMED_FILENAME + ".")) {
				placeholderFilename = PLACEHOLDER_FILE_PREFIX + DEFAULT_TRANSFORMED_FILENAME;
				placeholder = new File(asset.getParentFile().getPath() + File.separator + placeholderFilename);

				if (placeholder.exists()) return null;
			}
			
		}
		
		return asset;
		
	}

	/**
	 * Streams the asset directly from MediaBin to the response object. Setting content type and attachment headers appropriately
	 * This does no transformations (it calls the "(None)" retrieval task)
	 * 
	 * @param assetId		the asset id to stream
	 * @param assetPath		optional, if the assetId doesn't work fall back to this value 
	 * @param response		the response objecto to write the downloaded asset to
	 * 
	 * @throws MediaBinRequestException
	 */
	public static void streamAsset(String assetId, String assetPath, HttpServletResponse response) throws MediaBinRequestException, MediaBinRequestAssetNotFoundException {
		
		streamAsset(assetId, assetPath, "(None)", response);
		
	}
	
	/**
	 * Streams the asset directly from MediaBin to the response object. Setting content type and attachment headers appropriately
	 * This does no transformations (it calls the "(None)" retrieval task)
	 * 
	 * @param assetId		the asset id to stream
	 * @param transformation	the transformation task name
	 * @param assetPath		optional, if the assetId doesn't work fall back to this value 
	 * @param response		the response objecto to write the downloaded asset to
	 * 
	 * @throws MediaBinRequestException
	 */
	public static void streamAsset(String assetId, String assetPath, String transformation, HttpServletResponse response) throws MediaBinRequestException, MediaBinRequestAssetNotFoundException {
		
		MediaBinStreamingTransformationRequest mbr = new MediaBinStreamingTransformationRequest(assetId, assetPath, transformation, null, response);
		mbr.streamAsset();
		
	}
	/**
	 * Streams the asset directly from MediaBin to the response object. Setting content type and attachment headers appropriately
	 * 
	 * @param assetId		the asset id to stream
	 * @param response		the response objecto to write the downloaded asset to
	 * 
	 * @throws MediaBinRequestException
	 */
	public static void streamAsset(String assetId, HttpServletResponse response) throws MediaBinRequestException, MediaBinRequestAssetNotFoundException {
		streamAsset(assetId, null, response);
	}
	
	
}
