package com.saparate.pc.repository;

import com.rate.commons.RateUtils;
import com.saparate.pc.entity.PCArtifact;
import com.saparate.pc.entity.PCArtifactVersion;
import com.saparate.pc.model.PCArtifactType;
import com.saparate.pc.model.PCArtifactDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
@Slf4j
@Component
public class PCMysqlDAO {

    @Autowired
    private NamedParameterJdbcTemplate namedParamJdbcTemplate;

    @Autowired
    private RateUtils rateUtils;

    public List<PCArtifactVersion> getPcArtifactVersions(String tenantName, long projectId, long pcArtifactId) {
        String query = "select pcArtVer.*, pcArtVerUs.us_id, pcArtVerUs.ext_us_id, pcArtVerUs.proj_us_id from pc_artifact_version as pcArtVer " +
                "left outer join pc_artifact_user_story as pcArtVerUs on (pcArtVer.ro_artifact_id = pcArtVerUs.ro_pc_artifact_id and pcArtVer.ro_version_id=pcArtVerUs.ro_pc_artifact_version_id and pcArtVerUs.project_id = :projId) " +
                "where pcArtVer.tenant_name = :tenantName and pcArtVer.ro_artifact_id = :pcArtifactId order by pcArtVer.last_modified_date desc";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tenantName", tenantName);
        parameters.addValue("pcArtifactId", pcArtifactId);
        parameters.addValue("projId", projectId);
        List<PCArtifactVersion> pcArtifactVersionList = this.namedParamJdbcTemplate.query(query, parameters, this::preparePcArtifactVersion);
        return pcArtifactVersionList;
    }

    public List<PCArtifact> getPcArtifactWithVersions(String tenantName, List<Long> roPcArtVersionIds) {
        String query = "select pcArt.*, pcArtVer.* from pc_artifact as pcArt join pc_artifact_version as pcArtVer on(pcArt.ro_id = pcArtVer.ro_artifact_id) where pcArtVer.tenant_name = :tenantName and  pcArtVer.ro_version_id In (:roVersionId); ";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tenantName", tenantName);
        parameters.addValue("roVersionId", roPcArtVersionIds);
        List<PCArtifact> pcArtifactList = this.namedParamJdbcTemplate.query(query, parameters, this::preparePcArtifacts);
        return pcArtifactList;
    }

    public PCArtifact getPcArtifactByArtifactIdAndVersion(String tenantName, long projectId, String artifactId, String version) {
        try {
            String query = "select pcArt.*, pcArtVer.*, pcArtUs.us_id, pcArtUs.ext_us_id, pcArtUs.proj_us_id from pc_artifact as pcArt join pc_artifact_version as pcArtVer " +
                    "on (pcArt.ro_id = pcArtVer.ro_artifact_id) left outer join  pc_artifact_user_story as pcArtUs " +
                    "on (pcArtVer.ro_artifact_id = pcArtUs.ro_pc_artifact_id and pcArtVer.ro_version_id=pcArtUs.ro_pc_artifact_version_id) " +
                    "where pcArt.tenant_name = :tenantName and pcArt.artifact_id = :artifactId and pcArt.project_id = :projId and pcArtVer.version = :version";
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("tenantName", tenantName);
            parameters.addValue("projId", projectId);
            parameters.addValue("artifactId", artifactId);
            parameters.addValue("version", version);
            return this.namedParamJdbcTemplate.queryForObject(query, parameters, this::preparePcArtifactsUs);
        } catch (EmptyResultDataAccessException e) {
            log.error("The Artifact '{}' and version '{}' is not found", artifactId, version, e);
            return null;
        }
    }


