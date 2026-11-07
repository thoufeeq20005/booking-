package com.bookeasy.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bookeasy.user.entity.RoleCode;
import com.fasterxml.jackson.databind.JsonNode;

/** Helpers for walking a business through registration and review. */
public abstract class OrganizationTestBase extends AuthTestBase {

    protected static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    protected static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H',
            'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0};

    /** A signed-in person with a verified email, as a business applicant would be. */
    protected record Person(long id, String email, String token) {
    }

    protected Person verifiedCustomer() throws Exception {
        String email = uniqueEmail();
        Session s = register(email);
        long id = s.body().path("user").path("id").asLong();
        jdbc.update("UPDATE users SET email_verified_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()), id);
        return new Person(id, email, s.accessToken());
    }

    protected Person admin() throws Exception {
        String email = uniqueEmail();
        var user = createUser(email, RoleCode.SUPER_ADMIN);
        return new Person(user.getId(), email, login(email, PASSWORD).accessToken());
    }

    protected long subcategoryId(String slug) {
        return jdbc.queryForObject("SELECT id FROM subcategories WHERE slug = ?", Long.class, slug);
    }

    protected MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder builder, String token,
                                                        Object body) throws Exception {
        return authed(builder, token).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    protected Map<String, Object> orgBody(long subcategoryId, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("subcategoryId", subcategoryId);
        m.put("name", name);
        m.put("description", "Family clinic near the bus stand");
        m.put("contactPhone", "98765 43210");
        m.put("contactEmail", "Front.Desk@Example.test");
        m.put("website", "sunrise-clinic.example.in");
        m.put("confirmationMode", "INSTANT");
        return m;
    }

    protected static Map<String, Object> branchBody(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("addressLine1", "12 Anna Salai");
        m.put("locality", "Teynampet");
        m.put("city", "Chennai");
        m.put("state", "Tamil Nadu");
        m.put("pincode", "600018");
        m.put("latitude", 13.0418);
        m.put("longitude", 80.2341);
        return m;
    }

    protected static Map<String, Object> weekdayHours() {
        List<Map<String, Object>> days = new ArrayList<>();
        for (int d = 1; d <= 6; d++) {
            days.add(Map.of("dayOfWeek", d, "opensAt", "09:00", "closesAt", "18:00", "breakStart", "13:00",
                    "breakEnd", "14:00"));
        }
        return Map.of("days", days);
    }

    protected JsonNode createDraft(Person owner, long subcategoryId, String name) throws Exception {
        return read(mvc.perform(jsonRequest(post("/api/v1/business/organizations"), owner.token(),
                orgBody(subcategoryId, name))).andExpect(status().isOk()).andReturn()).path("data");
    }

    protected long addBranch(Person owner, long orgId) throws Exception {
        JsonNode b = read(mvc.perform(jsonRequest(post("/api/v1/owner/organizations/" + orgId + "/branches"),
                owner.token(), branchBody("Main branch"))).andExpect(status().isOk()).andReturn()).path("data");
        long branchId = b.path("id").asLong();
        mvc.perform(jsonRequest(put("/api/v1/owner/organizations/" + orgId + "/branches/" + branchId + "/hours"),
                owner.token(), weekdayHours())).andExpect(status().isOk());
        return branchId;
    }

    protected ResultActions upload(Person owner, long orgId, long requirementId, String filename, String type,
                                   byte[] bytes) throws Exception {
        return mvc.perform(authed(multipart("/api/v1/owner/organizations/" + orgId + "/documents/" + requirementId)
                .file(new MockMultipartFile("file", filename, type, bytes)), owner.token()));
    }

    protected JsonNode ownerDetail(Person owner, long orgId) throws Exception {
        return read(mvc.perform(authed(get("/api/v1/owner/organizations/" + orgId), owner.token()))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    /** Uploads a PDF for every mandatory requirement. */
    protected void uploadMandatoryDocuments(Person owner, long orgId) throws Exception {
        for (JsonNode slot : ownerDetail(owner, orgId).path("documents")) {
            if (slot.path("requirement").path("mandatory").asBoolean()) {
                upload(owner, orgId, slot.path("requirement").path("id").asLong(), "licence.pdf", "application/pdf",
                        PDF).andExpect(status().isOk());
            }
        }
    }

    /** Draft with a branch, hours and documents, submitted for review. */
    protected long pendingOrganization(Person owner, String name) throws Exception {
        long orgId = createDraft(owner, subcategoryId("general-clinic"), name).path("summary").path("id").asLong();
        addBranch(owner, orgId);
        uploadMandatoryDocuments(owner, orgId);
        mvc.perform(authed(post("/api/v1/owner/organizations/" + orgId + "/submit"), owner.token()))
                .andExpect(status().isOk());
        return orgId;
    }
}
