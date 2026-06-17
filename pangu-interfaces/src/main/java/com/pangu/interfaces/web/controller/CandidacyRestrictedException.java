package com.pangu.interfaces.web.controller;

import java.util.Map;

/**
 * 候选人资格受限特定业务异常
 */
public class CandidacyRestrictedException extends AppException {
    
    private final Map<String, Object> restrictionDetails;

    public CandidacyRestrictedException(ErrorCode errorCode, Map<String, Object> restrictionDetails, String message) {
        super(errorCode, message);
        this.restrictionDetails = restrictionDetails;
    }

    public Map<String, Object> getRestrictionDetails() {
        return restrictionDetails;
    }
}
