package vn.labtrack;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api")
class Management {
 private final Security security;private final ManagementService service;
 Management(Security s,ManagementService m){security=s;service=m;}
 record StudentInput(@NotBlank @Email @Size(max=160) String email,@NotBlank @Size(max=100) String name,@NotBlank @Size(min=10,max=72) String password){}
 record ActiveInput(@NotNull Boolean active){}
 record RetireInput(@NotNull Boolean retired){}
 record PasswordInput(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(min=10,max=72) String newPassword){}
 @GetMapping("/users") List<User> users(HttpServletRequest r){return service.users(security.user(r));}
 @PostMapping("/users") User create(@Valid @RequestBody StudentInput i,HttpServletRequest r){return service.createStudent(security.user(r),i);}
 @PostMapping("/users/{id}/active") User active(@PathVariable Long id,@Valid @RequestBody ActiveInput i,HttpServletRequest r){return service.setActive(security.user(r),id,i.active());}
 @PostMapping("/me/password") Map<String,Boolean> password(@Valid @RequestBody PasswordInput i,HttpServletRequest r){service.password(security.user(r),i);r.getSession().invalidate();return Map.of("ok",true);}
 @PostMapping("/assets/{id}/edit") Asset edit(@PathVariable Long id,@Valid @RequestBody Api.AssetInput i,HttpServletRequest r){return service.edit(security.user(r),id,i);}
 @PostMapping("/assets/{id}/retired") Asset retire(@PathVariable Long id,@Valid @RequestBody RetireInput i,HttpServletRequest r){return service.retire(security.user(r),id,i.retired());}
 @GetMapping("/reports") Map<String,Object> reports(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,HttpServletRequest r){return service.report(security.user(r),from,to);}
}

@Service @Transactional
class ManagementService {
 private final Users accounts;private final LabService lab;private final Loans loans;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 ManagementService(Users u,LabService l,Loans loans){accounts=u;lab=l;this.loans=loans;}
 static void validPassword(String password){if(password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu tối đa 72 byte UTF-8; hãy giảm số ký tự có dấu.");}
 @Transactional(readOnly=true) List<User> users(User actor){Security.staff(actor);return accounts.findAllByOrderByIdDesc();}
 User createStudent(User actor,Management.StudentInput i){
  Security.staff(actor);validPassword(i.password());User u=new User();u.email=i.email().trim().toLowerCase(Locale.ROOT);u.name=i.name().trim();u.role="STUDENT";u.passwordHash=encoder.encode(i.password());
  accounts.saveAndFlush(u);lab.log(actor,"CREATE_USER","USER-"+u.id,u.name);return u;
 }
 User setActive(User actor,Long id,boolean active){
  Security.staff(actor);User u=accounts.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản."));
  if(!u.role.equals("STUDENT"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ quản lý trạng thái tài khoản sinh viên.");
  if(u.active!=active){u.active=active;u.authVersion++;lab.log(actor,active?"ENABLE_USER":"DISABLE_USER","USER-"+id,u.name);}
  return u;
 }
 void password(User actor,Management.PasswordInput i){
  validPassword(i.currentPassword());validPassword(i.newPassword());User u=accounts.lockById(actor.id).orElseThrow();
  if(!encoder.matches(i.currentPassword(),u.passwordHash))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu hiện tại không đúng.");
  if(i.currentPassword().equals(i.newPassword()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu mới phải khác mật khẩu hiện tại.");
  u.passwordHash=encoder.encode(i.newPassword());u.authVersion++;lab.log(actor,"CHANGE_PASSWORD","USER-"+u.id,"Đã đổi mật khẩu và thu hồi các phiên đăng nhập.");
 }
 Asset edit(User actor,Long id,Api.AssetInput i){
  Security.staff(actor);Asset a=lab.lock(id);
  if(!a.code.equalsIgnoreCase(i.code().trim()))throw LabService.bad("Mã tài sản không thay đổi sau khi tạo.");
  a.name=i.name().trim();a.category=i.category().trim();a.location=i.location().trim();a.description=i.description().trim();lab.log(actor,"EDIT_ASSET","ASSET-"+id,a.code+" · "+a.name);return a;
 }
 Asset retire(User actor,Long id,boolean retired){
  Security.staff(actor);Asset a=lab.lock(id);
  if(retired&&loans.findByAssetId(id).stream().anyMatch(l->Set.of("PENDING","APPROVED","CHECKED_OUT").contains(l.status)))throw LabService.bad("Xử lý hết phiếu chờ duyệt, đã duyệt và đang mượn trước khi ngừng sử dụng.");
  if(a.retired!=retired){a.retired=retired;lab.log(actor,retired?"RETIRE_ASSET":"RESTORE_ASSET","ASSET-"+id,a.code);}
  return a;
 }
 @Transactional(readOnly=true) Map<String,Object> report(User actor,LocalDate from,LocalDate to){
  Security.staff(actor);
  if(from.isAfter(to)||from.plusDays(365).isBefore(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn khoảng ngày hợp lệ, tối đa 366 ngày.");
  var zone=ZoneId.of("Asia/Ho_Chi_Minh");
  List<Loan> period=loans.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from.atStartOfDay(zone).toInstant(),to.plusDays(1).atStartOfDay(zone).toInstant());
  Map<String,Long> statuses=new LinkedHashMap<>();for(String s:List.of("PENDING","APPROVED","CHECKED_OUT","RETURNED","REJECTED","CANCELLED"))statuses.put(s,period.stream().filter(l->l.status.equals(s)).count());
  List<Map<String,Object>> daily=new ArrayList<>();for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){final LocalDate day=d;daily.add(Map.of("date",d.toString(),"count",period.stream().filter(l->l.createdAt.atZone(zone).toLocalDate().equals(day)).count()));}
  Map<Long,List<Loan>> groups=new HashMap<>();for(Loan l:period)groups.computeIfAbsent(l.asset.id,k->new ArrayList<>()).add(l);
  var top=groups.values().stream().sorted(Comparator.<List<Loan>>comparingInt(List::size).reversed().thenComparing(g->g.getFirst().asset.code)).limit(5).map(g->Map.<String,Object>of("code",g.getFirst().asset.code,"name",g.getFirst().asset.name,"count",g.size())).toList();
  var overdue=loans.findByStatusAndDueOnBefore("CHECKED_OUT",LabService.today()).stream().sorted(Comparator.comparing(l->l.dueOn)).toList();
  return Map.of("from",from,"to",to,"requests",period.size(),"statuses",statuses,"daily",daily,"topAssets",top,"overdue",overdue);
 }
}
