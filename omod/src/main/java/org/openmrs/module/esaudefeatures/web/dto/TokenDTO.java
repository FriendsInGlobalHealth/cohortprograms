package org.openmrs.module.esaudefeatures.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/24/21.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenDTO {
	
	public String token;
	
	public String userID;
	
	public String username;
}
