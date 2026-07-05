package com.pangu.interfaces.web.exception;

import com.pangu.application.community.CommunitySettingsApplicationException;

public final class CommunitySettingsExceptionTranslator {

    private CommunitySettingsExceptionTranslator() {
    }

    public static CommunitySettingsErrorCode translate(CommunitySettingsApplicationException ex) {
        return switch (ex.getReason()) {
            case PARAM_INVALID -> CommunitySettingsErrorCode.PARAM_INVALID;
            case FORBIDDEN -> CommunitySettingsErrorCode.FORBIDDEN;
            case COMMUNITY_NOT_FOUND -> CommunitySettingsErrorCode.COMMUNITY_NOT_FOUND;
            case POLICY_NOT_FOUND -> CommunitySettingsErrorCode.POLICY_NOT_FOUND;
            case REVIEW_NOT_FOUND -> CommunitySettingsErrorCode.REVIEW_NOT_FOUND;
            case REVIEW_INVALID_STATUS -> CommunitySettingsErrorCode.REVIEW_INVALID_STATUS;
        };
    }
}
