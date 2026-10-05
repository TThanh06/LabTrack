package vn.labtrack;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface Users extends JpaRepository<User,Long> {
 Optional<User> findByEmail(String email);
 List<User> findAllByOrderByIdDesc();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.id=:id") Optional<User> lockById(Long id);
}
interface Assets extends JpaRepository<Asset,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Asset a where a.id=:id") Optional<Asset> lockById(Long id);
 List<Asset> findAllByOrderByCodeAsc();
}
interface Loans extends JpaRepository<Loan,Long> {
 List<Loan> findByPaidAtGreaterThanEqualAndPaidAtLessThan(java.time.Instant from,java.time.Instant to);
 List<Loan> findByRefundedAtGreaterThanEqualAndRefundedAtLessThan(java.time.Instant from,java.time.Instant to);
 List<Loan> findByCollateralReceivedAtIsNotNullAndCollateralReturnedAtIsNull();
 List<Loan> findByStatusInAndPaidAtIsNull(Collection<String> statuses);
 @Query("select l.asset.id from Loan l where l.id=:id") Optional<Long> assetIdFor(Long id);
 List<Loan> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from,java.time.Instant to);
 List<Loan> findByStatusAndDueOnBefore(String status,java.time.LocalDate day);
 List<Loan> findByAssetId(Long id);
 List<Loan> findByBorrowerIdOrderByIdDesc(Long id);
 List<Loan> findAllByOrderByIdDesc();
}
interface Issues extends JpaRepository<Issue,Long> {
 @Query("select i.asset.id from Issue i where i.id=:id") Optional<Long> assetIdFor(Long id);
 boolean existsByAssetIdAndStatus(Long id,String status);
 List<Issue> findByReporterIdOrderByIdDesc(Long id);
 List<Issue> findAllByOrderByIdDesc();
}
interface Audits extends JpaRepository<AuditEvent,Long> { List<AuditEvent> findTop100ByOrderByIdDesc(); }
