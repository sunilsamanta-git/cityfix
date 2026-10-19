package com.cityfix;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ComplaintFlowTest {
    @Autowired MockMvc mvc;

    private String login(String email, String password) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    @Test
    void potholeIsAutoRoutedToRoadsDepartment() throws Exception {
        String token = login("citizen@cityfix.com", "Citizen@123");
        mvc.perform(post("/api/complaints").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Deep pothole\",\"description\":\"Near the bus stop\",\"category\":\"POTHOLE\",\"location\":\"Janpath, Bhubaneswar\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.department").value("Roads & Infrastructure"));
    }

    @Test
    void citizenCannotSeeAllComplaints() throws Exception {
        String token = login("citizen@cityfix.com", "Citizen@123");
        mvc.perform(get("/api/complaints").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/complaints/my")).andExpect(status().isUnauthorized());
    }
}
