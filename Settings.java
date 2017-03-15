/**
 * Settings provides a statically accessible values for settings for 
 * connecting to the MediaBin installation associated with the current LiveSite
 * installation
 */
package com.eu.interflow.livesite.mediabin;

import java.io.IOException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Settings provides a statically accessible values for settings for 
 * connecting to the MediaBin installation associated with the current LiveSite
 * installation
 * 
 * @author brobertson
 */
public class Settings {

//	private static Properties properties;
	private static Log log = LogFactory.getLog(Settings.class);
	
	private static final int defaultHttpTimeout = 10000; // time in milliseconds
	private static final int defaultMaxConnections = 4;
	private static final int defaultUpdateCheckInterval = 3600000; // time in milliseconds
	private static final String defaultTransformationsDCRPath = "/templatedata/system/mediabin-transformations/data/transformations.xml";
	private static final int defaultTransformationRefreshInterval = 3600000; // time in milliseconds
	
	private static String mediaBinURL;
	private static int httpTimeout;
	private static int maxConnections;
	private static String mediaBinLoginDomain;
	private static String mediaBinLoginUsername;  
	private static String mediaBinLoginPassword;
	private static String downloadAuthType;
	private static String downloadUsername;
	private static String downloadPassword;
	private static String transformationsDCRPath;
	private static int updateCheckInterval;
	private static int transformationsRefreshInterval;
	
	static {

		Properties properties = new Properties();
		try {
			properties.load(Settings.class.getClassLoader().getResourceAsStream("com/eu/interflow/livesite/mediabin/mediabin.properties"));
			log.info("Found mediabin.properties : " + Settings.class.getClassLoader().getResource("com/eu/interflow/livesite/mediabin/mediabin.properties"));
			
		} catch (IOException e) {
			log.error("error reading mediabin.properties");
			
		} catch (NullPointerException e) {
			log.error("could not find mediabin.properties");
		}
		
		if (log.isDebugEnabled()) {
			log.debug("Num Prop Elements : " + properties.size());
			Enumeration<?> en = properties.propertyNames();
			try {
				String s = (String) en.nextElement();
				while (s != null) {
					log.debug(" >> " + s + " : " + properties.getProperty(s));
					s = (String) en.nextElement();
				}
			}
			catch(NoSuchElementException e) {
			}
		}
		
		mediaBinURL = properties.getProperty("mediabin-url");

		mediaBinLoginDomain = properties.getProperty("mediabin-login-domain");
		mediaBinLoginUsername = properties.getProperty("mediabin-login-username");
		mediaBinLoginPassword = properties.getProperty("mediabin-login-password");

		transformationsDCRPath = properties.getProperty("mediabin-transformations-dcr-path", defaultTransformationsDCRPath);

		downloadAuthType = properties.getProperty("mediabin-download-auth-type");
		downloadUsername = properties.getProperty("mediabin-download-username");
		downloadPassword = properties.getProperty("mediabin-download-password");

		String strInt = properties.getProperty("mediabin-http-timeout");
		if (strInt == null) {
			httpTimeout = defaultHttpTimeout;
		}
		else {
			try {
				httpTimeout = Integer.parseInt(strInt);
			} catch (NumberFormatException e) {
				log.error("INVALID mediabin-http-timeout value '" + strInt + "' in mediabin.properties, defaulting to " + defaultHttpTimeout);
				httpTimeout = defaultHttpTimeout;
			}
		}

		strInt = properties.getProperty("mediabin-max-connections");
		if (strInt == null) {
			maxConnections = defaultMaxConnections;
		}
		else {
			try {
				maxConnections = Integer.parseInt(strInt);
			} catch (NumberFormatException e) {
				log.error("INVALID mediabin-max-connections value '" + strInt + "' in mediabin.properties, defaulting to " + defaultMaxConnections);
				maxConnections = defaultMaxConnections;
			}
		}

		strInt = properties.getProperty("mediabin-update-check-interval");
		if (strInt == null) {
			updateCheckInterval = defaultUpdateCheckInterval;
		}
		else {
			try {
				updateCheckInterval = Integer.parseInt(strInt);
			} catch (NumberFormatException e) {
				log.error("INVALID mediabin-update-check-interval value '" + strInt + "' in mediabin.properties, defaulting to " + defaultUpdateCheckInterval);
				updateCheckInterval = defaultUpdateCheckInterval;
			}
		}

		strInt = properties.getProperty("mediabin-transformations-refresh-interval");
		if (strInt == null) {
			transformationsRefreshInterval = defaultTransformationRefreshInterval;
		}
		else {
			try {
				transformationsRefreshInterval = Integer.parseInt(strInt);
			} catch (NumberFormatException e) {
				log.error("INVALID mediabin-update-check-interval value '" + strInt + "' in mediabin.properties, defaulting to " + defaultTransformationRefreshInterval);
				transformationsRefreshInterval = defaultTransformationRefreshInterval;
			}
		}
		
		
	}

	/**
	 * @return the url to access the MediaBin webservice
	 */
	public static String getMediaBinURL() {
		return mediaBinURL;
	}

	/**
	 * @return the timeout in milliseconds for the HTTP connection to the MediaBin webserver URL
	 */
	public static int getHttpTimeout() {
		return httpTimeout;
	}

	/**
	 * @return the Windows domain required for login on the mediabin web service
	 * @see getMediaBinLoginUsername()
	 * @see getMediaBinLoginPassword()
	 */
	public static String getMediaBinLoginDomain() {
		return mediaBinLoginDomain;
	}
	
	/**
	 * @return the username required for login on the mediabin web service
	 * @see getMediaBinLoginDomain()
	 * @see getMediaBinLoginPassword()
	 */
	public static String getMediaBinLoginUsername() {
		return mediaBinLoginUsername;
	}
	
	/**
	 * @return the password required for login on the mediabin web service
	 * @see getMediaBinLoginDomain()
	 * @see getMediaBinLoginUsername()
	 */
	public static String getMediaBinLoginPassword() {
		return mediaBinLoginPassword;
	}

	/**
	 * @return the maximum number of threads we will have connecting to MediaBin at any one time
	 */
	public static int getMaxConnections() {
		return maxConnections;
	}

	/**
	 * @return the number of seconds between checks that an asset has been modified in MediaBin
	 */
	public static int getUpdateCheckInterval() {
		return updateCheckInterval;
	}

	/**
	 * @return the path from the workarea to the transformations dcr file in use
	 */
	public static String getTransformationsDCRPath() {
		return transformationsDCRPath;
	}

	/**
	 * @return the number of seconds between refreshes of the transformations DCR
	 */
	public static int getTransformationsRefreshInterval() {
		return transformationsRefreshInterval;
	}
	
	/**
	 * If the webserver on the MediaBin server has authentication setup for the TransferWS
	 * folder that is used to download the tranformed assets, then this 
	 * 
	 * @return "none" for no authentication required, "basic" for basic authentication
	 * @see getDownloadUsername()
	 * @see getDownloadPassword()
	 */
	public static String getDownloadAuthType() {
		return downloadAuthType;
	}

	/**
	 * If the getDownloadAuthType() does not return "none", this is the username
	 * used for authentication 
	 * 
	 * @return the username
	 * @see getDownloadAuthType()
	 * @see getDownloadPassword()
	 */
	public static String getDownloadUsername() {
		return downloadUsername;
	}

	/**
	 * If the getDownloadAuthType() does not return "none", this is the password
	 * used for authentication 
	 * 
	 * @return the password
	 * @see getDownloadAuthType()
	 * @see getDownloadUsername()
	 */
	public static String getDownloadPassword() {
		return downloadPassword;
	}

}
