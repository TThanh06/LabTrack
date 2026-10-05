package vn.labtrack;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTest {
 @Test void upgradeV1PreservesDataAndBackfillsSnapshots() throws Exception {
  String url="jdbc:h2:mem:migration_check;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
   s.execute("INSERT INTO app_users(id,email,display_name,password_hash,role) VALUES(100,'saved@example.test','Saved User','saved-hash','STUDENT')");
   s.execute("INSERT INTO assets(id,code,name,category,location,description,state) VALUES(100,'SAVED-100','Old asset','Laptop','Room','Original','AVAILABLE')");
   s.execute("INSERT INTO loans(id,asset_id,user_id,start_on,due_on,purpose,status,created_at,updated_at) VALUES(100,100,100,'2026-10-05','2026-10-06','Original purpose','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  }
  var flyway=Flyway.configure().dataSource(url,"sa","").load();flyway.migrate();assertEquals(0,flyway.migrate().migrationsExecuted);
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var rows=s.executeQuery("SELECT u.password_hash,u.active,l.asset_name,l.asset_code,l.purpose,l.fee_total,l.collateral_required,l.paid_at,l.collateral_received_at FROM loans l JOIN app_users u ON l.user_id=u.id WHERE l.id=100")){
   assertTrue(rows.next());assertEquals("saved-hash",rows.getString(1));assertTrue(rows.getBoolean(2));assertEquals("Old asset",rows.getString(3));assertEquals("SAVED-100",rows.getString(4));assertEquals("Original purpose",rows.getString(5));assertEquals(0,rows.getLong(6));assertTrue(rows.getBoolean(7));assertNull(rows.getObject(8));assertNull(rows.getObject(9));
  }
 }
}
