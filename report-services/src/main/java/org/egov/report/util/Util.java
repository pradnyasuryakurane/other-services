package org.egov.report.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.report.config.ReportServiceConfiguration;
import org.egov.report.model.AuditDetails;
import org.egov.report.model.DemandDetailAudit;
import org.egov.report.repository.ServiceRepository;
import org.egov.report.user.User;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Util {
    
    @Autowired
    private ObjectMapper mapper;
    
    @Autowired
    private ReportServiceConfiguration config;
    
    @Autowired
    private ServiceRepository repository;
    
    public static final String LOCAL_ZONE_ID = "Asia/Kolkata";
    
    public void validateTenantIdForUserType(String tenantId, RequestInfo requestInfo) {

        String userType = null;
        if(requestInfo.getUserInfo() != null)
        {
            userType = requestInfo.getUserInfo().getType();
        }
        if(Constants.EMPLOYEE_TYPE_CODE.equalsIgnoreCase(userType) && tenantId.split("\\.").length == 1) {
            throw new CustomException("EG_BS_INVALID_TENANTID","Employees cannot search based on state level tenantid");
        }
    }
    
    public JsonNode getJsonValue(PGobject pGobject){
        try {
            if(Objects.isNull(pGobject) || Objects.isNull(pGobject.getValue()))
                return null;
            else
                return mapper.readTree( pGobject.getValue());
        } catch (Exception e) {
            throw new CustomException(Constants.EG_BS_JSON_EXCEPTION_KEY, Constants.EG_BS_JSON_EXCEPTION_MSG);
        }
    }
    
    
    public List<Map<String, Object>> getServiceTypefromMdms(RequestInfo requestInfo, String tenantId) {
		MdmsCriteriaReq mdmsCriteriaReq = getServiceTypeCriteria(requestInfo, tenantId);
		StringBuilder uri = new StringBuilder(config.getMdmsHost()).append(config.getMdmsSearchUrl());
		Object res = repository.fetchResult(uri, mdmsCriteriaReq);
		if (res == null) {
			throw new CustomException("MDMS_ERROR", "Cannot fetch ServiceTypeData from MDMS");
		}
		List<Map<String, Object>> jsonOutput = JsonPath.read(res, "$.MdmsRes.sr-services-master.ServiceType");
		return jsonOutput;
	}
    
	private MdmsCriteriaReq getServiceTypeCriteria(RequestInfo requestInfo, String tenantId) {

		MasterDetail mstrDetail = MasterDetail.builder().name("ServiceType")
				.build();
		ModuleDetail moduleDetail = ModuleDetail.builder().moduleName("sr-services-master")
				.masterDetails(Arrays.asList(mstrDetail)).build();
		MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(Arrays.asList(moduleDetail)).tenantId(tenantId)
				.build();
		return MdmsCriteriaReq.builder().requestInfo(requestInfo).mdmsCriteria(mdmsCriteria).build();
	}

	
	/**
	 * Method to return auditDetails for create/update flows
	 *
	 * @param by
	 * @param isCreate
	 * @return AuditDetails
	 */
	public AuditDetails getAuditDetails(String by, Boolean isCreate) {
		Long time = System.currentTimeMillis();
		if (isCreate)
			return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time)
					.build();
		else
			return AuditDetails.builder().lastModifiedBy(by).lastModifiedTime(time).build();
	}
	
	
	/**
	 * validate time in unix epoch format
	 * 
	 * @param inputUnixTime
	 * @return true or false 
	 */
	public boolean isSameDay(Long inputUnixTime) {
		LocalDate lastModifiedDate = Instant.ofEpochSecond(inputUnixTime/1000)
			      .atZone(ZoneId.of(LOCAL_ZONE_ID))
			      .toLocalDate();
	    return lastModifiedDate.isEqual(LocalDate.now(ZoneId.of(LOCAL_ZONE_ID)));
	}
	
	public Long getEndingDateOfFinancialYear(String endingYear) {

		int inputYear = Integer.parseInt(endingYear);

		LocalDateTime financialYearEndDate = LocalDateTime.of(inputYear, 3, 31, 23, 59, 59);

		return financialYearEndDate.toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	public Long getStartingDateOfFinancialYear(String startingYear) {

		int inputYear = Integer.parseInt(startingYear);

		LocalDateTime financialYearStartDate = LocalDateTime.of(inputYear, 4, 1, 0, 0, 0);

		return financialYearStartDate.toInstant(ZoneOffset.UTC).toEpochMilli();
	}
	
	public void enrichOwnerDetailsInDemandDetailAudit(Map<String, User> userDetailResponse,
			List<DemandDetailAudit> demandDetails) {
        // Early return for invalid input
		if (isNullOrEmpty(userDetailResponse, demandDetails))
			return;

        // Process each audit object
		demandDetails.forEach(audit -> {
			if (audit == null) {
				log.info("Encountered null DemandDetailAudit object, skipping...");
				return;
			}

			log.info("Processing DemandDetailAudit with createdBy UUID: {}, lastModifiedBy UUID: {}",
					audit.getDemandDetailCreatedby(), audit.getDemandDetailLastmodifiedby());

        // Enrich fields for createdBy and lastModifiedBy
			audit.setDemandDetailCreatedby(
					enrichField(audit.getDemandDetailCreatedby(), userDetailResponse, "createdBy"));
			audit.setDemandDetailLastmodifiedby(
					enrichField(audit.getDemandDetailLastmodifiedby(), userDetailResponse, "lastModifiedBy"));
		});
	}

	private boolean isNullOrEmpty(Map<String, User> userDetailResponse, List<DemandDetailAudit> demandDetails) {
		if (userDetailResponse == null || userDetailResponse.isEmpty()) {
			log.info("UserDetailResponse map is null or contains no users");
			return true;
		}

		if (demandDetails == null || demandDetails.isEmpty()) {
			log.info("DemandDetails list is null or empty");
			return true;
		}

		return false;
	}

	private String enrichField(String uuid, Map<String, User> userDetailResponse, String fieldName) {
		if (uuid == null) {
			log.info("{} UUID is null, setting space", capitalize(fieldName));
			return " ";
		}

		User user = userDetailResponse.get(uuid);
		if (user != null) {
			log.info("Enriched {} UUID {} with user name: {}", fieldName, uuid, user.getName());
			return user.getName();
		} else {
			log.info("No user found for {} UUID: {}, setting space", fieldName, uuid);
			return " "; // Set space if user not found
		}
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty())
			return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
