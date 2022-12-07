package org.openmrs.module.esaudefeatures.web;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.openmrs.api.AdministrationService;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants.OPENCR_PATIENT_UUID_CONCEPT_MAP_GP;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/17/22.
 */
public class Utils {
	
	static final long CONNECT_TIMEOUT = 0;
	
	static final long READ_TIMEOUT = 0;
	
	static final long WRITE_TIMEOUT = 0;
	
	static SimpleDateFormat[] DATE_FORMARTS = { new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
	        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'"),
	        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), };
	
	private static OkHttpClient skipHostnameVerificationClient;
	
	private static OkHttpClient okHttpClient;
	
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
			if (skipHostnameVerificationClient == null) {
				skipHostnameVerificationClient = new OkHttpClient.Builder()
				        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
				        .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
				        .hostnameVerifier(new HostnameVerifier() {
					        
					        @Override
					        public boolean verify(String hostname, SSLSession session) {
						        return true;
					        }
				        }).build();
			}
			return skipHostnameVerificationClient;
		}
		
		if (okHttpClient == null) {
			okHttpClient = new OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
			        .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
			        .build();
		}
		return okHttpClient;
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
	
	public static String formatDate(Date date) {
		return DATE_FORMARTS[1].format(date);
	}
	
	public static String generatePassword() {
		int length = 10;
		
		final char[] lowercase = "abcdefghijklmnopqrstuvwxyz".toCharArray();
		final char[] uppercase = "ABCDEFGJKLMNPRSTUVWXYZ".toCharArray();
		final char[] numbers = "0123456789".toCharArray();
		final char[] allAllowed = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();
		
		//Use cryptographically secure random number generator
		Random random = new SecureRandom();
		
		StringBuilder password = new StringBuilder();
		
		for (int i = 0; i < length - 4; i++) {
			password.append(allAllowed[random.nextInt(allAllowed.length)]);
		}
		
		//Ensure password policy is met by inserting required random chars in random positions
		password.insert(random.nextInt(password.length()), lowercase[random.nextInt(lowercase.length)]);
		password.insert(random.nextInt(password.length()), uppercase[random.nextInt(uppercase.length)]);
		password.insert(random.nextInt(password.length()), numbers[random.nextInt(numbers.length)]);
		
		return password.toString();
	}
	
	public static String getOpenmrsIdentifierTypeUuid(final Identifier identifier, final String identifyTypeConceptMappings) {
		String[] maps = identifyTypeConceptMappings.split(",");
		for (String map : maps) {
			if (identifier.hasType() && identifier.getType().hasCoding()) {
				String[] mapParts = map.split(":");
				for (Coding coding : identifier.getType().getCoding()) {
					if (coding.hasCode() && coding.getCode().equalsIgnoreCase(mapParts[1])) {
						return mapParts[0];
					}
				}
			}
		}
		return null;
	}
	
	public static String getOpenmrsUuidFromOpencrIdentifiers(List<Identifier> identifiers, String opencrPatientUuidCode) {
		for (int i = 0; i < identifiers.size(); i++) {
			Identifier identifier = identifiers.get(i);
			if (identifier.hasType() && identifier.getType().hasCoding()) {
				for (Coding coding : identifier.getType().getCoding()) {
					if (opencrPatientUuidCode.equalsIgnoreCase(coding.getCode())) {
						return identifier.getValue();
					}
				}
			}
		}
		return null;
	}
}
