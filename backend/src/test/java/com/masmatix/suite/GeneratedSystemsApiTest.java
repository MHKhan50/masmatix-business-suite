package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every seed file seed/&lt;system&gt;-&lt;path&gt;.json must be reachable at /api/&lt;system&gt;/&lt;path&gt;,
 * list all its sample rows and export CSV. This covers the spec-only systems without one test per system.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeneratedSystemsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void everySeededEntityListsItsRowsAndExportsCsv() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(body).get("token").asText();

        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:seed/*.json");
        assertThat(files.length).isGreaterThanOrEqualTo(30);
        for (Resource file : files) {
            String name = file.getFilename().replace(".json", "");
            int dash = name.indexOf('-');
            String url = "/api/" + name.substring(0, dash) + "/" + name.substring(dash + 1);
            int rows;
            try (InputStream in = file.getInputStream()) {
                rows = json.readTree(in).get("rows").size();
            }
            JsonNode page = json.readTree(mvc.perform(get(url + "?size=100").header("Authorization", token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(page.get("totalElements").asInt()).as(url).isGreaterThanOrEqualTo(rows);
            mvc.perform(get(url + "/export.csv").header("Authorization", token)).andExpect(status().isOk());
        }
    }
}
