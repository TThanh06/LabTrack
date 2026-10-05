package vn.labtrack;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Component
class Seed implements CommandLineRunner {
 final Users users;final Assets assets;final Environment env;
 Seed(Users u,Assets a,Environment e){users=u;assets=a;env=e;}
 @Override @Transactional public void run(String... args){
  if(users.count()>0)return;
  boolean demo=env.matchesProfiles("demo");
  String staffPassword=demo?"LabTrack@123":env.getProperty("LABTRACK_STAFF_PASSWORD");
  String studentPassword=demo?"Student@123":env.getProperty("LABTRACK_STUDENT_PASSWORD");
  if(staffPassword==null||studentPassword==null||staffPassword.length()<10||studentPassword.length()<10)
   throw new IllegalStateException("Database mới: đặt LABTRACK_STAFF_PASSWORD và LABTRACK_STUDENT_PASSWORD ít nhất 10 ký tự.");
  addUser("staff@labtrack.local","Nguyễn Minh Anh","STAFF",staffPassword);
  addUser("student@labtrack.local","Trần Văn Thanh","STUDENT",studentPassword);
  String[][] list={
   {"CAM-001","Canon EOS 80D","Máy ảnh","Kho Media · A201","Kèm lens 18–135mm, pin, sạc và túi đựng."},
   {"CAM-002","Sony Alpha A6400","Máy ảnh","Kho Media · A201","Kèm lens 16–50mm, 2 pin và thẻ nhớ 64GB."},
   {"LAP-001","Dell Latitude 5420","Laptop","Phòng máy · B302","Core i5, RAM 16GB, SSD 512GB. Kèm bộ sạc."},
   {"LAP-002","Lenovo ThinkPad T14","Laptop","Phòng máy · B302","RAM 16GB, SSD 512GB. Kèm bộ sạc USB-C."},
   {"PRJ-001","Epson EB-X51","Máy chiếu","Kho thiết bị · A101","Kèm HDMI, remote và dây nguồn."},
   {"MIC-001","Rode Wireless GO II","Âm thanh","Kho Media · A201","Bộ thu, 2 bộ phát, cáp kết nối và hộp đựng."},
   {"TAB-001","Wacom Intuos Pro","Bảng vẽ","Phòng thiết kế · B205","Kèm bút cảm ứng, ngòi dự phòng và cáp USB."},
   {"KIT-001","Arduino Starter Kit","Thực hành","Phòng IoT · C102","Board Uno, breadboard, cảm biến và dây nối."}
  };
  for(String[] row:list){Asset a=new Asset();a.code=row[0];a.name=row[1];a.category=row[2];a.location=row[3];a.description=row[4];a.state="AVAILABLE";assets.save(a);}
 }
 void addUser(String email,String name,String role,String password){User u=new User();u.email=email;u.name=name;u.role=role;u.passwordHash=new BCryptPasswordEncoder().encode(password);users.save(u);}
}
