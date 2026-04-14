package com.saparate.pc.repository;

import tools.jackson.databind.JsonNode;
import com.rate.commons.RateUtils;
import com.saparate.pc.entity.CBCArtifact;
import com.saparate.pc.entity.CBCArtifactVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component

public class CBCMysqlDAO {

    @Autowired
    private NamedParameterJdbcTemplate namedParamJdbcTemplate;

    @Autowired
    private RateUtils rateUtils;

    public List<CBCArtifactVersion> getCBCArtifactVersions(String tenantName, long projectId, long cbcArtifactId) {
        String query = "select cbcArtVer.*, cbcArtVerUs.us_id, cbcArtVerUs.ext_us_id, cbcArtVerUs.proj_us_id from cbc_artifact_version as cbcArtVer " +
                "left outer join cbc_artifact_user_story as cbcArtVerUs on (cbcArtVer.ro_artifact_id = cbcArtVerUs.ro_cbc_artifact_id and cbcArtVer.ro_version_id=cbcArtVerUs.ro_cbc_artifact_version_id and cbcArtVerUs.project_id = :projId) " +
                "where cbcArtVer.tenant_name = :tenantName and cbcArtVer.ro_artifact_id = :cbcArtifactId order by cbcArtVer.last_modified_date desc";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tenantName", tenantName);
        parameters.addValue("cbcArtifactId", cbcArtifactId);
        parameters.addValue("projId", projectId);
        List<CBCArtifactVersion> cbcPkgVersionList = this.namedParamJdbcTemplate.query(query, parameters, this::prepareCBCArtifactVersion);
        return cbcPkgVersionList;
    }

    public List<CBCArtifact> getCbcArtifactWithVersions(String tenantName, List<Long> roCBCArtVersionIds) {
        String query = "select cbcArt.*, cbcArtVer.* from cbc_artifact as cbcArt join cbc_artifact_version as cbcArtVer on(cbcArt.ro_id = cbcArtVer.ro_artifact_id) where cbcArtVer.tenant_name = :tenantName and  cbcArtVer.ro_version_id In (:roVersionId); ";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tenantName", tenantName);
        parameters.addValue("roVersionId", roCBCArtVersionIds);
        List<CBCArtifact> cbcArtifactList = this.namedParamJdbcTemplate.query(query, parameters, this::prepareCbcArtifacts);
        return cbcArtifactList;
    }
    private CBCArtifact prepareCbcArtifacts(ResultSet rs, int i) throws SQLException {
        CBCArtifact cbcArtifact = new CBCArtifact();
        cbcArtifact.setRoId(rs.getByte("cbcArt.ro_id"));
        cbcArtifact.setName(rs.getString("cbcArt.name"));
        cbcArtifact.setEnvId(rs.getLong("cbcArt.env_id"));
        cbcArtifact.setProjectId(rs.getLong("cbcArt.project_id"));
        cbcArtifact.setDeleted(rs.getBoolean("cbcArt.is_deleted"));
        cbcArtifact.setTenantName(rs.getString("cbcArt.tenant_name"));
        cbcArtifact.setCreatedBy(rs.getString("cbcArt.created_by"));
        cbcArtifact.setLastModifiedBy(rs.getString("cbcArt.last_modified_by"));
        cbcArtifact.setCreationDate(rs.getTimestamp("cbcArt.creation_date"));
        cbcArtifact.setLastModifiedDate(rs.getTimestamp("cbcArt.last_modified_date"));

        CBCArtifactVersion cbcArtifactVersion = new CBCArtifactVersion();
        cbcArtifactVersion.setRoVersionId(rs.getLong("cbcArtVer.ro_version_id"));
        cbcArtifactVersion.setProjectId(rs.getLong("cbcArtVer.project_id"));
        cbcArtifactVersion.setRoArtifactId(rs.getLong("cbcArtVer.ro_artifact_id"));
        cbcArtifactVersion.setVersion(rs.getString("cbcArtVer.version"));
        cbcArtifactVersion.setUuid(rs.getString("cbcArtVer.uuid"));
        cbcArtifactVersion.setElementId(rs.getString("cbcArtVer.element_id"));
        cbcArtifactVersion.setStatus(rs.getString("cbcArtVer.status"));
        cbcArtifactVersion.setDescription(rs.getString("cbcArtVer.description"));
        cbcArtifactVersion.setDetailedDescription(rs.getString("cbcArtVer.detailed_description"));
        cbcArtifactVersion.setDueDate(rs.getTimestamp("cbcArtVer.due_date"));
        cbcArtifactVersion.setStartDate(rs.getTimestamp("cbcArtVer.start_date"));
        cbcArtifactVersion.setTimeStamp(rs.getTimestamp("cbcArtVer.time_stamp"));
        cbcArtifactVersion.setAssigneeUUID(rs.getString("cbcArtVer.assigneeuuid"));
        cbcArtifactVersion.setTitle(rs.getString("cbcArtVer.title"));
        cbcArtifactVersion.setParent(rs.getString("cbcArtVer.parent"));
        cbcArtifactVersion.setActualParent(rs.getString("cbcArtVer.actual_parent"));
        cbcArtifactVersion.setActivityGroupId(rs.getString("cbcArtVer.activity_group_id"));
        cbcArtifactVersion.setGoLiveRelevantCode(rs.getString("cbcArtVer.go_live_relevant_code"));
        cbcArtifactVersion.setMilestone(rs.getBoolean("cbcArtVer.milestone"));
        cbcArtifactVersion.setPhase(rs.getString("cbcArtVer.phase"));
        cbcArtifactVersion.setSequence(rs.getInt("cbcArtVer.sequence"));
        cbcArtifactVersion.setType(rs.getString("cbcArtVer.type"));
        cbcArtifactVersion.setBusinessArea(rs.getString("cbcArtVer.business_area"));
        cbcArtifactVersion.setTenantName(rs.getString("cbcArtVer.tenant_name"));
        cbcArtifactVersion.setCreatedBy(rs.getString("cbcArtVer.created_by"));
        cbcArtifactVersion.setLastModifiedBy(rs.getString("cbcArtVer.last_modified_by"));
        cbcArtifactVersion.setCreationDate(rs.getTimestamp("cbcArtVer.creation_date"));
        cbcArtifactVersion.setLastModifiedDate(rs.getTimestamp("cbcArtVer.last_modified_date"));

        cbcArtifact.setCbcArtifactVersion(cbcArtifactVersion);
        return cbcArtifact;
    }

