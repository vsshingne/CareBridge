package com.carebridge.care;

import static com.carebridge.care.CareDtos.*;
import com.carebridge.common.Pages;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care")
public class CareController {
  private final CareService service;
  public CareController(CareService service) {this.service=service;}
  @GetMapping("/plans") public Pages.Result<Plan> plans(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.plans(page,size);}
  @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED) public Plan create(@Valid @RequestBody CreatePlan r) {return service.create(r);}
  @GetMapping("/plans/{id}") public Plan get(@PathVariable UUID id) {return service.get(id);}
  @PutMapping("/plans/{id}") public Plan edit(@PathVariable UUID id,@Valid @RequestBody EditPlan r) {return service.edit(id,r);}
  @PatchMapping("/plans/{id}/status") public Plan finish(@PathVariable UUID id,@Valid @RequestBody FinishPlan r) {return service.finish(id,r);}
  @GetMapping("/plans/{id}/activities") public Pages.Result<Activity> activities(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.activities(id,page,size);}
  @PostMapping("/plans/{id}/activities") @ResponseStatus(HttpStatus.CREATED) public Activity add(@PathVariable UUID id,@Valid @RequestBody AddActivity r) {return service.addActivity(id,r);}
  @PutMapping("/plans/{planId}/activities/{id}") public Activity editActivity(@PathVariable UUID planId,@PathVariable UUID id,@Valid @RequestBody EditActivity r) {return service.editActivity(planId,id,r);}
  @PatchMapping("/plans/{planId}/activities/{id}/status") public Activity change(@PathVariable UUID planId,@PathVariable UUID id,@Valid @RequestBody ActivityChange r) {return service.changeActivity(planId,id,r);}
  @PostMapping("/plans/{id}/referrals") @ResponseStatus(HttpStatus.CREATED) public Referral referral(@PathVariable UUID id,@Valid @RequestBody CreateReferral r) {return service.addReferral(id,r);}
  @GetMapping("/referrals") public Pages.Result<Referral> referrals(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.referrals(page,size);}
  @GetMapping("/referrals/{id}") public Referral getReferral(@PathVariable UUID id) {return service.getReferral(id);}
  @PatchMapping("/referrals/{id}/status") public Referral changeReferral(@PathVariable UUID id,@Valid @RequestBody ReferralChange r) {return service.changeReferral(id,r);}
  @PatchMapping("/referrals/{id}/assignment") public Referral reassign(@PathVariable UUID id,@Valid @RequestBody Reassign r) {return service.reassign(id,r);}
  @GetMapping("/journey/{patientId}") public Pages.Result<Journey> journey(@PathVariable UUID patientId,@RequestParam(required=false) String type,@RequestParam(required=false) String status,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.journey(patientId,type,status,from,to,page,size);}
}
