package vn.labtrack;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
class LabService {
 final Assets assets; final Loans loans; final Issues issues; final Audits audits;
 LabService(Assets a,Loans l,Issues i,Audits au) { assets=a;loans=l;issues=i;audits=au; }
 @Transactional(readOnly=true) List<Asset> listAssets(){return assets.findAllByOrderByCodeAsc();}
 @Transactional(readOnly=true) List<Loan> listLoans(User u){return u.role.equals("STAFF")?loans.findAllByOrderByIdDesc():loans.findByBorrowerIdOrderByIdDesc(u.id);}
 @Transactional(readOnly=true) List<Issue> listIssues(User u){return u.role.equals("STAFF")?issues.findAllByOrderByIdDesc():issues.findByReporterIdOrderByIdDesc(u.id);}
 @Transactional(readOnly=true) List<AuditEvent> listAudit(){return audits.findTop100ByOrderByIdDesc();}
 @Transactional(readOnly=true) List<Map<String,Object>> schedule(Long id){return loans.findByAssetId(id).stream().filter(l->Set.of("APPROVED","CHECKED_OUT").contains(l.status)).map(l->Map.<String,Object>of("startOn",l.startOn,"dueOn",l.dueOn,"status",l.status)).toList();}
 static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")); }
 static ResponseStatusException bad(String msg) { return new ResponseStatusException(HttpStatus.CONFLICT,msg); }
 Asset lock(long id) { return assets.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thiết bị.")); }
 void log(User actor,String action,String target,String detail) {
  AuditEvent e=new AuditEvent(); e.actor=actor;e.action=action;e.target=target;e.detail=detail;e.createdAt=Instant.now();audits.save(e);
 }
 Asset createAsset(User user, Api.AssetInput input) {
  Security.staff(user); Asset a=new Asset();a.code=input.code().trim().toUpperCase(Locale.ROOT);a.name=input.name().trim();
  a.category=input.category().trim();a.location=input.location().trim();a.description=input.description().trim();a.state="AVAILABLE";
  assets.saveAndFlush(a);log(user,"CREATE_ASSET","ASSET-"+a.id,a.code);return a;
 }
 Loan request(User user,Api.LoanInput input) {
  if(!"STUDENT".equals(user.role)) throw bad("Dùng tài khoản sinh viên để gửi yêu cầu mượn.");
  Asset a=lock(input.assetId());
  if(a.retired||!a.state.equals("AVAILABLE")) throw bad("Thiết bị đã ngừng sử dụng hoặc đang bảo trì.");
  if(input.startOn().isBefore(today()) || input.dueOn().isBefore(input.startOn()) || input.dueOn().isAfter(input.startOn().plusDays(30)))
   throw bad("Chọn ngày mượn từ hôm nay; thời gian mượn tối đa 30 ngày.");
  if(loans.findByAssetId(a.id).stream().anyMatch(l->!Set.of("REJECTED","CANCELLED","RETURNED").contains(l.status)&&l.borrower.id.equals(user.id)&&!l.startOn.isAfter(input.dueOn())&&!l.dueOn.isBefore(input.startOn())))
   throw bad("Bạn đã gửi yêu cầu cho thiết bị trong thời gian này.");
  Loan l=new Loan();l.asset=a;l.borrower=user;l.startOn=input.startOn();l.dueOn=input.dueOn();l.purpose=input.purpose().trim();l.status="PENDING";l.createdAt=l.updatedAt=Instant.now();
  l.assetName=a.name;l.assetCode=a.code;
  l.rentalRate=a.rentalRate;l.rentalUnits=Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(l.startOn,l.dueOn)+1);
  l.feeTotal=Math.multiplyExact(l.rentalRate,l.rentalUnits);l.collateralRequired=true;
  if((input.expectedFee()==null&&l.feeTotal!=0)||(input.expectedFee()!=null&&input.expectedFee()!=l.feeTotal))throw bad("Phí mượn đã thay đổi. Hãy mở lại thiết bị để kiểm tra và xác nhận mức phí mới.");
  loans.save(l);log(user,"REQUEST","LOAN-"+l.id,l.purpose);return l;
 }
 Loan transition(User user,long id,Api.ActionInput input) {
  // Always acquire the asset lock before loading mutable loan state. Approval, returns and repairs share this lock.
  Long assetId=loans.assetIdFor(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phiếu mượn."));
  Asset a=lock(assetId);
  Loan l=loans.findById(id).orElseThrow();
  String action=input.action();
  if(action.equals("cancel")) {
   if(!l.borrower.id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không phải phiếu mượn của bạn.");
   if(!Set.of("PENDING","APPROVED").contains(l.status)) throw bad("Phiếu này không thể hủy.");
   if(l.collateralReceivedAt!=null&&l.collateralReturnedAt==null)throw bad("Nhân viên cần hoàn trả tài sản bảo đảm và hủy bàn giao trước.");
   l.status="CANCELLED";
  } else {
   Security.staff(user);
   switch(action) {
    case "approve" -> {
     require(l,"PENDING");
     if(l.dueOn.isBefore(today())) throw bad("Yêu cầu đã quá hạn mượn.");
     if(a.retired||!a.state.equals("AVAILABLE")) throw bad("Thiết bị đã ngừng sử dụng hoặc đang bảo trì.");
     if(!l.borrower.active)throw bad("Tài khoản người mượn đã bị khóa.");
     if(loans.findByAssetId(assetId).stream().anyMatch(other->!other.id.equals(l.id)&&Set.of("APPROVED","CHECKED_OUT").contains(other.status)&&!other.startOn.isAfter(l.dueOn)&&!other.dueOn.isBefore(l.startOn)))
      throw bad("Trùng lịch với phiếu đã duyệt. Hãy chọn thiết bị hoặc thời gian khác.");
     l.status="APPROVED";
    }
    case "reject" -> {require(l,"PENDING"); if(input.note().isBlank())throw bad("Nhập lý do từ chối."); l.status="REJECTED";l.returnNote=input.note().trim();}
    case "checkout" -> {
     require(l,"APPROVED");
     if(l.collateralRequired&&(l.collateralReceivedAt==null||l.collateralReturnedAt!=null))throw bad("Phải ghi nhận tài sản bảo đảm trước khi bàn giao thiết bị.");
     if(a.retired||!a.state.equals("AVAILABLE"))throw bad("Thiết bị đã ngừng sử dụng hoặc đang báo hỏng, không thể bàn giao.");
     if(!l.borrower.active)throw bad("Tài khoản người mượn đã bị khóa.");
     if(today().isBefore(l.startOn)||today().isAfter(l.dueOn))throw bad("Chỉ bàn giao trong khoảng ngày đã đăng ký.");
     if(loans.findByAssetId(assetId).stream().anyMatch(other->other.status.equals("CHECKED_OUT")))throw bad("Thiết bị vẫn chưa được trả từ lượt mượn trước.");
     l.status="CHECKED_OUT";
    }
    case "return" -> {require(l,"CHECKED_OUT");if(input.note().isBlank())throw bad("Ghi tình trạng khi nhận trả.");l.status="RETURNED";l.returnNote=input.note().trim();}
    default -> throw bad("Thao tác không hợp lệ.");
   }
  }
  l.updatedAt=Instant.now();log(user,action.toUpperCase(Locale.ROOT),"LOAN-"+id,input.note().trim());return loans.save(l);
 }
 void require(Loan l,String status) { if(!l.status.equals(status)) throw bad("Trạng thái đã thay đổi. Hãy tải lại danh sách."); }
 Issue report(User user,Api.IssueInput input) {
  Asset a=lock(input.assetId());
  if(!user.role.equals("STAFF")&&!loans.findByAssetId(a.id).stream().anyMatch(l->l.borrower.id.equals(user.id)&&l.status.equals("CHECKED_OUT")))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn chỉ được báo hỏng thiết bị đang mượn.");
  Issue i=new Issue();i.asset=a;i.reporter=user;i.description=input.description().trim();i.status="OPEN";i.createdAt=Instant.now();
  issues.save(i);a.state="MAINTENANCE";log(user,"REPORT_ISSUE","ISSUE-"+i.id,i.description);return i;
 }
 Issue resolve(User user,long id,Api.ResolutionInput input) {
  Security.staff(user);
  Long assetId=issues.assetIdFor(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy báo hỏng."));
  lock(assetId);
  Issue i=issues.findById(id).orElseThrow();
  if(!i.status.equals("OPEN"))throw bad("Phiếu báo hỏng đã được xử lý.");
  i.status="RESOLVED";i.resolution=input.resolution().trim();i.resolvedAt=Instant.now();issues.saveAndFlush(i);
  if(!issues.existsByAssetIdAndStatus(assetId,"OPEN"))i.asset.state="AVAILABLE";
  log(user,"RESOLVE_ISSUE","ISSUE-"+id,i.resolution);return i;
 }
}