    private PCArtifactVersion preparePcArtifactVersion(ResultSet rs, int i) throws SQLException {
        PCArtifactVersion pcArtifactVersion = new PCArtifactVersion();
        pcArtifactVersion.setRoVersionId(rs.getLong("pcArtVer.ro_version_id"));
        pcArtifactVersion.setProjectId(rs.getLong("pcArtVer.project_id"));
        pcArtifactVersion.setRoArtifactId(rs.getLong("pcArtVer.ro_artifact_id"));
        pcArtifactVersion.setVersion(rs.getString("pcArtVer.version"));
        pcArtifactVersion.setEnvId(rs.getLong("pcArtVer.env_id"));
        pcArtifactVersion.setSapChangedBy(rs.getString("pcArtVer.sap_changed_by"));
        pcArtifactVersion.setPcArtifactDetails(this.rateUtils.getTypeObjectFromResultset(rs, "pcArtVer.pc_artifact_details", PCArtifactDetails.class));
        pcArtifactVersion.setSapChangedOn(rs.getTimestamp("pcArtVer.sap_changed_on"));
        pcArtifactVersion.setTenantName(rs.getString("pcArtVer.tenant_name"));
        pcArtifactVersion.setCreatedBy(rs.getString("pcArtVer.created_by"));
        pcArtifactVersion.setLastModifiedBy(rs.getString("pcArtVer.last_modified_by"));
        pcArtifactVersion.setCreationDate(rs.getTimestamp("pcArtVer.creation_date"));
        pcArtifactVersion.setLastModifiedDate(rs.getTimestamp("pcArtVer.last_modified_date"));

        pcArtifactVersion.setExtUsId(rs.getString("pcArtVerUs.ext_us_id"));
        pcArtifactVersion.setProjUserStoryId(rs.getString("pcArtVerUs.proj_us_id"));
        pcArtifactVersion.setUserStoryId(rs.getLong("pcArtVerUs.us_id"));
        return pcArtifactVersion;
    }
    private PCArtifact preparePcArtifacts(ResultSet rs, int i) throws SQLException {
        PCArtifact pcArtifact = new PCArtifact();
        pcArtifact.setRoId(rs.getLong("pcArt.ro_id"));
        pcArtifact.setEnvId(rs.getLong("pcArt.env_id"));
        pcArtifact.setArtifactId(rs.getString("pcArt.artifact_id"));
        pcArtifact.setProjectId(rs.getLong("pcArt.project_id"));
        pcArtifact.setName(rs.getString("pcArt.name"));
        pcArtifact.setType(PCArtifactType.of(rs.getInt("pcArt.type")));
        pcArtifact.setDeleted(rs.getBoolean("pcArt.is_deleted"));
        pcArtifact.setTenantName(rs.getString("pcArtVer.tenant_name"));
        pcArtifact.setCreatedBy(rs.getString("pcArtVer.created_by"));
        pcArtifact.setLastModifiedBy(rs.getString("pcArtVer.last_modified_by"));
        pcArtifact.setCreationDate(rs.getTimestamp("pcArtVer.creation_date"));
        pcArtifact.setLastModifiedDate(rs.getTimestamp("pcArtVer.last_modified_date"));

        PCArtifactVersion pcArtVersion = new PCArtifactVersion();
        pcArtVersion.setRoVersionId(rs.getLong("pcArtVer.ro_version_id"));
        pcArtVersion.setRoArtifactId(rs.getLong("pcArtVer.ro_artifact_id"));
        pcArtifact.setEnvId(rs.getLong("pcArtVer.env_id"));
        pcArtVersion.setProjectId(rs.getLong("pcArtVer.project_id"));
        pcArtVersion.setVersion(rs.getString("pcArtVer.version"));
        pcArtVersion.setDescription(rs.getString("pcArtVer.description"));
        pcArtVersion.setPcArtifactDetails(this.rateUtils.getTypeObjectFromResultset(rs, "pcArtVer.pc_artifact_details", PCArtifactDetails.class));
        pcArtVersion.setSapChangedOn(rs.getTimestamp("pcArtVer.sap_changed_on"));
        pcArtVersion.setSapChangedBy(rs.getString("pcArtVer.sap_changed_by"));
        pcArtVersion.setTenantName(rs.getString("pcArtVer.tenant_name"));
        pcArtVersion.setCreatedBy(rs.getString("pcArtVer.created_by"));
        pcArtVersion.setLastModifiedBy(rs.getString("pcArtVer.last_modified_by"));
        pcArtVersion.setCreationDate(rs.getTimestamp("pcArtVer.creation_date"));
        pcArtVersion.setLastModifiedDate(rs.getTimestamp("pcArtVer.last_modified_date"));
        pcArtifact.setPcArtifactVersion(pcArtVersion);
        return pcArtifact;
    }

