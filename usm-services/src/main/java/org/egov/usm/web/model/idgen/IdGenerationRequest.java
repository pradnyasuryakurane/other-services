package org.egov.usm.web.model.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import java.util.List;

/**
 * <h1>IdGenerationRequest</h1>
 * 
 *
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IdGenerationRequest {

	@JsonProperty("RequestInfo")
	private RequestInfo requestInfo;

	private List<IdRequest> idRequests;

}
