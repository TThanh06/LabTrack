package vn.labtrack;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.Set;

@Component
class Security extends OncePerRequestFilter {
 private final Users users;
 @Value("${labtrack.origin}") String devOrigin;
 Security(Users users) { this.users=users; }
 User user(HttpServletRequest request) {
  HttpSession session=request.getSession(false);
  Object id=session==null?null:session.getAttribute("uid");
  if (!(id instanceof Long)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");
  User user=users.findById((Long)id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Phiên đã hết hạn."));
  if(!user.active || !Long.valueOf(user.authVersion).equals(session.getAttribute("authVersion"))) {
   session.invalidate();throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Phiên đã hết hạn hoặc tài khoản đã bị khóa. Vui lòng đăng nhập lại.");
  }
  return user;
 }
 static void staff(User user) {
  if (!"STAFF".equals(user.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ nhân viên được thực hiện thao tác này.");
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  res.setHeader("X-Content-Type-Options","nosniff");
  res.setHeader("X-Frame-Options","DENY");
  res.setHeader("Referrer-Policy","same-origin");
  if(req.getRequestURI().startsWith("/api/")) {
   res.setHeader("Cache-Control","no-store");
   if(!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())) {
    String origin=req.getHeader("Origin");
    String own=req.getScheme()+"://"+req.getServerName()+((req.getServerPort()==80||req.getServerPort()==443)?"":":"+req.getServerPort());
    if(!"1".equals(req.getHeader("X-LabTrack")) || (origin!=null&&!origin.equals(own)&&!origin.equals(devOrigin))) {
     res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
     res.getWriter().write("{\"message\":\"Yêu cầu không hợp lệ. Hãy tải lại trang.\"}"); return;
    }
   }
  }
  chain.doFilter(req,res);
 }
}
