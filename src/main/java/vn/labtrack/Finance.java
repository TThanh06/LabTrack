package vn.labtrack;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
class Finance {
 final Security security; final FinanceService service;
 Finance(Security s,FinanceService f){security=s;service=f;}
 record RateInput(@NotNull @Min(0) @Max(10000000) Long dailyRate){}
 record PaymentInput(@NotNull @Positive Long amount,@NotBlank @Pattern(regexp="CASH|BANK_TRANSFER") String method,@NotNull @Size(max=120) String reference){}
 record RefundInput(@NotBlank @Size(max=500) String reason){}
 record CollateralInput(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=120) String identifier,@NotNull @Min(1) @Max(1000000000000L) Long estimatedValue,@NotBlank @Size(max=500) String condition,@NotBlank @Size(max=120) String storage){}
 record CollateralReturn(@NotBlank @Size(max=500) String note){}
 @PostMapping("/assets/{id}/rate") Asset rate(@PathVariable Long id,@Valid @RequestBody RateInput i,HttpServletRequest r){return service.rate(security.user(r),id,i.dailyRate());}
 @PostMapping("/loans/{id}/payment") Loan payment(@PathVariable Long id,@Valid @RequestBody PaymentInput i,HttpServletRequest r){return service.pay(security.user(r),id,i);}
 @PostMapping("/loans/{id}/refund") Loan refund(@PathVariable Long id,@Valid @RequestBody RefundInput i,HttpServletRequest r){return service.refund(security.user(r),id,i.reason());}
 @PostMapping("/loans/{id}/collateral") Loan collateral(@PathVariable Long id,@Valid @RequestBody CollateralInput i,HttpServletRequest r){return service.receive(security.user(r),id,i);}
 @PostMapping("/loans/{id}/collateral-return") Loan collateralReturn(@PathVariable Long id,@Valid @RequestBody CollateralReturn i,HttpServletRequest r){return service.release(security.user(r),id,i.note());}
 @GetMapping("/revenue") Map<String,Object> revenue(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,HttpServletRequest r){return service.revenue(security.user(r),from,to);}
}

