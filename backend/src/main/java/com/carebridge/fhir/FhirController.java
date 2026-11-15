package com.carebridge.fhir;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/fhir/r4")
public class FhirController {
  private final FhirExportService service;
  public FhirController(FhirExportService service){this.service=service;}
  @GetMapping(value="/care-plans/{id}/bundle",produces="application/fhir+json")
  public String export(@PathVariable UUID id){return service.export(id);}
}
