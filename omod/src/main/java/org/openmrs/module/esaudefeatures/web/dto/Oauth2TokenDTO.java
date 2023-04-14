package org.openmrs.module.esaudefeatures.web.dto;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/6/23.
 */
public class Oauth2TokenDTO {
	
	public String access_token;
	
	public String id_token;
	
	public String token_type;
	
	public Integer expires_in;
	
	public Integer error;
	
	public String error_description;
}