@Service @Transactional
class FinanceService {
 private final LabService lab;private final Loans loans;
 FinanceService(LabService lab,Loans loans){this.lab=lab;this.loans=loans;}
 Loan lock(User actor,Long id){
  Security.staff(actor);Long assetId=loans.assetIdFor(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phiếu mượn."));
  lab.lock(assetId);return loans.findById(id).orElseThrow();
 }
 Asset rate(User actor,Long id,long dailyRate){Security.staff(actor);Asset a=lab.lock(id);a.rentalRate=dailyRate;lab.log(actor,"SET_RATE","ASSET-"+id,dailyRate+" VNĐ/ngày; không thay đổi phiếu cũ.");return a;}
 Loan pay(User actor,Long id,Finance.PaymentInput i){
  Loan l=lock(actor,id);
  if(l.paidAt!=null)throw LabService.bad("Phiếu đã ghi nhận thu phí; không thể thu trùng.");
  if(!Set.of("CHECKED_OUT","RETURNED").contains(l.status))throw LabService.bad("Chỉ thu phí khi đã bàn giao hoặc đã nhận trả thiết bị.");
  if(l.feeTotal==0||i.amount()!=l.feeTotal)throw LabService.bad("Số tiền phải đúng phí đã chốt: "+l.feeTotal+" VNĐ.");
  if(i.method().equals("BANK_TRANSFER")&&i.reference().isBlank())throw LabService.bad("Nhập mã tham chiếu giao dịch đã xác nhận.");
  l.paidAt=Instant.now();l.paidBy=actor;l.paymentMethod=i.method();l.paymentReference=i.reference().trim();
  lab.log(actor,"COLLECT_FEE","LOAN-"+id,l.feeTotal+" VNĐ · "+i.method());return l;
 }
 Loan refund(User actor,Long id,String reason){
  Loan l=lock(actor,id);if(l.paidAt==null||l.refundedAt!=null)throw LabService.bad("Chỉ hoàn phí một lần cho phiếu đã thu tiền.");
  l.refundedAt=Instant.now();l.refundedBy=actor;l.refundReason=reason.trim();
  lab.log(actor,"REFUND_FEE","LOAN-"+id,l.feeTotal+" VNĐ · "+l.refundReason);return l;
 }
 Loan receive(User actor,Long id,Finance.CollateralInput i){
  Loan l=lock(actor,id);
  if(!l.status.equals("APPROVED"))throw LabService.bad("Chỉ nhận tài sản bảo đảm cho phiếu đã duyệt, chưa bàn giao.");
  if(l.collateralReceivedAt!=null)throw LabService.bad("Phiếu đã ghi nhận tài sản bảo đảm.");
  l.collateralName=i.name().trim();l.collateralIdentifier=i.identifier().trim();l.collateralValue=i.estimatedValue();l.collateralCondition=i.condition().trim();l.collateralStorage=i.storage().trim();l.collateralReceivedAt=Instant.now();l.collateralReceivedBy=actor;
  lab.log(actor,"RECEIVE_COLLATERAL","LOAN-"+id,l.collateralName+" · "+l.collateralIdentifier+" · giá trị ước tính "+l.collateralValue+" VNĐ");return l;
 }
 Loan release(User actor,Long id,String note){
  Loan l=lock(actor,id);
  if(l.collateralReceivedAt==null||l.collateralReturnedAt!=null)throw LabService.bad("Không có tài sản đang giữ cần hoàn trả.");
  if(!Set.of("APPROVED","RETURNED").contains(l.status))throw LabService.bad("Nhận trả thiết bị trước khi hoàn trả tài sản bảo đảm.");
  if(l.status.equals("APPROVED")){l.status="CANCELLED";l.updatedAt=Instant.now();lab.log(actor,"CANCEL_HANDOVER","LOAN-"+id,"Hoàn tài sản trước khi bàn giao. "+note.trim());}
  l.collateralReturnedAt=Instant.now();l.collateralReturnedBy=actor;l.collateralReturnNote=note.trim();
  lab.log(actor,"RETURN_COLLATERAL","LOAN-"+id,l.collateralReturnNote);return l;
 }
 @Transactional(readOnly=true) Map<String,Object> revenue(User actor,LocalDate from,LocalDate to){
  Security.staff(actor);if(from.isAfter(to)||from.plusDays(365).isBefore(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn khoảng ngày hợp lệ, tối đa 366 ngày.");
  ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");Instant begin=from.atStartOfDay(zone).toInstant(),end=to.plusDays(1).atStartOfDay(zone).toInstant();
  var paid=loans.findByPaidAtGreaterThanEqualAndPaidAtLessThan(begin,end);var refunds=loans.findByRefundedAtGreaterThanEqualAndRefundedAtLessThan(begin,end);
  long collected=paid.stream().mapToLong(l->l.feeTotal).sum(),refunded=refunds.stream().mapToLong(l->l.feeTotal).sum();
  List<Map<String,Object>> daily=new ArrayList<>(),entries=new ArrayList<>();
  for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){final LocalDate day=d;long p=paid.stream().filter(l->l.paidAt.atZone(zone).toLocalDate().equals(day)).mapToLong(l->l.feeTotal).sum();long f=refunds.stream().filter(l->l.refundedAt.atZone(zone).toLocalDate().equals(day)).mapToLong(l->l.feeTotal).sum();daily.add(Map.of("date",d.toString(),"collected",p,"refunded",f,"net",p-f));}
  for(Loan l:paid)entries.add(Map.of("loanId",l.id,"assetName",l.assetName,"borrower",l.borrower.name,"type","COLLECT","amount",l.feeTotal,"at",l.paidAt,"staff",l.paidBy.name));
  for(Loan l:refunds)entries.add(Map.of("loanId",l.id,"assetName",l.assetName,"borrower",l.borrower.name,"type","REFUND","amount",-l.feeTotal,"at",l.refundedAt,"staff",l.refundedBy.name));
  entries.sort(Comparator.comparing(e->(Instant)e.get("at"),Comparator.reverseOrder()));
  var unpaid=loans.findByStatusInAndPaidAtIsNull(List.of("CHECKED_OUT","RETURNED")).stream().filter(l->l.feeTotal>0).toList();
  var held=loans.findByCollateralReceivedAtIsNotNullAndCollateralReturnedAtIsNull();
  return Map.of("from",from,"to",to,"collected",collected,"refunded",refunded,"net",collected-refunded,"daily",daily,"entries",entries,"unpaid",unpaid,"heldCollateral",held);
 }
}
