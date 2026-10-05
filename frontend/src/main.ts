import { bootstrapApplication } from '@angular/platform-browser';
import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

interface User {id:number;name:string;email:string;role:'STAFF'|'STUDENT';active:boolean}
interface Asset {id:number;code:string;name:string;category:string;location:string;description:string;state:string;retired:boolean;rentalRate:number}
interface Loan {rentalRate:number;rentalUnits:number;feeTotal:number;collateralRequired:boolean;paidAt:string|null;paidBy:User|null;paymentMethod:string;paymentReference:string;refundedAt:string|null;refundReason:string;collateralName:string|null;collateralIdentifier:string|null;collateralValue:number|null;collateralCondition:string|null;collateralStorage:string|null;collateralReceivedAt:string|null;collateralReturnedAt:string|null;collateralReturnNote:string|null;assetName:string;assetCode:string;id:number;asset:Asset;borrower:User;startOn:string;dueOn:string;status:string;purpose:string;returnNote:string}
interface Issue {id:number;asset:Asset;reporter:User;description:string;status:string;resolution:string}
interface Event {id:number;actor:User;action:string;target:string;detail:string;createdAt:string}
interface Report {from:string;to:string;requests:number;statuses:Record<string,number>;daily:{date:string;count:number}[];topAssets:{code:string;name:string;count:number}[];overdue:Loan[]}
interface Revenue {from:string;to:string;collected:number;refunded:number;net:number;daily:{date:string;collected:number;refunded:number;net:number}[];entries:{loanId:number;assetName:string;borrower:string;type:string;amount:number;at:string;staff:string}[];unpaid:Loan[];heldCollateral:Loan[]}
interface Schedule {startOn:string;dueOn:string;status:string}
function today(){return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());}

