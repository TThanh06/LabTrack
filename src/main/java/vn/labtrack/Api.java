package vn.labtrack;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.core.env.Environment;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api")
class Api {
 final Security security; final LabService service; final Users users; final Environment env;
 final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 Api(Security s,LabService l,Users u,Environment e){security=s;service=l;users=u;env=e;}
 record LoginInput(@NotBlank @Email String email,@NotBlank @Size(max=72) String password){}
 record AssetInput(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{2,40}") String code,@NotBlank @Size(max=120) String name,@NotBlank @Size(max=60) String category,@NotBlank @Size(max=80) String location,@NotNull @Size(max=800) String description){}
 record LoanInput(@NotNull Long assetId,@NotNull LocalDate startOn,@NotNull LocalDate dueOn,@NotBlank @Size(max=500) String purpose,@PositiveOrZero Long expectedFee){}
 record ActionInput(@NotBlank String action,@NotNull @Size(max=500) String note){}
 record IssueInput(@NotNull Long assetId,@NotBlank @Size(max=1000) String description){}
 record ResolutionInput(@NotBlank @Size(max=1000) String resolution){}
 @GetMapping("/health") Map<String,Object> health(){return Map.of("ok",true,"mode",env.matchesProfiles("postgres")?"postgres":"demo");}
 @PostMapping("/login") User login(@Valid @RequestBody LoginInput input,HttpServletRequest req) {
  ManagementService.validPassword(input.password());
  User user=users.findByEmail(input.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
  if(user==null||!user.active||!encoder.matches(input.password(),user.passwordHash))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email hoặc mật khẩu không đúng hoặc tài khoản đã bị khóa.");
  req.getSession(true);req.changeSessionId();req.getSession().setAttribute("uid",user.id);req.getSession().setAttribute("authVersion",user.authVersion);return user;
 }
 @GetMapping("/me") User me(HttpServletRequest r){return security.user(r);}
 @PostMapping("/logout") Map<String,Boolean> logout(HttpServletRequest r){HttpSession s=r.getSession(false);if(s!=null)s.invalidate();return Map.of("ok",true);}
 @GetMapping("/assets") List<Asset> assets(HttpServletRequest r){security.user(r);return service.listAssets();}
 @PostMapping("/assets") Asset asset(@Valid @RequestBody AssetInput i,HttpServletRequest r){return service.createAsset(security.user(r),i);}
 @GetMapping("/assets/{id}/schedule") List<Map<String,Object>> schedule(@PathVariable Long id,HttpServletRequest r){
  security.user(r);return service.schedule(id);
 }
 @GetMapping("/loans") List<Loan> loans(HttpServletRequest r){return service.listLoans(security.user(r));}
 @PostMapping("/loans") Loan loan(@Valid @RequestBody LoanInput i,HttpServletRequest r){return service.request(security.user(r),i);}
 @PostMapping("/loans/{id}/action") Loan action(@PathVariable long id,@Valid @RequestBody ActionInput i,HttpServletRequest r){return service.transition(security.user(r),id,i);}
 @GetMapping("/issues") List<Issue> issues(HttpServletRequest r){return service.listIssues(security.user(r));}
 @PostMapping("/issues") Issue issue(@Valid @RequestBody IssueInput i,HttpServletRequest r){return service.report(security.user(r),i);}
 @PostMapping("/issues/{id}/resolve") Issue resolve(@PathVariable long id,@Valid @RequestBody ResolutionInput i,HttpServletRequest r){return service.resolve(security.user(r),id,i);}
 @GetMapping("/audit") List<AuditEvent> audit(HttpServletRequest r){Security.staff(security.user(r));return service.listAudit();}
}

@RestControllerAdvice
class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> expected(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(e.getReason(),"Yêu cầu không hợp lệ.")));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 ResponseEntity<?> validation(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Kiểm tra trường bắt buộc, định dạng và độ dài nội dung."));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> duplicate(Exception e){return ResponseEntity.status(409).body(Map.of("message","Mã thiết bị hoặc email đã tồn tại, hoặc dữ liệu không hợp lệ."));}
}
