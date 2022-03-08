package org.openmrs.module.esaudefeatures.web;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
public class Utils {
	
	static SimpleDateFormat[] DATE_FORMARTS = { new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
	        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'"),
	        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), };
	
	/**
	 * Copied from org.apache.solr.common.util.Base64 class to avoid dependency issues between 1.x
	 * instances as opposed to 2.x instances.
	 * 
	 * @param a
	 * @param offset
	 * @param len
	 * @return
	 */
	public static String byteArrayToBase64(byte[] a, int offset, int len) {
		int aLen = len;
		int numFullGroups = aLen / 3;
		int numBytesInPartialGroup = aLen - 3 * numFullGroups;
		int resultLen = 4 * ((aLen + 2) / 3);
		StringBuffer result = new StringBuffer(resultLen);
		char[] intToAlpha = { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
		        'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
		        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
		        '9', '+', '/' };
		
		// Translate all full groups from byte array elements to Base64
		int inCursor = offset;
		for (int i = 0; i < numFullGroups; i++) {
			int byte0 = a[inCursor++] & 0xff;
			int byte1 = a[inCursor++] & 0xff;
			int byte2 = a[inCursor++] & 0xff;
			result.append(intToAlpha[byte0 >> 2]);
			result.append(intToAlpha[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
			result.append(intToAlpha[(byte1 << 2) & 0x3f | (byte2 >> 6)]);
			result.append(intToAlpha[byte2 & 0x3f]);
		}
		
		// Translate partial group if present
		if (numBytesInPartialGroup != 0) {
			int byte0 = a[inCursor++] & 0xff;
			result.append(intToAlpha[byte0 >> 2]);
			if (numBytesInPartialGroup == 1) {
				result.append(intToAlpha[(byte0 << 4) & 0x3f]);
				result.append("==");
			} else {
				// assert numBytesInPartialGroup == 2;
				int byte1 = a[inCursor++] & 0xff;
				result.append(intToAlpha[(byte0 << 4) & 0x3f | (byte1 >> 4)]);
				result.append(intToAlpha[(byte1 << 2) & 0x3f]);
				result.append('=');
			}
		}
		return result.toString();
	}
	
	public static OkHttpClient createOkHttpClient(final boolean skipHostnameVerification) throws Exception {
		if (skipHostnameVerification) {
			return new OkHttpClient.Builder().hostnameVerifier(new HostnameVerifier() {
				
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			}).build();
		}
		return new OkHttpClient();
	}
	
	public static Request createBasicAuthGetRequest(final String url, final String username, final String password,
	        final String[] pathSegments, final Map<String, String> queryParameters) {
		assert url != null && username != null && password != null;
		HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
		if (pathSegments != null) {
			for (String pathSegment : pathSegments) {
				if (StringUtils.isNotEmpty(pathSegment)) {
					if (pathSegment.contains("/")) {
						// segments
						urlBuilder.addPathSegments(pathSegment);
					} else {
						urlBuilder.addPathSegment(pathSegment);
					}
				}
			}
		}
		
		if (queryParameters != null && queryParameters.size() > 0) {
			for (Map.Entry<String, String> queryParameter : queryParameters.entrySet()) {
				urlBuilder.addQueryParameter(queryParameter.getKey(), queryParameter.getValue());
			}
		}
		
		String credentials = Credentials.basic(username, password);
		return new Request.Builder().url(urlBuilder.build()).get().header("Content-Length", "0")
		        .header("Authorization", credentials).header("Accept", "application/json")
		        .header("Content-Type", "application/json").build();
	}
	
	public static Date parseDateString(String toParse) {
		if (toParse == null)
			return null;
		
		Date ret = null;
		for (int i = 0; i < DATE_FORMARTS.length; i++) {
			try {
				ret = DATE_FORMARTS[i].parse(toParse);
				break;
			}
			catch (ParseException e) {
				// Do nothing because what can we do?
			}
		}
		return ret;
	}
}
