package vn.labtrack;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:labtrack_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.profiles.active=demo"})
class LabTrackIntegrationTest {
 @Autowired TestRestTemplate http;
 @Autowired Assets assets;
 @Autowired Users users;
 @Autowired Loans loanRepo;
 String student, staff, other;
 Long assetId;
 @BeforeEach void setup(){
  student=login("student@labtrack.local","Student@123");staff=login("staff@labtrack.local","LabTrack@123");
  if(users.findByEmail("other@labtrack.local").isEmpty()){User u=new User();u.email="other@labtrack.local";u.name="Sinh viên khác";u.role="STUDENT";u.passwordHash=new BCryptPasswordEncoder().encode("OtherUser@123");users.save(u);}
  other=login("other@labtrack.local","OtherUser@123");
  Asset a=new Asset();a.code="TEST-"+UUID.randomUUID().toString().substring(0,12);a.name="Thiết bị kiểm thử";a.category="Laptop";a.location="Kho test";a.description="Kèm sạc";a.state="AVAILABLE";assetId=assets.save(a).id;
 }
 ResponseEntity<JsonNode> call(String method,String path,String cookie,Object body){
  HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("X-LabTrack","1");if(cookie!=null)h.set("Cookie",cookie);
  return http.exchange("/api"+path,HttpMethod.valueOf(method),new HttpEntity<>(body,h),JsonNode.class);
 }
 String login(String email,String password){var r=call("POST","/login",null,Map.of("email",email,"password",password));assertEquals(200,r.getStatusCode().value());return r.getHeaders().getFirst("Set-Cookie").split(";")[0];}
 long request(String cookie,LocalDate start,LocalDate end){var r=call("POST","/loans",cookie,Map.of("assetId",assetId,"startOn",start.toString(),"dueOn",end.toString(),"purpose","Thực hiện đồ án","expectedFee",assets.findById(assetId).orElseThrow().rentalRate*(java.time.temporal.ChronoUnit.DAYS.between(start,end)+1)));assertEquals(200,r.getStatusCode().value(),r.toString());return r.getBody().get("id").asLong();}
 ResponseEntity<JsonNode> action(long id,String cookie,String action){if(action.equals("checkout"))call("POST","/loans/"+id+"/collateral",staff,Map.of("name","Tai nghe kiểm thử","identifier","QA-"+id,"estimatedValue",500000,"condition","Đầy đủ, hoạt động tốt","storage","Tủ A"));return call("POST","/loans/"+id+"/action",cookie,Map.of("action",action,"note","Thiết bị và phụ kiện đầy đủ"));}
 @Test void authenticationAndRoleBoundaries(){
  assertEquals(401,call("GET","/assets",null,null).getStatusCode().value());
  assertEquals(401,call("POST","/login",null,Map.of("email","student@labtrack.local","password","wrong")).getStatusCode().value());
  var me=call("GET","/me",student,null);assertEquals("STUDENT",me.getBody().get("role").asText());assertFalse(me.getBody().has("passwordHash"));
  assertEquals(403,call("GET","/audit",student,null).getStatusCode().value());
  call("POST","/logout",student,Map.of());assertEquals(401,call("GET","/me",student,null).getStatusCode().value());
 }
 @Test void csrfHeadersAndOriginsAreRequired(){
  var body=Map.of("email","student@labtrack.local","password","Student@123");
  assertEquals(403,http.postForEntity("/api/login",body,JsonNode.class).getStatusCode().value());
  HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("X-LabTrack","1");h.set("Origin","https://untrusted.example");
  assertEquals(403,http.postForEntity("/api/login",new HttpEntity<>(body,h),JsonNode.class).getStatusCode().value());
 }
 @Test void validateDatesAndPreventDuplicateRequests(){
  LocalDate day=LabService.today();
  assertEquals(409,call("POST","/loans",student,Map.of("assetId",assetId,"startOn",day.minusDays(1).toString(),"dueOn",day.toString(),"purpose","Test")).getStatusCode().value());
  request(student,day,day.plusDays(2));
  assertEquals(409,call("POST","/loans",student,Map.of("assetId",assetId,"startOn",day.toString(),"dueOn",day.toString(),"purpose","Test")).getStatusCode().value());
 }
 @Test void completeLoanLifecycleAndAudit(){
  long id=request(student,LabService.today(),LabService.today().plusDays(1));
  assertEquals(409,action(id,staff,"return").getStatusCode().value());
  assertEquals(403,action(id,student,"approve").getStatusCode().value());
  assertEquals(200,action(id,staff,"approve").getStatusCode().value());
  assertEquals(200,action(id,staff,"checkout").getStatusCode().value());
  assertEquals(409,action(id,student,"cancel").getStatusCode().value());
  var returned=action(id,staff,"return");assertEquals("RETURNED",returned.getBody().get("status").asText());
  assertEquals(409,action(id,staff,"return").getStatusCode().value());
  assertTrue(call("GET","/audit",staff,null).getBody().toString().contains("LOAN-"+id));
 }
 @Test void preventOverlappingApprovalsButAllowAdjacentDates(){
  LocalDate day=LabService.today();long one=request(student,day,day.plusDays(1)),two=request(other,day.plusDays(1),day.plusDays(2));
  assertEquals(200,action(one,staff,"approve").getStatusCode().value());assertEquals(409,action(two,staff,"approve").getStatusCode().value());
  assertEquals(200,action(two,other,"cancel").getStatusCode().value());
  long next=request(other,day.plusDays(2),day.plusDays(3));assertEquals(200,action(next,staff,"approve").getStatusCode().value());
  assertEquals(409,action(next,staff,"checkout").getStatusCode().value());
 }
 @Test void concurrentApprovalsOnlyOneWins() throws Exception {
  long one=request(student,LabService.today(),LabService.today()),two=request(other,LabService.today(),LabService.today());
  try(var executor=Executors.newFixedThreadPool(2)){
   CountDownLatch start=new CountDownLatch(1);
   Future<Integer> a=executor.submit(()->{start.await();return action(one,staff,"approve").getStatusCode().value();});
   Future<Integer> b=executor.submit(()->{start.await();return action(two,staff,"approve").getStatusCode().value();});start.countDown();
   var codes=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
  }
 }
 @Test void ownershipAndPublicScheduleDoNotLeakBorrowers(){
  long id=request(student,LabService.today(),LabService.today());assertEquals(403,action(id,other,"cancel").getStatusCode().value());
  assertFalse(call("GET","/loans",other,null).getBody().toString().contains("\"id\":"+id+",\"asset\""));
  action(id,staff,"approve");var schedule=call("GET","/assets/"+assetId+"/schedule",other,null).getBody();
  assertEquals(1,schedule.size());assertFalse(schedule.toString().contains("email"));assertFalse(schedule.toString().contains("purpose"));
 }
 @Test void damageStopsCheckoutAndRequiresAllIssuesResolved(){
  long id=request(student,LabService.today(),LabService.today());action(id,staff,"approve");
  assertEquals(403,call("POST","/issues",student,Map.of("assetId",assetId,"description","Không được báo khi chưa nhận")).getStatusCode().value());
  var i1=call("POST","/issues",staff,Map.of("assetId",assetId,"description","Sạc hỏng")).getBody().get("id").asLong();
  var i2=call("POST","/issues",staff,Map.of("assetId",assetId,"description","Màn hình lỗi")).getBody().get("id").asLong();
  assertEquals(409,action(id,staff,"checkout").getStatusCode().value());
  call("POST","/issues/"+i1+"/resolve",staff,Map.of("resolution","Thay sạc"));assertEquals("MAINTENANCE",assets.findById(assetId).orElseThrow().state);
  call("POST","/issues/"+i2+"/resolve",staff,Map.of("resolution","Thay màn hình"));assertEquals("AVAILABLE",assets.findById(assetId).orElseThrow().state);
  assertEquals(200,action(id,staff,"checkout").getStatusCode().value());
  assertEquals(200,call("POST","/issues",student,Map.of("assetId",assetId,"description","Lỗi trong khi dùng")).getStatusCode().value());
  assertEquals(200,action(id,staff,"return").getStatusCode().value());assertEquals("MAINTENANCE",assets.findById(assetId).orElseThrow().state);
 }
 @Test void assetsRequireStaffAndUniqueCode(){
  var body=Map.of("code","NEW-"+UUID.randomUUID().toString().substring(0,8),"name","Thiết bị mới","category","Laptop","location","B201","description","Kèm sạc");
  assertEquals(403,call("POST","/assets",student,body).getStatusCode().value());assertEquals(200,call("POST","/assets",staff,body).getStatusCode().value());assertEquals(409,call("POST","/assets",staff,body).getStatusCode().value());
 }