    private CBCArtifactVersion prepareCBCArtifactVersion(ResultSet rs, int i) throws SQLException {
        CBCArtifactVersion cbcArtifactVersion = new CBCArtifactVersion();
        cbcArtifactVersion.setRoVersionId(rs.getLong("cbcArtVer.ro_version_id"));
        cbcArtifactVersion.setProjectId(rs.getLong("cbcArtVer.project_id"));
        cbcArtifactVersion.setRoArtifactId(rs.getLong("cbcArtVer.ro_artifact_id"));
        cbcArtifactVersion.setVersion(rs.getString("cbcArtVer.version"));
        cbcArtifactVersion.setUuid(rs.getString("cbcArtVer.uuid"));
        cbcArtifactVersion.setElementId(rs.getString("cbcArtVer.element_id"));
        cbcArtifactVersion.setStatus(rs.getString("cbcArtVer.status"));
        cbcArtifactVersion.setDescription(rs.getString("cbcArtVer.description"));
        cbcArtifactVersion.setDetailedDescription(rs.getString("cbcArtVer.detailed_description"));
        cbcArtifactVersion.setDueDate(rs.getTimestamp("cbcArtVer.due_date"));
        cbcArtifactVersion.setStartDate(rs.getTimestamp("cbcArtVer.start_date"));
        cbcArtifactVersion.setTimeStamp(rs.getTimestamp("cbcArtVer.time_stamp"));
        cbcArtifactVersion.setAssigneeUUID(rs.getString("cbcArtVer.assigneeuuid"));
        cbcArtifactVersion.setTitle(rs.getString("cbcArtVer.title"));
        cbcArtifactVersion.setParent(rs.getString("cbcArtVer.parent"));
        cbcArtifactVersion.setActualParent(rs.getString("cbcArtVer.actual_parent"));
        cbcArtifactVersion.setActivityGroupId(rs.getString("cbcArtVer.activity_group_id"));
        cbcArtifactVersion.setGoLiveRelevantCode(rs.getString("cbcArtVer.go_live_relevant_code"));
        cbcArtifactVersion.setMilestone(rs.getBoolean("cbcArtVer.milestone"));
        cbcArtifactVersion.setPhase(rs.getString("cbcArtVer.phase"));
        cbcArtifactVersion.setSequence(rs.getInt("cbcArtVer.sequence"));
        cbcArtifactVersion.setType(rs.getString("cbcArtVer.type"));
        cbcArtifactVersion.setBusinessArea(rs.getString("cbcArtVer.business_area"));
        cbcArtifactVersion.setTenantName(rs.getString("cbcArtVer.tenant_name"));
        cbcArtifactVersion.setCreatedBy(rs.getString("cbcArtVer.created_by"));
        cbcArtifactVersion.setLastModifiedBy(rs.getString("cbcArtVer.last_modified_by"));
        cbcArtifactVersion.setCreationDate(rs.getTimestamp("cbcArtVer.creation_date"));
        cbcArtifactVersion.setLastModifiedDate(rs.getTimestamp("cbcArtVer.last_modified_date"));

        cbcArtifactVersion.setExtUsId(rs.getString("cbcArtVerUs.ext_us_id"));
        cbcArtifactVersion.setProjUserStoryId(rs.getString("cbcArtVerUs.proj_us_id"));
        cbcArtifactVersion.setUserStoryId(rs.getLong("cbcArtVerUs.us_id"));
        return cbcArtifactVersion;
    }
}
