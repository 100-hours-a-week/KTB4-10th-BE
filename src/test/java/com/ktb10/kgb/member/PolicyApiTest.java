package com.ktb10.kgb.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:policy-api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PolicyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void policiesArePublicAndReturnedInRegistryOrder() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("policy_list_success"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].policy_type").value("terms"))
                .andExpect(jsonPath("$.data.items[0].title").value("이용약관"))
                .andExpect(jsonPath("$.data.items[1].policy_type").value("privacy"))
                .andExpect(jsonPath("$.data.items[1].title").value("개인정보 처리방침"));
    }

    @Test
    void privacyPolicyReturnsMarkdownWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/policies/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("policy_get_success"))
                .andExpect(jsonPath("$.data.policy_type").value("privacy"))
                .andExpect(jsonPath("$.data.title").value("개인정보 처리방침"))
                .andExpect(jsonPath("$.data.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.content").value(
                        org.hamcrest.Matchers.containsString("# 개인정보 처리방침")));
    }

    @Test
    void unknownPolicyReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/policies/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }
}
