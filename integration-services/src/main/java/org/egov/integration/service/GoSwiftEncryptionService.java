package org.egov.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import org.egov.common.contract.request.RequestInfo;
import org.egov.encryption.web.contract.EncReqObject;
import org.egov.encryption.web.contract.EncryptionRequest;
import org.egov.integration.config.IntegrationConfiguration;
import org.egov.integration.model.otp.Otp;
import org.egov.integration.model.otp.OtpRequest;
import org.egov.integration.model.otp.OtpRequestType;
import org.egov.integration.model.user.UserSearchRequest;
import org.egov.integration.repository.ServiceRepository;
import org.egov.integration.validator.GoSwiftEncryptionValidator;
import org.egov.integration.web.model.GoSwiftInput;
import org.egov.integration.web.model.GoSwiftLoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@Slf4j
public class GoSwiftEncryptionService {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private IntegrationConfiguration configuration;

    @Autowired
    private ObjectMapper objectMapper;

	@Autowired
	private GoSwiftEncryptionValidator goSwiftEncryptionValidator;

	/**
	 *
	 * @param goSwiftInput
	 * @param requestInfo 
	 * @return
	 * To Encrypt the data provided by GoSwift in a Single Encrypted String
	 */
    public String encrypt(GoSwiftInput goSwiftInput, RequestInfo requestInfo){

    	goSwiftEncryptionValidator.validateEncodedInput(goSwiftInput,requestInfo);
        EncryptionRequest encryptionRequest = getEncryptionRequest(goSwiftInput);
        StringBuilder encryptionURL = getEncryptionURL();
        Object response=serviceRepository.fetchResultString(encryptionURL,encryptionRequest);
        List<String> responseList=new ArrayList<>();
		try {
			responseList = objectMapper.readValue(response.toString(), new TypeReference<List<String>>() {});
			 return responseList.get(0);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
       
    }

    private StringBuilder getEncryptionURL() {
        StringBuilder encryptionURL = new StringBuilder();
        encryptionURL.append(configuration.getEncHost());
        encryptionURL.append(configuration.getEncEncryptEndpoint());
        return encryptionURL;
    }

    private EncryptionRequest getEncryptionRequest(GoSwiftInput goSwiftInput) {
        try {
            String jsonString= objectMapper.writeValueAsString(goSwiftInput);
            EncReqObject encReqObject = EncReqObject.builder()
                    .tenantId(goSwiftInput.getTenantId())
                    .type("Normal")
                    .value(jsonString).build();
            List<EncReqObject> encReqObjects = new ArrayList<>();
            encReqObjects.add(encReqObject);
            EncryptionRequest encryptionRequest = EncryptionRequest.builder()
                    .encryptionRequests(encReqObjects).build();
            return encryptionRequest;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

	/**
	 *
	 * @param code
	 * @param requestInfo 
	 * @return
	 * To Decrypt the Encrypted String Passed by Go Swift
	 * and send otp to the corresponding user mobile number
	 */
	public GoSwiftLoginResponse login(String code, RequestInfo requestInfo) {
		//Decryption
		JsonNode decodedResponse = getDecodedResponse(code);
		log.info("@Class: GoSwiftEncryptionService @method:login @message:Received Decoded Response - {} ",decodedResponse);
		//Response Validate
		goSwiftEncryptionValidator.validateDecodedResponse(decodedResponse,requestInfo);
		//User Search
		Boolean isUserRegistered=isUserRegistered(decodedResponse);
		log.info("@Class: GoSwiftEncryptionService @method:login @message:Received Response From User Search- Is User registered : - {} ",isUserRegistered);
		//User Login or Registration
		OtpRequest otpRequest;
		if(isUserRegistered) {
			otpRequest= getOTPRequestForLogin(decodedResponse);
			log.info("@Class: GoSwiftEncryptionService @method:login @message:Received OTP Registration Request : - {} ",otpRequest);
		}
		else {
			otpRequest= getOTPRequestForRegistration(decodedResponse);
			log.info("@Class: GoSwiftEncryptionService @method:login @message:Received OTP Login Request : - {} ",otpRequest);

		}
		Map<String,Object> otpResponse= (Map<String, Object>) sendOTPToUser(otpRequest);
		GoSwiftLoginResponse response=generateResponse(otpResponse,isUserRegistered,decodedResponse);
		log.info("@Class: GoSwiftEncryptionService @method:login @message:OTP Sent to user : - {} ",otpResponse);

		return response;
	}

	private GoSwiftLoginResponse generateResponse(Map<String, Object> otpResponse, Boolean isUserRegistered, JsonNode decodedResponse) {
		decodedResponse = removeSecurityToken(decodedResponse);

		GoSwiftLoginResponse goSwiftLoginResponse= GoSwiftLoginResponse.builder()
				.isSuccessful((Boolean) otpResponse.get("isSuccessful"))
				.username(decodedResponse.path("mobileNo").asText())
				.type(isUserRegistered?"login":"registration")
				.goSwiftDetails(decodedResponse)
				.build();
		if(!isUserRegistered) {
			goSwiftLoginResponse.setTenantId(decodedResponse.path("tenantId").asText());
			goSwiftLoginResponse.setName(decodedResponse.path("name").asText());
		}
		return goSwiftLoginResponse;
	}

	private JsonNode removeSecurityToken(JsonNode decodedResponse) {
		if (decodedResponse.isObject()) {
            ObjectNode objectNode = (ObjectNode) decodedResponse;
            objectNode.remove("securityToken");
            decodedResponse=objectNode;
        }
		return decodedResponse;
	}

	private Object sendOTPToUser(OtpRequest otpRequest) {
		StringBuilder otpSendURL = getOTPSendURL();
		Object otpResponse = serviceRepository.fetchResult(otpSendURL, otpRequest);
		return otpResponse;
	}

	private StringBuilder getOTPSendURL() {
		StringBuilder otpURL= new StringBuilder();
		otpURL.append(configuration.getUserOTPHost());
		otpURL.append(configuration.getUserOTPEndpoint());
		return otpURL;
	}

	private OtpRequest getOTPRequestForRegistration(JsonNode decodedResponse) {
		Otp otp= Otp.builder()
				.mobileNumber(decodedResponse.path("mobileNo").asText())
				.name(decodedResponse.path("name").asText())
				.permanentCity(decodedResponse.path("tenantId").asText())
				.tenantId("od")
				.type("register").build();
		
		OtpRequest otpRequest= OtpRequest.builder().otp(otp).build();
		return otpRequest;
	}

	private OtpRequest getOTPRequestForLogin(JsonNode decodedResponse) {
		Otp otp= Otp.builder()
				.mobileNumber(decodedResponse.path("mobileNo").asText())
				.userType("CITIZEN")
				.tenantId("od")
				.type("login").build();
		
		OtpRequest otpRequest= OtpRequest.builder().otp(otp).build();
		return otpRequest;
	}

	private Boolean isUserRegistered(JsonNode decodedResponse) {
		UserSearchRequest userSearchRequest = UserSearchRequest.builder()
				.userName(decodedResponse.path("mobileNo").asText())
				.tenantId(decodedResponse.path("tenantId").asText())
				.userType("CITIZEN").build();
		
		StringBuilder userSearchURL= getUserSearchURL();
		LinkedHashMap<String, List<Object>> response=(LinkedHashMap<String, List<Object>>) serviceRepository.fetchResult(userSearchURL,userSearchRequest);
		if(response.containsKey("user")) {
			List<Object> users = response.get("user");
			if(CollectionUtils.isEmpty(users)) {
				return false;
			}
			else { 
				return true;
			}
		}
		return false;
		
		
	}

	private StringBuilder getUserSearchURL() {
		StringBuilder userSearchURL= new StringBuilder();
		userSearchURL.append(configuration.getUserHost());
		userSearchURL.append(configuration.getUserSearchEndpoint());
		return userSearchURL;
	}

	private JsonNode getDecodedResponse(String code) {
		StringBuilder decryptionURL=getDecryptionURL();
		String response=serviceRepository.fetchResultObject(decryptionURL,code);
		JsonNode responseNode =null;
		try {
			responseNode = objectMapper.readTree(response);
		} catch (JsonProcessingException e) {	
			e.printStackTrace();
		}
		return responseNode;
	}

	private StringBuilder getDecryptionURL() {
		
		StringBuilder decryptionURL = new StringBuilder();
        decryptionURL.append(configuration.getEncHost());
        decryptionURL.append(configuration.getEncDecryptEndpoint());
        return decryptionURL;
	}
}