@Component({selector:'app-root',standalone:true,imports:[CommonModule,FormsModule,MatButtonModule,MatProgressSpinnerModule,A11yModule],templateUrl:'./app.html'})
class App implements OnInit {
 user=signal<User|null>(null); ready=signal(false); busy=signal(false); demo=signal(false);
 assets=signal<Asset[]>([]); loans=signal<Loan[]>([]); issues=signal<Issue[]>([]); events=signal<Event[]>([]);
 error=signal(''); success=signal(''); tab=signal('assets'); search=signal(''); category=signal('Tất cả');
 email=''; password=''; modal=signal(''); chosen=signal<Asset|null>(null); selectedLoan:Loan|null=null; selectedIssue:Issue|null=null;
 schedule=signal<Schedule[]>([]); scheduleLoading=signal(false); startOn=today();dueOn=today();purpose='';note='';action='';
 editingAssetId:number|null=null; showRetired=signal(false); accounts=signal<User[]>([]); reportData=signal<Report|null>(null); reportFrom=today().slice(0,8)+'01'; reportTo=today();
 selectedUser:User|null=null; accountDraft={email:'',name:'',password:''}; currentPassword='';newPassword='';confirmPassword='';
 rateDraft=0;paymentMethod='CASH';paymentReference='';financialConfirmed=false;financeLoan:Loan|null=null;revenueData=signal<Revenue|null>(null);revenueFrom=today().slice(0,8)+'01';revenueTo=today();collateralDraft={name:'',identifier:'',estimatedValue:1,condition:'',storage:''};
 draft={code:'',name:'',category:'Laptop',location:'',description:''};
 categories=computed(()=>['Tất cả',...new Set(this.assets().map(a=>a.category))]);
 filtered=computed(()=>this.assets().filter(a=>(!a.retired||(this.staff()&&this.showRetired()))&&(this.category()==='Tất cả'||a.category===this.category())&&`${a.name} ${a.code} ${a.location}`.toLocaleLowerCase('vi').includes(this.search().toLocaleLowerCase('vi'))));
 pending=computed(()=>this.loans().filter(l=>l.status==='PENDING').length);
 active=computed(()=>this.loans().filter(l=>l.status==='CHECKED_OUT').length);
 damaged=computed(()=>this.assets().filter(a=>a.state==='MAINTENANCE').length);
 staff=computed(()=>this.user()?.role==='STAFF');
 labels:Record<string,string>={SET_RATE:'Đổi đơn giá mượn',COLLECT_FEE:'Thu phí mượn',REFUND_FEE:'Hoàn phí mượn',RECEIVE_COLLATERAL:'Nhận tài sản bảo đảm',RETURN_COLLATERAL:'Trả tài sản bảo đảm',CANCEL_HANDOVER:'Hủy bàn giao',RETIRED:'Ngừng sử dụng',CREATE_USER:'Tạo tài khoản',ENABLE_USER:'Mở khóa tài khoản',DISABLE_USER:'Khóa tài khoản',CHANGE_PASSWORD:'Đổi mật khẩu',EDIT_ASSET:'Sửa thiết bị',RETIRE_ASSET:'Ngừng sử dụng',RESTORE_ASSET:'Sử dụng lại',AVAILABLE:'Hoạt động',MAINTENANCE:'Bảo trì',PENDING:'Chờ duyệt',APPROVED:'Đã duyệt',CHECKED_OUT:'Đang mượn',RETURNED:'Đã trả',REJECTED:'Từ chối',CANCELLED:'Đã hủy',OPEN:'Chờ xử lý',RESOLVED:'Đã xử lý',CREATE_ASSET:'Thêm thiết bị',REQUEST:'Gửi yêu cầu',APPROVE:'Duyệt mượn',CHECKOUT:'Bàn giao',RETURN:'Nhận trả',REJECT:'Từ chối',CANCEL:'Hủy yêu cầu',REPORT_ISSUE:'Báo hỏng',RESOLVE_ISSUE:'Hoàn tất sửa chữa'};
 label(s:string){return this.labels[s]||s;}
 icon(a:Asset){return ({'Máy ảnh':'◎','Laptop':'▱','Máy chiếu':'◧','Âm thanh':'♬','Bảng vẽ':'▧','Thực hành':'⌘'} as Record<string,string>)[a.category]||'▣';}
 date(s:string){return new Date(s.length===10?s+'T00:00:00':s).toLocaleDateString('vi-VN');}
 overdue(l:Loan){return l.status==='CHECKED_OUT'&&l.dueOn<today();}
 async api<T>(path:string,body?:unknown):Promise<T>{
  const res=await fetch('/api'+path,{method:body===undefined?'GET':'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-LabTrack':'1'},body:body===undefined?undefined:JSON.stringify(body)});
  const data=await res.json();if(!res.ok){if(res.status===401&&path!=='/login')this.user.set(null);throw new Error(data.message||'Không thể thực hiện yêu cầu.');}return data;
 }
 async ngOnInit(){try{this.demo.set((await this.api<{mode:string}>('/health')).mode==='demo');try{this.user.set(await this.api<User>('/me'));}catch{}if(this.user())await this.load();}catch{this.error.set('Chưa kết nối được máy chủ. Hãy kiểm tra cửa sổ chạy LabTrack.');}finally{this.ready.set(true);}}
 async run(task:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');this.success.set('');try{await task();}catch(e){this.error.set(e instanceof Error?e.message:'Có lỗi xảy ra.');}finally{this.busy.set(false);}}
 async load(){const [a,l,i]=await Promise.all([this.api<Asset[]>('/assets'),this.api<Loan[]>('/loans'),this.api<Issue[]>('/issues')]);this.assets.set(a);this.loans.set(l);this.issues.set(i);if(this.staff()){const [events,accounts]=await Promise.all([this.api<Event[]>('/audit'),this.api<User[]>('/users')]);this.events.set(events);this.accounts.set(accounts);}}
 login(){this.run(async()=>{this.user.set(await this.api<User>('/login',{email:this.email,password:this.password}));this.password='';await this.load();});}
 logout(){this.run(async()=>{await this.api('/logout',{});this.user.set(null);this.modal.set('');this.loans.set([]);this.issues.set([]);this.events.set([]);this.accounts.set([]);this.reportData.set(null);this.tab.set('assets');});}
 fill(role:string){this.email=role==='STAFF'?'staff@labtrack.local':'student@labtrack.local';this.password=role==='STAFF'?'LabTrack@123':'Student@123';}
 refresh(){this.run(async()=>{await this.load();if(this.tab()==='reports')await this.loadReport();if(this.tab()==='revenue')await this.loadRevenue();});}
 async openAsset(a:Asset){this.chosen.set(a);this.modal.set('borrow');this.purpose='';this.startOn=this.dueOn=today();this.schedule.set([]);this.error.set('');this.scheduleLoading.set(true);try{this.schedule.set(await this.api<Schedule[]>('/assets/'+a.id+'/schedule'));}catch{this.error.set('Không tải được lịch. Hãy đóng và mở lại thiết bị.');}finally{this.scheduleLoading.set(false);}}
 borrow(){this.run(async()=>{await this.api('/loans',{assetId:this.chosen()!.id,startOn:this.startOn,dueOn:this.dueOn,purpose:this.purpose,expectedFee:this.estimatedFee()});this.modal.set('');await this.load();this.tab.set('loans');this.success.set('Đã gửi yêu cầu. Nhân viên sẽ kiểm tra lịch trước khi duyệt.');});}
 openAction(l:Loan,action:string){this.selectedLoan=l;this.action=action;this.note='';this.error.set('');this.modal.set('action');}
 act(){this.run(async()=>{await this.api('/loans/'+this.selectedLoan!.id+'/action',{action:this.action,note:this.note});this.modal.set('');await this.load();this.success.set('Đã cập nhật phiếu mượn.');});}
 openIssue(a:Asset){this.chosen.set(a);this.note='';this.error.set('');this.modal.set('issue');}
 report(){this.run(async()=>{await this.api('/issues',{assetId:this.chosen()!.id,description:this.note});this.modal.set('');await this.load();this.tab.set('issues');this.success.set('Đã ghi nhận báo hỏng và chuyển thiết bị sang bảo trì.');});}
 openResolve(i:Issue){this.selectedIssue=i;this.note='';this.error.set('');this.modal.set('resolve');}
 resolve(){this.run(async()=>{await this.api('/issues/'+this.selectedIssue!.id+'/resolve',{resolution:this.note});this.modal.set('');await this.load();this.success.set('Đã lưu kết quả sửa chữa.');});}
 openNew(){this.editingAssetId=null;this.draft={code:'',name:'',category:'Laptop',location:'',description:''};this.error.set('');this.modal.set('new');}
 create(){this.run(async()=>{await this.api(this.editingAssetId?'/assets/'+this.editingAssetId+'/edit':'/assets',this.draft);this.modal.set('');await this.load();this.success.set('Đã lưu thông tin thiết bị.');});}

 openEdit(a:Asset){this.editingAssetId=a.id;this.draft={code:a.code,name:a.name,category:a.category,location:a.location,description:a.description};this.error.set('');this.modal.set('new');}
 openRetire(a:Asset){this.chosen.set(a);this.error.set('');this.modal.set('retire');}
 retire(){this.run(async()=>{await this.api('/assets/'+this.chosen()!.id+'/retired',{retired:!this.chosen()!.retired});this.modal.set('');await this.load();this.success.set('Đã cập nhật trạng thái sử dụng. Lịch sử phiếu được giữ nguyên.');});}
 openUser(){this.accountDraft={email:'',name:'',password:''};this.error.set('');this.modal.set('user');}
 createUser(){this.run(async()=>{await this.api('/users',this.accountDraft);this.accountDraft.password='';this.modal.set('');await this.load();this.success.set('Đã tạo tài khoản sinh viên.');});}
 openUserStatus(u:User){this.selectedUser=u;this.error.set('');this.modal.set('userStatus');}
 setUserStatus(){this.run(async()=>{await this.api('/users/'+this.selectedUser!.id+'/active',{active:!this.selectedUser!.active});this.modal.set('');await this.load();this.success.set('Đã cập nhật trạng thái tài khoản.');});}
 openPassword(){this.currentPassword=this.newPassword=this.confirmPassword='';this.error.set('');this.modal.set('password');}
 changePassword(){this.run(async()=>{if(this.newPassword!==this.confirmPassword)throw new Error('Hai lần nhập mật khẩu mới chưa khớp.');await this.api('/me/password',{currentPassword:this.currentPassword,newPassword:this.newPassword});this.currentPassword=this.newPassword=this.confirmPassword='';this.modal.set('');this.user.set(null);this.password='';this.tab.set('assets');this.success.set('Đã đổi mật khẩu. Hãy đăng nhập lại bằng mật khẩu mới.');});}
 async loadReport(){const r=await this.api<Report>('/reports?from='+encodeURIComponent(this.reportFrom)+'&to='+encodeURIComponent(this.reportTo));this.reportData.set(r);}
 showReports(){this.tab.set('reports');this.run(()=>this.loadReport());}
 updateReport(){this.run(()=>this.loadReport());}
 maxDaily(){return Math.max(1,...(this.reportData()?.daily.map(d=>d.count)||[]));}
 title(){return ({assets:'Khám phá kho thiết bị',loans:'Mượn đúng lúc. Trả đúng hẹn.',issues:'Giữ thiết bị luôn sẵn sàng',audit:'Mọi thao tác, một lịch sử',users:'Tài khoản trong trường',reports:'Hiểu nhu cầu sử dụng thiết bị',revenue:'Thu phí rõ ràng. Giữ đồ đầy đủ.'} as Record<string,string>)[this.tab()];}

 money(n:number|null){return new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(n||0);}
 rentalDays(){const d=Math.round((Date.parse(this.dueOn+'T00:00:00Z')-Date.parse(this.startOn+'T00:00:00Z'))/86400000)+1;return Number.isFinite(d)&&d>0?d:0;}
 estimatedFee(){return (this.chosen()?.rentalRate||0)*this.rentalDays();}
 openRate(a:Asset){this.chosen.set(a);this.rateDraft=a.rentalRate;this.error.set('');this.modal.set('rate');}
 saveRate(){this.run(async()=>{await this.api('/assets/'+this.chosen()!.id+'/rate',{dailyRate:this.rateDraft});this.modal.set('');await this.load();this.success.set('Đã đổi đơn giá. Các phiếu cũ giữ nguyên phí đã chốt.');});}
 openFinance(l:Loan,kind:string){this.financeLoan=l;this.note='';this.paymentMethod='CASH';this.paymentReference='';this.financialConfirmed=false;this.collateralDraft={name:'',identifier:'',estimatedValue:1,condition:'',storage:''};this.error.set('');this.modal.set(kind);}
 financeSave(kind:string){this.run(async()=>{if(!this.financialConfirmed)throw new Error('Hãy xác nhận giao nhận thực tế trước khi lưu.');const l=this.financeLoan!;let body:unknown;
 if(kind==='payment')body={amount:l.feeTotal,method:this.paymentMethod,reference:this.paymentReference};
 else if(kind==='refund')body={reason:this.note};
 else if(kind==='collateral')body=this.collateralDraft;
 else body={note:this.note};
 await this.api('/loans/'+l.id+'/'+kind,body);this.modal.set('');await this.load();if(this.tab()==='revenue')await this.loadRevenue();this.success.set('Đã lưu giao dịch và nhật ký thao tác.');});}
 async loadRevenue(){this.revenueData.set(await this.api<Revenue>('/revenue?from='+encodeURIComponent(this.revenueFrom)+'&to='+encodeURIComponent(this.revenueTo)));}
 showRevenue(){this.tab.set('revenue');this.run(()=>this.loadRevenue());}
 updateRevenue(){this.run(()=>this.loadRevenue());}
 maxRevenue(){return Math.max(1,...(this.revenueData()?.daily.flatMap(d=>[d.collected,d.refunded])||[]));}
 close(){if(!this.busy()){this.modal.set('');this.error.set('');this.accountDraft.password='';this.currentPassword=this.newPassword=this.confirmPassword='';}}
}
bootstrapApplication(App).catch(console.error);
