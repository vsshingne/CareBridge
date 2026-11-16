package com.carebridge.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ErrorsTest {
  @RestController
  static class Inputs {
    @GetMapping("/timezone")
    String timezone(@RequestParam("zone") String zone) {
      return ZoneId.of(zone).getId();
    }

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> json(@RequestBody Map<String, String> body) {
      return body;
    }
  }

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new Inputs())
          .setControllerAdvice(
              new Errors(
                  org.mockito.Mockito.mock(com.carebridge.audit.AuditService.class),
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry()))
          .build();

  @Test
  void invalidTimezoneIsClientError() throws Exception {
    mvc.perform(get("/timezone").param("zone", "Not/A_Zone"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void missingRequiredParameterIsClientError() throws Exception {
    mvc.perform(get("/timezone")).andExpect(status().isBadRequest());
  }

  @Test
  void unsupportedContentTypeIs415() throws Exception {
    mvc.perform(post("/json").contentType(MediaType.TEXT_PLAIN).content("private input"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("Use application/json for this request"));
  }
}
