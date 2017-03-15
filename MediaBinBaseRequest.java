/**
 * 
 */
package com.eu.interflow.livesite.mediabin;

import java.rmi.RemoteException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mediabin.www.MBAsset;
import com.mediabin.www.MBContainer;
import com.mediabin.www.MediaBinServer;
import com.mediabin.www.MediaBinServerLocator;
import com.mediabin.www.MediaBinServerSoap;

/**
 * @author gjones
 *
 */
public class MediaBinBaseRequest {
	
	private static Log mLogger = LogFactory.getLog(MediaBinBaseRequest.class);

	protected MediaBinServerSoap server = null;

	/**
	 * 
	 * @throws MediaBinRequestException when we cannot connect to the MediaBin server
	 * 
	 */
	public MediaBinBaseRequest() throws MediaBinRequestException
	{
		try {
			this.server = ConnectToMediaBin(
						Settings.getMediaBinURL(), 
						Settings.getMediaBinLoginDomain(),
						Settings.getMediaBinLoginUsername(),
						Settings.getMediaBinLoginPassword(),
						Settings.getHttpTimeout()
			);
		} catch (Exception e) {
			throw new MediaBinRequestException("Unable to connect to MediaBin", e);
		}
	}

	/**
	 * Returns a MediaBin asset object for the passed assetId, falling back to the assetPath if 
	 * the assetId is invalid or the asset it represents has been deleted
	 *  
	 * @param assetId		the id of the asset to retrieve
	 * @param assetPath		optional, if the assetId doesn't work fall back to this value 
	 * @return the asset object from MediaBin
	 * @throws RemoteException
	 * @throws MediaBinRequestException when the asset cannot be found
	 */
	public MBAsset getAsset(String assetId, String assetPath) throws RemoteException, MediaBinRequestAssetNotFoundException {
		MBAsset asset = null;
		
		if (assetId != null) {
			// if we have been supplied an assetId, check to see if we can find it
			try {
				asset = server.getAsset(assetId);
				if (asset != null && asset.isIsDeleted()) {
					// if this asset has been deleted, then it is no use to us, so fallback to the assetPath
					asset = null;
					mLogger.info("Unable to use asset '" + assetId + "' as it has been deleted");
				}
			}
			catch (Exception e) {
				// catch any exceptions, so that we can try the assetPath below
				mLogger.debug("Unable to get asset : " + assetId);
			}
		}
		
		if (asset == null && assetPath != null && assetPath.length() > 0) {
			// we either don't have an assetId, or it is invalid so try the assetPath 
			mLogger.info("Asset not found for assetId '" + assetId + "', trying assetPath : " + assetPath);

			MBContainer rootContainer = server.getRootContainer();
			String rootContainerId = rootContainer.getMID();
			
			int lastSlashPos = assetPath.lastIndexOf("/");
			if (lastSlashPos > 0) {
				String containerPath = assetPath.substring(0, lastSlashPos);
				containerPath = containerPath.replace("/".charAt(0), "\\".charAt(0));
				MBContainer container = server.getContainerByPath(containerPath, rootContainerId, false); // * -> null :(
				if (container == null) 	throw new MediaBinRequestAssetNotFoundException("Parent container not found for asset path");
	
				String assetName = assetPath.substring(lastSlashPos + 1);
				asset = server.getAssetByName(assetName, container.getMID());
				if (asset != null && asset.isIsDeleted()) {
					// if this asset has been deleted, then it is no use to us
					asset = null;
					mLogger.info("Unable to use asset '" + assetPath + "' as it has been deleted");
				}
			}
		}

		if (asset == null) {
			// still no asset? then get out of here...
			throw new MediaBinRequestAssetNotFoundException("Asset not found for assetId '" + assetId + "', assetPath : " + assetPath);
		}
		
		return asset;
		
	}
	
	//A MediaBinServerSoap object only has to be created once
	private static MediaBinServerSoap ConnectToMediaBin( String strURL, String strDomain, String strUsername, String strPassword, int intHttpTimeout ) throws Exception
	{
		//This creates the service
		MediaBinServer myService = new MediaBinServerLocator();
		//Two ways to get the Java stub to the service...
		MediaBinServerSoap myServiceStub;
		
		if( strURL.equals("") )
		{
			//...using the URL specified in the WSDL
			myServiceStub = myService.getMediaBinServerSoap();
		}
		else
		{
			//...or we can specify the URL of the web service
			java.net.URL serviceURL = new java.net.URL( strURL );
			myServiceStub = myService.getMediaBinServerSoap( serviceURL );
		}

		//We need to specify the username\password thru the Stub and
		//use basic authentication since we can't use Windows Integrated
//		org.apache.axis.client.Stub t = (org.apache.axis.client.Stub) myServiceStub;
		String strUser = "";

		if( strDomain.equals("") )
			strUser = strUsername;
		else
			strUser = strDomain + "\\" + strUsername;
		
		((org.apache.axis.client.Stub) myServiceStub).setUsername( strUser );
		((org.apache.axis.client.Stub) myServiceStub).setPassword( strPassword );		
		((org.apache.axis.client.Stub) myServiceStub).setTimeout( intHttpTimeout );		

		//Now we can make calls into the web service using the Java stub		
		return myServiceStub;
	}

}
