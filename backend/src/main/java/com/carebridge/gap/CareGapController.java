package com.carebridge.gap;
import com.carebridge.common.Pages;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/care/gaps")
public class CareGapController {
  private final CareGapService service;
  public CareGapController(CareGapService service){this.service=service;}
  @PostMapping("/rules") @ResponseStatus(HttpStatus.CREATED) public Map<String,UUID> create(@Valid @RequestBody CareGapService.RuleInput r){return Map.of("id",service.create(r));}
  @GetMapping("/rules") public List<CareGapService.Rule> rules(){return service.rules();}
  @PatchMapping("/rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void enable(@PathVariable UUID id,@Valid @RequestBody CareGapService.Enable r){service.enable(id,r);}
  @PostMapping("/evaluate") @ResponseStatus(HttpStatus.NO_CONTENT) public void evaluate(){service.runByAdmin();}
  @GetMapping public Pages.Result<CareGapService.Alert> alerts(@RequestParam(defaultValue="false") boolean resolved,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.alerts(resolved,page,size);}
}
