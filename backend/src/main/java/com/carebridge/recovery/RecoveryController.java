package com.carebridge.recovery;

import com.carebridge.appointment.AppointmentDtos;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/recovery")
public class RecoveryController {
  private final RecoveryService service;
  public RecoveryController(RecoveryService service){this.service=service;}
  @PostMapping("/waiting-list") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,UUID> join(@Valid @RequestBody RecoveryService.Join r){return Map.of("id",service.join(r));}
  @GetMapping("/waiting-list") public List<RecoveryService.Entry> entries(){return service.entries();}
  @DeleteMapping("/waiting-list/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id){service.remove(id);}
  @PatchMapping("/waiting-list/{id}/priority") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void priority(@PathVariable UUID id,@Valid @RequestBody RecoveryService.Priority r){service.prioritize(id,r.priority());}
  @GetMapping("/offers") public List<RecoveryService.Offer> offers(){return service.offers();}
  @PostMapping("/offers/{id}/accept") public AppointmentDtos.View accept(@PathVariable UUID id,@Valid @RequestBody RecoveryService.Claim r){return service.accept(id,r);}
}
