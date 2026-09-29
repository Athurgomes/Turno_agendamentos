package br.com.reservas.area.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.area.support.InMemoryFileStorage;
import br.com.reservas.area.support.TestAreaReservationsGateway;
import br.com.reservas.area.support.TestImages;
import br.com.reservas.shared.storage.FileStorage;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** `/areas/{id}/photos` (RF-ARE-06, RN-17). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class AreaPhotoControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteAdmin1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorage fileStorage;

    private UUID condominiumId;
    private String adminToken;
    private String syndicToken;
    private String unitToken;
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Fotos");

        adminToken = createAccount("ADMIN", null);
        syndicToken = createAccount("SYNDIC", null);
        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'A', '1', 'a-1')",
            unitId, condominiumId);
        unitToken = createAccount("UNIT", unitId);

        areaId = createArea();
    }

    private String createAccount(String role, UUID unitId) throws Exception {
        boolean isUnit = "UNIT".equals(role);
        String username = isUnit ? "u-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String email = isUnit ? null : role.toLowerCase() + "." + UUID.randomUUID() + "@exemplo.test";
        String login = isUnit ? username : email;
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, email, display_name, unit_id, password_hash) "
                + "values (?, ?, ?, ?, ?, ?, ?)",
            condominiumId, role, username, email, role + " Teste", unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, login, PASSWORD);
    }

    private UUID createArea() throws Exception {
        String dataJson = """
            {
              "name": "Churrasqueira %s",
              "category": "BARBECUE",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
              "requiresPayment": false,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(multipart("/api/v1/areas")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .file(new MockMultipartFile("photos", "a.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    @DisplayName("RN-17: cada upload grava uma chave nova (dois uploads da mesma foto -> duas fotos distintas)")
    void uploadNeverOverwritesPreviousPhoto() throws Exception {
        String first = upload(syndicToken, TestImages.jpeg()).get(0).get("url").asText();
        String second = upload(syndicToken, TestImages.jpeg()).get(0).get("url").asText();

        org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("RN-17: extensao .jpg com conteudo que nao e imagem -> 422 INVALID_FILE")
    void fakeImageContentIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", TestImages.fakePdfWithJpgExtension()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-17: arquivo maior que 5 MB -> 422 INVALID_FILE")
    void oversizedFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.oversized()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-01/D-20: UNIT recebe 403 em /areas/{id}/photos")
    void unitCannotAccessPhotos() throws Exception {
        mockMvc.perform(get("/api/v1/areas/" + areaId + "/photos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-ARE-04: UNIT enxerga so fotos de vitrine nao arquivadas em /areas/{id}")
    void unitSeesOnlyShowcaseNonArchivedPhotos() throws Exception {
        JsonNode uploaded = upload(adminToken, TestImages.jpeg()).get(0);
        UUID photoId = UUID.fromString(uploaded.get("id").asText());
        // foto do endpoint dedicado nasce featured=false; nao deve aparecer no detalhe da area.
        mockMvc.perform(get("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[?(@.id=='" + photoId + "')]").isEmpty());
    }

    @Test
    @DisplayName("RN-17: foto arquivada nao pode voltar para a vitrine -> 409 INVALID_STATUS_TRANSITION")
    void archivedPhotoCannotBecomeFeaturedAgain() throws Exception {
        JsonNode uploaded = upload(adminToken, TestImages.jpeg()).get(0);
        UUID photoId = UUID.fromString(uploaded.get("id").asText());

        mockMvc.perform(post("/api/v1/areas/" + areaId + "/photos/" + photoId + "/archive")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/photos/" + photoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"featured\": true }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RF-ARE-07: inspectionId de outra area -> 404 NOT_FOUND, nada e gravado no storage")
    void uploadWithInspectionFromAnotherAreaIsRejected() throws Exception {
        UUID otherAreaId = createArea();
        String inspectionResponse = mockMvc.perform(multipart("/api/v1/areas/" + otherAreaId + "/inspections")
                .file(new MockMultipartFile("data", "data", "application/json",
                    "{ \"inspectedAt\": \"2026-01-10\", \"overallCondition\": \"GOOD\" }".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID otherAreaInspectionId = UUID.fromString(objectMapper.readTree(inspectionResponse).get("id").asText());

        int before = ((InMemoryFileStorage) fileStorage).size();

        mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", TestImages.jpeg()))
                .param("inspectionId", otherAreaInspectionId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(((InMemoryFileStorage) fileStorage).size()).isEqualTo(before);
    }

    @Test
    @DisplayName("RF-ARE-07: inspectionId inexistente -> 404 NOT_FOUND")
    void uploadWithNonExistentInspectionIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", TestImages.jpeg()))
                .param("inspectionId", UUID.randomUUID().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("D-52: UNIT recebe uploadedBy nulo em GET /areas/{id}; SYNDIC/ADMIN vê o autor")
    void uploadedByIsHiddenFromUnit() throws Exception {
        upload(adminToken, TestImages.jpeg());
        // a foto do endpoint dedicado nasce featured=false e nao aparece no detalhe;
        // usamos a foto de vitrine criada no cadastro (createArea) para checar o campo.
        mockMvc.perform(get("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].uploadedBy").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].uploadedBy.name").value("Administração"));
    }

    private java.util.List<JsonNode> upload(String token, byte[] content) throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        java.util.List<JsonNode> result = new java.util.ArrayList<>();
        objectMapper.readTree(response).forEach(result::add);
        return result;
    }
}
