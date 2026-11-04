package com.bookeasy.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.bookeasy.support.OrganizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;

/** One owner can never see or touch another owner's business, branches or private documents. */
class TenantIsolationIT extends OrganizationTestBase {

    @Test
    void anotherOwnerGetsNotFoundForEveryOperation() throws Exception {
        Person alice = verifiedCustomer();
        Person bob = verifiedCustomer();
        long aliceOrg = createDraft(alice, subcategoryId("general-clinic"), "Alice Clinic").path("summary").path("id").asLong();
        long aliceBranch = addBranch(alice, aliceOrg);
        uploadMandatoryDocuments(alice, aliceOrg);
        long reqId = ownerDetail(alice, aliceOrg).path("documents").get(0).path("requirement").path("id").asLong();
        createDraft(bob, subcategoryId("gym"), "Bob Gym");

        String base = "/api/v1/owner/organizations/" + aliceOrg;
        mvc.perform(authed(get(base), bob.token())).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(put(base), bob.token(), orgBody(subcategoryId("gym"), "Stolen")))
                .andExpect(status().isNotFound());
        mvc.perform(authed(post(base + "/submit"), bob.token())).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post(base + "/branches"), bob.token(), branchBody("Mine now")))
                .andExpect(status().isNotFound());
        mvc.perform(jsonRequest(put(base + "/branches/" + aliceBranch + "/hours"), bob.token(), weekdayHours()))
                .andExpect(status().isNotFound());
        mvc.perform(authed(delete(base + "/branches/" + aliceBranch), bob.token())).andExpect(status().isNotFound());
        mvc.perform(authed(get(base + "/branches/" + aliceBranch + "/holidays"), bob.token()))
                .andExpect(status().isNotFound());
        mvc.perform(authed(get(base + "/documents/" + reqId + "/file"), bob.token())).andExpect(status().isNotFound());
        upload(bob, aliceOrg, reqId, "x.pdf", "application/pdf", PDF).andExpect(status().isNotFound());
        mvc.perform(authed(delete(base + "/documents/" + reqId), bob.token())).andExpect(status().isNotFound());
        mvc.perform(authed(multipart(base + "/logo").file(new MockMultipartFile("file", "l.png", "image/png", PNG)),
                bob.token())).andExpect(status().isNotFound());

        mvc.perform(authed(get("/api/v1/owner/organizations"), bob.token()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Bob Gym"));

        // Nothing changed for Alice, and her owner can still read her document.
        JsonNode mine = ownerDetail(alice, aliceOrg);
        assertThat(mine.path("summary").path("name").asText()).isEqualTo("Alice Clinic");
        assertThat(mine.path("branches")).hasSize(1);
        mvc.perform(authed(get(base + "/documents/" + reqId + "/file"), alice.token()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void branchOfOneOrganizationCannotBeAddressedThroughAnother() throws Exception {
        Person owner = verifiedCustomer();
        long orgA = createDraft(owner, subcategoryId("gym"), "Gym A").path("summary").path("id").asLong();
        long orgB = createDraft(owner, subcategoryId("gym"), "Gym B").path("summary").path("id").asLong();
        long branchOfA = addBranch(owner, orgA);
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgB + "/branches/" + branchOfA), owner.token(),
                branchBody("Moved"))).andExpect(status().isNotFound());
    }

    @Test
    void roleGatesAndAnonymousAccess() throws Exception {
        Person customer = verifiedCustomer();
        mvc.perform(authed(get("/api/v1/owner/organizations"), customer.token())).andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/admin/organizations"), customer.token())).andExpect(status().isForbidden());
        mvc.perform(authed(get("/api/v1/admin/categories"), customer.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/owner/organizations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/business/organizations").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());

        // Owners are not admins.
        createDraft(customer, subcategoryId("gym"), "Owner Gym");
        mvc.perform(authed(get("/api/v1/admin/organizations"), customer.token())).andExpect(status().isForbidden());
    }

    @Test
    void privateDocumentsAreNeverServedPubliclyButLogosAre() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("general-clinic"), "Logo Clinic").path("summary").path("id").asLong();
        uploadMandatoryDocuments(owner, orgId);
        String docPublicId = jdbc.queryForObject("SELECT f.public_id FROM uploaded_files f JOIN organization_documents d "
                + "ON d.file_id = f.id WHERE d.organization_id = ? LIMIT 1", String.class, orgId);
        mvc.perform(get("/api/v1/public/files/" + docPublicId)).andExpect(status().isNotFound());

        String logoUrl = read(mvc.perform(authed(multipart("/api/v1/owner/organizations/" + orgId + "/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG)), owner.token()))
                .andExpect(status().isOk()).andReturn()).path("data").path("summary").path("logoUrl").asText();
        assertThat(logoUrl).startsWith("/api/v1/public/files/");
        mvc.perform(get(logoUrl)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Replacing the logo deletes the old one.
        mvc.perform(authed(multipart("/api/v1/owner/organizations/" + orgId + "/logo")
                .file(new MockMultipartFile("file", "logo2.png", "image/png", PNG)), owner.token())).andExpect(status().isOk());
        mvc.perform(get(logoUrl)).andExpect(status().isNotFound());
    }

    @Test
    void uploadsAreCheckedByContentNotByNameOrDeclaredType() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("general-clinic"), "Upload Clinic").path("summary").path("id").asLong();
        long reqId = ownerDetail(owner, orgId).path("documents").get(0).path("requirement").path("id").asLong();

        byte[] script = "<script>alert(1)</script>".getBytes();
        upload(owner, orgId, reqId, "id.pdf", "application/pdf", script)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();
        upload(owner, orgId, reqId, "id.webp", "image/webp", webp)
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED")); // not allowed for this requirement
        upload(owner, orgId, reqId, "empty.pdf", "application/pdf", new byte[0])
                .andExpect(jsonPath("$.error.code").value("FILE_EMPTY"));
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(owner, orgId, reqId, "big.pdf", "application/pdf", big)
                .andExpect(status().isPayloadTooLarge());

        // A JPEG named ../../evil.exe is stored as a .jpg under a generated key.
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
        upload(owner, orgId, reqId, "../../evil.exe", "application/x-msdownload", jpeg)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.fileName").value("evil.jpg"))
                .andExpect(jsonPath("$.data.document.contentType").value("image/jpeg"));
        String key = jdbc.queryForObject("SELECT f.storage_key FROM uploaded_files f JOIN organization_documents d "
                + "ON d.file_id = f.id WHERE d.organization_id = ?", String.class, orgId);
        assertThat(key).matches("private/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg");
        assertThat(Files.exists(Path.of("target/test-uploads").resolve(key))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uploaded_files WHERE owner_user_id = ?", Integer.class,
                owner.id())).as("rejected uploads leave no records").isEqualTo(1);
    }

    @Test
    void branchLocationIsDerivedForSpatialSearch() throws Exception {
        Person owner = verifiedCustomer();
        long orgId = createDraft(owner, subcategoryId("gym"), "Geo Gym").path("summary").path("id").asLong();
        long branchId = addBranch(owner, orgId);
        var row = jdbc.queryForMap("SELECT ST_Latitude(location) AS lat, ST_Longitude(location) AS lng, "
                + "ST_SRID(location) AS srid FROM organization_branches WHERE id = ?", branchId);
        assertThat(((Number) row.get("lat")).doubleValue()).isEqualTo(13.0418);
        assertThat(((Number) row.get("lng")).doubleValue()).isEqualTo(80.2341);
        assertThat(((Number) row.get("srid")).intValue()).isEqualTo(4326);

        var swapped = branchBody("Swapped");
        swapped.put("latitude", 80.2341);
        swapped.put("longitude", 13.0418);
        mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/branches"), owner.token(), swapped))
                .andExpect(status().isBadRequest());
    }
}
