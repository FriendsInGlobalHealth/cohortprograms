package org.openmrs.module.esaudefeatures.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 12/2/21.
 */
public class FhirBundleHttpMessageConverter extends AbstractHttpMessageConverter<Bundle> {
	
	private IParser parser = FhirContext.forR4().newJsonParser();
	
	public FhirBundleHttpMessageConverter() {
		super(MediaType.APPLICATION_JSON);
	}
	
	@Override
	protected boolean supports(Class<?> clazz) {
		return Bundle.class.isAssignableFrom(clazz);
	}
	
	@Override
	protected Bundle readInternal(Class<? extends Bundle> clazz, HttpInputMessage inputMessage) throws IOException,
	        HttpMessageNotReadableException {
		return parser.parseResource(clazz, IOUtils.toString(inputMessage.getBody()));
	}
	
	@Override
	protected void writeInternal(Bundle bundle, HttpOutputMessage outputMessage) throws IOException,
	        HttpMessageNotWritableException {
		outputMessage.getBody().write(parser.encodeResourceToString(bundle).getBytes());
	}
}