 @Test void studentManagementAndSessionRevocation(){
  String email="new-"+UUID.randomUUID()+"@labtrack.local";
  var body=Map.of("email",email,"name","Sinh viên mới","password","NewStudent@123","role","STAFF");
  assertEquals(403,call("POST","/users",student,body).getStatusCode().value());
  var created=call("POST","/users",staff,body);assertEquals(200,created.getStatusCode().value());
  assertEquals("STUDENT",created.getBody().get("role").asText());assertFalse(created.getBody().has("passwordHash"));assertFalse(created.getBody().has("authVersion"));
  long uid=created.getBody().get("id").asLong();String cookie=login(email,"NewStudent@123");long pendingId=request(cookie,LabService.today(),LabService.today());
  assertEquals(403,call("GET","/users",student,null).getStatusCode().value());
  assertEquals(200,call("POST","/users/"+uid+"/active",staff,Map.of("active",false)).getStatusCode().value());
  assertEquals(401,call("GET","/me",cookie,null).getStatusCode().value());
  assertEquals(409,action(pendingId,staff,"approve").getStatusCode().value());
  assertEquals(401,call("POST","/login",null,Map.of("email",email,"password","NewStudent@123")).getStatusCode().value());
  call("POST","/users/"+uid+"/active",staff,Map.of("active",true));assertEquals(401,call("GET","/me",cookie,null).getStatusCode().value());
  login(email,"NewStudent@123");
  long staffId=users.findByEmail("staff@labtrack.local").orElseThrow().id;
  assertEquals(403,call("POST","/users/"+staffId+"/active",staff,Map.of("active",false)).getStatusCode().value());
 }
 @Test void passwordChangeExpiresAllSessionsAndRejectsLongUtf8(){
  String email="pass-"+UUID.randomUUID()+"@labtrack.local";
  call("POST","/users",staff,Map.of("email",email,"name","Đổi mật khẩu","password","OldPassword@123"));
  String c1=login(email,"OldPassword@123"),c2=login(email,"OldPassword@123");
  assertEquals(400,call("POST","/me/password",c1,Map.of("currentPassword","wrong","newPassword","NewPassword@123")).getStatusCode().value());
  assertEquals(400,call("POST","/me/password",c1,Map.of("currentPassword","OldPassword@123","newPassword","ệ".repeat(30))).getStatusCode().value());
  assertEquals(200,call("POST","/me/password",c1,Map.of("currentPassword","OldPassword@123","newPassword","NewPassword@123")).getStatusCode().value());
  assertEquals(401,call("GET","/me",c2,null).getStatusCode().value());
  assertEquals(401,call("POST","/login",null,Map.of("email",email,"password","OldPassword@123")).getStatusCode().value());
  login(email,"NewPassword@123");
  assertFalse(call("GET","/audit",staff,null).getBody().toString().contains("NewPassword@123"));
 }
 @Test void assetEditsPreserveLoanSnapshotsAndRetirementBlocksNewLoans(){
  String oldName=assets.findById(assetId).orElseThrow().name;long id=request(student,LabService.today(),LabService.today());
  var a=assets.findById(assetId).orElseThrow();var edit=Map.of("code",a.code,"name","Tên thiết bị mới","category",a.category,"location","Kho khác","description","Đã chỉnh sửa");
  assertEquals(403,call("POST","/assets/"+assetId+"/edit",student,edit).getStatusCode().value());
  assertEquals(200,call("POST","/assets/"+assetId+"/edit",staff,edit).getStatusCode().value());
  var list=call("GET","/loans",student,null).getBody();boolean found=false;for(JsonNode l:list)if(l.get("id").asLong()==id){assertEquals(oldName,l.get("assetName").asText());found=true;}assertTrue(found);
  assertEquals(409,call("POST","/assets/"+assetId+"/retired",staff,Map.of("retired",true)).getStatusCode().value());
  action(id,student,"cancel");assertEquals(200,call("POST","/assets/"+assetId+"/retired",staff,Map.of("retired",true)).getStatusCode().value());
  assertEquals(409,call("POST","/loans",student,Map.of("assetId",assetId,"startOn",LabService.today().toString(),"dueOn",LabService.today().toString(),"purpose","Test")).getStatusCode().value());
  call("POST","/assets/"+assetId+"/retired",staff,Map.of("retired",false));request(student,LabService.today(),LabService.today());
 }
 @Test void reportPermissionsDateRangeAndCounts(){
  String path="/reports?from="+LabService.today()+"&to="+LabService.today();
  assertEquals(403,call("GET",path,student,null).getStatusCode().value());
  var before=call("GET",path,staff,null);assertEquals(200,before.getStatusCode().value());int count=before.getBody().get("requests").asInt();
  request(student,LabService.today(),LabService.today());var r=call("GET",path,staff,null).getBody();assertEquals(count+1,r.get("requests").asInt());assertEquals(count+1,r.get("daily").get(0).get("count").asInt());
  int sum=0;var vals=r.get("statuses").elements();while(vals.hasNext())sum+=vals.next().asInt();assertEquals(count+1,sum);
  assertEquals(400,call("GET","/reports?from=2026-12-01&to=2026-01-01",staff,null).getStatusCode().value());
  assertEquals(400,call("GET","/reports?from=2026-02-30&to=2026-03-01",staff,null).getStatusCode().value());
 }

