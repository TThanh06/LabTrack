package vn.labtrack;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.*;

@Entity @Table(name="app_users")
class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String email;
 @Column(name="display_name") public String name;
 @JsonIgnore public String passwordHash;
 public String role;
 public boolean active=true;
 @JsonIgnore public long authVersion=0;
}
@Entity @Table(name="assets")
class Asset {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String code, name, category, location;
 @Column(length=800) public String description;
 public String state;
 public boolean retired=false;
 public long rentalRate=0;
}
@Entity @Table(name="loans")
class Loan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="asset_id") public Asset asset;
 @ManyToOne(optional=false) @JoinColumn(name="user_id") public User borrower;
 public LocalDate startOn, dueOn;
 @Column(length=120) public String assetName;
 @Column(length=40) public String assetCode;
 @Column(length=500) public String purpose;
 public String status;
 public Instant createdAt, updatedAt;
 @Column(length=500) public String returnNote;
 public long rentalRate=0, feeTotal=0;
 public int rentalUnits=1;
 public boolean collateralRequired=true;
 public Instant paidAt, refundedAt;
 @ManyToOne @JoinColumn(name="paid_by_id") public User paidBy;
 @ManyToOne @JoinColumn(name="refunded_by_id") public User refundedBy;
 public String paymentMethod;
 @Column(length=120) public String paymentReference;
 @Column(length=500) public String refundReason;
 @Column(length=120) public String collateralName, collateralIdentifier, collateralStorage;
 public Long collateralValue;
 @Column(length=500) public String collateralCondition, collateralReturnNote;
 public Instant collateralReceivedAt, collateralReturnedAt;
 @ManyToOne @JoinColumn(name="collateral_received_by_id") public User collateralReceivedBy;
 @ManyToOne @JoinColumn(name="collateral_returned_by_id") public User collateralReturnedBy;
}
@Entity @Table(name="issues")
class Issue {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="asset_id") public Asset asset;
 @ManyToOne(optional=false) @JoinColumn(name="reporter_id") public User reporter;
 @Column(length=1000) public String description;
 public String status;
 public Instant createdAt, resolvedAt;
 @Column(length=1000) public String resolution;
}
@Entity @Table(name="audit_events")
class AuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="actor_id") public User actor;
 public String action, target;
 @Column(length=1000) public String detail;
 public Instant createdAt;
}
