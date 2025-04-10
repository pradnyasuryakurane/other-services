package org.egov.usm.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
	
	ACTIVE("ACTIVE"),

    INACTIVE("INACTIVE");

	private String value;

	Status(String value) {
		this.value = value;
	}

	@Override
	@JsonValue
	public String toString() {
		return String.valueOf(value);
	}

	@JsonCreator
	public static Status fromValue(String text) {
		for (Status s : Status.values()) {
			if (String.valueOf(s.value).equalsIgnoreCase(text)) {
				return s;
			}
		}
		return null;
	}
}
