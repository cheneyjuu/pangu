package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.Size;

public record UpdateCommunityOrganizationRequest(
        @Size(max = 128) String tenantName,
        @Size(max = 16) String provinceCode,
        @Size(max = 64) String provinceName,
        @Size(max = 16) String cityCode,
        @Size(max = 64) String cityName,
        @Size(max = 16) String districtCode,
        @Size(max = 64) String districtName,
        @Size(max = 32) String streetCode,
        @Size(max = 64) String streetName,
        @Size(max = 32) String communityCode,
        @Size(max = 64) String communityName,
        @Size(max = 256) String address,
        Boolean ownersAssemblyEstablished,
        Boolean committeeEstablished,
        @Size(max = 128) String currentCommitteeTermName,
        @Size(max = 64) String transitionOrgType,
        @Size(max = 64) String transitionOrgStatus
) {
    public CommunitySettingsCommands.Organization toCommand() {
        return new CommunitySettingsCommands.Organization(
                tenantName, provinceCode, provinceName, cityCode, cityName,
                districtCode, districtName, streetCode, streetName,
                communityCode, communityName, address, ownersAssemblyEstablished,
                committeeEstablished, currentCommitteeTermName, transitionOrgType, transitionOrgStatus);
    }
}
