package com.eu.interflow.livesite.mediabin;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mediabin.www.MBParameter;
import com.mediabin.www.MBParameterElement;
import com.mediabin.www.MBParameterType;
import com.mediabin.www.MBPrimitive;
import com.mediabin.www.MBRunTimeParameter;
import com.mediabin.www.MBRunTimeParameterFlags;
import com.mediabin.www.MBRunTimePrimitive;
import com.mediabin.www.MBTask;
import com.mediabin.www.MediaBinServerSoap;

public class MediaBinRequestUtils {
	
	private static Log mLogger = LogFactory.getLog(MediaBinRequestUtils.class);
	
	/**
	 * Takes a RuntimeParameter name and value, and if the name is recognised,
	 * returns an object representation of the value. If the value cannot be 
	 * made into a valid object (e.g. text into a number) null is returned
	 * 
	 * @param name   name of runtime parameter
	 * @param value  value of runtime parameter
	 * 
	 * @return the object representing the value of the runtime parameter, or null if no parameter known or the value is invalid
	 */
	protected static Object createParameter(String name, String value)
	{
		if (name.equals("Image Size/Image Sizer Parameters/Output Width") ||
			name.equals("Image Size/Image Sizer Parameters/Output Height") ||
			name.equals("Image Size/Image Sizer Parameters/Resolution") ||
			name.equals("Rotator/Rotation in degrees/Rotation in degrees")) {
			try {
				return new Double(value);
			} catch (NumberFormatException e) {
				mLogger.error("Transformation Runtime Parameter '" + name + "' has value not interpretable as a number : " + value, e);
			}
		} else if (name.equals("Padder/Pad Primitive Parameter/Width") ||
					name.equals("Padder/Pad Primitive Parameter/Height") ||
					name.equals("JPEG Encoder/JPEG Quality/JPEG Quality")) {
			try {
				return new Integer(value);
			} catch (NumberFormatException e) {
				mLogger.error("Transformation Runtime Parameter '" + name + "' has value not interpretable as a number : " + value, e);
			}
		} else if (name.equals("Image Size/Image Sizer Parameters/Constrain Proportions")) {
			return new Boolean(value);
		} else {
			mLogger.warn("Transformation Runtime Parameter '" + name + "' unknown");
		}
		
		return null;
	}
		
	
    @SuppressWarnings("unchecked")
	public static MBRunTimePrimitive[] convertRTParameters(MediaBinServerSoap mbServer, String taskId, HashMap importCtx)
    throws RemoteException
	{
	    if(importCtx == null || importCtx.size() == 0)
	        return null;
	    MBTask theTask = mbServer.getTask(taskId);
	    if(theTask.getMPrimitives() == null)
	        return null;
	    MBPrimitive prims[] = theTask.getMPrimitives();
	    if(prims.length == 0)
	        return null;
	    List rtPrims = new ArrayList();
	    for(int j = 0; j < prims.length; j++)
	    {
	        if(prims[j].getMParameters() == null)
	            continue;
	        MBParameter params[] = prims[j].getMParameters();
	        if(params == null)
	            continue;
	        List rtParams = new ArrayList();
	        for(int k = 0; k < params.length; k++)
	        {
	            MBParameter param = params[k];
	            if(param.getMType() == MBParameterType.Metadata || param.getMElements() == null)
	                continue;
	            Object elements[] = param.getMElements();
	            List rtElements = new ArrayList();
	            for(int l = 0; l < elements.length; l++)
	            {
	                Object el = elements[l];
	                if(!(el instanceof MBParameterElement))
	                    continue;
	                MBParameterElement pe = (MBParameterElement)el;
	                MBRunTimeParameterFlags flag = pe.getMFlag();
	                Object rtValue = pe.getMValue();
	                if(flag != MBRunTimeParameterFlags.RTPNone)
	                {
	                    String peName = pe.getMName();
	                    Object value = importCtx.get(prims[j].getMName() + "/" + param.getMName() + "/" + peName);
	                    if(value != null)
	                    {
	                        rtValue = value;
	                    }
	                }
	                if(pe.getMFlag() != MBRunTimeParameterFlags.RTPNone || param.getMType() == MBParameterType.Compound)
	                {
	                    MBParameterElement rtEl = new MBParameterElement();
	                    rtEl.setMName(pe.getMName());
	                    rtEl.setMFlag(flag);
	                    rtEl.setMValue(rtValue);
	                    rtElements.add(rtEl);
	                }
	            }
	
	            if(!rtElements.isEmpty())
	            {
	                MBRunTimeParameter rtParam = new MBRunTimeParameter();
	                rtParam.setMID(param.getMID());
	                rtParam.setMElements(rtElements.toArray());
	                rtParams.add(rtParam);
	            }
	        }
	
	        if(!rtParams.isEmpty())
	        {
	            MBRunTimePrimitive rtPrim = new MBRunTimePrimitive();
	            rtPrim.setMID(prims[j].getMID());
	            rtPrim.setMParameters((MBRunTimeParameter[])rtParams.toArray(new MBRunTimeParameter[0]));
	            rtPrims.add(rtPrim);
	        }
	    }

	    return (MBRunTimePrimitive[])(MBRunTimePrimitive[])rtPrims.toArray(new MBRunTimePrimitive[0]);
	}
	
	
}