 @Test void reportUsesVietnamDayBoundaryAndListsGlobalOverdue(){
  var day=LabService.today();String path="/reports?from="+day+"&to="+day;
  int baseline=call("GET",path,staff,null).getBody().get("requests").asInt();
  long id=request(student,day,day);Loan loan=loanRepo.findById(id).orElseThrow();
  loan.createdAt=day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().minusSeconds(1);loan.startOn=day.minusDays(2);loan.dueOn=day.minusDays(1);loan.status="CHECKED_OUT";loanRepo.save(loan);
  var r=call("GET",path,staff,null).getBody();assertEquals(baseline,r.get("requests").asInt());boolean found=false;for(JsonNode l:r.get("overdue"))if(l.get("id").asLong()==id)found=true;assertTrue(found);
  loan.createdAt=day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();loanRepo.save(loan);assertEquals(baseline+1,call("GET",path,staff,null).getBody().get("requests").asInt());
 }

 Map<String,Object> collateral(){return Map.of("name","Đồng hồ","identifier","WATCH-TEST","estimatedValue",2000000,"condition","Mặt kính nguyên vẹn","storage","Tủ A, ngăn 3");}
 Map<String,Object> payment(long amount){return Map.of("amount",amount,"method","CASH","reference","");}
 @Test void dailyFeeSnapshotAndMandatoryCollateralEvenForFreeLoans(){
  assertEquals(403,call("POST","/assets/"+assetId+"/rate",student,Map.of("dailyRate",25000)).getStatusCode().value());
  assertEquals(200,call("POST","/assets/"+assetId+"/rate",staff,Map.of("dailyRate",25000)).getStatusCode().value());
  var day=LabService.today();
  assertEquals(409,call("POST","/loans",student,Map.of("assetId",assetId,"startOn",day.toString(),"dueOn",day.plusDays(2).toString(),"purpose","Sai giá","expectedFee",1)).getStatusCode().value());
  long id=request(student,day,day.plusDays(2));Loan l=loanRepo.findById(id).orElseThrow();assertEquals(75000,l.feeTotal);assertEquals(3,l.rentalUnits);assertTrue(l.collateralRequired);
  call("POST","/assets/"+assetId+"/rate",staff,Map.of("dailyRate",50000));assertEquals(75000,loanRepo.findById(id).orElseThrow().feeTotal);
  assertEquals(409,call("POST","/loans/"+id+"/payment",staff,payment(75000)).getStatusCode().value());action(id,staff,"approve");
  assertEquals(409,call("POST","/loans/"+id+"/action",staff,Map.of("action","checkout","note","")).getStatusCode().value());
  assertEquals(403,call("POST","/loans/"+id+"/collateral",student,collateral()).getStatusCode().value());
  assertEquals(200,call("POST","/loans/"+id+"/collateral",staff,collateral()).getStatusCode().value());
  assertEquals(409,action(id,student,"cancel").getStatusCode().value());
  assertEquals(200,action(id,staff,"checkout").getStatusCode().value());
  assertEquals(409,call("POST","/loans/"+id+"/collateral-return",staff,Map.of("note","Trả sớm")).getStatusCode().value());
  action(id,staff,"return");assertEquals(200,call("POST","/loans/"+id+"/collateral-return",staff,Map.of("note","Đã trao đủ cho sinh viên, nguyên vẹn")).getStatusCode().value());
  assertEquals(409,call("POST","/loans/"+id+"/collateral-return",staff,Map.of("note","Trả lần nữa")).getStatusCode().value());
 }
 @Test void cashCollectionIsExactAndConcurrentRequestsDoNotDoubleCount() throws Exception {
  call("POST","/assets/"+assetId+"/rate",staff,Map.of("dailyRate",20000));long id=request(student,LabService.today(),LabService.today());action(id,staff,"approve");action(id,staff,"checkout");
  assertEquals(403,call("POST","/loans/"+id+"/payment",student,payment(20000)).getStatusCode().value());
  assertEquals(409,call("POST","/loans/"+id+"/payment",staff,payment(10000)).getStatusCode().value());
  assertEquals(400,call("POST","/loans/"+id+"/payment",staff,Map.of("amount",20000.5,"method","CASH","reference","")).getStatusCode().value());
  try(var executor=Executors.newFixedThreadPool(2)){CountDownLatch start=new CountDownLatch(1);
   var a=executor.submit(()->{start.await();return call("POST","/loans/"+id+"/payment",staff,payment(20000)).getStatusCode().value();});
   var b=executor.submit(()->{start.await();return call("POST","/loans/"+id+"/payment",staff,payment(20000)).getStatusCode().value();});start.countDown();
   var codes=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
  }
  String path="/revenue?from="+LabService.today()+"&to="+LabService.today();var result=call("GET",path,staff,null);assertEquals(200,result.getStatusCode().value());
  long entries=0;for(JsonNode row:result.getBody().get("entries"))if(row.get("loanId").asLong()==id){entries++;assertEquals(20000,row.get("amount").asLong());}assertEquals(1,entries);
  assertEquals(403,call("GET",path,student,null).getStatusCode().value());
 }
 @Test void refundsUseTheirOwnDateAndCollateralIsNotRevenue(){
  call("POST","/assets/"+assetId+"/rate",staff,Map.of("dailyRate",30000));long id=request(student,LabService.today(),LabService.today());action(id,staff,"approve");action(id,staff,"checkout");
  call("POST","/loans/"+id+"/payment",staff,payment(30000));Loan l=loanRepo.findById(id).orElseThrow();l.paidAt=LabService.today().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().minusSeconds(1);loanRepo.save(l);
  String path="/revenue?from="+LabService.today()+"&to="+LabService.today();var before=call("GET",path,staff,null).getBody();
  assertEquals(200,call("POST","/loans/"+id+"/refund",staff,Map.of("reason","Hoàn phí theo xác nhận của nhân viên")).getStatusCode().value());
  assertEquals(409,call("POST","/loans/"+id+"/refund",staff,Map.of("reason","Lặp")).getStatusCode().value());
  var after=call("GET",path,staff,null).getBody();assertEquals(before.get("collected").asLong(),after.get("collected").asLong());assertEquals(before.get("refunded").asLong()+30000,after.get("refunded").asLong());assertEquals(before.get("net").asLong()-30000,after.get("net").asLong());
  long sum=0;for(JsonNode e:after.get("entries"))sum+=e.get("amount").asLong();assertEquals(sum,after.get("net").asLong());
  boolean held=false;for(JsonNode h:after.get("heldCollateral"))if(h.get("id").asLong()==id)held=true;assertTrue(held);
 }
 @Test void returningCollateralBeforeCheckoutCancelsApprovedLoan(){
  long id=request(student,LabService.today(),LabService.today());action(id,staff,"approve");
  assertEquals(409,call("POST","/loans/"+id+"/action",staff,Map.of("action","checkout","note","")).getStatusCode().value());
  call("POST","/loans/"+id+"/collateral",staff,collateral());
  var release=call("POST","/loans/"+id+"/collateral-return",staff,Map.of("note","Sinh viên không nhận thiết bị, đã nhận lại đồ"));assertEquals(200,release.getStatusCode().value());assertEquals("CANCELLED",release.getBody().get("status").asText());assertNotNull(release.getBody().get("collateralReturnedAt"));
  assertEquals(409,action(id,staff,"checkout").getStatusCode().value());
 }
}