    private PCArtifact preparePcArtifactsUs(ResultSet rs, int i) throws SQLException {
        PCArtifact pcArtifact = new PCArtifact();
        pcArtifact.setRoId(rs.getLong("pcArt.ro_id"));
        pcArtifact.setEnvId(rs.getLong("pcArt.env_id"));
        pcArtifact.setArtifactId(rs.getString("pcArt.artifact_id"));
        pcArtifact.setProjectId(rs.getLong("pcArt.project_id"));
        pcArtifact.setName(rs.getString("pcArt.name"));
        pcArtifact.setType(PCArtifactType.of(rs.getInt("pcArt.type")));
        pcArtifact.setDeleted(rs.getBoolean("pcArt.is_deleted"));
        pcArtifact.setTenantName(rs.getString("pcArtVer.tenant_name"));
        pcArtifact.setCreatedBy(rs.getString("pcArtVer.created_by"));
        pcArtifact.setLastModifiedBy(rs.getString("pcArtVer.last_modified_by"));
        pcArtifact.setCreationDate(rs.getTimestamp("pcArtVer.creation_date"));
        pcArtifact.setLastModifiedDate(rs.getTimestamp("pcArtVer.last_modified_date"));

        PCArtifactVersion pcArtVersion = new PCArtifactVersion();
        pcArtVersion.setRoVersionId(rs.getLong("pcArtVer.ro_version_id"));
        pcArtVersion.setRoArtifactId(rs.getLong("pcArtVer.ro_artifact_id"));
        pcArtifact.setEnvId(rs.getLong("pcArtVer.env_id"));
        pcArtVersion.setProjectId(rs.getLong("pcArtVer.project_id"));
        pcArtVersion.setVersion(rs.getString("pcArtVer.version"));
        pcArtVersion.setDescription(rs.getString("pcArtVer.description"));
        pcArtVersion.setPcArtifactDetails(this.rateUtils.getTypeObjectFromResultset(rs, "pcArtVer.pc_artifact_details", PCArtifactDetails.class));
        pcArtVersion.setSapChangedOn(rs.getTimestamp("pcArtVer.sap_changed_on"));
        pcArtVersion.setSapChangedBy(rs.getString("pcArtVer.sap_changed_by"));
        pcArtVersion.setTenantName(rs.getString("pcArtVer.tenant_name"));
        pcArtVersion.setCreatedBy(rs.getString("pcArtVer.created_by"));
        pcArtVersion.setLastModifiedBy(rs.getString("pcArtVer.last_modified_by"));
        pcArtVersion.setCreationDate(rs.getTimestamp("pcArtVer.creation_date"));
        pcArtVersion.setLastModifiedDate(rs.getTimestamp("pcArtVer.last_modified_date"));

        pcArtVersion.setExtUsId(rs.getString("pcArtUs.ext_us_id"));
        pcArtVersion.setProjUserStoryId(rs.getString("pcArtUs.proj_us_id"));
        pcArtVersion.setUserStoryId(rs.getLong("pcArtUs.us_id"));

        pcArtifact.setPcArtifactVersion(pcArtVersion);
        return pcArtifact;
    }

/*    private boolean hasColumn(ResultSet rs) {
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if ("pcArtUs.us_id".equalsIgnoreCase(metaData.getColumnLabel(i))) {
                    return true;
                }
            }
        } catch (SQLException e) {
            // Log or handle error
        }
        return false;
    }*/

}
